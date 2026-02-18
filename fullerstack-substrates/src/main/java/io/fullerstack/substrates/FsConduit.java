package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

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
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Tap;

/// A container of channels that can be subscribed to and looked up by name.
///
/// All subscriber state is managed exclusively on the circuit thread.
/// subscribe() and unsubscribe() enqueue to the circuit thread per the API contract.
/// No synchronization needed for subscriber state - single-threaded on circuit.
///
/// @param <P> the percept type (extends Percept)
/// @param <E> the emission type
@Provided
public final class FsConduit < P extends Percept, E > extends FsSubstrate < Conduit < P, E > > implements Conduit < P, E > {

  private final Function < Channel < E >, P > composer;
  private final Configurer < Flow < E > >     flowConfigurer;
  private final FsCircuit                     circuit;

  /// Cache of percepts by name - copy-on-write for fast reads.
  /// Using IdentityHashMap since FsName is interned (same path = same object).
  /// Reads are lock-free via volatile; writes synchronize and replace.
  /// Lazy: only allocated on first percept() call to minimize conduit create cost.
  private volatile Map < Name, P > percepts;
  // Lock uses 'this' when percepts is null, then switches to dedicated lock after init

  /// Last lookup cache - optimizes repeated lookups of the same name.
  /// Not volatile: benign race (worst case: extra map lookup).
  private Name lastLookupName;
  private P    lastLookupPercept;

  /// Channels by name - copy-on-write for fast reads.
  /// Lazily allocated on first percept creation.
  private volatile Map < Name, FsChannel < E > > channels; // Lazy

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber state - ALL plain fields, only accessed from circuit thread
  // ─────────────────────────────────────────────────────────────────────────────

  /// Subscribers list - only modified on circuit thread via queued operations.
  private ArrayList < FsSubscriber < E > > subscribersList;  // Lazy

  /// Snapshot array for fast iteration - rebuilt on circuit thread when dirty.
  private static final FsSubscriber < ? >[] EMPTY_SUBSCRIBERS   = new FsSubscriber < ? >[0];
  @SuppressWarnings ( "unchecked" )
  private              FsSubscriber < E >[] subscribersSnapshot = (FsSubscriber < E >[]) EMPTY_SUBSCRIBERS;

  /// Dirty flag - set on subscribe/unsubscribe, cleared on rebuild.
  /// Package-private: read by FsChannel.receive() on circuit thread.
  boolean subscribersDirty;

  /// Fast-path flag for no-subscriber optimization.
  /// Volatile: read by external emit threads, written by circuit thread.
  private volatile boolean hasSubscribers = false;

  /// Returns true if this conduit has any subscribers.
  /// Used by pipes for fast-path optimization when no flowConfigurer.
  boolean hasSubscribers () {
    return hasSubscribers;
  }

  /// Returns true if this conduit has a flow configurer (transformations).
  /// When true, emissions must always be processed (operators may have side effects).
  boolean hasFlowConfigurer () {
    return flowConfigurer != null;
  }

  public FsConduit ( FsSubject < ? > parent, Name name, Function < Channel < E >, P > composer, FsCircuit circuit ) {
    this ( parent, name, composer, circuit, null );
  }

  public FsConduit ( FsSubject < ? > parent, Name name, Function < Channel < E >, P > composer, FsCircuit circuit,
                     Configurer < Flow < E > > flowConfigurer ) {
    super ( parent, name );
    this.composer = composer;
    this.circuit = circuit;
    this.flowConfigurer = flowConfigurer;
  }

  @Override
  protected Class < ? > type () {
    return Conduit.class;
  }

  @Override
  public Subject < Conduit < P, E > > subject () {
    return lazySubject ();
  }

  @NotNull
  @Override
  public P percept ( @NotNull Name name ) {
    // Fast path: same name as last lookup (identity check, ~2ns)
    Name last = lastLookupName;
    if ( last != null && name == last ) {
      return lastLookupPercept;
    }

    if ( name == null ) {
      throw new NullPointerException ( "name must not be null" );
    }

    // Check if percepts initialized (single volatile read)
    Map < Name, P > map = percepts;
    if ( map != null ) {
      // Normal path: map lookup (~7ns)
      P cached = map.get ( name );
      if ( cached != null ) {
        lastLookupName = name;
        lastLookupPercept = cached;
        return cached;
      }
    }

    // Slow path: create and cache (will init map if needed)
    return createAndCachePercept ( name );
  }

  private P createAndCachePercept ( Name name ) {
    synchronized ( this ) {
      // Lazy init percepts map on first call
      Map < Name, P > map = percepts;
      if ( map == null ) {
        map = percepts = new IdentityHashMap <> ();
      }

      // Double-check: another thread might have created it
      P cached = map.get ( name );
      if ( cached != null ) {
        return cached;
      }

      // Lazy init channels if needed
      if ( channels == null ) {
        channels = new IdentityHashMap <> ();
      }

      FsSubject < Channel < E > > channelSubject = new FsSubject <> ( name, (FsSubject < ? >) lazySubject (), Channel.class );

      // Create channel (conduit-managed, with subscriber support).
      // Router is set below after construction to break circular dependency:
      // the channel wraps itself via ReceptorReceiver for monomorphic drain.
      FsChannel < E > channel = new FsChannel <> ( channelSubject, circuit, this, null );

      // Wrap channel as ReceptorReceiver so drain loop stays monomorphic.
      // FsChannel implements Receptor directly — eliminates lambda dispatch.
      @SuppressWarnings ( "unchecked" )
      Consumer < E > receiver = (Consumer < E >) (Consumer < ? >) new FsCircuit.ReceptorReceiver <> ( channel );

      // Apply flow configurer if present — exceptions wrapped per API contract
      if ( flowConfigurer != null ) {
        try {
          @SuppressWarnings ( "unchecked" )
          Subject < Pipe < E > > pipeSubject = (Subject < Pipe < E > >) (Subject < ? >) channelSubject;
          @SuppressWarnings ( "unchecked" )
          Pipe < E > basePipe = circuit.createPipe ( name, channelSubject, (Consumer < Object >) (Consumer < ? >) receiver );
          FsFlow < E > flow = new FsFlow <> ( pipeSubject, circuit, basePipe );
          flowConfigurer.configure ( flow );
          channel.router = flow.consumer ();
        } catch ( FsException e ) {
          throw e;
        } catch ( RuntimeException e ) {
          throw new FsException ( "Flow configuration failed", e );
        }
      } else {
        channel.router = receiver;
      }

      P percept = composer.apply ( channel );

      // Copy-on-write: create new maps with this entry
      Map < Name, P > newPercepts = new IdentityHashMap <> ( percepts );
      newPercepts.put ( name, percept );
      Map < Name, FsChannel < E > > newChannels = new IdentityHashMap <> ( channels );
      newChannels.put ( name, channel );

      // Publish atomically via volatile write
      channels = newChannels;
      percepts = newPercepts;

      // Update last lookup cache
      lastLookupName = name;
      lastLookupPercept = percept;

      return percept;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Emission - circuit thread only
  // ─────────────────────────────────────────────────────────────────────────────

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber management - circuit thread only (queued operations)
  // ─────────────────────────────────────────────────────────────────────────────

  /// Called on circuit thread via submitIngress. Adds subscriber and marks dirty.
  /// O(1) amortized — snapshot is rebuilt lazily on next emission.
  private void addSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList == null ) {
      subscribersList = new ArrayList <> ();
    }
    subscribersList.add ( subscriber );
    hasSubscribers = true;
    markAllChannelsDirty ();
  }

  /// Called on circuit thread via submitIngress. Removes subscriber and marks dirty.
  /// O(1) — snapshot is rebuilt lazily on next emission.
  private void removeSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList != null ) {
      subscribersList.remove ( subscriber );
      hasSubscribers = !subscribersList.isEmpty ();
      markAllChannelsDirty ();
    }
  }

  /// Mark all channel states as dirty so they rebuild on next emit.
  /// Deferred: channels check subscribersDirty on emission instead of eager iteration.
  private void markAllChannelsDirty () {
    subscribersDirty = true;
    // Don't iterate channels here - each channel checks subscribersDirty on emit
  }

  /// Rebuild the subscribers snapshot if dirty. Called on circuit thread only.
  @SuppressWarnings ( "unchecked" )
  private FsSubscriber < E >[] ensureSubscribersSnapshot () {
    if ( subscribersDirty ) {
      subscribersSnapshot = ( subscribersList == null || subscribersList.isEmpty () )
                            ? (FsSubscriber < E >[]) EMPTY_SUBSCRIBERS
                            : subscribersList.toArray ( new FsSubscriber[0] );
      subscribersDirty = false;
    }
    return subscribersSnapshot;
  }

  /// Rebuild receptors for a channel. Only activates NEW subscribers.
  /// Package-private: called by FsChannel.receive() on circuit thread.
  void rebuildChannelPipes ( FsChannel < E > channel ) {
    FsSubscriber < E >[] currentSubs = ensureSubscribersSnapshot ();

    // Create identity-based set for O(1) lookup instead of O(n) nested loop
    java.util.Set < FsSubscriber < E > > activeSet = java.util.Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < E > sub : currentSubs ) {
      activeSet.add ( sub );
    }

    // Remove receptors for unsubscribed subscribers
    channel.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    // Activate any NEW subscribers for this channel
    for ( FsSubscriber < E > subscriber : currentSubs ) {
      if ( !channel.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < E > registrar = new FsRegistrar <> ();
        subscriber.activate ( channel.subject (), registrar );
        channel.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    // Rebuild flat array for fast iteration
    channel.rebuildReceptorsArray ();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Public API
  // ─────────────────────────────────────────────────────────────────────────────

  @New
  @NotNull
  @Queued
  @Override
  public Subscription subscribe ( @NotNull Subscriber < E > subscriber ) {
    FsSubject < ? > subSubject = (FsSubject < ? >) subscriber.subject ();
    FsSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
    if ( subscriberCircuit != null && subscriberCircuit != circuit.subject () ) {
      throw new FsException ( "Subscriber belongs to a different circuit" );
    }

    FsSubscriber < E > fs = (FsSubscriber < E >) subscriber;

    // Create subscription handle with lazy subject and unsubscribe callback
    Subscription subscription = new FsSubscription ( subscriber.subject ().name (),
      (FsSubject < ? >) lazySubject (), () -> enqueueUnsubscribe ( fs ) );

    // Track subscription on subscriber so subscriber.close() can unsubscribe
    fs.trackSubscription ( subscription );

    // Enqueue to circuit thread per @Queued contract (ReceptorReceiver preserves monomorphism)
    circuit.submitIngress ( new FsCircuit.ReceptorReceiver < Object > ( _ -> addSubscriber ( fs ) ), null );

    return subscription;
  }

  /// Enqueue unsubscribe to circuit thread.
  private void enqueueUnsubscribe ( FsSubscriber < E > subscriber ) {
    circuit.submitIngress ( new FsCircuit.ReceptorReceiver < Object > ( _ -> removeSubscriber ( subscriber ) ), null );
  }

  @New
  @NotNull
  @Override
  public Reservoir < E > reservoir () {
    FsSubject < Reservoir < E > > resSubject = new FsSubject <> ( cortex ().name ( "reservoir" ), (FsSubject < ? >) lazySubject (),
      Reservoir.class );
    FsReservoir < E > reservoir = new FsReservoir <> ( resSubject );

    FsSubscriber < E > sub = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "reservoir.subscriber" ), resSubject, Subscriber.class ), ( channelSubject,
                                                                                                      registrar ) -> registrar.register (
      emission -> reservoir.capture ( emission, channelSubject ) ) );
    subscribe ( sub );
    return reservoir;
  }

  @New
  @NotNull
  @Override
  public < T > Tap < T > tap ( @NotNull Function < ? super E, ? extends T > mapper ) {
    java.util.Objects.requireNonNull ( mapper, "mapper must not be null" );
    return new FsTap <> ( (FsSubject < ? >) lazySubject (), cortex ().name ( "tap" ), this, circuit, mapper, null );
  }

  @New
  @NotNull
  @Override
  public < T > Tap < T > tap (
    @NotNull Function < ? super E, ? extends T > mapper,
    @NotNull Configurer < Flow < T > > configurer ) {
    java.util.Objects.requireNonNull ( mapper, "mapper must not be null" );
    java.util.Objects.requireNonNull ( configurer, "configurer must not be null" );
    // Eager validation: invoke configurer against temporary flow per API contract
    try {
      FsFlow < T > validationFlow = new FsFlow <> ( cortex ().name ( "tap" ), circuit, null );
      configurer.configure ( validationFlow );
    } catch ( FsException e ) {
      throw e;
    } catch ( RuntimeException e ) {
      throw new FsException ( "Flow configuration failed", e );
    }
    return new FsTap <> ( (FsSubject < ? >) lazySubject (), cortex ().name ( "tap" ), this, circuit, mapper, configurer );
  }
}
