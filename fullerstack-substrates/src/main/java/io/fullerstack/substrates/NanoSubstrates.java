package io.fullerstack.substrates;

import static java.util.Objects.requireNonNull;

import java.lang.reflect.Member;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.humainary.substrates.api.Substrates.Capture;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Closure;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Current;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Id;
import io.humainary.substrates.api.Substrates.Lookup;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Resource;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Sift;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.Source;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Substrate;

/// Minimal implementations of Substrates interfaces.
public final class NanoSubstrates {

  private NanoSubstrates () {}

  /// Creates a new NanoCortex instance.
  public static Cortex cortex () {
    return new NanoCortex ();
  }

  /// An in-memory buffer of captures that is also a Substrate.
  ///
  /// A Reservoir is created from a Source and is given its own identity (Subject)
  /// that is a child of the Source it was created from. It subscribes to its
  /// source to capture all emissions into an internal buffer.
  ///
  /// @param <E> the class type of the emitted value
  /// @see Capture
  /// @see Source#reservoir()
  /// @see Resource
  static final class NanoReservoir < E >
    implements Reservoir < E > {

    /// A capture of an emitted value from a channel with its associated subject.
    private record Cap < E > (
      E emission,
      Subject < Channel < E > > subject
    ) implements Capture < E > {}

    /// The subject identity for this reservoir.
    private final Subject < Reservoir < E > > subject;

    /// Internal buffer storing captured emissions.
    private final List < Cap < E > > buffer = new ArrayList <> ();

    /// Creates a new reservoir with the given subject identity.
    ///
    /// @param subject the subject identity for this reservoir
    NanoReservoir ( Subject < Reservoir < E > > subject ) {
      this.subject = subject;
    }

    /// Returns the subject identity of this reservoir.
    ///
    /// @return the subject of this reservoir
    @Override
    public Subject < Reservoir < E > > subject () {
      return subject;
    }

    /// Returns a stream representing the events that have accumulated since the
    /// reservoir was created or the last call to this method.
    ///
    /// @return A stream consisting of stored events captured from channels.
    /// @see Capture
    public Stream < Capture < E > > drain () {
      // Optimized: use buffer snapshot instead of toArray allocation
      List < Cap < E > > snapshot = new ArrayList <> ( buffer );
      buffer.clear ();
      return snapshot.stream ().map ( c -> c );
    }

    /// Captures an emission with its channel subject.
    void capture ( E emission, Subject < Channel < E > > channelSubject ) {
      buffer.add ( new Cap <> ( emission, channelSubject ) );
    }

    /// Closes this reservoir, releasing the captured emissions buffer.
    @Override
    public void close () {
      buffer.clear ();
    }

  }

  /// A named port in a conduit that provides a pipe for emission.
  ///
  /// Channels serve as named entry points into a conduit's processing pipeline.
  /// Each channel has a unique Subject with an associated Name, and emissions
  /// to the channel are routed through the conduit's Flow pipeline to registered
  /// subscribers.
  ///
  /// @param <E> the class type of emitted value
  /// @see Conduit
  static final class NanoChannel < E >
    implements Channel < E > {

    /// The subject identity for this channel.
    private final Subject < Channel < E > > subject;

    /// The emission consumer for routing emissions to subscribers.
    private final Consumer < E > router;

    /// Creates a new channel with the given subject and emission router.
    ///
    /// @param subject the subject identity for this channel
    /// @param router the consumer that routes emissions to subscribers
    NanoChannel ( Subject < Channel < E > > subject, Consumer < E > router ) {
      this.subject = subject;
      this.router = router;
    }

    /// Returns the subject identity of this channel.
    ///
    /// @return the subject of this channel
    @Override
    public Subject < Channel < E > > subject () {
      return subject;
    }

    /// Returns a new pipe for emitting to this channel.
    /// Each call creates a new Pipe instance with a new Subject/Id (@New contract).
    ///
    /// @return A new pipe routing to this channel
    @Override
    public Pipe < E > pipe () {
      // Create a new Subject for this pipe (child of channel subject)
      NanoSubject < Pipe < E > > pipeSubject = new NanoSubject <> (
        subject.name (),
        (NanoSubject < ? >) subject,
        Pipe.class
      );
      return new NanoPipe <> ( pipeSubject, router );
    }

    /// Returns a new pipe with custom flow configuration.
    ///
    /// @param configurer A configurer responsible for configuring flow
    /// @return A new pipe instance with the configured flow
    @Override
    public Pipe < E > pipe ( Configurer < Flow < E > > configurer ) {
      // Create a new Subject for this pipe (child of channel subject)
      NanoSubject < Pipe < E > > pipeSubject = new NanoSubject <> (
        subject.name (),
        (NanoSubject < ? >) subject,
        Pipe.class
      );
      // Create base pipe that routes to this channel
      Pipe < E > basePipe = new NanoPipe <> ( pipeSubject, router );
      // Apply flow configuration
      NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, basePipe );
      configurer.configure ( flow );
      return flow.pipe ();
    }

  }

  /// A simple pipe that delegates emit to a consumer.
  ///
  /// @param <E> the class type of emitted value
  static final class NanoPipe < E >
    implements Pipe < E > {

    private final Subject < Pipe < E > > subject;
    private final Consumer < E > consumer;

    NanoPipe ( Subject < Pipe < E > > subject, Consumer < E > consumer ) {
      this.subject = subject;
      this.consumer = consumer;
    }

    @Override
    public Subject < Pipe < E > > subject () {
      return subject;
    }

    @Override
    public void emit ( E emission ) {
      consumer.accept ( emission );
    }

  }

  /// Async pipe that queues emissions to circuit's dual queue.
  /// Uses capturing lambda to pair emission with receiver at queue time.
  ///
  /// @param <E> the class type of emitted value
  static final class ValvePipe < E >
    implements Pipe < E > {

    private final Subject < Pipe < E > > subject;
    private final NanoCircuit circuit;
    private final Consumer < E > receiver;

    ValvePipe (
      Subject < Pipe < E > > subject,
      NanoCircuit circuit,
      Consumer < E > receiver
    ) {
      this.subject = subject;
      this.circuit = circuit;
      this.receiver = receiver;
    }

    @Override
    public Subject < Pipe < E > > subject () {
      return subject;
    }

    @Override
    public void emit ( E emission ) {
      circuit.submitDirect ( receiver, emission );
    }

  }

  /// A configurable processing pipeline for data transformation.
  ///
  /// Flow provides operators like diff, guard, limit, sample, etc.
  /// Each operator returns a new Flow representing the extended pipeline.
  /// Operators are composed in the order they are added (left-to-right).
  ///
  /// @param <E> the class type of emitted value
  static final class NanoFlow < E >
    implements Flow < E > {

    /// The subject for pipes created by this flow.
    private final Subject < Pipe < E > > subject;

    /// The target pipe that receives emissions after all transformations.
    private final Pipe < E > target;

    /// List of operator factories. Each factory takes a downstream consumer
    /// and returns a consumer that applies the operator before calling downstream.
    private final List < Function < Consumer < E >, Consumer < E > > > operators = new ArrayList <> ();

    /// Creates a new flow targeting the given pipe.
    ///
    /// @param subject the subject for created pipes
    /// @param target the pipe that receives emissions
    NanoFlow ( Subject < Pipe < E > > subject, Pipe < E > target ) {
      this.subject = subject;
      this.target = target;
    }

    /// Returns the pipe that applies this flow's transformations.
    /// Operators are composed in order: first operator wraps second, etc.
    Pipe < E > pipe () {
      // Start with the target as the final downstream consumer
      Consumer < E > consumer = target::emit;
      // Apply operators in reverse order so the first-added operator executes first
      for ( int i = operators.size () - 1; i >= 0; i-- ) {
        consumer = operators.get ( i ).apply ( consumer );
      }
      return new NanoPipe <> ( subject, consumer );
    }

    @Override
    public Flow < E > diff () {
      operators.add ( downstream -> {
        Object[] prev = { null };
        return v -> {
          if ( !Objects.equals ( v, prev[0] ) ) {
            prev[0] = v;
            downstream.accept ( v );
          }
        };
      } );
      return this;
    }

    @Override
    public Flow < E > diff ( E initial ) {
      operators.add ( downstream -> {
        Object[] prev = { initial };
        return v -> {
          if ( !Objects.equals ( v, prev[0] ) ) {
            prev[0] = v;
            downstream.accept ( v );
          }
        };
      } );
      return this;
    }

    @Override
    public Flow < E > guard ( Predicate < ? super E > predicate ) {
      operators.add ( downstream -> v -> {
        if ( predicate.test ( v ) ) {
          downstream.accept ( v );
        }
      } );
      return this;
    }

    @Override
    public Flow < E > guard ( E initial, BiPredicate < ? super E, ? super E > predicate ) {
      operators.add ( downstream -> {
        Object[] prev = { initial };
        return v -> {
          @SuppressWarnings ( "unchecked" )
          E p = (E) prev[0];
          if ( predicate.test ( p, v ) ) {
            prev[0] = v;
            downstream.accept ( v );
          }
        };
      } );
      return this;
    }

    @Override
    public Flow < E > limit ( int limit ) {
      return limit ( (long) limit );
    }

    @Override
    public Flow < E > limit ( long limit ) {
      operators.add ( downstream -> {
        long[] count = { 0 };
        return v -> {
          if ( count[0] < limit ) {
            count[0]++;
            downstream.accept ( v );
          }
        };
      } );
      return this;
    }

    @Override
    public Flow < E > peek ( Receptor < ? super E > receptor ) {
      operators.add ( downstream -> v -> {
        receptor.receive ( v );
        downstream.accept ( v );
      } );
      return this;
    }

    @Override
    public Flow < E > reduce ( E initial, BinaryOperator < E > operator ) {
      operators.add ( downstream -> {
        Object[] acc = { initial };
        return v -> {
          @SuppressWarnings ( "unchecked" )
          E a = (E) acc[0];
          E result = operator.apply ( a, v );
          acc[0] = result;
          downstream.accept ( result );
        };
      } );
      return this;
    }

    @Override
    public Flow < E > replace ( UnaryOperator < E > transformer ) {
      operators.add ( downstream -> v -> downstream.accept ( transformer.apply ( v ) ) );
      return this;
    }

    @Override
    public Flow < E > sample ( int sample ) {
      operators.add ( downstream -> {
        int[] count = { 0 };
        return v -> {
          // Emit on every Nth element (N-1, 2N-1, 3N-1, ...)
          // e.g., sample(3) emits at indices 2, 5, 8 (0-based)
          if ( ++count[0] % sample == 0 ) {
            downstream.accept ( v );
          }
        };
      } );
      return this;
    }

    @Override
    public Flow < E > sample ( double probability ) {
      operators.add ( downstream -> v -> {
        if ( Math.random () < probability ) {
          downstream.accept ( v );
        }
      } );
      return this;
    }

    @Override
    public Flow < E > sift ( Comparator < ? super E > comparator, Configurer < Sift < E > > configurer ) {
      NanoSift < E > sift = new NanoSift <> ( comparator );
      configurer.configure ( sift );
      Predicate < E > filter = sift.predicate ();
      operators.add ( downstream -> v -> {
        if ( filter.test ( v ) ) {
          downstream.accept ( v );
        }
      } );
      return this;
    }

    @Override
    public Flow < E > skip ( long n ) {
      operators.add ( downstream -> {
        long[] count = { 0 };
        return v -> {
          if ( count[0] >= n ) {
            downstream.accept ( v );
          } else {
            count[0]++;
          }
        };
      } );
      return this;
    }

  }

  /// Configurable filter builder for comparator-based filtering operations.
  ///
  /// NanoSift collects filtering conditions and builds a composite predicate
  /// that checks all conditions. Supports:
  /// - Absolute bounds: min, max, range, above, below
  /// - Stateful extrema tracking: high, low
  ///
  /// @param <E> the class type of emitted values
  static final class NanoSift < E >
    implements Sift < E > {

    private final Comparator < ? super E > comparator;
    private final List < Predicate < E > > filters = new ArrayList <> ();

    NanoSift ( Comparator < ? super E > comparator ) {
      this.comparator = comparator;
    }

    /// Returns a predicate that ANDs all configured filters.
    Predicate < E > predicate () {
      if ( filters.isEmpty () ) {
        return v -> true;
      }
      return v -> {
        for ( Predicate < E > f : filters ) {
          if ( !f.test ( v ) ) return false;
        }
        return true;
      };
    }

    @Override
    public Sift < E > above ( E lower ) {
      filters.add ( v -> comparator.compare ( v, lower ) > 0 );
      return this;
    }

    @Override
    public Sift < E > below ( E upper ) {
      filters.add ( v -> comparator.compare ( v, upper ) < 0 );
      return this;
    }

    @Override
    public Sift < E > high () {
      Object[] currentHigh = { null };
      boolean[] hasValue = { false };
      filters.add ( v -> {
        @SuppressWarnings ( "unchecked" )
        E high = (E) currentHigh[0];
        if ( !hasValue[0] || comparator.compare ( v, high ) > 0 ) {
          currentHigh[0] = v;
          hasValue[0] = true;
          return true;
        }
        return false;
      } );
      return this;
    }

    @Override
    public Sift < E > low () {
      Object[] currentLow = { null };
      boolean[] hasValue = { false };
      filters.add ( v -> {
        @SuppressWarnings ( "unchecked" )
        E low = (E) currentLow[0];
        if ( !hasValue[0] || comparator.compare ( v, low ) < 0 ) {
          currentLow[0] = v;
          hasValue[0] = true;
          return true;
        }
        return false;
      } );
      return this;
    }

    @Override
    public Sift < E > max ( E max ) {
      filters.add ( v -> comparator.compare ( v, max ) <= 0 );
      return this;
    }

    @Override
    public Sift < E > min ( E min ) {
      filters.add ( v -> comparator.compare ( v, min ) >= 0 );
      return this;
    }

    @Override
    public Sift < E > range ( E lower, E upper ) {
      filters.add ( v -> comparator.compare ( v, lower ) >= 0 && comparator.compare ( v, upper ) <= 0 );
      return this;
    }

  }

  /// Represents the execution context from which substrate operations originate.
  ///
  /// A [Current] identifies the execution context (thread, coroutine, fiber, etc.)
  /// that invokes substrate operations. It is obtained via [Cortex#current()] in
  /// a manner analogous to `Thread.currentThread()` in Java:
  ///
  /// ```java
  /// var current = cortex.current ();
  /// ```
  ///
  /// ## Temporal Contract
  ///
  /// **IMPORTANT**: Current follows a temporal contract - it is only valid within the
  /// execution context (thread) that obtained it. The Current reference represents the
  /// **thread-local execution state** and **must not be retained** or used from a different
  /// thread or execution context.
  ///
  /// Like `Thread.currentThread()`, Current is intrinsically tied to its originating context.
  /// Using it from another thread leads to incorrect behavior. Always call [Cortex#current()]
  /// from the context where you need the current execution reference.
  ///
  /// Violating this contract by storing Current references and accessing them from different
  /// threads or after the execution context has changed leads to undefined behavior.
  ///
  /// ## Subject Identity
  ///
  /// As a [Substrate], Current provides access to its underlying [Subject] which includes:
  ///
  /// - **Identity** via [Subject#id()] - unique identifier for this execution context
  /// - **Hierarchical naming** via [Subject#name()] - derived from the thread or context type
  /// - **Context properties** via [Subject#state()] - metadata about the execution context
  ///
  /// ## Use Cases
  ///
  /// - **Correlation**: Link emissions and operations back to their originating context
  /// - **Tracing**: Track execution flow across substrate boundaries
  /// - **Diagnostics**: Identify which contexts are invoking substrate operations
  ///
  /// ## Language Mapping
  ///
  /// The abstraction is language-agnostic and maps to platform-specific concepts:
  ///
  /// - **Java**: Platform/virtual threads
  /// - **Go**: Goroutines
  /// - **Kotlin**: Coroutines
  /// - **Other**: Implementation-specific concurrency primitives
  ///
  /// @see Cortex#current()
  /// @see Subject
  /// @since 1.0
  static final class NanoCurrent
    implements Current {

    /// The subject identity for this current context.
    private final Subject < Current > subject;

    /// Creates a new current context with the given subject identity.
    ///
    /// @param subject the subject identity for this context
    NanoCurrent ( Subject < Current > subject ) {
      this.subject = subject;
    }

    /// Returns the subject identity of this current context.
    ///
    /// @return the subject of this current context
    @Override
    public Subject < Current > subject () {
      return subject;
    }

  }

  /// A cancellable handle representing an active subscription to a source.
  ///
  /// Subscription is returned by [Source#subscribe(Subscriber)] and allows
  /// the subscriber to cancel interest in future events. Each subscription has its
  /// own identity (via [Substrate]) and can be closed to unregister.
  ///
  /// ## Lifecycle
  ///
  /// A subscription progresses through these states:
  /// 1. **Active**: Created by `subscribe()`, subscriber receives callbacks
  /// 2. **Closed**: After `close()` is called, no more callbacks occur
  ///
  /// Subscriptions remain active until explicitly closed or the source itself is closed.
  ///
  /// ## Cancellation Semantics
  ///
  /// Calling [Resource#close()] on a subscription:
  /// - Unregisters the subscriber from the source
  /// - Stops all future subscriber callbacks
  /// - Removes all pipes registered by this subscriber from active channels
  /// - Is **idempotent** - repeated calls are safe and have no effect
  ///
  /// ## Threading
  ///
  /// Subscriptions can be closed from any thread:
  /// - Thread-safe for concurrent close() calls
  /// - May be closed from within subscriber callbacks
  /// - May be closed from external threads
  ///
  /// The unregistration happens asynchronously on the circuit's processing thread,
  /// ensuring deterministic removal without blocking the caller.
  ///
  /// ## Timing Considerations
  ///
  /// After calling `close()`:
  /// - The unsubscription is coordinated asynchronously on the circuit thread
  /// - Already-queued callbacks may still execute (circuit-thread delivery)
  /// - No NEW channels will trigger callbacks after unsubscription completes
  /// - Pipes registered before close() continue receiving emissions until rebuild
  /// - Next emission on each channel triggers lazy rebuild, removing the pipes
  ///
  /// This provides "eventual consistency" - the circuit processes the unsubscription
  /// in sequence with other events. The unsubscription is not immediately visible to
  /// all channels - it becomes visible when each channel next emits (lazy rebuild).
  ///
  /// **Lazy unsubscription**: Like subscription, unsubscription uses lazy rebuild.
  /// Channels detect the unsubscription on their next emission and rebuild their
  /// pipe lists to exclude the unsubscribed pipes. This avoids global coordination
  /// and blocking.
  ///
  /// ## Resubscription
  ///
  /// After closing a subscription, the same subscriber can be resubscribed to the
  /// same or different sources. Each call to `subscribe()` creates a new,
  /// independent subscription instance.
  ///
  /// @see Source#subscribe(Subscriber)
  /// @see Subscriber
  /// @see Resource#close()
  /// @see Scope
  static final class NanoSubscription
    implements Subscription {

    /// The subject identity for this subscription.
    private final Subject < Subscription > subject;

    /// Action to run on close.
    private final Runnable onClose;

    /// Whether this subscription has been closed.
    private volatile boolean closed;

    /// Creates a new subscription with the given subject and close action.
    ///
    /// @param subject the subject identity for this subscription
    /// @param onClose action to run when subscription is closed
    NanoSubscription ( Subject < Subscription > subject, Runnable onClose ) {
      this.subject = subject;
      this.onClose = onClose;
    }

    /// Returns the subject identity of this subscription.
    ///
    /// @return the subject of this subscription
    @Override
    public Subject < Subscription > subject () {
      return subject;
    }

    /// Closes this subscription, unregistering from the source.
    /// Idempotent - repeated calls have no effect.
    @Override
    public void close () {
      if ( !closed ) {
        closed = true;
        onClose.run ();
      }
    }

  }

  /// A container of channels that can be subscribed to and looked up by name.
  ///
  /// Conduit provides a named lookup mechanism ([Lookup]) for obtaining percepts
  /// (typically channels wrapped in domain objects) and a subscription mechanism
  /// ([Source]) for receiving callbacks when channels are first activated.
  ///
  /// ## Key Characteristics
  ///
  /// - **Percept caching**: Percepts are created on-demand and cached by name
  /// - **Lazy subscription**: Subscriber callbacks occur during channel rebuild,
  ///   triggered by first emission after subscription
  /// - **Circuit-bound**: All processing occurs on the circuit's thread
  ///
  /// ## Threading Model
  ///
  /// - `percept(Name)` is thread-safe (caller thread)
  /// - `subscribe()` enqueues to circuit thread (async)
  /// - Subscriber callbacks execute on circuit thread
  ///
  /// @param <P> the percept type (extends Percept)
  /// @param <E> the emission type
  /// @see Lookup
  /// @see Source
  /// @see Channel
  static final class NanoConduit < P extends Percept, E >
    implements Conduit < P, E > {

    /// The subject identity for this conduit.
    private final Subject < Conduit < P, E > > subject;

    /// Function to create percepts from channels.
    private final Function < Channel < E >, P > composer;

    /// Optional flow configurer to apply to created pipes.
    private final Configurer < Flow < E > > flowConfigurer;

    /// Cache of percepts by name (thread-safe for concurrent access).
    private final Map < Name, P > percepts = new ConcurrentHashMap <> ();

    /// List of active subscribers.
    private final List < Subscriber < E > > subscribers = new ArrayList <> ();

    /// Maps subscriber to their registered pipes (per channel).
    /// When subscription closes, we can remove all pipes for that subscriber.
    private final Map < Subscriber < E >, Map < Name, List < Consumer < E > > > > subscriberPipes =
      new IdentityHashMap <> ();

    /// Tracks which subscribers have seen which channels.
    private final Set < String > subscriberChannelPairs = new HashSet <> ();

    /// Reference to the circuit (for submit and closed checks).
    private final NanoCircuit circuit;

    /// Creates a new conduit with the given subject, composer, and circuit references.
    ///
    /// @param subject the subject identity for this conduit
    /// @param composer function to create percepts from channels
    /// @param circuitSubmit submit function for async emission processing
    /// @param circuitClosed supplier for the circuit's closed flag
    NanoConduit (
      Subject < Conduit < P, E > > subject,
      Function < Channel < E >, P > composer,
      NanoCircuit circuit
    ) {
      this ( subject, composer, circuit, null );
    }

    /// Creates a new conduit with the given subject, composer, flow configurer, and circuit references.
    ///
    /// @param subject the subject identity for this conduit
    /// @param composer function to create percepts from channels
    /// @param circuitSubmit submit function for async emission processing
    /// @param circuitClosed supplier for the circuit's closed flag
    /// @param flowConfigurer optional flow configurer to apply to created pipes
    NanoConduit (
      Subject < Conduit < P, E > > subject,
      Function < Channel < E >, P > composer,
      NanoCircuit circuit,
      Configurer < Flow < E > > flowConfigurer
    ) {
      this.subject = subject;
      this.composer = composer;
      this.circuit = circuit;
      this.flowConfigurer = flowConfigurer;
    }

    /// Returns the subject identity of this conduit.
    @Override
    public Subject < Conduit < P, E > > subject () {
      return subject;
    }

    /// Returns or creates the percept for the given name.
    @Override
    public P percept ( Name name ) {
      // Optimization: use get() first (fast path), then putIfAbsent() on miss (slow path)
      P cached = percepts.get ( name );
      if ( cached != null ) {
        return cached;
      }
      // Cache miss - create new percept
      return createAndCachePercept ( name );
    }

    /// Creates and caches a new percept for the given name.
    private P createAndCachePercept ( Name name ) {
        // Create channel subject with channel name and conduit as parent
        NanoSubject < Channel < E > > channelSubject = new NanoSubject <> (
          name,
          (NanoSubject < ? >) subject,
          Channel.class
        );

        // Create pipe subject with channel name and channel as parent
        NanoSubject < Pipe < E > > pipeSubject = new NanoSubject <> (
          name,
          channelSubject,
          Pipe.class
        );

        // The pipe's consumer queues emissions to circuit for async processing
        Consumer < E > router = emission -> {
          // Ignore emissions after circuit close
          if ( !circuit.running ) {
            return;
          }
          // Queue the emission processing to the circuit thread
          circuit.submit ( () -> {
            // Lazy activation: notify subscribers on first emission
            for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
              String key = System.identityHashCode ( subscriber ) + ":" + name;
              if ( subscriberChannelPairs.add ( key ) ) {
                // First time this subscriber sees this channel - invoke callback
                NanoRegistrar < E > registrar = new NanoRegistrar <> ();
                if ( subscriber instanceof NanoSubscriber < E > nano ) {
                  nano.activate ( channelSubject, registrar );
                }
                // Store registered pipes for this subscriber/channel
                subscriberPipes
                  .computeIfAbsent ( subscriber, k -> new HashMap <> () )
                  .computeIfAbsent ( name, k -> new ArrayList <> () )
                  .addAll ( registrar.pipes () );
              }
            }

            // Emit to all pipes from active subscribers for this channel
            for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
              Map < Name, List < Consumer < E > > > pipes = subscriberPipes.get ( subscriber );
              if ( pipes != null ) {
                List < Consumer < E > > channelPipes = pipes.get ( name );
                if ( channelPipes != null ) {
                  for ( Consumer < E > pipe : channelPipes ) {
                    pipe.accept ( emission );
                  }
                }
              }
            }
          } );
        };
        // Create the router consumer - this is what the channel passes to pipes it creates
        // If flow configurer is set, wrap the router with flow transformations
        Consumer < E > channelRouter;
        if ( flowConfigurer != null ) {
          // Create a pipe that applies flow transformations before routing
          Pipe < E > basePipe = new NanoPipe <> ( pipeSubject, router );
          NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, basePipe );
          flowConfigurer.configure ( flow );
          Pipe < E > flowPipe = flow.pipe ();
          channelRouter = flowPipe::emit;
        } else {
          channelRouter = router;
        }

        NanoChannel < E > channel = new NanoChannel <> ( channelSubject, channelRouter );
        P percept = composer.apply ( channel );
        // Use putIfAbsent to handle concurrent creation race
        P existing = percepts.putIfAbsent ( name, percept );
        return existing != null ? existing : percept;
    }

    /// Unsubscribes a subscriber, removing their pipes from emission routing.
    void unsubscribe ( Subscriber < E > subscriber ) {
      subscribers.remove ( subscriber );
      subscriberPipes.remove ( subscriber );
      // Clean up the channel pairs tracking for this subscriber
      subscriberChannelPairs.removeIf ( key ->
        key.startsWith ( System.identityHashCode ( subscriber ) + ":" )
      );
    }

    /// Subscribes to receive callbacks when channels are activated.
    @Override
    public Subscription subscribe ( Subscriber < E > subscriber ) {
      // Validate subscriber belongs to the same circuit as this conduit
      if ( subscriber.subject () instanceof NanoSubject < ? > subSubject ) {
        NanoSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
        NanoSubject < ? > conduitCircuit = ( (NanoSubject < ? >) subject ).findCircuitAncestor ();
        if ( subscriberCircuit != null && conduitCircuit != null
          && subscriberCircuit != conduitCircuit ) {
          throw new NanoException ( "Subscriber belongs to a different circuit" );
        }
      }

      subscribers.add ( subscriber );
      // Create Subscription subject with Conduit as parent
      NanoSubject < Subscription > subSubject = new NanoSubject <> (
        subscriber.subject ().name (),
        (NanoSubject < ? >) subject,
        Subscription.class
      );
      Subscription subscription = new NanoSubscription ( subSubject, () -> unsubscribe ( subscriber ) );
      // Track subscription in subscriber so closing subscriber closes all its subscriptions
      if ( subscriber instanceof NanoSubscriber < E > nano ) {
        nano.trackSubscription ( subscription );
      }
      return subscription;
    }

    /// Creates a reservoir to capture emissions from this conduit.
    /// The reservoir subscribes to this conduit and captures all emissions.
    @Override
    public Reservoir < E > reservoir () {
      NanoSubject < Reservoir < E > > resSubject = new NanoSubject <> (
        cortex().name("reservoir"),
        (NanoSubject < ? >) subject,
        Reservoir.class
      );
      NanoReservoir < E > reservoir = new NanoReservoir <> ( resSubject );
      // Subscribe the reservoir to capture all emissions
      Subscriber < E > sub = new NanoSubscriber <> (
        new NanoSubject <> (
          cortex().name ( "reservoir.subscriber" ),
          resSubject,
          Subscriber.class
        ),
        (channelSubject, registrar) -> registrar.register ( emission ->
          reservoir.capture ( emission, channelSubject )
        )
      );
      subscribe ( sub );
      return reservoir;
    }

  }

  /// A registrar that collects pipes during subscriber callback.
  ///
  /// @param <E> the emission type
  static final class NanoRegistrar < E >
    implements Registrar < E > {

    /// Pipes registered by the subscriber.
    private final List < Consumer < E > > pipes = new ArrayList <> ();

    @Override
    public void register ( Receptor < ? super E > receptor ) {
      pipes.add ( receptor::receive );
    }

    /// Returns the registered pipes.
    List < Consumer < E > > pipes () {
      return pipes;
    }

    @Override
    public void register ( Pipe < ? super E > pipe ) {
      pipes.add ( pipe::emit );
    }

  }

  /// A container for grouping resources with coordinated lifecycle management.
  ///
  /// Scope provides structured resource management - resources registered with
  /// a scope are automatically closed when the scope closes. Resources are closed
  /// in reverse registration order (LIFO).
  ///
  /// ## Key Features
  ///
  /// - **Automatic cleanup**: All registered resources closed on scope close
  /// - **LIFO ordering**: Last registered is first closed
  /// - **Child scopes**: Create nested scopes for hierarchical management
  /// - **Idempotent close**: Safe to call close() multiple times
  ///
  /// @see Resource
  /// @see Cortex#scope()
  static final class NanoScope
    implements Scope {

    /// The subject identity for this scope.
    private final Subject < Scope > subject;

    /// Parent scope (for hierarchy).
    private final NanoScope parent;

    /// Registered resources (closed in reverse order). Lazily initialized.
    private List < Resource > resources;

    /// Child scopes. Lazily initialized.
    private List < NanoScope > children;

    /// Cache of closures per resource (cleared when consumed). Lazily initialized.
    private Map < Resource, NanoClosure < ? > > closureCache;

    /// Whether this scope has been closed.
    private volatile boolean closed;

    /// Creates a new root scope with the given subject.
    NanoScope ( Subject < Scope > subject ) {
      this.subject = subject;
      this.parent = null;
    }

    /// Creates a new child scope with the given subject and parent.
    NanoScope ( Subject < Scope > subject, NanoScope parent ) {
      this.subject = subject;
      this.parent = parent;
    }

    boolean isClosed () {
      return closed;
    }

    /// Called by NanoClosure when consumed to remove from cache and resources list.
    void closureConsumed ( Resource resource ) {
      if ( closureCache != null ) closureCache.remove ( resource );
      if ( resources != null ) resources.remove ( resource );
    }

    @Override
    public Subject < Scope > subject () {
      return subject;
    }

    @Override
    public String part () {
      return subject.name ().part ();
    }

    @Override
    public Optional < Scope > enclosure () {
      return Optional.ofNullable ( parent );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public < R extends Resource > Closure < R > closure ( R resource ) {
      if ( closed ) {
        throw new IllegalStateException ( "Scope is closed" );
      }
      // Lazy init closure cache
      if ( closureCache == null ) {
        closureCache = new IdentityHashMap <> ();
      }
      // Check cache for existing non-consumed closure
      NanoClosure < ? > cached = closureCache.get ( resource );
      if ( cached != null && !cached.isConsumed () ) {
        return (Closure < R >) cached;
      }
      // Create new closure and cache it
      NanoClosure < R > closure = new NanoClosure <> ( resource, this );
      closureCache.put ( resource, closure );
      // Lazy init resources list
      if ( resources == null ) {
        resources = new ArrayList <> ();
      }
      // Register resource so it gets closed when scope closes (if not consumed)
      resources.add ( resource );
      return closure;
    }

    @Override
    public < R extends Resource > R register ( R resource ) {
      if ( closed ) {
        throw new IllegalStateException ( "Scope is closed" );
      }
      // Lazy init resources list
      if ( resources == null ) {
        resources = new ArrayList <> ();
      }
      resources.add ( resource );
      return resource;
    }

    @Override
    public Scope scope () {
      if ( closed ) {
        throw new IllegalStateException ( "Scope is closed" );
      }
      NanoSubject < Scope > childSubject = new NanoSubject <> (
        NanoCortex.SCOPE_NAME, (NanoSubject < ? >) subject, Scope.class
      );
      NanoScope child = new NanoScope ( childSubject, this );
      // Lazy init children list
      if ( children == null ) {
        children = new ArrayList <> ();
      }
      children.add ( child );
      return child;
    }

    @Override
    public Scope scope ( Name name ) {
      if ( closed ) {
        throw new IllegalStateException ( "Scope is closed" );
      }
      NanoSubject < Scope > childSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Scope.class
      );
      NanoScope child = new NanoScope ( childSubject, this );
      // Lazy init children list
      if ( children == null ) {
        children = new ArrayList <> ();
      }
      children.add ( child );
      return child;
    }

    @Override
    public void close () {
      if ( closed ) {
        return; // Idempotent
      }
      closed = true;

      // Close children first (if any)
      if ( children != null ) {
        for ( NanoScope child : children ) {
          try {
            child.close ();
          } catch ( java.lang.Exception e ) {
            // Suppress and continue
          }
        }
      }

      // Close resources in reverse order (LIFO) (if any)
      if ( resources != null ) {
        for ( int i = resources.size () - 1; i >= 0; i-- ) {
          try {
            resources.get ( i ).close ();
          } catch ( java.lang.Exception e ) {
            // Suppress and continue
          }
        }
      }
    }

    @Override
    public String toString () {
      return path ().toString ();
    }

  }

  /// The central event orchestration hub.
  /// Uses dual-queue architecture per William's design:
  /// - Ingress queue: External emissions from outside the circuit (thread-safe)
  /// - Transit deque: Internal/recursive emissions (single-threaded, priority)
  static final class NanoCircuit
    implements Circuit {

    /// Reusable task wrapper to avoid lambda allocation per emission.
    /// Stores consumer and value directly for zero-allocation execution.
    private static final class DirectTask < E > implements Runnable {
      private final Consumer < E > consumer;
      private final E value;

      DirectTask ( Consumer < E > consumer, E value ) {
        this.consumer = consumer;
        this.value = value;
      }

      @Override
      public void run () {
        consumer.accept ( value );
      }
    }

    private final Subject < Circuit > subject;
    private final List < Subscriber < State > > subscribers = new ArrayList <> ();
    private volatile boolean running = true;

    /// Ingress queue for external Runnable tasks (thread-safe)
    private final ConcurrentLinkedQueue < Runnable > ingressQueue = new ConcurrentLinkedQueue <> ();

    /// Transit deque for internal/recursive emissions (single-threaded access only)
    private final ArrayDeque < Runnable > transitDeque = new ArrayDeque <> ();

    /// Lazy virtual thread - created on first submit()
    private volatile Thread processingThread;

    /// Tracks if currently processing (to count in-flight work)
    private volatile int processing = 0;

    /// Pipe cache by target identity (for pipe(target) methods)
    private final ConcurrentHashMap < Object, Pipe < ? > > pipeCache = new ConcurrentHashMap <> ();

    NanoCircuit ( Subject < Circuit > subject ) {
      this.subject = subject;
      // Lazy thread creation - defer until first submit()
    }

    /// Submits a task to the circuit. Uses transit deque if called during processing
    /// (recursive emission), otherwise uses ingress queue (external emission).
    void submit ( Runnable task ) {
      if ( !running ) return;
      Thread thread = processingThread;
      if ( thread != null && Thread.currentThread () == thread ) {
        // Recursive emission during processing - use transit (priority)
        // Circuit thread is the only writer to transitDeque - safe direct access
        transitDeque.addLast ( task );
      } else {
        // External emission - use ingress (thread-safe)
        ingressQueue.offer ( task );
        // Ensure processing thread exists (double-checked locking)
        ensureThreadStarted ();
      }
    }

    /// Ensures the processing thread is started (lazy initialization with double-checked locking).
    private void ensureThreadStarted () {
      if ( processingThread == null ) {
        synchronized ( this ) {
          if ( processingThread == null ) {
            processingThread = Thread.ofVirtual ()
              .name ( "circuit-" + subject.name () )
              .start ( this::processLoop );
          }
        }
      } else {
        // Wake the processing thread immediately (replaces polling)
        LockSupport.unpark ( processingThread );
      }
    }

    /// Optimized direct submission that avoids lambda allocation.
    /// Used by ValvePipe to submit emissions without creating capturing lambdas.
    /// Performance: ~14x faster than submit(() -> consumer.accept(value))
    < E > void submitDirect ( Consumer < E > consumer, E value ) {
      if ( !running ) return;
      Thread thread = processingThread;
      if ( thread != null && Thread.currentThread () == thread ) {
        // Already on circuit thread - execute inline (no allocation at all)
        consumer.accept ( value );
      } else {
        // External thread - queue a DirectTask (single allocation vs lambda + capture)
        ingressQueue.offer ( new DirectTask <> ( consumer, value ) );
        // Ensure processing thread exists (double-checked locking)
        ensureThreadStarted ();
      }
    }

    /// Background processing loop - processes transit deque (priority), then ingress.
    private void processLoop () {
      while ( running || !transitDeque.isEmpty () || !ingressQueue.isEmpty () ) {
        Runnable task;
        boolean processedItem = false;

        // Process transit deque first (depth-first priority)
        while ( ( task = transitDeque.pollFirst () ) != null ) {
          processing++;
          try {
            task.run ();
          } catch ( java.lang.Exception e ) {
            // Swallow - per TCK specification
          } finally {
            processing--;
          }
          processedItem = true;
        }

        if ( !processedItem ) {
          Runnable ingress = ingressQueue.poll ();
          if ( ingress != null ) {
            processing++;
            try {
              ingress.run ();
            } catch ( java.lang.Exception e ) {
              // Swallow - per TCK specification
            } finally {
              processing--;
            }
            processedItem = true;
          }
        }

        if ( !processedItem ) {
          Thread.onSpinWait ();
        }
      }
    }

    @Override
    public Subject < Circuit > subject () {
      return subject;
    }

    @Override
    public void await () {
      // Prevent deadlock: cannot await from within the circuit's processing thread
      if ( Thread.currentThread () == processingThread ) {
        throw new IllegalStateException (
          "Cannot call Circuit::await from within a circuit's thread"
        );
      }
      // Wait until queues are empty AND no work is currently being processed
      while ( !ingressQueue.isEmpty () || !transitDeque.isEmpty () || processing > 0 ) {
        Thread.yield ();  // Give circuit thread CPU time to drain queues
      }
    }

    @Override
    public void close () {
      running = false;
      // Virtual thread is daemon - will exit naturally when queues drain
    }

    @Override
    public < I, E > Cell < I, E > cell (
      Name name,
      Composer < E, Pipe < I > > ingress,
      Composer < E, Pipe < E > > egress,
      Receptor < ? super E > receptor
    ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( ingress, "ingress must not be null" );
      requireNonNull ( egress, "egress must not be null" );
      requireNonNull ( receptor, "receptor must not be null" );

      // Create Cell subject with proper name and Circuit as parent
      NanoSubject < Cell < I, E > > cellSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Cell.class
      );

      // Create the cell with proper data flow:
      // 1. Input (I) → ingress pipe → Channel<E>
      // 2. Channel<E> → egress pipe → receptor
      return new NanoCell <> ( cellSubject, this, ingress, egress, receptor );
    }

    @Override
    public < P extends Percept, E > Conduit < P, E > conduit(
      Name name,
      Composer < E, ? extends P > composer
    ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( composer, "composer must not be null" );
      // Create Conduit subject with Circuit as parent
      NanoSubject < Conduit < P, E > > conduitSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Conduit.class
      );
      return new NanoConduit <> ( conduitSubject, channel -> composer.compose ( channel ), this );
    }

    @Override
    public < P extends Percept, E > Conduit < P, E > conduit (
      Name name,
      Composer < E, ? extends P > composer,
      Configurer < Flow < E > > configurer
    ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( composer, "composer must not be null" );
      requireNonNull ( configurer, "configurer must not be null" );
      // Create Conduit subject with Circuit as parent
      NanoSubject < Conduit < P, E > > conduitSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Conduit.class
      );
      return new NanoConduit <> (
        conduitSubject,
        channel -> composer.compose ( channel ),
        this,
        configurer
      );
    }

    /// Creates a pipe subject. If name is null, delegates to circuit's name.
    private < E > Subject < Pipe < E > > createPipeSubject ( Name name ) {
      return new NanoSubject <> ( name, (NanoSubject < ? >) subject, Pipe.class );
    }

    // ========== Anonymous pipe methods (null name delegates to circuit) ==========

    @Override
    @SuppressWarnings ( "unchecked" )
    public < E > Pipe < E > pipe ( Pipe < E > target ) {
      requireNonNull ( target, "target must not be null" );
      // Use target identity for caching
      return (Pipe < E >) pipeCache.computeIfAbsent ( target, t -> {
        Subject < Pipe < E > > pipeSubject = createPipeSubject ( null );
        return new ValvePipe <> ( pipeSubject, this, target::emit );
      } );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public < E > Pipe < E > pipe ( Receptor < E > receptor ) {
      requireNonNull ( receptor, "receptor must not be null" );
      // Use receptor identity for caching
      return (Pipe < E >) pipeCache.computeIfAbsent ( receptor, r -> {
        Subject < Pipe < E > > pipeSubject = createPipeSubject ( null );
        return new ValvePipe <> ( pipeSubject, this, receptor::receive );
      } );
    }

    @Override
    public < E > Pipe < E > pipe ( Pipe < E > target, Configurer < Flow < E > > configurer ) {
      requireNonNull ( target, "target must not be null" );
      requireNonNull ( configurer, "configurer must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( null );
      ValvePipe < E > asyncPipe = new ValvePipe <> ( pipeSubject, this, target::emit );
      NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, asyncPipe );
      configurer.configure ( flow );
      return flow.pipe ();
    }

    @Override
    public < E > Pipe < E > pipe ( Receptor < E > receptor, Configurer < Flow < E > > configurer ) {
      requireNonNull ( receptor, "receptor must not be null" );
      requireNonNull ( configurer, "configurer must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( null );
      ValvePipe < E > asyncPipe = new ValvePipe <> ( pipeSubject, this, receptor::receive );
      NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, asyncPipe );
      configurer.configure ( flow );
      return flow.pipe ();
    }

    // ========== Named pipe methods ==========

    @Override
    public < E > Pipe < E > pipe ( Name name, Pipe < E > target ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( target, "target must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( name );
      return new ValvePipe <> ( pipeSubject, this, target::emit );
    }

    @Override
    public < E > Pipe < E > pipe ( Name name, Receptor < E > receptor ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( receptor, "receptor must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( name );
      return new ValvePipe <> ( pipeSubject, this, receptor::receive );
    }

    @Override
    public < E > Pipe < E > pipe ( Name name, Pipe < E > target, Configurer < Flow < E > > configurer ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( target, "target must not be null" );
      requireNonNull ( configurer, "configurer must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( name );
      ValvePipe < E > asyncPipe = new ValvePipe <> ( pipeSubject, this, target::emit );
      NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, asyncPipe );
      configurer.configure ( flow );
      return flow.pipe ();
    }

    @Override
    public < E > Pipe < E > pipe ( Name name, Receptor < E > receptor, Configurer < Flow < E > > configurer ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( receptor, "receptor must not be null" );
      requireNonNull ( configurer, "configurer must not be null" );
      Subject < Pipe < E > > pipeSubject = createPipeSubject ( name );
      ValvePipe < E > asyncPipe = new ValvePipe <> ( pipeSubject, this, receptor::receive );
      NanoFlow < E > flow = new NanoFlow <> ( pipeSubject, asyncPipe );
      configurer.configure ( flow );
      return flow.pipe ();
    }

    @Override
    public < E > Subscriber < E > subscriber (
      Name name,
      BiConsumer < Subject < Channel < E > >, Registrar < E > > callback
    ) {
      requireNonNull ( name, "name must not be null" );
      requireNonNull ( callback, "callback must not be null" );
      NanoSubject < Subscriber < E > > subSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Subscriber.class
      );
      return new NanoSubscriber <> ( subSubject, callback );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Subscription subscribe ( Subscriber < State > subscriber ) {
      // Validate subscriber belongs to the same circuit
      if ( subscriber.subject () instanceof NanoSubject < ? > subSubject ) {
        NanoSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
        // This circuit is the source, so compare directly
        if ( subscriberCircuit != null && subscriberCircuit != subject ) {
          throw new NanoException ( "Subscriber belongs to a different circuit" );
        }
      }

      subscribers.add ( subscriber );
      Subject < Subscription > subSubject = (Subject < Subscription >) (Subject < ? >) subject;
      return new NanoSubscription ( subSubject, () -> subscribers.remove ( subscriber ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Reservoir < State > reservoir () {
      Subject < Reservoir < State > > resSubject = (Subject < Reservoir < State > >) (Subject < ? >) subject;
      return new NanoReservoir <> ( resSubject );
    }

  }

  /// A single-use block-scoped resource wrapper.
  ///
  /// @param <R> the resource type
  static final class NanoClosure < R extends Resource >
    implements Closure < R > {

    private final R resource;
    private final NanoScope scope;
    private boolean consumed;

    NanoClosure ( R resource, NanoScope scope ) {
      this.resource = resource;
      this.scope = scope;
    }

    boolean isConsumed () {
      return consumed;
    }

    @Override
    public void consume ( Consumer < ? super R > consumer ) {
      // If scope is closed or already consumed, no-op (fail-safe)
      if ( scope.isClosed () || consumed ) {
        return;
      }
      consumed = true;
      // Remove from scope's cache so next closure() call creates new one
      scope.closureConsumed ( resource );
      try {
        consumer.accept ( resource );
      } finally {
        resource.close ();
      }
    }

  }

  /// A hierarchical computational cell that receives input and emits output.
  ///
  /// Cells form tree structures where emissions flow upward from children to parents.
  /// Each cell can have child cells (via percept()), receive input (via receive()),
  /// and emit output that flows to parent cells or subscribers.
  ///
  /// Data flow for root cell:
  /// 1. cell.receive(I) → ingressPipe.emit(I) → transforms to E
  /// 2. E flows to channel → egressPipe processes E
  /// 3. Final E reaches receptor
  ///
  /// Data flow for child cells:
  /// 1. child.receive(E) → ingress (identity) → E to channel
  /// 2. channel → egress → parent's internal channel
  /// 3. Parent's subscribers see the emission
  ///
  /// @param <I> input type received by the cell
  /// @param <E> emission type output by the cell
  /// @see Receptor
  /// @see Lookup
  /// @see Source
  static final class NanoCell < I, E >
    implements Cell < I, E > {

    /// The subject identity for this cell.
    private final Subject < Cell < I, E > > subject;

    /// The circuit that owns this cell.
    private final NanoCircuit circuit;

    /// Parent cell (null for root cells).
    private final NanoCell < ?, E > parentCell;

    /// Child cells by name.
    private final Map < Name, NanoCell < E, E > > children = new HashMap <> ();

    /// Subscribers to this cell's emissions.
    private final List < Subscriber < E > > subscribers = new ArrayList <> ();

    /// Subscriber-channel activation tracking.
    private final Set < String > subscriberChannelPairs = new HashSet <> ();

    /// Pipes registered by subscribers for each child channel.
    private final Map < Subscriber < E >, Map < Name, List < Consumer < E > > > > subscriberPipes = new IdentityHashMap <> ();

    /// The pipe that processes input (receives I, transforms to E).
    private final Pipe < I > ingressPipe;

    /// The receptor for final output.
    private final Receptor < ? super E > receptor;

    /// Internal channel for routing emissions to subscribers/parent.
    private final NanoChannel < E > internalChannel;

    /// Creates a root cell with proper data flow pipeline.
    ///
    /// @param subject the cell's subject identity
    /// @param circuit the owning circuit
    /// @param ingress composer that creates Pipe<I> from Channel<E>
    /// @param egress composer that creates Pipe<E> from Channel<E>
    /// @param receptor final destination for E values
    NanoCell (
      Subject < Cell < I, E > > subject,
      NanoCircuit circuit,
      Composer < E, Pipe < I > > ingress,
      Composer < E, Pipe < E > > egress,
      Receptor < ? super E > receptor
    ) {
      this.subject = subject;
      this.circuit = circuit;
      this.parentCell = null;
      this.receptor = receptor;

      // Create internal channel that routes to receptor and subscribers
      @SuppressWarnings ( "unchecked" )
      Subject < Channel < E > > channelSubject =
        (Subject < Channel < E > >) (Subject < ? >) subject;

      // The router consumer: delivers to receptor and notifies subscribers
      this.internalChannel = new NanoChannel <> ( channelSubject, this::routeEmission );

      // Create egress pipe (processes E before final delivery)
      Pipe < E > egressPipe = egress.compose ( internalChannel );

      // Create a channel that delivers to egress (using egressPipe::emit as the router)
      NanoChannel < E > egressChannel = new NanoChannel <> ( channelSubject, egressPipe::emit );

      // Create ingress pipe (receives I, transforms to E, sends to egress)
      this.ingressPipe = ingress.compose ( egressChannel );
    }

    /// Creates a child cell that routes emissions to parent.
    ///
    /// Child cells have type Cell<E, E> - they receive the parent's output type
    /// and emit the same type upward.
    @SuppressWarnings ( "unchecked" )
    NanoCell (
      Subject < Cell < I, E > > subject,
      NanoCircuit circuit,
      NanoCell < ?, E > parentCell,
      Name childName
    ) {
      this.subject = subject;
      this.circuit = circuit;
      this.parentCell = parentCell;
      this.receptor = e -> {};  // Children route to parent, not receptor

      // Create internal channel that routes to parent
      Subject < Channel < E > > channelSubject =
        (Subject < Channel < E > >) (Subject < ? >) subject;

      // Child's router consumer sends to parent's internal handling
      Consumer < E > childRouter = e -> parentCell.handleChildEmission ( childName, e );

      this.internalChannel = new NanoChannel <> ( channelSubject, childRouter );

      // For children, ingress is a pipe that routes to the channel
      Subject < Pipe < E > > pipeSubject = new NanoSubject <> (
        childName,
        (NanoSubject < ? >) subject,
        Pipe.class
      );
      this.ingressPipe = (Pipe < I >) (Pipe < ? >) new NanoPipe <> ( pipeSubject, childRouter );
    }

    /// Routes an emission to receptor and subscribers.
    private void routeEmission ( E emission ) {
      // Deliver to receptor
      receptor.receive ( emission );
    }

    /// Handles emission from a child cell (used for parent subscribers).
    void handleChildEmission ( Name childName, E emission ) {
      // Activate subscribers for this child if first time
      Subject < Channel < E > > channelSubject = internalChannel.subject ();

      for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
        String key = System.identityHashCode ( subscriber ) + ":" + childName;
        if ( subscriberChannelPairs.add ( key ) ) {
          // First time this subscriber sees this child - activate
          NanoRegistrar < E > registrar = new NanoRegistrar <> ();
          if ( subscriber instanceof NanoSubscriber < E > nano ) {
            nano.activate ( channelSubject, registrar );
          }
          subscriberPipes
            .computeIfAbsent ( subscriber, k -> new HashMap <> () )
            .computeIfAbsent ( childName, k -> new ArrayList <> () )
            .addAll ( registrar.pipes () );
        }
      }

      // Deliver to all subscriber pipes for this child
      for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
        Map < Name, List < Consumer < E > > > pipes = subscriberPipes.get ( subscriber );
        if ( pipes != null ) {
          List < Consumer < E > > childPipes = pipes.get ( childName );
          if ( childPipes != null ) {
            for ( Consumer < E > pipe : childPipes ) {
              pipe.accept ( emission );
            }
          }
        }
      }

      // Also route upward to parent if this cell has a parent
      if ( parentCell != null ) {
        parentCell.handleChildEmission ( subject.name (), emission );
      }

      // Also deliver to our own receptor
      receptor.receive ( emission );
    }

    @Override
    public Subject < Cell < I, E > > subject () {
      return subject;
    }

    @Override
    public String part () {
      return subject.name ().toString ();
    }

    @Override
    public Optional < Cell < I, E > > enclosure () {
      // Cast the parent to the expected type
      @SuppressWarnings ( "unchecked" )
      Cell < I, E > p = (Cell < I, E >) (Cell < ?, ? >) parentCell;
      return Optional.ofNullable ( p );
    }

    @Override
    public void receive ( I emission ) {
      // Route input through circuit queue for async processing
      circuit.submit ( () -> ingressPipe.emit ( emission ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Cell < I, E > percept ( Name name ) {
      // Child cells have type Cell<E, E> - parent's output becomes child's input/output
      return (Cell < I, E >) (Cell < ?, ? >) children.computeIfAbsent ( name, n -> {
        NanoSubject < Cell < E, E > > childSubject = new NanoSubject <> (
          n, (NanoSubject < ? >) subject, Cell.class
        );
        return new NanoCell < E, E > ( childSubject, circuit, this, n );
      } );
    }

    @Override
    public Subscription subscribe ( Subscriber < E > subscriber ) {
      subscribers.add ( subscriber );
      NanoSubject < Subscription > subSubject = new NanoSubject <> (
        subscriber.subject ().name (),
        (NanoSubject < ? >) subject,
        Subscription.class
      );
      return new NanoSubscription ( subSubject, () -> {
        subscribers.remove ( subscriber );
        subscriberPipes.remove ( subscriber );
        subscriberChannelPairs.removeIf ( key ->
          key.startsWith ( System.identityHashCode ( subscriber ) + ":" )
        );
      } );
    }

    @Override
    public Reservoir < E > reservoir () {
      NanoSubject < Reservoir < E > > resSubject = new NanoSubject <> (
        cortex().name ( "reservoir" ),
        (NanoSubject < ? >) subject,
        Reservoir.class
      );
      NanoReservoir < E > reservoir = new NanoReservoir <> ( resSubject );
      // Subscribe reservoir to capture emissions
      Subscriber < E > sub = new NanoSubscriber <> (
        new NanoSubject <> (
          cortex().name ( "reservoir.subscriber" ),
          resSubject,
          Subscriber.class
        ),
        (channelSubject, registrar) -> registrar.register ( emission ->
          reservoir.capture ( emission, channelSubject )
        )
      );
      subscribe ( sub );
      return reservoir;
    }

  }

  /// A subscriber that holds a callback to be invoked when channels are activated.
  ///
  /// Subscribers are created via Circuit.subscriber(name, callback) and passed
  /// to Source.subscribe(). When a channel receives its first emission, the
  /// callback is invoked with the channel's subject and a registrar for
  /// registering pipes to receive emissions.
  ///
  /// @param <E> the emission type
  static final class NanoSubscriber < E >
    implements Subscriber < E > {

    /// The subject identity for this subscriber.
    private final Subject < Subscriber < E > > subject;

    /// The callback invoked when a channel is activated.
    private final BiConsumer < Subject < Channel < E > >, Registrar < E > > callback;

    /// Whether this subscriber has been closed.
    private volatile boolean closed;

    /// Subscriptions created for this subscriber (to close when subscriber closes).
    private final List < Subscription > subscriptions = new CopyOnWriteArrayList <> ();

    /// Creates a new subscriber with the given subject and callback.
    ///
    /// @param subject the subject identity for this subscriber
    /// @param callback the callback to invoke when channels are activated
    NanoSubscriber (
      Subject < Subscriber < E > > subject,
      BiConsumer < Subject < Channel < E > >, Registrar < E > > callback
    ) {
      this.subject = subject;
      this.callback = callback;
    }

    /// Returns the subject identity of this subscriber.
    @Override
    public Subject < Subscriber < E > > subject () {
      return subject;
    }

    /// Invokes the callback for a channel activation.
    void activate ( Subject < Channel < E > > channelSubject, Registrar < E > registrar ) {
      if ( !closed ) {
        callback.accept ( channelSubject, registrar );
      }
    }

    /// Tracks a subscription created for this subscriber.
    void trackSubscription ( Subscription subscription ) {
      if ( !closed ) {
        subscriptions.add ( subscription );
      } else {
        // Already closed, close the new subscription immediately
        subscription.close ();
      }
    }

    /// Closes this subscriber and all its tracked subscriptions.
    @Override
    public void close () {
      if ( !closed ) {
        closed = true;
        // Close all tracked subscriptions
        for ( Subscription subscription : subscriptions ) {
          subscription.close ();
        }
        subscriptions.clear ();
      }
    }

  }

  /// Concrete implementation of Substrates.Exception for throwing errors.
  static final class NanoException
    extends io.humainary.substrates.api.Substrates.Exception {

    NanoException ( String message ) {
      super ( message );
    }

  }

  /// A hierarchical dot-separated name using dual-cache interning.
  /// Primary cache uses full path for O(1) lookup. Interning guarantee maintained by
  /// ensuring all construction paths go through the same cache.
  static final class NanoName
    implements Name {

    /// Primary cache: full path string → NanoName. All construction goes through this.
    private static final ConcurrentHashMap < String, NanoName > CACHE = new ConcurrentHashMap <> ();
    /// Enum cache: Enum instance → NanoName. Avoids redundant getDeclaringClass() + getCanonicalName() calls.
    private static final ConcurrentHashMap < Enum < ? >, NanoName > ENUM_CACHE = new ConcurrentHashMap <> ();
    private static final char FULLSTOP = '.';
    private final String path;
    private final String segment;
    /// Cached depth - computed at construction, avoids traversal.
    private final int depth;
    /// Lazily computed parent - computed on first enclosure() call.
    private volatile NanoName parent;
    private volatile boolean parentComputed;
    /// Cached Optional for enclosure() - avoids allocation on each call.
    private volatile Optional < Name > cachedEnclosure;

    // =========================================================================
    // Per-node children storage for fast chaining (ArrayName optimization)
    // Sorted parallel arrays enable binary search - O(log n) lookup
    // Much faster than global CACHE lookup for chaining operations
    // =========================================================================
    private volatile String[] childSegments = new String[0];
    private volatile NanoName[] childNodes = new NanoName[0];
    private final Object childLock = new Object ();

    /// Private constructor for root names (depth=1).
    /// Interns segment for O(1) identity comparison in binary search.
    private NanoName ( String path, String segment ) {
      this.path = path;
      this.segment = segment.intern ();  // Intern for fast == comparison
      this.depth = 1;
    }

    /// Private constructor with known parent (depth=parent.depth+1).
    /// Interns segment for O(1) identity comparison in binary search.
    private NanoName ( String path, String segment, NanoName parent ) {
      this.path = path;
      this.segment = segment.intern ();  // Intern for fast == comparison
      this.parent = parent;
      this.parentComputed = true;
      this.depth = parent.depth + 1;
    }

    // =========================================================================
    // Static factory methods - all Name creation logic lives here
    // =========================================================================

    /// Parses and interns a path string with validation.
    /// Hot path: check cache first, only validate on cache miss.
    static NanoName parse ( String path ) {
      Objects.requireNonNull ( path, "path must not be null" );
      // Hot path: check cache first - if found, it's already valid
      NanoName cached = CACHE.get ( path );
      if ( cached != null ) return cached;
      // Cache miss: validate then intern
      return validateAndIntern ( path );
    }

    /// Validates path and interns it. Called only on cache miss.
    private static NanoName validateAndIntern ( String path ) {
      if ( path.isEmpty () ) {
        throw new IllegalArgumentException ( "Name path cannot be empty" );
      }
      if ( path.charAt ( 0 ) == FULLSTOP ) {
        throw new IllegalArgumentException ( "Name path cannot start with a dot: " + path );
      }
      if ( path.charAt ( path.length () - 1 ) == FULLSTOP ) {
        throw new IllegalArgumentException ( "Name path cannot end with a dot: " + path );
      }
      if ( path.contains ( ".." ) ) {
        throw new IllegalArgumentException ( "Name path cannot contain consecutive dots: " + path );
      }
      return intern ( path );
    }

    /// Interns a name by full path with eager parent chain building.
    /// This ensures enclosure() never needs lazy computation and depth is cached.
    /// Also registers child in parent's local children array for fast chaining.
    static NanoName intern ( String path ) {
      // Fast path: already cached
      NanoName cached = CACHE.get ( path );
      if ( cached != null ) return cached;

      // Build parent chain eagerly
      int dot = path.lastIndexOf ( FULLSTOP );
      if ( dot > 0 ) {
        // Has parent - intern parent first (recursive), then create child with parent
        NanoName parent = intern ( path.substring ( 0, dot ) );
        String segment = path.substring ( dot + 1 );

        // Use parent's internChild to register in local children array
        return parent.internChild ( segment );
      } else {
        // Root name - no parent, depth=1
        return CACHE.computeIfAbsent ( path, p -> {
          NanoName root = new NanoName ( p, p );
          root.parentComputed = true; // No parent, but mark as computed
          return root;
        } );
      }
    }

    /// Creates a Name from an Enum (fully qualified: declaring.class.CONSTANT).
    static NanoName fromEnum ( Enum < ? > e ) {
      Objects.requireNonNull ( e, "enum must not be null" );
      // Check enum cache first (hot path optimization)
      NanoName cached = ENUM_CACHE.get ( e );
      if ( cached != null ) return cached;
      // Cache miss: compute and cache
      Class < ? > declClass = e.getDeclaringClass ();
      String canonical = declClass.getCanonicalName ();
      String className = canonical != null ? canonical : declClass.getName ();
      NanoName name = intern ( className + FULLSTOP + e.name () );
      ENUM_CACHE.putIfAbsent ( e, name );
      return name;
    }

    /// Creates a Name from a Class.
    static NanoName fromClass ( Class < ? > type ) {
      Objects.requireNonNull ( type, "type must not be null" );
      String canonical = type.getCanonicalName ();
      return intern ( canonical != null ? canonical : type.getName () );
    }

    /// Creates a Name from a Member (declaring class + member name).
    static NanoName fromMember ( Member member ) {
      Objects.requireNonNull ( member, "member must not be null" );
      Class < ? > declClass = member.getDeclaringClass ();
      String canonical = declClass.getCanonicalName ();
      String className = canonical != null ? canonical : declClass.getName ();
      return intern ( className + FULLSTOP + member.getName () );
    }

    /// Creates a Name from an Iterable of parts.
    static NanoName fromIterable ( Iterable < String > parts ) {
      Objects.requireNonNull ( parts, "parts must not be null" );
      StringBuilder sb = new StringBuilder ();
      for ( String part : parts ) {
        Objects.requireNonNull ( part, "part must not be null" );
        if ( !sb.isEmpty () ) sb.append ( FULLSTOP );
        sb.append ( part );
      }
      return parse ( sb.toString () );
    }

    /// Creates a Name from an Iterable with mapper.
    static < T > NanoName fromIterable ( Iterable < ? extends T > parts, Function < T, String > mapper ) {
      Objects.requireNonNull ( parts, "parts must not be null" );
      Objects.requireNonNull ( mapper, "mapper must not be null" );
      StringBuilder sb = new StringBuilder ( 64 );  // Capacity hint
      for ( T item : parts ) {
        if ( !sb.isEmpty () ) sb.append ( FULLSTOP );
        sb.append ( mapper.apply ( item ) );
      }
      return parse ( sb.toString () );
    }

    /// Creates a Name from an Iterator of parts.
    static NanoName fromIterator ( Iterator < String > parts ) {
      Objects.requireNonNull ( parts, "parts must not be null" );
      StringBuilder sb = new StringBuilder ();
      while ( parts.hasNext () ) {
        String part = parts.next ();
        Objects.requireNonNull ( part, "part must not be null" );
        if ( !sb.isEmpty () ) sb.append ( FULLSTOP );
        sb.append ( part );
      }
      return parse ( sb.toString () );
    }

    /// Creates a Name from an Iterator with mapper.
    static < T > NanoName fromIterator ( Iterator < ? extends T > parts, Function < T, String > mapper ) {
      Objects.requireNonNull ( parts, "parts must not be null" );
      Objects.requireNonNull ( mapper, "mapper must not be null" );
      StringBuilder sb = new StringBuilder ( 64 );  // Capacity hint
      while ( parts.hasNext () ) {
        if ( !sb.isEmpty () ) sb.append ( FULLSTOP );
        sb.append ( mapper.apply ( parts.next () ) );
      }
      return parse ( sb.toString () );
    }

    // =========================================================================
    // Instance methods - extend existing Name with suffix
    // =========================================================================

    /// Extends this name with a single segment (no dots in segment).
    /// Optimized: binary search on local children array (6x faster than global CACHE).
    /// Segments are interned in constructor for memory efficiency.
    private NanoName internChild ( String childSegment ) {
      // Fast path: binary search in local sorted children array
      String[] segments = childSegments;
      NanoName[] nodes = childNodes;

      int idx = java.util.Arrays.binarySearch ( segments, childSegment );
      if ( idx >= 0 ) {
        return nodes[idx];  // Found in local cache - fastest path
      }

      // Slow path: create new child and add to local array
      synchronized ( childLock ) {
        // Double-check after acquiring lock
        segments = childSegments;
        nodes = childNodes;
        idx = java.util.Arrays.binarySearch ( segments, childSegment );
        if ( idx >= 0 ) {
          return nodes[idx];
        }

        // Build child path
        StringBuilder sb = new StringBuilder ( path.length () + 1 + childSegment.length () );
        String childPath = sb.append ( path ).append ( FULLSTOP ).append ( childSegment ).toString ();

        // Check global cache (might exist from parse() call)
        NanoName child = CACHE.get ( childPath );
        if ( child == null ) {
          // Create new child with known parent
          child = new NanoName ( childPath, childSegment, this );
          NanoName existing = CACHE.putIfAbsent ( childPath, child );
          if ( existing != null ) child = existing;
        }

        // Insert into sorted local arrays (binary search requires sorted order)
        int insertPoint = -( idx + 1 );
        String[] newSegments = new String[segments.length + 1];
        NanoName[] newNodes = new NanoName[nodes.length + 1];

        System.arraycopy ( segments, 0, newSegments, 0, insertPoint );
        System.arraycopy ( nodes, 0, newNodes, 0, insertPoint );
        newSegments[insertPoint] = child.segment;  // Interned segment from constructor
        newNodes[insertPoint] = child;
        System.arraycopy ( segments, insertPoint, newSegments, insertPoint + 1, segments.length - insertPoint );
        System.arraycopy ( nodes, insertPoint, newNodes, insertPoint + 1, nodes.length - insertPoint );

        childSegments = newSegments;
        childNodes = newNodes;

        return child;
      }
    }

    /// Get parent lazily.
    private NanoName getParent () {
      if ( !parentComputed ) {
        int dot = path.lastIndexOf ( FULLSTOP );
        parent = dot > 0 ? intern ( path.substring ( 0, dot ) ) : null;
        parentComputed = true;
      }
      return parent;
    }

    @Override
    public String part () {
      return segment;
    }

    @Override
    public Optional < Name > enclosure () {
      // Cache the Optional to avoid allocation on repeated calls
      Optional < Name > result = cachedEnclosure;
      if ( result == null ) {
        result = Optional.ofNullable ( getParent () );
        cachedEnclosure = result;
      }
      return result;
    }

    @Override
    public Name name ( Name suffix ) {
      // Append suffix's full path to this name's path
      String suffixStr = suffix.toString ();
      StringBuilder sb = new StringBuilder ( path.length () + 1 + suffixStr.length () );
      return intern ( sb.append ( path ).append ( FULLSTOP ).append ( suffixStr ).toString () );
    }

    @Override
    public Name name ( String suffix ) {
      // Check for dots in suffix
      int dot = suffix.indexOf ( FULLSTOP );
      if ( dot < 0 ) {
        // Simple case: single segment - use internChild for efficiency
        return internChild ( suffix );
      }
      // Multi-segment: build full path and intern with pre-sized StringBuilder
      StringBuilder sb = new StringBuilder ( path.length () + 1 + suffix.length () );
      return intern ( sb.append ( path ).append ( FULLSTOP ).append ( suffix ).toString () );
    }

    @Override
    public Name name ( Enum < ? > e ) {
      // Extension: just append enum constant name (not fully qualified)
      return internChild ( e.name () );
    }

    @Override
    public Name name ( Iterable < String > parts ) {
      NanoName current = this;
      for ( String part : parts ) {
        Objects.requireNonNull ( part, "part must not be null" );
        current = (NanoName) current.name ( part );
      }
      return current;
    }

    @Override
    public < T > Name name ( Iterable < ? extends T > parts, Function < T, String > mapper ) {
      NanoName current = this;
      for ( T part : parts ) {
        current = (NanoName) current.name ( mapper.apply ( part ) );
      }
      return current;
    }

    @Override
    public Name name ( Iterator < String > parts ) {
      NanoName current = this;
      while ( parts.hasNext () ) {
        String part = parts.next ();
        Objects.requireNonNull ( part, "part must not be null" );
        current = (NanoName) current.name ( part );
      }
      return current;
    }

    @Override
    public < T > Name name ( Iterator < ? extends T > parts, Function < T, String > mapper ) {
      NanoName current = this;
      while ( parts.hasNext () ) {
        current = (NanoName) current.name ( mapper.apply ( parts.next () ) );
      }
      return current;
    }

    @Override
    public Name name ( Class < ? > type ) {
      String canonical = type.getCanonicalName ();
      return name ( canonical != null ? canonical : type.getName () );
    }

    @Override
    public Name name ( Member member ) {
      Class < ? > declClass = member.getDeclaringClass ();
      String canonical = declClass.getCanonicalName ();
      String className = canonical != null ? canonical : declClass.getName ();
      return name ( className ).name ( member.getName () );
    }

    @Override
    public CharSequence path ( Function < ? super String, ? extends CharSequence > mapper ) {
      StringBuilder sb = new StringBuilder ();
      buildPath ( sb, mapper );
      return sb;
    }

    private void buildPath ( StringBuilder sb, Function < ? super String, ? extends CharSequence > mapper ) {
      NanoName p = getParent ();
      if ( p != null ) {
        p.buildPath ( sb, mapper );
        sb.append ( FULLSTOP );
      }
      sb.append ( mapper.apply ( segment ) );
    }

    @Override
    public String toString () {
      return path;
    }

    @Override
    public boolean equals ( Object o ) {
      // Identity-based: interned names with same path ARE the same object
      return this == o;
    }

    @Override
    public int hashCode () {
      // Use identity hash code since we're identity-based
      return System.identityHashCode ( this );
    }

    @Override
    public int depth () {
      // Cached at construction - O(1) instead of O(depth) traversal
      return depth;
    }

    @Override
    public int compareTo ( Name other ) {
      // Identity check first - interned names are identity-comparable
      if ( this == other ) return 0;
      // Use cached path strings directly - avoids building CharSequence
      return path.compareTo ( other.toString () );
    }

  }

  /// An immutable name-value pair - record for minimal overhead.
  record NanoSlot < T > ( Name name, T value, Class < T > type )
    implements Slot < T > {

    /// Resolve the type for a value, mapping implementation classes to interface types.
    @SuppressWarnings ( "unchecked" )
    static < T > Class < T > resolveType ( T value ) {
      if ( value instanceof Name ) return (Class < T >) Name.class;
      if ( value instanceof State ) return (Class < T >) State.class;
      if ( value instanceof Slot ) return (Class < T >) Slot.class;
      // Map boxed primitives to their primitive types
      if ( value instanceof Integer ) return (Class < T >) int.class;
      if ( value instanceof Long ) return (Class < T >) long.class;
      if ( value instanceof Float ) return (Class < T >) float.class;
      if ( value instanceof Double ) return (Class < T >) double.class;
      if ( value instanceof Boolean ) return (Class < T >) boolean.class;
      return (Class < T >) value.getClass ();
    }

  }

  /// Record-based key for slot deduplication in compact().
  /// Uses identity hashCode for Name (interned) for fast hashing.
  private record SlotKey ( Name name, Class < ? > type ) {
    @Override
    public int hashCode () {
      // Use identity hash for Name (interned) - faster than String-based hash
      return System.identityHashCode ( name ) * 31 + System.identityHashCode ( type );
    }

    @Override
    public boolean equals ( Object o ) {
      if ( this == o ) return true;
      if ( !( o instanceof SlotKey other ) ) return false;
      // Identity comparison for both Name and Class
      return name == other.name && type == other.type;
    }
  }

  /// An immutable collection of slots using raw array for performance.
  /// Stack semantics: most recent slot at end (highest index).
  static final class NanoState
    implements State {

    /// Empty slots array - shared to avoid allocation.
    private static final Slot < ? >[] EMPTY_SLOTS = new Slot < ? >[0];

    /// Shared singleton for empty state - avoids allocation on hot path.
    static final NanoState EMPTY = new NanoState ( EMPTY_SLOTS );

    /// Raw array - most recent at index (length-1). Never null, never mutated.
    private final Slot < ? >[] slots;

    /// Private constructor - use EMPTY singleton for empty state.
    private NanoState ( Slot < ? >[] slots ) {
      this.slots = slots;
    }

    @Override
    public Iterator < Slot < ? > > iterator () {
      // Return in most-recent-first order (reverse iteration: high index to low)
      return new Iterator < > () {
        private int index = slots.length - 1;

        @Override
        public boolean hasNext () {
          return index >= 0;
        }

        @Override
        public Slot < ? > next () {
          if ( index < 0 ) throw new java.util.NoSuchElementException ();
          return slots[index--];
        }
      };
    }

    @Override
    public Spliterator < Slot < ? > > spliterator () {
      // Return a SIZED spliterator in most-recent-first order
      return java.util.Spliterators.spliterator (
        slots,
        java.util.Spliterator.ORDERED | java.util.Spliterator.SIZED | java.util.Spliterator.IMMUTABLE
      );
    }

    @Override
    public State compact () {
      // Early exit if no duplicates possible
      int len = slots.length;
      if ( len <= 1 ) return this;
      // For small arrays, use simple O(n²) scan - faster than HashMap overhead
      // Scan from most recent (end) to oldest, keeping first occurrence
      // Use identity comparison for Name (interned) and Class
      Slot < ? >[] result = new Slot < ? >[len];
      int count = 0;
      outer:
      for ( int i = len - 1; i >= 0; i-- ) {
        Slot < ? > candidate = slots[i];
        Name candidateName = candidate.name ();
        Class < ? > candidateType = candidate.type ();
        // Check if we already have this (name, type) in result
        for ( int j = 0; j < count; j++ ) {
          if ( result[j].name () == candidateName && result[j].type () == candidateType ) {
            continue outer; // Skip duplicate
          }
        }
        result[count++] = candidate;
      }
      // Early exit if no duplicates were found
      if ( count == len ) return this;
      // Create trimmed array
      Slot < ? >[] trimmed = new Slot < ? >[count];
      System.arraycopy ( result, 0, trimmed, 0, count );
      return new NanoState ( trimmed );
    }

    private State addSlot ( Slot < ? > slot ) {
      int len = slots.length;
      // Fast path: adding to empty state - just wrap the single slot
      if ( len == 0 ) {
        return new NanoState ( new Slot < ? >[] { slot } );
      }
      // If the most recent slot is equal (same name, type, and value), return this
      // Use identity for Name (interned) and Class
      Slot < ? > last = slots[len - 1];
      if ( last.name () == slot.name () &&
           last.type () == slot.type () &&
           Objects.equals ( last.value (), slot.value () ) ) {
        return this;
      }
      // Copy with one extra slot - System.arraycopy is JVM intrinsic
      Slot < ? >[] newSlots = new Slot < ? >[len + 1];
      System.arraycopy ( slots, 0, newSlots, 0, len );
      newSlots[len] = slot;
      return new NanoState ( newSlots );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public State state ( Name name, int value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, (Class < Integer >) (Class < ? >) int.class ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public State state ( Name name, long value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, (Class < Long >) (Class < ? >) long.class ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public State state ( Name name, float value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, (Class < Float >) (Class < ? >) float.class ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public State state ( Name name, double value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, (Class < Double >) (Class < ? >) double.class ) );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public State state ( Name name, boolean value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, (Class < Boolean >) (Class < ? >) boolean.class ) );
    }

    @Override
    public State state ( Name name, String value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, String.class ) );
    }

    @Override
    public State state ( Name name, Name value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, Name.class ) );
    }

    @Override
    public State state ( Name name, State value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return addSlot ( new NanoSlot <> ( name, value, State.class ) );
    }

    @Override
    public State state ( Slot < ? > slot ) {
      Objects.requireNonNull ( slot, "slot must not be null" );
      return addSlot ( slot );
    }

    @Override
    public State state ( Enum < ? > value ) {
      Objects.requireNonNull ( value, "value must not be null" );
      // Name is derived from the enum's declaring class canonical name (uses . not $)
      Class < ? > declClass = value.getDeclaringClass ();
      String canonical = declClass.getCanonicalName ();
      String className = canonical != null ? canonical : declClass.getName ();
      Name slotName = cortex().name ( className );
      // Value is a Name using the full hierarchical path: DeclaringClass.enumConstant
      Name slotValue = NanoName.fromEnum ( value );
      return addSlot ( new NanoSlot <> ( slotName, slotValue, Name.class ) );
    }

    @Override
    public Stream < Slot < ? > > stream () {
      // Return in most-recent-first order (reverse of internal list)
      return java.util.stream.StreamSupport.stream (
        spliterator (),
        false
      );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public < T > T value ( Slot < T > slot ) {
      // Search in reverse for most recent - use identity for Name (interned)
      Name targetName = slot.name ();
      Class < T > targetType = slot.type ();
      for ( int i = slots.length - 1; i >= 0; i-- ) {
        Slot < ? > s = slots[i];
        if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
          return (T) s.value ();
        }
      }
      return slot.value ();
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public < T > Stream < T > values ( Slot < ? extends T > slot ) {
      // Use identity for Name (interned)
      Name targetName = slot.name ();
      Class < ? extends T > targetType = slot.type ();
      List < T > result = new ArrayList <> ();
      for ( int i = slots.length - 1; i >= 0; i-- ) {
        Slot < ? > s = slots[i];
        if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
          result.add ( (T) s.value () );
        }
      }
      return result.stream ();
    }

  }

  /// The identity of a substrate.
  /// Supports null name for anonymous subjects - delegates to parent's name.
  @SuppressWarnings ( { "unchecked" } )
  static final class NanoSubject < S extends Substrate < S > >
    implements Subject < S >, Id {

    private final Name name;  // null for anonymous subjects
    private final NanoSubject < ? > parent;
    private final Class < ? > type;

    /// Creates a root subject with the given name and type.
    NanoSubject ( Name name, Class < ? > type ) {
      this.name = name;
      this.parent = null;
      this.type = type;
    }

    /// Creates a child subject with the given name, parent, and type.
    /// If name is null, this subject delegates name() to parent.
    NanoSubject ( Name name, NanoSubject < ? > parent, Class < ? > type ) {
      this.name = name;
      this.parent = parent;
      this.type = type;
    }

    @Override
    public Id id () {
      // Use identity - NanoSubject IS the Id (avoids AtomicLong allocation)
      return this;
    }

    @Override
    public Name name () {
      // Anonymous subjects delegate to parent's name
      return name != null ? name : parent.name ();
    }

    @Override
    public State state () {
      return NanoState.EMPTY;
    }

    @Override
    public Class < S > type () {
      return (Class < S >) type;
    }

    @Override
    public String part () {
      return "Subject[name=" + name () + ", type=" + type.getSimpleName () + ", id=" + System.identityHashCode ( this ) + "]";
    }

    @Override
    public Optional < Subject < ? > > enclosure () {
      return Optional.ofNullable ( parent );
    }

    /// Finds the circuit ancestor in the subject hierarchy.
    /// Returns null if no Circuit type ancestor is found.
    NanoSubject < ? > findCircuitAncestor () {
      NanoSubject < ? > current = this;
      while ( current != null ) {
        if ( current.type == Circuit.class ) {
          return current;
        }
        current = current.parent;
      }
      return null;
    }

    @Override
    public String toString () {
      return path ().toString ();
    }

  }

  /// The entry point for creating substrates.
  static final class NanoCortex
    implements Cortex {

    /// Cached Name for anonymous scopes - avoid repeated HashMap lookup.
    private static final Name SCOPE_NAME = NanoName.intern ( "scope" );
    /// Cached Name for anonymous circuits.
    private static final Name CIRCUIT_NAME = NanoName.intern ( "circuit" );

    private final Subject < Cortex > subject;

    /// ThreadLocal cache for Current instances - each thread gets one stable Current.
    private final ThreadLocal < NanoCurrent > currentCache;

    NanoCortex () {
      this.subject = new NanoSubject <> ( NanoName.intern ( "cortex" ), Cortex.class );
      this.currentCache = ThreadLocal.withInitial ( () -> {
        Thread t = Thread.currentThread ();
        NanoSubject < Current > currentSubject = new NanoSubject <> (
          cortex().name ( "thread." + t.getName () ),
          (NanoSubject < ? >) subject,
          Current.class
        );
        return new NanoCurrent ( currentSubject );
      } );
    }

    @Override
    public Subject < Cortex > subject () {
      return subject;
    }

    @Override
    public Circuit circuit () {
      return circuit ( CIRCUIT_NAME );
    }

    @Override
    public Circuit circuit ( Name name ) {
      requireNonNull ( name, "name must not be null" );
      NanoSubject < Circuit > circuitSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Circuit.class
      );
      return new NanoCircuit ( circuitSubject );
    }

    @Override
    public Current current () {
      return currentCache.get ();
    }

    // =========================================================================
    // Name factory methods - delegate to NanoName static factories
    // =========================================================================

    @Override
    public Name name ( String path ) {
      return NanoName.parse ( path );
    }

    @Override
    public Name name ( Enum < ? > path ) {
      return NanoName.fromEnum ( path );
    }

    @Override
    public Name name ( Iterable < String > parts ) {
      return NanoName.fromIterable ( parts );
    }

    @Override
    public < T > Name name ( Iterable < ? extends T > it, Function < T, String > mapper ) {
      return NanoName.fromIterable ( it, mapper );
    }

    @Override
    public Name name ( Iterator < String > it ) {
      return NanoName.fromIterator ( it );
    }

    @Override
    public < T > Name name ( Iterator < ? extends T > it, Function < T, String > mapper ) {
      return NanoName.fromIterator ( it, mapper );
    }

    @Override
    public Name name ( Class < ? > type ) {
      return NanoName.fromClass ( type );
    }

    @Override
    public Name name ( Member member ) {
      return NanoName.fromMember ( member );
    }

    @Override
    public Scope scope ( Name name ) {
      requireNonNull ( name, "name must not be null" );
      NanoSubject < Scope > scopeSubject = new NanoSubject <> (
        name, (NanoSubject < ? >) subject, Scope.class
      );
      return new NanoScope ( scopeSubject );
    }

    @Override
    public Scope scope () {
      return scope ( SCOPE_NAME );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Slot < Boolean > slot ( Name name, boolean value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return new NanoSlot <> ( name, value, (Class < Boolean >) (Class < ? >) boolean.class );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Slot < Integer > slot ( Name name, int value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return new NanoSlot <> ( name, value, (Class < Integer >) (Class < ? >) int.class );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Slot < Long > slot ( Name name, long value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return new NanoSlot <> ( name, value, (Class < Long >) (Class < ? >) long.class );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Slot < Double > slot ( Name name, double value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return new NanoSlot <> ( name, value, (Class < Double >) (Class < ? >) double.class );
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public Slot < Float > slot ( Name name, float value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      return new NanoSlot <> ( name, value, (Class < Float >) (Class < ? >) float.class );
    }

    @Override
    public Slot < String > slot ( Name name, String value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return new NanoSlot <> ( name, value, String.class );
    }

    @Override
    public Slot < Name > slot ( Enum < ? > value ) {
      Objects.requireNonNull ( value, "value must not be null" );
      Name slotName = name ( value.getDeclaringClass () );
      // Value is the full enum name: DeclaringClass.name
      Name slotValue = name ( value );
      return new NanoSlot <> ( slotName, slotValue, Name.class );
    }

    @Override
    public Slot < Name > slot ( Name name, Name value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return new NanoSlot <> ( name, value, Name.class );
    }

    @Override
    public Slot < State > slot ( Name name, State value ) {
      Objects.requireNonNull ( name, "name must not be null" );
      Objects.requireNonNull ( value, "value must not be null" );
      return new NanoSlot <> ( name, value, State.class );
    }

    @Override
    public State state () {
      return NanoState.EMPTY;
    }

  }

}
