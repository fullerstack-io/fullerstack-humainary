package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

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
/// @param <P> the percept type (extends Percept)
/// @param <E> the emission type
public final class FsConduit<P extends Percept, E> implements Conduit<P, E> {

  /// The subject identity for this conduit.
  private final Subject<Conduit<P, E>> subject;

  /// Function to create percepts from channels.
  private final Function<Channel<E>, P> composer;

  /// Optional flow configurer to apply to created pipes.
  private final Configurer<Flow<E>> flowConfigurer;

  /// Cache of percepts by name (thread-safe for concurrent access).
  private final Map<Name, P> percepts = new ConcurrentHashMap<>();

  /// List of active subscribers.
  private final List<Subscriber<E>> subscribers = new ArrayList<>();

  /// Maps subscriber to their registered pipes (per channel).
  /// When subscription closes, we can remove all pipes for that subscriber.
  private final Map<Subscriber<E>, Map<Name, List<Consumer<E>>>> subscriberPipes = new IdentityHashMap<>();

  /// Tracks which subscribers have seen which channels.
  private final Set<String> subscriberChannelPairs = new HashSet<>();

  /// Reference to the circuit (for submit and closed checks).
  private final FsInternalCircuit circuit;

  /// Creates a new conduit with the given subject, composer, and circuit references.
  ///
  /// @param subject the subject identity for this conduit
  /// @param composer function to create percepts from channels
  /// @param circuit the circuit reference
  public FsConduit(
      Subject<Conduit<P, E>> subject,
      Function<Channel<E>, P> composer,
      FsInternalCircuit circuit) {
    this(subject, composer, circuit, null);
  }

  /// Creates a new conduit with the given subject, composer, flow configurer, and circuit references.
  ///
  /// @param subject the subject identity for this conduit
  /// @param composer function to create percepts from channels
  /// @param circuit the circuit reference
  /// @param flowConfigurer optional flow configurer to apply to created pipes
  public FsConduit(
      Subject<Conduit<P, E>> subject,
      Function<Channel<E>, P> composer,
      FsInternalCircuit circuit,
      Configurer<Flow<E>> flowConfigurer) {
    this.subject = subject;
    this.composer = composer;
    this.circuit = circuit;
    this.flowConfigurer = flowConfigurer;
  }

  /// Returns the subject identity of this conduit.
  @Override
  public Subject<Conduit<P, E>> subject() {
    return subject;
  }

  /// Returns or creates the percept for the given name.
  @Override
  public P percept(Name name) {
    // Optimization: use get() first (fast path), then putIfAbsent() on miss (slow path)
    P cached = percepts.get(name);
    if (cached != null) {
      return cached;
    }
    // Cache miss - create new percept
    return createAndCachePercept(name);
  }

  /// Creates and caches a new percept for the given name.
  private P createAndCachePercept(Name name) {
    // Create channel subject with channel name and conduit as parent
    FsSubject<Channel<E>> channelSubject = new FsSubject<>(name, (FsSubject<?>) subject, Channel.class);

    // Create pipe subject with channel name and channel as parent
    FsSubject<Pipe<E>> pipeSubject = new FsSubject<>(name, channelSubject, Pipe.class);

    // The pipe's consumer queues emissions to circuit for async processing
    Consumer<E> router = emission -> {
      // Ignore emissions after circuit close
      if (!circuit.isRunning()) {
        return;
      }
      // Queue the emission processing to the circuit thread
      circuit.submit(() -> {
        // Lazy activation: notify subscribers on first emission
        for (Subscriber<E> subscriber : new ArrayList<>(subscribers)) {
          String key = System.identityHashCode(subscriber) + ":" + name;
          if (subscriberChannelPairs.add(key)) {
            // First time this subscriber sees this channel - invoke callback
            FsRegistrar<E> registrar = new FsRegistrar<>();
            if (subscriber instanceof FsSubscriber<E> fs) {
              fs.activate(channelSubject, registrar);
            }
            // Store registered pipes for this subscriber/channel
            subscriberPipes
              .computeIfAbsent(subscriber, k -> new HashMap<>())
              .computeIfAbsent(name, k -> new ArrayList<>())
              .addAll(registrar.pipes());
          }
        }

        // Emit to all pipes from active subscribers for this channel
        for (Subscriber<E> subscriber : new ArrayList<>(subscribers)) {
          Map<Name, List<Consumer<E>>> pipes = subscriberPipes.get(subscriber);
          if (pipes != null) {
            List<Consumer<E>> channelPipes = pipes.get(name);
            if (channelPipes != null) {
              for (Consumer<E> pipe : channelPipes) {
                pipe.accept(emission);
              }
            }
          }
        }
      });
    };
    // Create the router consumer - this is what the channel passes to pipes it creates
    // If flow configurer is set, wrap the router with flow transformations
    Consumer<E> channelRouter;
    if (flowConfigurer != null) {
      // Create a pipe that applies flow transformations before routing
      Pipe<E> basePipe = new FsConsumerPipe<>(pipeSubject, router);
      FsFlow<E> flow = new FsFlow<>(pipeSubject, basePipe);
      flowConfigurer.configure(flow);
      Pipe<E> flowPipe = flow.pipe();
      channelRouter = flowPipe::emit;
    } else {
      channelRouter = router;
    }

    FsChannel<E> channel = new FsChannel<>(channelSubject, channelRouter);
    P percept = composer.apply(channel);
    // Use putIfAbsent to handle concurrent creation race
    P existing = percepts.putIfAbsent(name, percept);
    return existing != null ? existing : percept;
  }

  /// Unsubscribes a subscriber, removing their pipes from emission routing.
  void unsubscribe(Subscriber<E> subscriber) {
    subscribers.remove(subscriber);
    subscriberPipes.remove(subscriber);
    // Clean up the channel pairs tracking for this subscriber
    subscriberChannelPairs.removeIf(key -> key.startsWith(System.identityHashCode(subscriber) + ":"));
  }

  /// Subscribes to receive callbacks when channels are activated.
  @Override
  public Subscription subscribe(Subscriber<E> subscriber) {
    // Validate subscriber belongs to the same circuit as this conduit
    if (subscriber.subject() instanceof FsSubject<?> subSubject) {
      FsSubject<?> subscriberCircuit = subSubject.findCircuitAncestor();
      FsSubject<?> conduitCircuit = ((FsSubject<?>) subject).findCircuitAncestor();
      if (subscriberCircuit != null && conduitCircuit != null && subscriberCircuit != conduitCircuit) {
        throw new FsException("Subscriber belongs to a different circuit");
      }
    }

    subscribers.add(subscriber);
    // Create Subscription subject with Conduit as parent
    FsSubject<Subscription> subSubject = new FsSubject<>(subscriber.subject().name(), (FsSubject<?>) subject, Subscription.class);
    Subscription subscription = new FsSubscription(subSubject, () -> unsubscribe(subscriber));
    // Track subscription in subscriber so closing subscriber closes all its subscriptions
    if (subscriber instanceof FsSubscriber<E> fs) {
      fs.trackSubscription(subscription);
    }
    return subscription;
  }

  /// Creates a reservoir to capture emissions from this conduit.
  /// The reservoir subscribes to this conduit and captures all emissions.
  @Override
  public Reservoir<E> reservoir() {
    FsSubject<Reservoir<E>> resSubject = new FsSubject<>(cortex().name("reservoir"), (FsSubject<?>) subject, Reservoir.class);
    FsReservoir<E> reservoir = new FsReservoir<>(resSubject);
    // Subscribe the reservoir to capture all emissions
    Subscriber<E> sub = new FsSubscriber<>(new FsSubject<>(cortex().name("reservoir.subscriber"), resSubject, Subscriber.class), (channelSubject, registrar) ->
      registrar.register(emission -> reservoir.capture(emission, channelSubject))
    );
    subscribe(sub);
    return reservoir;
  }
}
