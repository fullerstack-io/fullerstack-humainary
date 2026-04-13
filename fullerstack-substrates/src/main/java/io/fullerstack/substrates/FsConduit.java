package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Pool;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Routing;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Tap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.humainary.substrates.api.Substrates.cortex;
import static java.util.Objects.requireNonNull;

/// A container of pipes that can be subscribed to and looked up by name.
///
/// In 2.0, Conduit<E> extends Pool<Pipe<E>> — the get(Name) method returns
/// Pipe<E> directly. Channels are an internal implementation detail; the
/// public API exposes only Pipes. Derived views via pool(Function) provide
/// composable transformations with per-name caching.
///
/// All subscriber state is managed exclusively on the circuit thread.
/// subscribe() and unsubscribe() enqueue to the circuit thread per the API contract.
///
/// @param <E> the emission type
@Provided
public final class FsConduit < E > extends FsSubstrate < Conduit < E > > implements Conduit < E > {

  private final FsCircuit circuit;
  private final Routing   routing;

  /// Cache of pipes by name - copy-on-write for fast reads.
  /// Using IdentityHashMap since FsName is interned (same path = same object).
  /// Reads are lock-free via volatile; writes synchronize and replace.
  /// Lazy: only allocated on first get() call to minimize conduit create cost.
  private volatile Map < Name, Pipe < E > > pipes;

  /// Last lookup cache - optimizes repeated lookups of the same name.
  private Name     lastLookupName;
  private Pipe < E > lastLookupPipe;

  /// Channels by name - internal implementation detail.
  /// Each channel manages per-name subscriber receptors and emission dispatch.
  private volatile Map < Name, FsChannel < E > > channels; // Lazy

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber state - ALL plain fields, only accessed from circuit thread
  // ─────────────────────────────────────────────────────────────────────────────

  private ArrayList < FsSubscriber < E > > subscribersList; // Lazy

  private static final FsSubscriber < ? >[] EMPTY_SUBSCRIBERS   = new FsSubscriber < ? >[0];
  @SuppressWarnings ( "unchecked" )
  private              FsSubscriber < E >[] subscribersSnapshot = (FsSubscriber < E >[]) EMPTY_SUBSCRIBERS;

  /// Version counter — incremented on subscriber add/remove.
  /// Channels compare against their cached version to detect rebuild need.
  int subscriberVersion;

  private volatile boolean hasSubscribers = false;

  boolean hasSubscribers () {
    return hasSubscribers;
  }

  public FsConduit ( FsSubject < ? > parent, Name name, FsCircuit circuit ) {
    this ( parent, name, circuit, Routing.PIPE );
  }

  public FsConduit ( FsSubject < ? > parent, Name name, FsCircuit circuit, Routing routing ) {
    super ( parent, name );
    this.circuit = circuit;
    this.routing = routing;
  }

  @Override
  protected Class < ? > type () {
    return Conduit.class;
  }

  Routing routing () {
    return routing;
  }

  /// Returns the channel for the given name, or null if not yet created.
  FsChannel < E > channel ( Name name ) {
    Map < Name, FsChannel < E > > map = channels;
    return map != null ? map.get ( name ) : null;
  }

  @Override
  public Subject < Conduit < E > > subject () {
    return lazySubject ();
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Pool<Pipe<E>> — get(Name) returns Pipe directly
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Pipe < E > get ( @NotNull Name name ) {
    // Fast path: same name as last lookup
    Name last = lastLookupName;
    if ( last != null && name == last ) {
      return lastLookupPipe;
    }

    // Check pipes map (single volatile read)
    Map < Name, Pipe < E > > map = pipes;
    if ( map != null ) {
      Pipe < E > cached = map.get ( name );
      if ( cached != null ) {
        lastLookupName = name;
        lastLookupPipe = cached;
        return cached;
      }
    }

    return createAndCachePipe ( name );
  }

  @NotNull
  @Override
  public < U > Pool < U > pool ( @NotNull Function < ? super Pipe < E >, ? extends U > fn ) {
    return new FsDerivedPool <> ( this, fn );
  }

  private Pipe < E > createAndCachePipe ( Name name ) {
    synchronized ( this ) {
      Map < Name, Pipe < E > > map = pipes;
      if ( map == null ) {
        map = pipes = new IdentityHashMap <> ();
      }
      Pipe < E > cached = map.get ( name );
      if ( cached != null ) {
        return cached;
      }

      if ( channels == null ) {
        channels = new IdentityHashMap <> ();
      }

      @SuppressWarnings ( "unchecked" )
      FsSubject < Pipe < E > > pipeSubject = new FsSubject <> ( name, (FsSubject < ? >) lazySubject (), Pipe.class );

      // Create channel for cold-path subscriber management
      FsChannel < E > channel = new FsChannel <> ( pipeSubject, circuit, this, null );

      // Pipe receiver: transit calls accept() → target.receive().
      // Channel is the initial target (handles first-emission rebuild).
      // After rebuild, conduit swaps target to the dispatch receptor directly.
      @SuppressWarnings ( "unchecked" )
      FsCircuit.ReceptorAdapter < E > dispatch = new FsCircuit.ReceptorAdapter <> ( channel );

      Pipe < E > pipe = circuit.createPipe ( name, pipeSubject, dispatch );

      channel.pipeDispatch = dispatch;
      channel.router = (Consumer < E >) (Consumer < ? >) dispatch;

      // Copy-on-write: publish new maps
      Map < Name, Pipe < E > > newPipes = new IdentityHashMap <> ( pipes );
      newPipes.put ( name, pipe );
      Map < Name, FsChannel < E > > newChannels = new IdentityHashMap <> ( channels );
      newChannels.put ( name, channel );

      channels = newChannels;
      pipes = newPipes;

      lastLookupName = name;
      lastLookupPipe = pipe;

      return pipe;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber management - circuit thread only
  // ─────────────────────────────────────────────────────────────────────────────

  private void addSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList == null ) {
      subscribersList = new ArrayList <> ();
    }
    subscribersList.add ( subscriber );
    hasSubscribers = true;
    subscriberVersion++;
    resetDispatches ();
  }

  private void removeSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList != null ) {
      subscribersList.remove ( subscriber );
      hasSubscribers = !subscribersList.isEmpty ();
      subscriberVersion++;
      resetDispatches ();
    }
  }

  /// Reset all dispatches to point back at their channels so the next
  /// emission triggers a rebuild with the updated subscriber list.
  @SuppressWarnings ( "unchecked" )
  private void resetDispatches () {
    Map < Name, FsChannel < E > > ch = channels;
    if ( ch == null ) return;
    for ( FsChannel < E > channel : ch.values () ) {
      if ( channel.pipeDispatch != null ) {
        channel.pipeDispatch.receptor = (Receptor < ? super E >) (Receptor < ? >) channel;
      }
    }
  }

  private int snapshotVersion = -1;

  @SuppressWarnings ( "unchecked" )
  private FsSubscriber < E >[] ensureSubscribersSnapshot () {
    if ( snapshotVersion != subscriberVersion ) {
      subscribersSnapshot = ( subscribersList == null || subscribersList.isEmpty () )
                            ? (FsSubscriber < E >[]) EMPTY_SUBSCRIBERS
                            : subscribersList.toArray ( new FsSubscriber[0] );
      snapshotVersion = subscriberVersion;
    }
    return subscribersSnapshot;
  }

  void rebuildChannelPipes ( FsChannel < E > channel ) {
    FsSubscriber < E >[] currentSubs = ensureSubscribersSnapshot ();

    Set < FsSubscriber < E > > activeSet = Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < E > sub : currentSubs ) {
      activeSet.add ( sub );
    }

    channel.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < E > subscriber : currentSubs ) {
      if ( !channel.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < E > registrar = new FsRegistrar <> ();
        subscriber.activate ( channel.subject (), registrar );
        channel.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    channel.rebuildReceptorsArray ();
    channel.builtVersion = subscriberVersion;
    // For PIPE routing, swap the pipe's dispatch to point directly at
    // dispatch, removing the channel from the hot emission path.
    // For STEM routing, keep the channel in the path — it handles
    // ancestor propagation in receive().
    if ( channel.pipeDispatch != null && routing == Routing.PIPE ) {
      @SuppressWarnings ( "unchecked" )
      Receptor < ? super E > target = channel.dispatch != null
        ? channel.dispatch : (Receptor < ? super E >) (Receptor < ? >) channel;
      channel.pipeDispatch.receptor = target;
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Public API
  // ─────────────────────────────────────────────────────────────────────────────

  @New
  @NotNull
  @Queued
  @Override
  public Subscription subscribe (
    @NotNull Subscriber < E > subscriber,
    @NotNull @Queued Consumer < ? super Subscription > onClose ) {

    FsSubject < ? > subSubject = (FsSubject < ? >) subscriber.subject ();
    FsSubject < ? > subscriberCircuit = subSubject.findCircuitAncestor ();
    if ( subscriberCircuit != null && subscriberCircuit != circuit.subject () ) {
      throw new FsFault ( "Subscriber belongs to a different circuit" );
    }

    FsSubscriber < E > fs = (FsSubscriber < E >) subscriber;

    Subscription subscription = new FsSubscription ( subscriber.subject ().name (),
      (FsSubject < ? >) lazySubject (), () -> enqueueUnsubscribe ( fs ), onClose );

    fs.trackSubscription ( subscription );

    circuit.submitIngress ( new FsCircuit.ReceptorAdapter < Object > ( _ -> addSubscriber ( fs ) ), null );

    return subscription;
  }

  private void enqueueUnsubscribe ( FsSubscriber < E > subscriber ) {
    circuit.submitIngress ( new FsCircuit.ReceptorAdapter < Object > ( _ -> removeSubscriber ( subscriber ) ), null );
  }

  @New
  @NotNull
  @Override
  public Reservoir < E > reservoir () {
    FsSubject < Reservoir < E > > resSubject = new FsSubject <> ( cortex ().name ( "reservoir" ), (FsSubject < ? >) lazySubject (),
      Reservoir.class );
    FsReservoir < E > reservoir = new FsReservoir <> ( resSubject );

    FsSubscriber < E > sub = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "reservoir.subscriber" ), resSubject, Subscriber.class ),
      ( pipeSubject, registrar ) -> registrar.register (
        emission -> reservoir.capture ( emission, pipeSubject ) ) );
    subscribe ( sub );
    return reservoir;
  }

  @New
  @NotNull
  @Override
  public < T > Tap < T > tap ( @NotNull Function < Pipe < T >, Pipe < E > > fn ) {
    requireNonNull ( fn, "fn must not be null" );
    return new FsTap <> ( (FsSubject < ? >) lazySubject (), cortex ().name ( "tap" ), this, circuit, fn );
  }

}
