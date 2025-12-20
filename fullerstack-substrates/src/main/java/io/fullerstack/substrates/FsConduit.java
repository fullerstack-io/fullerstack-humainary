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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
/// - **Lazy subscription**: Subscriber callbacks occur on first emission to channel
/// - **Zero-allocation hot path**: After first emit, no allocations on emit
/// - **Circuit-bound**: All processing occurs on the circuit's thread
///
/// @param <P> the percept type (extends Percept)
/// @param <E> the emission type
public final class FsConduit<P extends Percept, E>
    extends FsSubstrate<Conduit<P, E>> implements Conduit<P, E> {

  /// Per-channel state for lazy initialization and cached pipes.
  private static final class ChannelState<E> {
    final Subject<Channel<E>> channelSubject;

    // Pipes per subscriber - for proper unsubscribe handling
    final Map<FsSubscriber<E>, List<Consumer<E>>> subscriberPipes = new IdentityHashMap<>();

    // Cached flat array for fast iteration (rebuilt when subscribers change)
    volatile Consumer<E>[] pipes;
    volatile int builtVersion = -1;

    ChannelState(Subject<Channel<E>> channelSubject) {
      this.channelSubject = channelSubject;
    }

    // Rebuild the flat pipes array from subscriberPipes map
    @SuppressWarnings("unchecked")
    void rebuildPipesArray() {
      List<Consumer<E>> allPipes = new ArrayList<>();
      for (List<Consumer<E>> pipeList : subscriberPipes.values()) {
        allPipes.addAll(pipeList);
      }
      pipes = allPipes.isEmpty() ? null : allPipes.toArray(new Consumer[0]);
    }
  }

  private final Function<Channel<E>, P> composer;
  private final Configurer<Flow<E>> flowConfigurer;
  private final FsInternalCircuit circuit;

  /// Cache of percepts by name - copy-on-write for fast reads.
  /// Using IdentityHashMap since FsName uses identity-based equals.
  /// Reads are lock-free via volatile; writes synchronize and replace.
  /// Lazily allocated on first percept access.
  private volatile Map<Name, P> percepts;  // Lazy
  private final Object perceptLock = new Object();

  /// Channel states by name - copy-on-write for fast reads.
  /// Lazily allocated on first percept creation.
  private volatile Map<Name, ChannelState<E>> channelStates;  // Lazy

  /// Subscribers stored in ArrayList for O(1) amortized add.
  /// Snapshot array is created lazily when needed for iteration.
  /// Lazily allocated on first subscribe.
  private java.util.ArrayList<FsSubscriber<E>> subscribersList;  // Lazy
  private volatile FsSubscriber<E>[] subscribersSnapshot; // Lazy snapshot for iteration
  private final Object subscriberLock = new Object();

  /// Version counter - incremented on subscribe/unsubscribe.
  private volatile int version = 0;

  public FsConduit(
      FsSubject<?> parent,
      Name name,
      Function<Channel<E>, P> composer,
      FsInternalCircuit circuit) {
    this(parent, name, composer, circuit, null);
  }

  public FsConduit(
      FsSubject<?> parent,
      Name name,
      Function<Channel<E>, P> composer,
      FsInternalCircuit circuit,
      Configurer<Flow<E>> flowConfigurer) {
    super(parent, name);
    this.composer = composer;
    this.circuit = circuit;
    this.flowConfigurer = flowConfigurer;
  }

  @Override
  protected Class<?> type() {
    return Conduit.class;
  }

  @Override
  public Subject<Conduit<P, E>> subject() {
    return lazySubject();
  }

  @Override
  public P percept(Name name) {
    // Inline null check is faster than Objects.requireNonNull
    if (name == null) throw new NullPointerException("name must not be null");
    // Fast path: volatile read of HashMap - no synchronization
    Map<Name, P> current = percepts;
    if (current != null) {
      P cached = current.get(name);
      if (cached != null) {
        return cached;
      }
    }
    return createAndCachePercept(name);
  }

  private P createAndCachePercept(Name name) {
    synchronized (perceptLock) {
      // Lazy init maps if needed
      Map<Name, P> currentPercepts = percepts;
      if (currentPercepts == null) {
        currentPercepts = new IdentityHashMap<>();
        percepts = currentPercepts;
        channelStates = new IdentityHashMap<>();
      }

      // Double-check under lock
      P cached = currentPercepts.get(name);
      if (cached != null) {
        return cached;
      }

      FsSubject<Channel<E>> channelSubject =
          new FsSubject<>(name, (FsSubject<?>) lazySubject(), Channel.class);

      // Create channel state
      ChannelState<E> state = new ChannelState<>(channelSubject);

      // The router - captures state, zero allocations on hot path after init
      Consumer<E> router = emission -> {
        if (!circuit.isRunning()) {
          return;
        }
        circuit.submit(() -> emitToChannel(state, emission));
      };

      // Apply flow configurer if present
      Consumer<E> channelRouter;
      if (flowConfigurer != null) {
        // Lazy subject creation - pass channelSubject as parent
        Pipe<E> basePipe = new FsConsumerPipe<>(channelSubject, name, router);
        FsFlow<E> flow = new FsFlow<>(channelSubject, name, basePipe);
        flowConfigurer.configure(flow);
        channelRouter = flow.pipe()::emit;
      } else {
        channelRouter = router;
      }

      FsChannel<E> channel = new FsChannel<>(channelSubject, channelRouter);
      P percept = composer.apply(channel);

      // Copy-on-write: create new maps with this entry
      Map<Name, P> newPercepts = new IdentityHashMap<>(currentPercepts);
      newPercepts.put(name, percept);
      Map<Name, ChannelState<E>> newStates = new IdentityHashMap<>(channelStates);
      newStates.put(name, state);

      // Publish atomically via volatile write
      channelStates = newStates;
      percepts = newPercepts;

      return percept;
    }
  }

  /// Emit to channel - called on circuit thread. Lazy init on first emit.
  private void emitToChannel(ChannelState<E> state, E emission) {
    int currentVersion = this.version;

    // Check if we need to rebuild (first emit or subscriber change)
    if (state.builtVersion != currentVersion) {
      rebuildChannelPipes(state, currentVersion);
    }

    // Hot path: iterate cached pipes array - NO ALLOCATIONS
    Consumer<E>[] cachedPipes = state.pipes;
    if (cachedPipes != null) {
      for (Consumer<E> pipe : cachedPipes) {
        pipe.accept(emission);
      }
    }
  }

  /// Empty array constant for no subscribers
  @SuppressWarnings("unchecked")
  private static final FsSubscriber<?>[] EMPTY_SUBSCRIBERS = new FsSubscriber<?>[0];

  /// Get snapshot of current subscribers (creates if invalidated)
  @SuppressWarnings("unchecked")
  private FsSubscriber<E>[] getSubscribersSnapshot() {
    FsSubscriber<E>[] snapshot = subscribersSnapshot;
    if (snapshot == null) {
      synchronized (subscriberLock) {
        snapshot = subscribersSnapshot;
        if (snapshot == null) {
          if (subscribersList == null || subscribersList.isEmpty()) {
            snapshot = (FsSubscriber<E>[]) EMPTY_SUBSCRIBERS;
          } else {
            snapshot = subscribersList.toArray(new FsSubscriber[0]);
          }
          subscribersSnapshot = snapshot;
        }
      }
    }
    return snapshot;
  }

  /// Rebuild pipes for a channel. Only activates NEW subscribers.
  private void rebuildChannelPipes(ChannelState<E> state, int targetVersion) {
    // Get current subscribers snapshot
    FsSubscriber<E>[] currentSubs = getSubscribersSnapshot();

    // Remove pipes for unsubscribed subscribers (use identity-based contains)
    state.subscriberPipes.keySet().removeIf(sub -> {
      for (FsSubscriber<E> s : currentSubs) {
        if (s == sub) return false; // Keep it
      }
      return true; // Remove it
    });

    // Activate any NEW subscribers for this channel
    for (FsSubscriber<E> subscriber : currentSubs) {
      if (!state.subscriberPipes.containsKey(subscriber)) {
        // First time this subscriber sees this channel - invoke callback ONCE
        FsRegistrar<E> registrar = new FsRegistrar<>();
        subscriber.activate(state.channelSubject, registrar);
        state.subscriberPipes.put(subscriber, registrar.pipes());
      }
    }

    // Rebuild flat array for fast iteration
    state.rebuildPipesArray();
    state.builtVersion = targetVersion;
  }

  void unsubscribe(FsSubscriber<E> subscriber) {
    synchronized (subscriberLock) {
      if (subscribersList != null) {
        subscribersList.remove(subscriber);
        subscribersSnapshot = null; // Invalidate snapshot
      }
    }
    version++; // Trigger rebuild on next emit
  }

  @Override
  @SuppressWarnings("unchecked")
  public Subscription subscribe(Subscriber<E> subscriber) {
    if (subscriber.subject() instanceof FsSubject<?> subSubject) {
      FsSubject<?> subscriberCircuit = subSubject.findCircuitAncestor();
      FsSubject<?> conduitCircuit = parent().findCircuitAncestor();
      if (subscriberCircuit != null
          && conduitCircuit != null
          && subscriberCircuit != conduitCircuit) {
        throw new FsException("Subscriber belongs to a different circuit");
      }
    }

    if (!(subscriber instanceof FsSubscriber<E> fs)) {
      throw new FsException("Subscriber must be an FsSubscriber");
    }

    // Add to subscribers list - O(1) amortized with ArrayList growth
    synchronized (subscriberLock) {
      if (subscribersList == null) {
        subscribersList = new ArrayList<>();
      }
      subscribersList.add(fs);
      subscribersSnapshot = null; // Invalidate snapshot
    }
    version++; // Trigger rebuild on next emit

    FsSubject<Subscription> subSubject =
        new FsSubject<>(subscriber.subject().name(), (FsSubject<?>) lazySubject(), Subscription.class);
    Subscription subscription = new FsSubscription(subSubject, () -> unsubscribe(fs));
    fs.trackSubscription(subscription);

    return subscription;
  }

  @Override
  public Reservoir<E> reservoir() {
    FsSubject<Reservoir<E>> resSubject =
        new FsSubject<>(cortex().name("reservoir"), (FsSubject<?>) lazySubject(), Reservoir.class);
    FsReservoir<E> reservoir = new FsReservoir<>(resSubject);

    FsSubscriber<E> sub =
        new FsSubscriber<>(
            new FsSubject<>(cortex().name("reservoir.subscriber"), resSubject, Subscriber.class),
            (channelSubject, registrar) ->
                registrar.register(emission -> reservoir.capture(emission, channelSubject)));
    subscribe(sub);
    return reservoir;
  }
}
