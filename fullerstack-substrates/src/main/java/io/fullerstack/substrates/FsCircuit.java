package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static java.util.Objects.requireNonNull;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Routing;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Tap;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Single-digit nanosecond latency Substrates Circuit implementation.
 *
 * <p>Achieves low emission latency through:
 * <ul>
 *   <li>Intrusive MPSC linked list for ingress (external threads)
 *   <li>Pre-allocated ring buffer for transit (cascade emissions)
 *   <li>VarHandle release/acquire semantics for thread coordination
 *   <li>Thread identity routing (worker vs external)
 *   <li>Self-waking timed park — producers never wake the worker
 * </ul>
 *
 * <p><b>Dual-queue architecture:</b>
 * <ul>
 *   <li><b>Ingress:</b> MPSC linked list for external threads (wait-free)
 *   <li><b>Transit:</b> Ring buffer for cascade emissions (single-threaded, zero allocation)
 * </ul>
 */
@Provided
public final class FsCircuit implements Circuit {

  private static final VarHandle AWAITER;

  static {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup ();
      AWAITER = l.findVarHandle ( FsCircuit.class, "awaiterThread", Thread.class );
    } catch ( Exception e ) {
      throw new ExceptionInInitializerError ( e );
    }
  }

  // Spin count before parking (~100μs with Thread.onSpinWait)
  // Based on benchmarking: hot spin provides no benefit, cool spin of 1000 is optimal
  private static final int SPIN_COUNT = 1000;

  // Timed park interval when no work after spin phase.
  // Worker self-wakes — producers never need to unpark.
  // 1μs: on virtual threads this is just an FJP reschedule, no carrier blocking.
  private static final long PARK_NANOS = 1_000L;

  // Spin iterations the awaiter performs before falling back to park().
  // The marker fires within ~2µs in the common case (worker self-wakes every
  // PARK_NANOS, drains, fires marker). Spinning catches the signal without
  // paying the ~5-15µs virtual-thread park/unpark round-trip.
  //
  // Tuned via sweep — 500 falls below the cliff (shallow cyclic_emit_await
  // regresses 10ns/op), 1000 catches the marker, 5000+ wastes ~3ns/op on
  // deep-cascade awaits (140µs cascades the spin can never catch). For real
  // awaits (rare — tests, shutdown, bridges), the spin cost is negligible.
  private static final int AWAIT_SPIN_COUNT = 1_000;


  // ─────────────────────────────────────────────────────────────────────────────
  // Circuit state (read-only after construction)
  // ─────────────────────────────────────────────────────────────────────────────

  private final Subject < Circuit > subject;
  private final Thread              worker;

  /// Internal conduit for State emissions — lazy, created on first Source<State> operation.
  /// Backs circuit.subscribe(State), circuit.tap(), and circuit.reservoir().
  private volatile FsConduit < State > stateConduit;

  // ─────────────────────────────────────────────────────────────────────────────
  // Queue infrastructure
  // ─────────────────────────────────────────────────────────────────────────────

  private final IngressQueue     ingress = new IngressQueue ();
  private final TransitQueueRing transit = new TransitQueueRing ();

  // ─────────────────────────────────────────────────────────────────────────────
  // Synchronization state
  // ─────────────────────────────────────────────────────────────────────────────

  // awaiterThread only modified on await calls
  @SuppressWarnings ( "unused" ) // accessed via VarHandle
  private volatile Thread awaiterThread;

  // Set by close marker (runs on circuit thread) to signal worker exit.
  // Plain field - only accessed from circuit thread.
  private boolean shouldExit;

  // Set when close() is called to reject new emissions
  volatile boolean closed;

  // Observability — incremented from FsChannel.rebuild (worker thread).
  volatile long rebuildCount;

  // Pre-allocated marker receivers — ReceptorAdapter wrapping marker lambdas.
  // Drain loop splits the call site: isMarker() identity check routes markers
  // to fireMarker() (cold, separate type profile), keeping the hot-path
  // r.accept(v) and receptor.receive() fully monomorphic — zero class_check traps.
  private final Consumer < Object > awaitMarkerReceiver;
  private final Consumer < Object > closeMarkerReceiver;

  // ─────────────────────────────────────────────────────────────────────────────
  // ReceptorAdapter — wraps a user Receptor in a Consumer<Object>.
  //
  // Hot path: ingress/transit drain → r.accept(v) → receptor.receive(v).
  // Used ONLY for user receptors (circuit.pipe(receptor) and friends).
  // Markers and circuit jobs use distinct concrete classes below so the
  // bci=5 receptor.receive call site here stays monomorphic per circuit.
  // ─────────────────────────────────────────────────────────────────────────────

  @SuppressWarnings ( "unchecked" )
  static final class ReceptorAdapter < E > implements Consumer < Object >, Receptor < E > {
    final Receptor < ? super E > receptor;

    ReceptorAdapter ( Receptor < ? super E > receptor ) {
      this.receptor = receptor;
    }

    @Override
    public void accept ( Object o ) {
      receptor.receive ( (E) o );
    }

    @Override
    public void receive ( E emission ) {
      receptor.receive ( emission );
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Marker classes — DO NOT COLLAPSE.
  //
  // AwaitMarker, CloseMarker, CircuitJob, and ReceptorAdapter (above) are
  // deliberately separate concrete classes, each with a distinct .accept()
  // body. Each call site in the dispatch chain only ever sees ONE of these
  // class types, which keeps every site monomorphic in the JIT type profile:
  //
  //   ReceptorAdapter.accept → receptor.receive   (user emissions, hot path)
  //   AwaitMarker.accept     → circuit.onAwaitMarker
  //   CloseMarker.accept     → circuit.onCloseMarker
  //   CircuitJob.accept      → action.run         (subscribe / unsubscribe etc.)
  //
  // Collapsing any of these into a shared base reintroduces a bimorphic (or
  // worse) call-site profile on r.accept(v) in the drain loop. HotSpot then
  // emits a class_check trap that deoptimizes the inlined receptor chain,
  // regressing PipeOps.async_emit_batch_await from ~22 ns to ~30+ ns.
  //
  // Canary benchmark: io.humainary.substrates.jmh.PipeOps.async_emit_batch_await
  // Structural test:  FsCircuitMarkerInvariantTest
  // ─────────────────────────────────────────────────────────────────────────────

  /// Await marker — delivered through the ingress queue and routed to
  /// {@link FsCircuit#onAwaitMarker(Object)} via isMarker() identity check.
  static final class AwaitMarker implements Consumer < Object > {
    final FsCircuit circuit;
    AwaitMarker ( FsCircuit circuit ) { this.circuit = circuit; }
    @Override public void accept ( Object o ) { circuit.onAwaitMarker ( o ); }
  }

  /// Close marker — delivered through the ingress queue and routed to
  /// {@link FsCircuit#onCloseMarker(Object)} via isMarker() identity check.
  static final class CloseMarker implements Consumer < Object > {
    final FsCircuit circuit;
    CloseMarker ( FsCircuit circuit ) { this.circuit = circuit; }
    @Override public void accept ( Object o ) { circuit.onCloseMarker ( o ); }
  }

  /// Generic one-shot circuit job — wraps a Runnable for ingress dispatch.
  /// Used by FsConduit subscribe/unsubscribe and similar circuit-thread-only work.
  /// Distinct compiled body keeps the action.run() call site separate from
  /// ReceptorAdapter.accept's receptor.receive() call site.
  static final class CircuitJob implements Consumer < Object > {
    final Runnable action;
    CircuitJob ( Runnable action ) { this.action = action; }
    @Override public void accept ( Object o ) { action.run (); }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Construction
  // ─────────────────────────────────────────────────────────────────────────────

  public FsCircuit ( Subject < Circuit > subject ) {
    this.subject = subject;

    // Pre-allocate marker receivers as concrete classes — distinct types
    // from ReceptorAdapter so the hot-path r.accept(v) → receptor.receive()
    // call site stays monomorphic when only user receptors flow through it.
    this.awaitMarkerReceiver = new AwaitMarker ( this );
    this.closeMarkerReceiver = new CloseMarker ( this );

    // Create and start worker thread
    this.worker = Thread.ofVirtual ()
      .name ( "circuit-" + subject.name () )
      .start ( this::workerLoop );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Emission API (called by FsPipe)
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * Submit emission from external thread to ingress queue.
   * Fire-and-forget: just enqueue, no worker wake-up.
   * Worker self-wakes via timed park — producers never pay unpark cost.
   */
  @jdk.internal.vm.annotation.ForceInline
  final void submitIngress ( Consumer < Object > receiver, Object value ) {
    ingress.enqueue ( receiver, value );
  }

  /**
   * Submit cascade emission from worker thread to transit queue.
   * Single-threaded — uses shared QChunk buffer for zero-allocation transit.
   */
  @jdk.internal.vm.annotation.ForceInline
  final void submitTransit ( Consumer < Object > receiver, Object value ) {
    transit.enqueue ( receiver, value );
  }

  /**
   * Returns true if transit queue has pending cascade work.
   * Plain field read — single-threaded, no volatile needed.
   */
  @jdk.internal.vm.annotation.ForceInline
  boolean transitHasWork () {
    return transit.hasWork ();
  }

  /**
   * Drain all queued cascade emissions. Delegates to TransitQueueRing.
   */
  @jdk.internal.vm.annotation.ForceInline
  boolean drainTransit () {
    return transit.drain ();
  }

  /**
   * Identity check: is this receiver a marker (await/close)?
   * Splits the call site so the hot path {@code r.accept(v)} only
   * ever sees regular ReceptorAdapters — monomorphic, no class_check traps.
   */
  @jdk.internal.vm.annotation.ForceInline
  boolean isMarker ( Consumer < Object > r ) {
    return r == awaitMarkerReceiver || r == closeMarkerReceiver;
  }

  /**
   * Fire a marker receiver. NOT inlined — cold path with its own
   * type profile so marker receptor types don't pollute the hot path.
   */
  void fireMarker ( Consumer < Object > marker, Object value ) {
    marker.accept ( value );
  }

  /**
   * Returns the worker thread for thread identity checks.
   */
  final Thread worker () {
    return worker;
  }

  /**
   * Worker-thread callback from {@link FsChannel#rebuild()} so the circuit
   * can count subscriber-graph rebuilds for {@link #stats()}.
   */
  void recordRebuild () {
    rebuildCount++;
  }

  /**
   * <b>Fullerstack-internal diagnostic.</b> Not part of any stability contract.
   * Returns a snapshot of internal queue and dispatch counters for use by our
   * own test harness, JMH benchmarks, and tuning work — not as a public
   * observability API. See {@link CircuitStats}.
   */
  public CircuitStats stats () {
    return new CircuitStats (
      ingress.drainBatchCount,
      transit.drainCount,
      transit.enqueueCount,
      transit.entriesProcessed,
      transit.growCount,
      transit.currentSize (),
      transit.currentCapacity (),
      rebuildCount
    );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Worker Loop - FIFO depth-first processing with spin-before-park
  // ─────────────────────────────────────────────────────────────────────────────

  private void workerLoop () {
    // Hoist final field to local — guarantees register allocation.
    final IngressQueue q = ingress;

    for ( ; ; ) {

      // Drain up to 64 ingress slots with depth-first cascade interleaving.
      boolean didWork = q.drainBatch ( this );

      if ( didWork ) continue;

      // shouldExit is set by close marker (runs as ingress job on this thread).
      // Check here after drain confirms empty — no work left, safe to exit.
      if ( shouldExit ) return;

      // No work available - spin before parking
      Object found = null;

      for ( int i = 0; i < SPIN_COUNT && found == null; i++ ) {
        Thread.onSpinWait ();
        found = q.peek ();
      }

      if ( found == null ) {
        // Self-waking park — no producer wake-up needed.
        // Worker resumes after PARK_NANOS or explicit unpark (await/close).
        LockSupport.parkNanos ( PARK_NANOS );
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Pipe Factory Helper
  // ─────────────────────────────────────────────────────────────────────────────

  /// Creates a pipe wrapping a receptor adapter.
  private < E > FsPipe < E > newPipe ( Receptor < ? super E > receptor ) {
    return new FsPipe <> ( new ReceptorAdapter <> ( receptor ), this );
  }


  // ─────────────────────────────────────────────────────────────────────────────
  // Lifecycle
  // ─────────────────────────────────────────────────────────────────────────────

  @Override
  public Subject < Circuit > subject () {
    return subject;
  }

  @Override
  public void await () {
    // Don't allow await from circuit thread (would deadlock)
    if ( Thread.currentThread () == worker ) {
      throw new IllegalStateException ( "Cannot call Circuit::await from within a circuit's thread" );
    }
    if ( closed ) {
      awaitClosed ();
      return;
    }
    awaitImpl ();
  }

  private void awaitClosed () {
    try {
      worker.join ();
    } catch ( InterruptedException e ) {
      Thread.currentThread ().interrupt ();
    }
  }

  /**
   * Lightweight await using direct park/unpark.
   * First caller injects marker and parks.
   * Subsequent callers piggyback - park until first caller's marker completes.
   */
  private void awaitImpl () {
    Thread current = Thread.currentThread ();

    // Try to register as the awaiter (CAS null → current)
    Thread existing = (Thread) AWAITER.compareAndExchange ( this, null, current );
    if ( existing != null ) {
      // Another thread is awaiting - piggyback: spin-wait until their marker completes.
      // The marker fires in microseconds; spin is cheaper than a timed park here.
      while ( AWAITER.getOpaque ( this ) == existing ) {
        Thread.onSpinWait ();
      }
      return;
    }

    // We're the first awaiter - inject marker job (uses pre-allocated ReceptorAdapter)
    marker ( awaitMarkerReceiver );

    // Unpark worker to process marker promptly (cold path)
    LockSupport.unpark ( worker );

    // Spin briefly before parking. Marker fires in single-digit µs in the
    // common case, faster than a park/unpark round-trip on virtual threads.
    for ( int i = 0; i < AWAIT_SPIN_COUNT; i++ ) {
      if ( AWAITER.getOpaque ( this ) != current ) return;
      Thread.onSpinWait ();
    }

    // Park until marker wakes us (spurious wakeup safe via loop)
    while ( AWAITER.getOpaque ( this ) == current ) {
      LockSupport.park ();
    }
  }

  /**
   * Marker callback - unpark the awaiting thread.
   */
  private void onAwaitMarker ( Object ignored ) {
    Thread awaiter = (Thread) AWAITER.getAndSet ( this, null );
    if ( awaiter != null ) {
      LockSupport.unpark ( awaiter );
    }
  }

  /**
   * Inject a marker job into the ingress queue.
   */
  private void marker ( Consumer < Object > callback ) {
    if ( closed ) return;
    submitIngress ( callback, null );
  }

  /**
   * Close marker callback - signals worker to exit.
   */
  private void onCloseMarker ( Object ignored ) {
    shouldExit = true;
  }

  @Queued
  @Idempotent
  @Override
  public void close () {
    if ( closed ) return;


    // Inject close marker FIRST (while still accepting emissions)
    // This ensures all emissions before close() are processed
    submitIngress ( closeMarkerReceiver, null );

    // NOW reject new emissions
    closed = true;

    // Always wake worker on close (may be parked)
    LockSupport.unpark ( worker );

    // Release any pending awaiters
    Thread awaiter = (Thread) AWAITER.getAndSet ( this, null );
    if ( awaiter != null ) LockSupport.unpark ( awaiter );
  }


  // ===================================================================================
  // Factory Methods - Conduit (2.0: conduit(Name, Class<E>) — no Composer/Configurer)
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < E > Conduit < E > conduit () {
    // 2.3: no-arg conduit uses the circuit's own subject name.
    return new FsConduit <> ( (FsSubject < ? >) subject, subject.name (), this );
  }

  @New
  @NotNull
  @Override
  public < E > Conduit < E > conduit ( @NotNull Name name, @NotNull Class < E > type ) {
    requireNonNull ( name );
    requireNonNull ( type );
    return new FsConduit <> ( (FsSubject < ? >) subject, name, this );
  }

  @New
  @NotNull
  @Override
  public < E > Conduit < E > conduit ( @NotNull Name name, @NotNull Class < E > type,
                                       @NotNull Routing routing ) {
    requireNonNull ( name );
    requireNonNull ( type );
    requireNonNull ( routing );
    return new FsConduit <> ( (FsSubject < ? >) subject, name, this, routing );
  }

  // ===================================================================================
  // Factory Methods - Pipe
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe () {
    // 2.3: no-arg pipe — queues emissions and discards them on the circuit thread.
    // Equivalent to circuit.pipe(Receptor.NOOP) but without the wrapper.
    return newPipe ( v -> { /* no-op */ } );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Pipe < E > target ) {
    requireNonNull ( target );
    // Same-circuit pipe already routes through this circuit — return as-is
    if ( target instanceof FsPipe < E > fsPipe && fsPipe.circuit () == this ) {
      return target;
    }
    // Cross-circuit: wrap in a receptor pipe that calls target.emit()
    return newPipe ( target::emit );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Receptor < ? super E > receptor ) {
    requireNonNull ( receptor );
    return newPipe ( receptor );
  }

  // ===================================================================================
  // Source<State, Circuit> — delegates to internal State conduit
  // ===================================================================================

  private FsConduit < State > stateConduit () {
    FsConduit < State > c = stateConduit;
    if ( c == null ) {
      synchronized ( this ) {
        c = stateConduit;
        if ( c == null ) {
          c = new FsConduit <> ( (FsSubject < ? >) subject,
            cortex ().name ( "circuit.state" ), this );
          stateConduit = c;
        }
      }
    }
    return c;
  }

  @New
  @NotNull
  @Override
  public < T > Tap < T > tap ( @NotNull java.util.function.Function < Pipe < T >, Pipe < State > > fn ) {
    requireNonNull ( fn );
    return stateConduit ().tap ( fn );
  }

  @New
  @NotNull
  @Override
  public < T > Tap < T > tap ( @NotNull Flow < State, T > flow ) {
    requireNonNull ( flow );
    return stateConduit ().tap ( flow );
  }

  @New
  @NotNull
  @Override
  public Tap < State > tap ( @NotNull Fiber < State > fiber ) {
    requireNonNull ( fiber );
    return stateConduit ().tap ( fiber );
  }

  @New
  @NotNull
  @Override
  public Subscription subscribe ( @NotNull Subscriber < State > subscriber,
                                  @NotNull @Queued java.util.function.Consumer < ? super Subscription > onClose ) {
    requireNonNull ( subscriber );
    requireNonNull ( onClose );
    return stateConduit ().subscribe ( subscriber, onClose );
  }

  @New
  @NotNull
  @Override
  public Reservoir < State > reservoir () {
    return stateConduit ().reservoir ();
  }

  // ===================================================================================
  // Factory Methods - Subscriber (2.0: Subject<Pipe<E>> instead of Subject<Channel<E>>)
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < E > Subscriber < E > subscriber (
    @NotNull Name name, @NotNull @Queued BiConsumer < ? super Subject < Pipe < E > >, ? super Registrar < E > > callback ) {
    requireNonNull ( name );
    requireNonNull ( callback );
    return new FsSubscriber <> (
      new FsSubject <> ( name, (FsSubject < ? >) subject, Subscriber.class ), callback );
  }

  // 1-arg `subscribe(Subscriber<State>)` is inherited from Source's default impl,
  // which delegates to the 2-arg subscribe. We don't override it — the previous
  // override added to a `subscribers` list that nothing read, dropping callbacks.

}
