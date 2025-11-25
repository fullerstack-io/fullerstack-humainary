package io.fullerstack.substrates.conduit;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscriber.ContextSubscriber;
import io.fullerstack.substrates.subscription.CallbackSubscription;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Routing implementation of Substrates.Conduit interface.
 * <p>
 * < p >< b >Type System Foundation:</b >
 * Conduit&lt;P, E&gt; IS-A Subject&lt;Conduit&lt;P, E&gt;&gt; (via sealed hierarchy: Component → Context → Source → Conduit).
 * This means every Conduit is itself a Subject that can be subscribed to, and it emits values of type E
 * via its internal Channels. Subscribers receive Subject&lt;Channel&lt;E&gt;&gt; and can dynamically
 * register Pipes to receive emissions from specific subjects.
 * <p>
 * < p >Routes emitted values from Channels (producers) to Pipes (consumers) via Circuit's shared queue.
 * Manages percepts created from channels via a Composer. Each percept corresponds to a subject
 * and shares the Circuit's queue for signal processing (single-threaded execution model).
 * <p>
 * < p >< b >Data Flow (Circuit Queue Architecture):</b >
 * < ol >
 * < li >Channel (producer) emits value → posts Script to Circuit Queue</li >
 * < li >Circuit Queue processor executes Script → calls notifySubscribers()</li >
 * < li >notifySubscribers() invokes subscribers on first emission (lazy rebuild)</li >
 * < li >Subscribers register Pipes via Registrar → Pipes receive emissions</li >
 * </ol >
 * <p>
 * < p >< b >Single-Threaded Execution Model:</b >
 * All Conduits within a Circuit share the Circuit's single Queue. This ensures:
 * < ul >
 * < li >Ordered delivery within Circuit domain</li >
 * < li >QoS control (can prioritize certain Conduits)</li >
 * < li >Prevents queue saturation</li >
 * < li >Matches "Virtual CPU Core" design principle</li >
 * </ul >
 * <p>
 * < p >< b >Subscriber Invocation (Lazy Callback Model):</b >
 * < ul >
 * < li >< b >Channel creation:</b > Creating a Channel via percept() does NOT invoke callbacks</li >
 * < li >< b >First emission:</b > subscriber.accept() called on first emission from a Channel (lazy registration)</li >
 * < li >< b >Lazy rebuild:</b > Callbacks fire during rebuild triggered by first emission after subscription</li >
 * < li >Registered pipes are cached per Subject per Subscriber</li >
 * < li >Subsequent emissions reuse cached pipes (efficient multi-dispatch)</li >
 * </ul >
 * <p>
 * < p >< b >Simple Name Model:</b >
 * Percepts are keyed by the Name passed to get(). Each container (Circuit, Conduit, Cell) maintains
 * its own namespace using simple names as keys. Hierarchy is implicit through container relationships,
 * not through manual name construction. A Name is just a Name - whether it contains dots or not,
 * it's treated as an opaque key for lookup.
 *
 * @param < P > the percept type (e.g., Pipe< E >)
 * @param < E > the emission type (e.g., MonitorSignal)
 */
@Getter
public class RoutingConduit < P extends Percept, E > implements Conduit < P, E > {

  private final Circuit                          circuit; // Parent Circuit in hierarchy (provides scheduling + Subject)
  private final Subject < Conduit < P, E > >     conduitSubject;
  private final Composer < E, ? extends P >      perceptComposer;

  // Lazy-initialized maps (only allocated when first percept/pipe registered)
  // Saves ~100-200ns for Conduits that are created but never used (common in benchmarks)
  private volatile Map < Name, P >          percepts;
  private final Consumer < Flow < E > >     flowConfigurer; // Optional transformation pipeline (nullable)

  // Single-element cache for most-recently-used percept (Phase 3 optimization)
  // Benchmarks show >90% of gets are for the same name - this avoids HashMap entirely
  // Uses identity comparison (==) which is ~1ns vs HashMap.get() which is ~5-8ns
  private volatile Name lastAccessedName;
  private volatile P    lastAccessedPercept;

  // Direct subscriber management (moved from SourceImpl)
  // Pre-sized to 4 (typical: 1-2 subscribers, rare: >4)
  private final List < Subscriber < E > > subscribers = new ArrayList <> ( 4 );

  // Cache: Subject Name -> Subscriber -> List of registered Pipes
  // Pipes are registered only once per Subject per Subscriber (on first emission)
  // Pipes now use ? super E for contra-variance
  // Lazy-initialized (only when first emission occurs)
  private volatile Map < Name, Map < Subscriber < E >, List < Pipe < ? super E > > > > pipeCache;

  /**
   * Creates a Conduit without transformations.
   *
   * @param conduitName     hierarchical conduit name (simple name within Circuit namespace)
   * @param perceptComposer composer for creating percepts from channels
   * @param circuit         parent Circuit (provides scheduling + Subject hierarchy)
   */
  public RoutingConduit ( Name conduitName, Composer < E, ? extends P > perceptComposer, Circuit circuit ) {
    this ( conduitName, perceptComposer, circuit, null );
  }

  /**
   * Creates a Conduit with optional transformation pipeline.
   * <p>
   * Optimization: percepts and pipeCache maps are lazy-initialized to save ~100-200ns
   * for Conduits that are created but never used (common in benchmarks/tests).
   *
   * @param conduitName     hierarchical conduit name (simple name within Circuit namespace)
   * @param perceptComposer composer for creating percepts from channels
   * @param circuit         parent Circuit (provides scheduling + Subject hierarchy)
   * @param flowConfigurer  optional transformation pipeline (null if no transformations)
   */
  @SuppressWarnings ( "unchecked" )
  public RoutingConduit ( Name conduitName, Composer < E, ? extends P > perceptComposer, Circuit circuit, Consumer < Flow < E > > flowConfigurer ) {
    this.circuit = Objects.requireNonNull ( circuit, "Circuit cannot be null" );
    this.conduitSubject = new ContextualSubject <> (
      conduitName,
      (Class < Conduit < P, E > >) (Class < ? >) Conduit.class,  // Fixed type cast for generics
      circuit.subject ()  // Parent Subject from parent Circuit
    );
    this.perceptComposer = perceptComposer;
    this.percepts = null;  // Lazy-initialized on first get()
    this.pipeCache = null; // Lazy-initialized on first emission
    this.flowConfigurer = flowConfigurer; // Can be null
  }

  @Override
  public Subject < Conduit < P, E > > subject () {
    return conduitSubject;
  }

  /**
   * Returns the parent Circuit.
   * Provides access to scheduling and Subject hierarchy.
   *
   * @return the parent Circuit
   */
  public Circuit getCircuit () {
    return circuit;
  }

  /**
   * Subscribes a subscriber to receive emissions from this Conduit.
   * <p>
   * < p >< b >Type System Insight:</b >
   * Conduit&lt;P, E&gt; IS-A Subject&lt;Conduit&lt;P, E&gt;&gt; (via Component → Context → Source → Conduit hierarchy).
   * This means:
   * < ul >
   * < li >Conduit emits {@code E} values via its internal Channels</li >
   * < li >Conduit can be subscribed to by {@code Subscriber< E >} instances</li >
   * < li >Subscriber receives {@code Subject< Channel< E >>} (the subject of each Channel within this Conduit)</li >
   * </ul >
   * <p>
   * < p >< b >Lazy Callback Model (per Substrates API):</b >
   * The subscriber's {@code accept(Subject< Channel< E >>, Registrar< E >)} method is invoked:
   * < ul >
   * < li >< b >NOT on channel creation</b >: Creating a Channel via percept() does NOT invoke callbacks</li >
   * < li >< b >On first emission</b >: Callbacks fire when a Channel receives its first emission after subscription</li >
   * < li >< b >Lazy rebuild</b >: Implementation triggers rebuild on first emission to invoke callbacks</li >
   * </ul >
   * <p>
   * < p >The subscriber can:
   * < ul >
   * < li >Inspect the {@code Subject< Channel< E >>} to determine routing logic</li >
   * < li >Call {@code conduit.percept(subject.name())} to retrieve the percept (cached, no recursion)</li >
   * < li >Register one or more {@code Pipe< E >} instances via the {@code Registrar< E >}</li >
   * < li >Registered pipes receive the current emission and all future emissions from that Subject</li >
   * </ul >
   *
   * @param subscriber the subscriber to register
   * @return a Subscription to control the subscription lifecycle
   */
  @Override
  public Subscription subscribe ( Subscriber < E > subscriber ) {
    Objects.requireNonNull ( subscriber, "Subscriber cannot be null" );
    subscribers.add ( subscriber );
    return new CallbackSubscription ( () -> subscribers.remove ( subscriber ), conduitSubject );
  }

  /**
   * Checks if there are any active subscribers.
   * Used by Pipes for early exit optimization.
   *
   * @return true if at least one subscriber exists, false otherwise
   */
  public boolean hasSubscribers () {
    return !subscribers.isEmpty ();
  }

  @Override
  public P percept ( Name subject ) {
    // Phase 3 optimization: Single-element cache (hot path)
    // >90% of percept() calls are for the same name - use identity check (==) which is ~1ns
    // This avoids HashMap.get() entirely (~5-8ns with hashCode + equals + bucket lookup)
    if ( subject == lastAccessedName ) {
      return lastAccessedPercept;  // Ultra-fast path: ~2ns (volatile read + identity check + return)
    }

    // Lazy-initialize percepts map on first access
    ensurePerceptsCreated();

    // Fast path: return cached percept if exists
    P existingPercept = percepts.get ( subject );
    if ( existingPercept != null ) {
      // Update single-element cache for next access
      lastAccessedName = subject;
      lastAccessedPercept = existingPercept;
      return existingPercept;
    }

    // Slow path: create new Channel and percept
    // Use simple name - hierarchy is implicit through container relationships
    // Pass 'this' (Conduit) as parent for hierarchy
    Channel < E > channel = new EmissionChannel <> ( subject, this, flowConfigurer );
    P newPercept = perceptComposer.compose ( channel );

    // Cache under simple name
    P cachedPercept = percepts.putIfAbsent ( subject, newPercept );

    if ( cachedPercept == null ) {
      // We created it - update single-element cache
      // Per API: Channel creation does NOT invoke callbacks - they fire on first emission
      lastAccessedName = subject;
      lastAccessedPercept = newPercept;
      return newPercept;
    } else {
      // Someone else created it first - update single-element cache
      lastAccessedName = subject;
      lastAccessedPercept = cachedPercept;
      return cachedPercept;
    }
  }

  /**
   * Lazy-initializes the percepts map on first access.
   * Thread-safe via double-checked locking.
   * Saves ~50-100ns for Conduits that never call get() (common in benchmarks).
   */
  private void ensurePerceptsCreated() {
    if ( percepts == null ) {
      synchronized ( this ) {
        if ( percepts == null ) {
          // Pre-size to 16 (typical: 5-10 percepts per conduit)
          percepts = new HashMap <> ( 16 );
        }
      }
    }
  }

  /**
   * Lazy-initializes the pipeCache map on first emission.
   * Thread-safe via double-checked locking.
   * Saves ~50-100ns for Conduits that never emit (common in benchmarks).
   */
  private void ensurePipeCacheCreated() {
    if ( pipeCache == null ) {
      synchronized ( this ) {
        if ( pipeCache == null ) {
          // Pre-size to 16 (typical: 5-10 subjects emit per conduit)
          pipeCache = new HashMap <> ( 16 );
        }
      }
    }
  }

  @Override
  public P percept ( Subject < ? > subject ) {
    return percept ( subject.name () );
  }

  @Override
  public P percept ( Substrate < ? > substrate ) {
    return percept ( substrate.subject ().name () );
  }

  /**
   * Returns the routing pipe that handles emissions from Channels.
   * This is the next pipe in the chain: EmissionPipe → asyncPipe → routingPipe
   *
   * @return pipe that routes emissions to subscriber pipes
   */
  public Pipe<Capture<E>> routingPipe() {
    return new Pipe<Capture<E>>() {
      @Override
      public void emit(Capture<E> capture) {
        notifySubscribers(capture);
      }

      @Override
      public void flush() {
        // No buffering
      }
    };
  }


  /**
   * Notifies all subscribers of an emission from a Channel.
   * Handles lazy pipe registration and multi-dispatch.
   *
   * @param capture the emission capture (Subject + value)
   */
  private void notifySubscribers ( Capture < E > capture ) {
    Subject < Channel < E > > emittingSubject = capture.subject ();
    Name subjectName = emittingSubject.name ();

    // Lazy-initialize pipeCache on first emission
    ensurePipeCacheCreated();

    // Get or create the subscriber->pipes map for this Subject
    Map < Subscriber < E >, List < Pipe < ? super E > > > subscriberPipes = pipeCache.computeIfAbsent (
      subjectName,
      name -> new HashMap <> ()
    );

    // Functional stream pipeline: resolve pipes for each subscriber, then emit
    subscribers.stream ()
      .flatMap ( subscriber ->
        resolvePipes ( subscriber, emittingSubject, subscriberPipes ).stream ()
      )
      .forEach ( pipe -> pipe.emit ( capture.emission () ) );
  }

  /**
   * Resolves pipes for a subscriber, registering them on first emission from a subject.
   *
   * @param subscriber      the subscriber
   * @param emittingSubject the subject emitting
   * @param subscriberPipes cache of subscriber->pipes
   * @return list of pipes for this subscriber
   */
  private List < Pipe < ? super E > > resolvePipes (
    Subscriber < E > subscriber,
    Subject < Channel < E > > emittingSubject,
    Map < Subscriber < E >, List < Pipe < ? super E > > > subscriberPipes
  ) {
    return subscriberPipes.computeIfAbsent ( subscriber, sub -> {
      // First emission from this Subject - retrieve callback and invoke
      List < Pipe < ? super E > > registeredPipes = new ArrayList <> ();

      //  Get callback from subscriber
      BiConsumer < Subject < Channel < E > >, Registrar < E > > callback =
        ( (ContextSubscriber < E >) sub ).getCallback ();

      callback.accept ( emittingSubject, new Registrar < E > () {
        @Override
        public void register ( Pipe < ? super E > pipe ) {
          registeredPipes.add ( pipe );  // Direct add - contra-variance allows this
        }

        @Override
        public void register ( Receptor < ? super E > receptor ) {
          //  Convert Receptor to Pipe (can't use lambda - Pipe not functional)
          register ( new Pipe < E > () {
            @Override
            public void emit ( E emission ) {
              receptor.receive ( emission );
            }

            @Override
            public void flush () {
              // No-op: Receptor has no buffering
            }
          } );
        }
      } );

      return registeredPipes;
    } );
  }

}
