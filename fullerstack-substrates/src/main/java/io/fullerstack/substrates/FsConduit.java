package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Percept;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
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
@Provided
public final class FsConduit<P extends Percept, E>
    extends FsSubstrate<Conduit<P, E>> implements Conduit<P, E> {

  /// Per-channel state for lazy initialization and cached pipes.
  /// All fields except channelSubject are only accessed from circuit thread.
  private static final class ChannelState<E> {
    final Subject<Channel<E>> channelSubject;

    // Pipes per subscriber - for proper unsubscribe handling
    final Map<FsSubscriber<E>, List<Consumer<E>>> subscriberPipes = new IdentityHashMap<>();

    // Cached flat array for fast iteration (rebuilt when subscribers change)
    // Non-volatile: only accessed from circuit thread
    Consumer<E>[] pipes;
    int builtVersion = -1;

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
  private final FsCircuit circuit;

  /// Cache of percepts by name - copy-on-write for fast reads.
  /// Using IdentityHashMap since FsName is interned (same path = same object).
  /// Reads are lock-free via volatile; writes synchronize and replace.
  private volatile Map<Name, P> percepts = new IdentityHashMap<>();
  private final Object perceptLock = new Object();

  /// Last lookup cache - optimizes repeated lookups of the same name.
  /// Not volatile: benign race (worst case: extra map lookup).
  private Name lastLookupName;
  private P lastLookupPercept;

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

  /// Fast-path flag for no-subscriber optimization.
  /// When false AND no flowConfigurer, pipes can skip enqueue entirely.
  private volatile boolean hasSubscribers = false;

  /// Returns true if this conduit has any subscribers.
  /// Used by pipes for fast-path optimization when no flowConfigurer.
  boolean hasSubscribers() {
    return hasSubscribers;
  }

  /// Returns true if this conduit has a flow configurer (transformations).
  /// When true, emissions must always be processed (operators may have side effects).
  boolean hasFlowConfigurer() {
    return flowConfigurer != null;
  }

  public FsConduit(
      FsSubject<?> parent,
      Name name,
      Function<Channel<E>, P> composer,
      FsCircuit circuit) {
    this(parent, name, composer, circuit, null);
  }

  public FsConduit(
      FsSubject<?> parent,
      Name name,
      Function<Channel<E>, P> composer,
      FsCircuit circuit,
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

  @NotNull
  @Override
  public P percept(@NotNull Name name) {
    // Fast path: same name as last lookup (identity check, ~2ns)
    // Check lastLookupName != null to handle initial state and null argument
    Name last = lastLookupName;
    if (last != null && name == last) {
      return lastLookupPercept;
    }

    // Null check (only reached on cache miss or first call)
    if (name == null) {
      throw new NullPointerException("name must not be null");
    }

    // Normal path: map lookup (~7ns)
    P cached = percepts.get(name);
    if (cached != null) {
      lastLookupName = name;
      lastLookupPercept = cached;
      return cached;
    }

    // Slow path: create and cache
    return createAndCachePercept(name);
  }

  private P createAndCachePercept(Name name) {
    synchronized (perceptLock) {
      // Double-check under lock
      P cached = percepts.get(name);
      if (cached != null) {
        return cached;
      }

      // Lazy init channelStates if needed
      if (channelStates == null) {
        channelStates = new IdentityHashMap<>();
      }

      FsSubject<Channel<E>> channelSubject =
          new FsSubject<>(name, (FsSubject<?>) lazySubject(), Channel.class);

      // Create channel state
      ChannelState<E> state = new ChannelState<>(channelSubject);

      // Cast channel subject to pipe subject (same identity, different type param)
      @SuppressWarnings("unchecked")
      Subject<Pipe<E>> pipeSubject = (Subject<Pipe<E>>) (Subject<?>) channelSubject;

      // Direct receiver that runs on circuit thread - NO intermediate pipe wrapper
      // This avoids double-enqueue: channel.pipe().emit() → Task → emitToChannel
      // Note: No isRunning() check - tasks already queued should complete during shutdown
      Consumer<E> directReceiver = emission -> emitToChannel(state, emission);

      // Apply flow configurer if present
      Consumer<E> channelRouter;
      FsChannel<E> channel;
      if (flowConfigurer != null) {
        // Flow needs a pipe to wrap - use directReceiver as terminal
        Pipe<E> basePipe = new FsPipe<>(pipeSubject, circuit, directReceiver);
        FsFlow<E> flow = new FsFlow<>(pipeSubject, circuit, basePipe);
        flowConfigurer.configure(flow);
        channelRouter = flow.pipe()::emit;
        // With flow configurer: always process (operators may have side effects)
        channel = new FsChannel<>(channelSubject, circuit, channelRouter);
      } else {
        // No flow - use direct receiver (single Task per emission)
        channelRouter = directReceiver;
        channel = new FsChannel<>(channelSubject, circuit, channelRouter);
      }
      P percept = composer.apply(channel);

      // Copy-on-write: create new maps with this entry
      Map<Name, P> newPercepts = new IdentityHashMap<>(percepts);
      newPercepts.put(name, percept);
      Map<Name, ChannelState<E>> newStates = new IdentityHashMap<>(channelStates);
      newStates.put(name, state);

      // Publish atomically via volatile write
      channelStates = newStates;
      percepts = newPercepts;

      // Update last lookup cache
      lastLookupName = name;
      lastLookupPercept = percept;

      return percept;
    }
  }

  /// Emit to channel - called on circuit thread. Lazy init on first emit.
  private void emitToChannel(ChannelState<E> state, E emission) {
    // Single volatile read at start (read-once principle)
    int v = this.version;

    // Check if we need to rebuild (version changed since last build)
    // Note: builtVersion starts at -1, so first emit always rebuilds
    if (state.builtVersion != v) {
      rebuildChannelPipes(state, v);
    }

    // Get cached pipes (may be null if no subscribers)
    Consumer<E>[] cachedPipes = state.pipes;
    if (cachedPipes == null) return;  // No subscribers

    // Hot path: index-based loop (no iterator allocation)
    for (int i = 0, len = cachedPipes.length; i < len; i++) {
      cachedPipes[i].accept(emission);
    }
  }

  /// Empty array constant for no subscribers
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

    // Create identity-based set for O(1) lookup instead of O(n) nested loop
    java.util.Set<FsSubscriber<E>> activeSet =
        java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    for (FsSubscriber<E> sub : currentSubs) {
      activeSet.add(sub);
    }

    // Remove pipes for unsubscribed subscribers - O(n) instead of O(n*m)
    state.subscriberPipes.keySet().removeIf(sub -> !activeSet.contains(sub));

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
        hasSubscribers = !subscribersList.isEmpty(); // Update fast-path flag
      }
    }
    version++; // Trigger rebuild on next emit
  }

  @New
  @NotNull
  @Override
  public Subscription subscribe(@NotNull Subscriber<E> subscriber) {
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
      hasSubscribers = true; // Enable processing path
    }
    version++; // Trigger rebuild on next emit

    FsSubject<Subscription> subSubject =
        new FsSubject<>(subscriber.subject().name(), (FsSubject<?>) lazySubject(), Subscription.class);
    Subscription subscription = new FsSubscription(subSubject, () -> unsubscribe(fs));
    fs.trackSubscription(subscription);

    return subscription;
  }

  @New
  @NotNull
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
