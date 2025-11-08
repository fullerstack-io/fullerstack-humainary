package io.fullerstack.substrates.conduit;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.id.UuidIdentifier;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscriber.FunctionalSubscriber;
import io.fullerstack.substrates.subscription.CallbackSubscription;
import io.fullerstack.substrates.circuit.Scheduler;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * < li >Circuit Queue processor executes Script → calls processEmission()</li >
 * < li >processEmission() invokes subscribers (on channel creation + first emission)</li >
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
 * < p >< b >Subscriber Invocation (Two-Phase Notification):</b >
 * < ul >
 * < li >< b >Phase 1 (Channel creation):</b > subscriber.accept() called when new Channel created via get()</li >
 * < li >< b >Phase 2 (First emission):</b > subscriber.accept() called on first emission from a Subject (lazy registration)</li >
 * < li >Registered pipes are cached per Subject per Subscriber</li >
 * < li >Subsequent emissions reuse cached pipes (efficient multi-dispatch)</li >
 * < li >Example: Hierarchical routing where pipes register parent pipes once</li >
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

  private final Circuit                     circuit; // Parent Circuit in hierarchy (provides scheduling + Subject)
  private final Subject                     conduitSubject;
  private final Composer < E, ? extends P > perceptComposer;
  private final Map < Name, P >             percepts;
  private final Consumer < Flow < E > >     flowConfigurer; // Optional transformation pipeline (nullable)

  // Direct subscriber management (moved from SourceImpl)
  private final List < Subscriber < E > > subscribers = new CopyOnWriteArrayList <> ();

  // Cache: Subject Name -> Subscriber -> List of registered Pipes
  // Pipes are registered only once per Subject per Subscriber (on first emission)
  //  Pipes now use ? super E for contra-variance
  private final Map < Name, Map < Subscriber < E >, List < Pipe < ? super E > > > > pipeCache = new ConcurrentHashMap <> ();

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
   *
   * @param conduitName     hierarchical conduit name (simple name within Circuit namespace)
   * @param perceptComposer composer for creating percepts from channels
   * @param circuit         parent Circuit (provides scheduling + Subject hierarchy)
   * @param flowConfigurer  optional transformation pipeline (null if no transformations)
   */
  public RoutingConduit ( Name conduitName, Composer < E, ? extends P > perceptComposer, Circuit circuit, Consumer < Flow < E > > flowConfigurer ) {
    this.circuit = Objects.requireNonNull ( circuit, "Circuit cannot be null" );
    this.conduitSubject = new ContextualSubject <> (
      UuidIdentifier.generate (),
      conduitName,
      LinkedState.empty (),
      Conduit.class,
      circuit.subject ()  // Parent Subject from parent Circuit
    );
    this.perceptComposer = perceptComposer;
    this.percepts = new ConcurrentHashMap <> ();
    this.flowConfigurer = flowConfigurer; // Can be null
  }

  @Override
  public Subject subject () {
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
   * < li >Subscriber receives {@code Subject< Channel< E >>} (the subject of each Channel created within this Conduit)</li >
   * </ul >
   * <p>
   * < p >< b >Subscriber Behavior:</b >
   * The subscriber's {@code accept(Subject< Channel< E >>, Registrar< E >)} method is invoked:
   * < ol >
   * < li >< b >On Channel creation</b >: When a new Channel is created via {@link #get(Name)}</li >
   * < li >< b >On first emission</b >: Lazy registration when a Subject emits for the first time</li >
   * </ol >
   * <p>
   * < p >The subscriber can:
   * < ul >
   * < li >Inspect the {@code Subject< Channel< E >>} to determine routing logic</li >
   * < li >Call {@code conduit.get(subject.name())} to retrieve the percept (cached, no recursion)</li >
   * < li >Register one or more {@code Pipe< E >} instances via the {@code Registrar< E >}</li >
   * < li >Registered pipes receive all future emissions from that Subject</li >
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
  public P get ( Name subject ) {
    // Fast path: return cached percept if exists
    P existingPercept = percepts.get ( subject );
    if ( existingPercept != null ) {
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
      // We created it - notify subscribers AFTER caching
      // Subscribers receive Subject with simple name, so conduit.get(subject.name()) works
      notifySubscribersOfNewSubject ( channel.subject () );
      return newPercept;
    } else {
      // Someone else created it first
      return cachedPercept;
    }
  }

  @Override
  public P get ( Subject < ? > subject ) {
    return get ( subject.name () );
  }

  @Override
  public P get ( Substrate < ? > substrate ) {
    return get ( substrate.subject ().name () );
  }

  /**
   * Provides an emission handler callback for Channel/Pipe creation.
   * Channels pass this callback to Pipes, allowing Pipes to notify subscribers.
   *
   * @return callback that routes emissions to subscribers
   */
  public Consumer < Capture < E > > emissionHandler () {
    return this::notifySubscribers;
  }

  /**
   * Notifies all subscribers that a new Subject (Channel) has become available.
   * Called AFTER the Channel is cached in percepts map.
   * <p>
   * < p >Per the Substrates API contract: "the subscriber's behavior is invoked each time
   * a new channel or emitting subject is created within that source."
   * <p>
   * < p >The subscriber can safely call {@code conduit.get(subject.name())} to retrieve
   * the percept, as it's already cached.
   *
   * @param subject the Subject of the newly created Channel
   */
  private void notifySubscribersOfNewSubject ( Subject < Channel < E > > subject ) {
    for ( Subscriber < E > subscriber : subscribers ) {
      //  Get callback from subscriber (stored internally)
      BiConsumer < Subject < Channel < E > >, Registrar < E > > callback =
        ( (FunctionalSubscriber < E >) subscriber ).getCallback ();

      callback.accept ( subject, new Registrar < E > () {
        @Override
        public void register ( Pipe < ? super E > pipe ) {
          // Cache the registered pipe for this (subscriber, subject) pair
          Name subjectName = subject.name ();
          pipeCache
            .computeIfAbsent ( subjectName, k -> new ConcurrentHashMap <> () )
            .computeIfAbsent ( subscriber, k -> new CopyOnWriteArrayList <> () )
            .add ( pipe );
        }

        @Override
        public void register ( Observer < ? super E > observer ) {
          //  Convenience method for Observer registration
          // Convert Observer to anonymous Pipe and register it
          register ( new Pipe < E > () {
            @Override
            public void emit ( E emission ) {
              observer.observe ( emission );
            }

            @Override
            public void flush () {
              // No-op: Observer has no buffering
            }
          } );
        }
      } );
    }
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

    // Get or create the subscriber->pipes map for this Subject
    Map < Subscriber < E >, List < Pipe < ? super E > > > subscriberPipes = pipeCache.computeIfAbsent (
      subjectName,
      name -> new ConcurrentHashMap <> ()
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
    Subject emittingSubject,
    Map < Subscriber < E >, List < Pipe < ? super E > > > subscriberPipes
  ) {
    return subscriberPipes.computeIfAbsent ( subscriber, sub -> {
      // First emission from this Subject - retrieve callback and invoke
      List < Pipe < ? super E > > registeredPipes = new CopyOnWriteArrayList <> ();

      //  Get callback from subscriber
      BiConsumer < Subject < Channel < E > >, Registrar < E > > callback =
        ( (FunctionalSubscriber < E >) sub ).getCallback ();

      callback.accept ( emittingSubject, new Registrar < E > () {
        @Override
        public void register ( Pipe < ? super E > pipe ) {
          registeredPipes.add ( pipe );  // Direct add - contra-variance allows this
        }

        @Override
        public void register ( Observer < ? super E > observer ) {
          //  Convert Observer to Pipe (can't use lambda - Pipe not functional)
          register ( new Pipe < E > () {
            @Override
            public void emit ( E emission ) {
              observer.observe ( emission );
            }

            @Override
            public void flush () {
              // No-op: Observer has no buffering
            }
          } );
        }
      } );

      return registeredPipes;
    } );
  }

}
