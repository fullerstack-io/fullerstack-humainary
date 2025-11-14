package io.fullerstack.substrates.circuit;

import io.humainary.substrates.api.Substrates.*;
import static io.humainary.substrates.api.Substrates.cortex;
import io.fullerstack.substrates.cell.CellNode;
import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.conduit.RoutingConduit;
import io.fullerstack.substrates.id.SequentialIdentifier;
import io.fullerstack.substrates.id.UuidIdentifier;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscription.CallbackSubscription;
import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.valve.Valve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sequential implementation of Substrates.Circuit using the Virtual CPU Core pattern.
 * <p>
 * < p >This implementation processes all emissions sequentially through a FIFO queue with a single virtual thread,
 * ensuring ordered execution and eliminating the need for locks within the Circuit domain.
 * <p>
 * < p >< b >Virtual CPU Core Pattern (Valve Architecture):</b >
 * < ul >
 * < li >Single {@link Valve} processes all emissions sequentially (FIFO ordering)</li >
 * < li >Valve = BlockingQueue + Virtual Thread processor</li >
 * < li >Emissions → Tasks (submitted to valve)</li >
 * < li >All Conduits share the same valve (isolation per Circuit)</li >
 * < li >Guarantees ordering, eliminates locks, prevents race conditions</li >
 * </ul >
 * <p>
 * < p >< b >Component Management:</b >
 * < ul >
 * < li >Conduit creation via direct construction (no caching - matches reference implementation)</li >
 * < li >Cell creation with hierarchical structure</li >
 * < li >State subscriber management (Circuit IS-A Source&lt;State&gt;)</li >
 * </ul >
 * <p>
 * < p >< b >Thread Safety:</b >
 * Sequential execution within Circuit domain eliminates need for synchronization.
 * External callers can emit from any thread - emissions are posted to queue and processed serially.
 *
 * @see Circuit
 * @see Scheduler
 */
public class SequentialCircuit implements Circuit, Scheduler {
  private final Subject circuitSubject;

  // Lazy-initialized Valve (only created when first Conduit emits)
  // Most circuits in tests/benchmarks never emit, so this saves ~100-200ns
  private volatile Valve valve;

  private final String valveName;  // Store name for lazy init

  private volatile boolean closed = false;

  // Direct subscriber management for State (Circuit IS-A Source< State >)
  // Pre-sized to 4 (typical: 0-2 subscribers, rare: >4)
  private final List < Subscriber < State > > stateSubscribers = new ArrayList <> ( 4 );


  /**
   * Creates a single-threaded circuit with the specified name.
   * <p>
   * < p >Initializes:
   * < ul >
   * < li >Subject with sequential ID (avoids UUID generation overhead)</li >
   * < li >Valve created lazily on first schedule() call (saves ~100-200ns for unused circuits)</li >
   * </ul >
   *
   * @param name circuit name (hierarchical, e.g., "account.region.cluster")
   */
  public SequentialCircuit ( Name name ) {
    Objects.requireNonNull ( name, "Circuit name cannot be null" );
    Id id = SequentialIdentifier.generate ();
    this.circuitSubject = new ContextualSubject <> (
      id,
      name,
      LinkedState.empty (),
      Circuit.class
    );

    // Store name for lazy valve creation (saves ~100-200ns if circuit never emits)
    this.valveName = "circuit-" + name.part ();
    this.valve = null;  // Lazy-initialized on first schedule()
  }

  @Override
  public Subject subject () {
    return circuitSubject;
  }

  @Override
  public Subscription subscribe ( Subscriber < State > subscriber ) {
    Objects.requireNonNull ( subscriber, "Subscriber cannot be null" );
    stateSubscribers.add ( subscriber );
    return new CallbackSubscription ( () -> stateSubscribers.remove ( subscriber ), circuitSubject );
  }

  // Circuit.await() - public API
  @Override
  public void await () {
    // If valve was never created (no emissions), nothing to await
    if ( valve == null ) {
      return;  // Fast path: no valve = no pending tasks
    }

    // Wait for valve to drain all queued emissions
    // Fast path: If valve is already closed (running=false), returns immediately
    valve.await ( "Circuit" );

    // Lazy shutdown: First await() after close() performs actual valve shutdown
    // This ensures pending emissions submitted before close() are fully processed
    // Subsequent await() calls use fast path (valve.await exits immediately when closed)
    if ( closed ) {
      valve.close ();  // Idempotent - safe to call multiple times
    }
  }

  // Scheduler.schedule() - internal API for components
  @Override
  public void schedule ( Runnable task ) {
    // Silently ignore emissions after circuit is closed (TCK requirement)
    if ( closed ) {
      return;
    }
    if ( task != null ) {
      ensureValveCreated();  // Lazy-init valve on first emission
      valve.submit ( task );  // Submit to valve
    }
  }

  /**
   * Lazy-initializes the Valve on first emission.
   * Thread-safe via double-checked locking.
   * Saves ~100-200ns for circuits that never emit (common in tests/benchmarks).
   */
  private void ensureValveCreated() {
    if ( valve == null ) {
      synchronized ( this ) {
        if ( valve == null ) {
          valve = new Valve ( valveName );
        }
      }
    }
  }

  // ========== Cell API (PREVIEW) ==========

  @Override
  public < I, E > Cell < I, E > cell (
    Composer < E, Pipe < I > > ingress,
    Composer < E, Pipe < E > > egress,
    Pipe < ? super E > pipe ) {
    // Use circuit's subject name per API contract
    // "Multiple calls to this method will create cells with the same name (the circuit's name)"
    return cell ( circuitSubject.name (), ingress, egress, pipe );
  }

  @Override
  public < I, E > Cell < I, E > cell (
    Name name,
    Composer < E, Pipe < I > > ingress,
    Composer < E, Pipe < E > > egress,
    Pipe < ? super E > pipe ) {
    Objects.requireNonNull ( name, "Cell name cannot be null" );
    Objects.requireNonNull ( ingress, "Ingress composer cannot be null" );
    Objects.requireNonNull ( egress, "Egress composer cannot be null" );
    Objects.requireNonNull ( pipe, "Output pipe cannot be null" );

    // PREVIEW Cell Pattern (per API contract):
    //
    // Composers signature: Composer<E, Pipe<X>> transforms Channel<E> → Pipe<X>
    // - Ingress: Channel<E> → Pipe<I> (creates input pipe for cell's input type I)
    // - Egress: Channel<E> → Pipe<E> (transforms output before upward routing)
    //
    // Flow: cell.emit(I) → ingressPipe → egressPipe → channel → outlet pipe
    //
    // Implementation strategy:
    // - Ingress receives a "fake" channel that routes to egressPipe (see ingressChannel below)
    // - This ensures: cell input flows through ingress → egress → channel → outlet
    // - Egress wraps channel.pipe() and transforms before upward emission
    // - Channel emissions are subscribed to the outlet pipe (see cellConduit.subscribe below)
    //
    // Stack safety: Upward flow uses async dispatch through circuit valve (Subscription),
    // enabling arbitrarily deep hierarchies without stack overflow.

    // Create a conduit for subscription infrastructure
    Conduit < Pipe < E >, E > cellConduit = conduit ( name, Composer.pipe () );

    // Create an internal channel
    @SuppressWarnings ( "unchecked" )
    RoutingConduit < Pipe < E >, E > transformingConduit = (RoutingConduit < Pipe < E >, E >) cellConduit;
    Channel < E > channel = new EmissionChannel <> ( name, transformingConduit, null );

    // Apply egress composer: creates a pipe that wraps channel.pipe()
    // Egress transforms before emitting to channel
    Pipe < E > egressPipe = egress.compose ( channel );

    // Apply ingress composer but pass a FAKE channel that routes to egressPipe
    // This way ingress will emit to egress instead of directly to the channel
    Channel < E > ingressChannel = new Channel < E > () {
      @Override
      public Subject < Channel < E > > subject () {
        return channel.subject ();
      }

      @Override
      public Pipe < E > pipe () {
        // Return egressPipe instead of channel.pipe()!
        // This routes ingress output through egress
        return egressPipe;
      }

      @Override
      public Pipe < E > pipe ( Consumer < Flow < E > > configurer ) {
        // Not used in tests, but for completeness
        return SequentialCircuit.this.pipe ( egressPipe, configurer );
      }
    };

    // Now apply ingress composer with the fake channel
    Pipe < I > ingressPipe = ingress.compose ( ingressChannel );

    // Subscribe outlet to channel emissions
    cellConduit.subscribe ( cortex().subscriber (
      cortex().name ( "cell-outlet-" + name ),
      ( Subject < Channel < E > > subject, Registrar < E > registrar ) -> {
        registrar.register ( pipe::emit );
      }
    ) );

    // Cast conduit for CellNode
    @SuppressWarnings ( "unchecked" )
    Conduit < ?, E > conduit = (Conduit < ?, E >) cellConduit;

    // Create CellNode
    return new CellNode < I, E > (
      null,           // No parent (this is root)
      name,
      ingressPipe,    // Input: created by ingress composer
      egressPipe,     // Output: created by egress composer (not used directly by CellNode)
      conduit,
      ingress,        // Pass ingress composer for child creation
      egress,         // Pass egress composer for child creation
      circuitSubject
    );
  }

  @Override
  public < E > Pipe < E > pipe ( Pipe < ? super E > target ) {
    Objects.requireNonNull ( target, "Target pipe cannot be null" );

    // Creates async pipe wrapper that breaks synchronous call chains (per API contract):
    // 1. Caller thread: wrappedPipe.emit(value) → enqueues to valve → returns immediately
    // 2. Circuit thread: valve dequeues → target.emit(value) executes sequentially
    //
    // Benefits:
    // - Prevents stack overflow in deep pipe chains
    // - Enables cyclic pipe connections (feedback loops, recurrent networks)
    // - Target pipe executes on circuit thread (no synchronization needed)
    // - Sequential execution with deterministic ordering (FIFO queue)

    return new Pipe < E > () {
      @Override
      public void emit ( E value ) {
        schedule ( () -> target.emit ( value ) );  // Async dispatch via valve
      }

      @Override
      public void flush () {
        schedule ( target::flush );  // Async flush
      }
    };
  }

  @Override
  public < E > Pipe < E > pipe ( Pipe < ? super E > target, Consumer < Flow < E > > configurer ) {
    Objects.requireNonNull ( target, "Target pipe cannot be null" );
    Objects.requireNonNull ( configurer, "Flow configurer cannot be null" );

    // Create Flow regulator and configure it
    io.fullerstack.substrates.flow.FlowRegulator < E > flow = new io.fullerstack.substrates.flow.FlowRegulator <> ();
    configurer.accept ( flow );

    // Per API contract: Flow operations execute on circuit's worker thread
    // "Flow operations execute on the circuit's worker thread after emissions are dequeued"
    //
    // Threading model:
    // 1. Caller thread: emit(value) → enqueue to valve → return immediately (non-blocking)
    // 2. Circuit thread: dequeue → flow.apply(value) → target.emit(transformed)
    //
    // This enables stateful Flow operators to use mutable state without synchronization,
    // since all Flow operations execute sequentially on the single circuit thread.

    return new Pipe < E > () {
      @Override
      public void emit ( E value ) {
        // Enqueue the Flow application to circuit thread
        schedule ( () -> {
          // Flow transformation executes ON CIRCUIT THREAD (single-threaded guarantees)
          E transformed = flow.apply ( value );
          // If not filtered (null), emit to target (still on circuit thread)
          if ( transformed != null ) {
            target.emit ( transformed );
          }
        } );
      }

      @Override
      public void flush () {
        schedule ( target::flush );
      }
    };
  }

  @Override
  public < P extends Percept, E > Conduit < P, E > conduit ( Composer < E, ? extends P > composer ) {
    // Per API contract: Use circuit's subject name (not unique UUID)
    // "Convenience method that uses this circuit's subject name for the conduit"
    // Multiple calls create conduits with the same name (the circuit's name)
    checkClosed ();
    Objects.requireNonNull ( composer, "Composer cannot be null" );

    // Use circuit's name per API contract
    return new RoutingConduit <> ( circuitSubject.name (), composer, this );
  }

  @Override
  public < P extends Percept, E > Conduit < P, E > conduit ( Name name, Composer < E, ? extends P > composer ) {
    checkClosed ();
    Objects.requireNonNull ( name, "Conduit name cannot be null" );
    Objects.requireNonNull ( composer, "Composer cannot be null" );

    // Direct construction - no caching (matches William's 21ns reference implementation)
    // Use simple name - hierarchy is implicit through parent Subject references
    return new RoutingConduit <> (
      name, composer, this  // Pass Circuit as parent
    );
  }

  @Override
  public < P extends Percept, E > Conduit < P, E > conduit ( Name name, Composer < E, ? extends P > composer, Consumer < Flow < E > > configurer ) {
    checkClosed ();
    Objects.requireNonNull ( name, "Conduit name cannot be null" );
    Objects.requireNonNull ( composer, "Composer cannot be null" );
    Objects.requireNonNull ( configurer, "Flow configurer cannot be null" );

    // Direct construction with flow configurer - no caching
    // Use simple name - hierarchy is implicit through parent Subject references
    return new RoutingConduit < P, E > (
      name, composer, this, configurer
    );
  }

  @Override
  public void close () {
    if ( !closed ) {
      closed = true;

      // Lazy shutdown pattern (per API contract):
      // - close() is non-blocking - just marks circuit as closed
      // - First await() after close() performs actual valve shutdown
      // - This allows pending emissions submitted before close() to drain
      // - schedule() rejects new emissions after close() (line 119)
      //
      // Pattern: circuit.await() → circuit.close() → circuit.await() (fast path)
    }
  }

  private void checkClosed () {
    if ( closed ) {
      throw new IllegalStateException ( "Circuit is closed" );
    }
  }
}
