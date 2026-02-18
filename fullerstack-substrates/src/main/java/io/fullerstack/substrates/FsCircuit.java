package io.fullerstack.substrates;

import static java.util.Objects.requireNonNull;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
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


  // ─────────────────────────────────────────────────────────────────────────────
  // Circuit state (read-only after construction)
  // ─────────────────────────────────────────────────────────────────────────────

  private final Subject < Circuit >           subject;
  private final List < Subscriber < State > > subscribers;
  private final Thread                        worker;

  // ─────────────────────────────────────────────────────────────────────────────
  // Queue infrastructure
  // ─────────────────────────────────────────────────────────────────────────────

  private final IngressQueue ingress = new IngressQueue ();
  private final TransitQueue transit = new TransitQueue ();

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

  // Pre-allocated marker receivers — ReceptorReceiver wrapping marker lambdas.
  // Drain loop splits the call site: isMarker() identity check routes markers
  // to fireMarker() (cold, separate type profile), keeping the hot-path
  // r.accept(v) and receptor.receive() fully monomorphic — zero class_check traps.
  private final Consumer < Object > awaitMarkerReceiver;
  private final Consumer < Object > closeMarkerReceiver;

  // ─────────────────────────────────────────────────────────────────────────────
  // ReceptorReceiver - Concrete class for JIT devirtualization/inlining
  // ─────────────────────────────────────────────────────────────────────────────

  /**
   * Concrete Consumer wrapper for receptors.
   * Using a named class instead of a lambda allows the JIT to:
   * 1. Devirtualize the accept() call (lambda has invokedynamic overhead)
   * 2. Inline the receptor.receive() call when the receptor type is known
   */
  static final class ReceptorReceiver < E > implements Consumer < Object > {
    final Receptor < ? super E > receptor;

    ReceptorReceiver ( Receptor < ? super E > receptor ) {
      this.receptor = receptor;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( Object o ) {
      receptor.receive ( (E) o );
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Construction
  // ─────────────────────────────────────────────────────────────────────────────

  public FsCircuit ( Subject < Circuit > subject ) {
    this.subject = subject;
    this.subscribers = new ArrayList <> ();

    // Pre-allocate marker receivers as ReceptorReceiver instances.
    // Drain loop uses isMarker() identity check to route markers to
    // fireMarker() (cold path, separate type profile). Hot-path
    // r.accept(v) → receptor.receive() stays fully monomorphic.
    Receptor < Object > awaitReceptor = this::onAwaitMarker;
    this.awaitMarkerReceiver = new ReceptorReceiver <> ( awaitReceptor );
    Receptor < Object > closeReceptor = this::onCloseMarker;
    this.closeMarkerReceiver = new ReceptorReceiver <> ( closeReceptor );

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
   * Drain all queued cascade emissions. Delegates to TransitQueue.
   */
  @jdk.internal.vm.annotation.ForceInline
  boolean drainTransit () {
    return transit.drain ();
  }

  /**
   * Identity check: is this receiver a marker (await/close)?
   * Splits the call site so the hot path {@code r.accept(v)} only
   * ever sees regular ReceptorReceivers — monomorphic, no class_check traps.
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

  private < E > FsPipe < E > newPipe ( Name name, Consumer < Object > receiver ) {
    return new FsPipe <> ( name, this, (FsSubject < ? >) subject, receiver );
  }

  /** Package-private pipe factory for FsChannel, FsConduit, FsFlow, FsCell */
  < E > FsPipe < E > createPipe ( Name name, FsSubject < ? > parent, Consumer < Object > receiver ) {
    return new FsPipe <> ( name, this, parent, receiver );
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

    // We're the first awaiter - inject marker job (uses pre-allocated ReceptorReceiver)
    marker ( awaitMarkerReceiver );

    // Unpark worker to process marker promptly (cold path)
    LockSupport.unpark ( worker );

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
  // Factory Methods - Cell
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < I, E > Cell < I, E > cell (
    @NotNull Name name,
    @NotNull Composer < E, Pipe < I > > ingress,
    @NotNull Composer < E, Pipe < E > > egress,
    @NotNull Receptor < ? super E > receptor ) {
    requireNonNull ( name );
    requireNonNull ( ingress );
    requireNonNull ( egress );
    requireNonNull ( receptor );
    return new FsCell <> (
      new FsSubject <> ( name, (FsSubject < ? >) subject, Cell.class ), this, ingress, egress, receptor );
  }

  // ===================================================================================
  // Factory Methods - Conduit
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < P extends Percept, E > Conduit < P, E > conduit (
    @NotNull Name name, @NotNull Composer < E, ? extends P > composer ) {
    requireNonNull ( name );
    requireNonNull ( composer );
    return new FsConduit <> ( (FsSubject < ? >) subject, name, channel -> composer.compose ( channel ), this );
  }

  @New
  @NotNull
  @Override
  public < P extends Percept, E > Conduit < P, E > conduit (
    @NotNull Name name,
    @NotNull Composer < E, ? extends P > composer,
    @NotNull Configurer < Flow < E > > configurer ) {
    requireNonNull ( name );
    requireNonNull ( composer );
    requireNonNull ( configurer );
    // Eagerly validate configurer per API contract — exceptions wrapped in Substrates.Exception
    try {
      FsFlow < E > validationFlow = new FsFlow <> ( name, this, null );
      configurer.configure ( validationFlow );
    } catch ( FsException e ) {
      throw e;
    } catch ( RuntimeException e ) {
      throw new FsException ( "Flow configuration failed", e );
    }
    return new FsConduit <> (
      (FsSubject < ? >) subject, name, channel -> composer.compose ( channel ), this, configurer );
  }

  // ===================================================================================
  // Factory Methods - Pipe (without name)
  // ===================================================================================

  /**
   * Extract receiver for same-circuit FsPipe targets to avoid double-queue.
   * For cross-circuit targets, wrap with target.emit() as normal.
   */
  private < E > Consumer < Object > targetReceiver ( Pipe < E > target ) {
    if ( target instanceof FsPipe < E > fsPipe && fsPipe.circuit () == this ) {
      return fsPipe.receiver ();
    }
    return new ReceptorReceiver <> ( target::emit );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Pipe < E > target ) {
    requireNonNull ( target );
    if ( target instanceof FsPipe < E > fsPipe && fsPipe.circuit () == this ) {
      return target;
    }
    return newPipe ( null, targetReceiver ( target ) );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Receptor < ? super E > receptor ) {
    requireNonNull ( receptor );
    return newPipe ( null, new ReceptorReceiver <> ( receptor ) );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Pipe < E > target, @NotNull Configurer < ? super Flow < E > > configurer ) {
    requireNonNull ( target );
    requireNonNull ( configurer );
    Pipe < E > basePipe = newPipe ( null, targetReceiver ( target ) );
    FsFlow < E > flow = new FsFlow <> ( (Name) null, this, basePipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Receptor < ? super E > receptor, @NotNull Configurer < ? super Flow < E > > configurer ) {
    requireNonNull ( receptor );
    requireNonNull ( configurer );
    Pipe < E > basePipe = newPipe ( null, new ReceptorReceiver <> ( receptor ) );
    FsFlow < E > flow = new FsFlow <> ( (Name) null, this, basePipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  // ===================================================================================
  // Factory Methods - Pipe (with name)
  // ===================================================================================

  @New ( conditional = true )
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Name name, @NotNull Pipe < E > target ) {
    requireNonNull ( name );
    requireNonNull ( target );
    if ( target instanceof FsPipe < E > fsPipe && fsPipe.circuit () == this ) {
      return target;
    }
    return newPipe ( name, targetReceiver ( target ) );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe ( @NotNull Name name, @NotNull Receptor < ? super E > receptor ) {
    requireNonNull ( name );
    requireNonNull ( receptor );
    return newPipe ( name, new ReceptorReceiver <> ( receptor ) );
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe (
    @NotNull Name name, @NotNull Pipe < E > target, @NotNull Configurer < ? super Flow < E > > configurer ) {
    requireNonNull ( name );
    requireNonNull ( target );
    requireNonNull ( configurer );
    Pipe < E > basePipe = newPipe ( name, targetReceiver ( target ) );
    FsFlow < E > flow = new FsFlow <> ( name, this, basePipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  @New
  @NotNull
  @Override
  public < E > Pipe < E > pipe (
    @NotNull Name name,
    @NotNull Receptor < ? super E > receptor,
    @NotNull Configurer < ? super Flow < E > > configurer ) {
    requireNonNull ( name );
    requireNonNull ( receptor );
    requireNonNull ( configurer );
    Pipe < E > basePipe = newPipe ( name, new ReceptorReceiver <> ( receptor ) );
    FsFlow < E > flow = new FsFlow <> ( name, this, basePipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }

  // ===================================================================================
  // Factory Methods - Subscriber
  // ===================================================================================

  @New
  @NotNull
  @Override
  public < E > Subscriber < E > subscriber (
    @NotNull Name name, @NotNull BiConsumer < Subject < Channel < E > >, Registrar < E > > callback ) {
    requireNonNull ( name );
    requireNonNull ( callback );
    return new FsSubscriber <> (
      new FsSubject <> ( name, (FsSubject < ? >) subject, Subscriber.class ), callback );
  }

  // ===================================================================================
  // Factory Methods - Subscribe & Reservoir
  // ===================================================================================

  @New
  @NotNull
  @Queued
  @Override
  @SuppressWarnings ( "unchecked" )
  public Subscription subscribe ( @NotNull Subscriber < State > subscriber ) {
    requireNonNull ( subscriber );
    if ( subscriber.subject () instanceof FsSubject < ? > subSubject ) {
      FsSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
      if ( subscriberCircuit != null && subscriberCircuit != subject ) {
        throw new FsException ( "Subscriber belongs to a different circuit" );
      }
    }
    subscribers.add ( subscriber );
    return new FsSubscription (
      subject.name (), (FsSubject < ? >) subject, () -> subscribers.remove ( subscriber ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Reservoir < State > reservoir () {
    return new FsReservoir <> ( (Subject < Reservoir < State > >) (Subject < ? >) subject );
  }
}
