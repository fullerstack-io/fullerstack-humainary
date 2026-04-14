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

/// Conduit — pools named pipes and manages subscriptions.
///
/// Named pipes are FsPipe instances stored directly by name.
/// No separate channel object — the pipe IS the named dispatch point.
/// Version-tracked lazy rebuild per the spec (§7.6.2).
@Provided
public final class FsConduit < E > extends FsSubstrate < Conduit < E > > implements Conduit < E > {

  private final FsCircuit circuit;
  private final Routing   routing;

  /// Named pipes by name — the pipe IS the dispatch point.
  /// Copy-on-write for thread-safe reads from caller contexts.
  private volatile Map < Name, FsPipe < E > > namedPipes;

  /// Last lookup cache.
  private Name         lastLookupName;
  private FsPipe < E > lastLookupPipe;

  // ─── Subscriber state (circuit-thread only) ───

  private ArrayList < FsSubscriber < E > > subscribersList;

  private static final FsSubscriber < ? >[] EMPTY_SUBSCRIBERS = new FsSubscriber < ? >[0];
  @SuppressWarnings ( "unchecked" )
  private FsSubscriber < E >[] subscribersSnapshot = (FsSubscriber < E >[]) EMPTY_SUBSCRIBERS;

  /// Version counter — incremented on subscriber add/remove.
  /// Named pipes compare their builtVersion against this.
  int subscriberVersion;

  private int snapshotVersion = -1;

  private volatile boolean hasSubscribers = false;

  // ─── Construction ───

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

  @Override
  public Subject < Conduit < E > > subject () {
    return lazySubject ();
  }

  FsCircuit circuit () {
    return circuit;
  }

  /// Returns the named pipe for the given name, or null if not yet created.
  FsPipe < E > namedPipe ( Name name ) {
    Map < Name, FsPipe < E > > map = namedPipes;
    return map != null ? map.get ( name ) : null;
  }

  // ─── Pool<Pipe<E>> ───

  @NotNull
  @Override
  public Pipe < E > get ( @NotNull Name name ) {
    // Fast path: same name as last lookup
    Name last = lastLookupName;
    if ( last != null && name == last ) {
      return lastLookupPipe;
    }
    // Check map
    Map < Name, FsPipe < E > > map = namedPipes;
    if ( map != null ) {
      FsPipe < E > cached = map.get ( name );
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

  private FsPipe < E > createAndCachePipe ( Name name ) {
    synchronized ( this ) {
      Map < Name, FsPipe < E > > map = namedPipes;
      if ( map == null ) {
        map = namedPipes = new IdentityHashMap <> ();
      }
      FsPipe < E > cached = map.get ( name );
      if ( cached != null ) {
        lastLookupName = name;
        lastLookupPipe = cached;
        return cached;
      }

      @SuppressWarnings ( "unchecked" )
      FsSubject < Pipe < E > > pipeSubject = new FsSubject <> ( name, (FsSubject < ? >) lazySubject (), Pipe.class );

      // The named pipe IS the dispatch point — no separate channel.
      FsPipe < E > pipe = new FsPipe <> ( name, circuit, pipeSubject );
      pipe.conduit = this;
      pipe.stem = ( routing == Routing.STEM );

      // Copy-on-write
      Map < Name, FsPipe < E > > newMap = new IdentityHashMap <> ( namedPipes );
      newMap.put ( name, pipe );
      namedPipes = newMap;

      lastLookupName = name;
      lastLookupPipe = pipe;

      return pipe;
    }
  }

  // ─── Subscriber management (circuit-thread only) ───

  private void addSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList == null ) {
      subscribersList = new ArrayList <> ();
    }
    subscribersList.add ( subscriber );
    hasSubscribers = true;
    subscriberVersion++;
  }

  private void removeSubscriber ( FsSubscriber < E > subscriber ) {
    if ( subscribersList != null ) {
      subscribersList.remove ( subscriber );
      hasSubscribers = !subscribersList.isEmpty ();
      subscriberVersion++;
    }
  }

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

  /// Rebuild a named pipe's subscriber dispatch.
  /// Called lazily on first emission after subscriber changes (§7.6.2).
  void rebuildPipe ( FsPipe < E > pipe ) {
    FsSubscriber < E >[] currentSubs = ensureSubscribersSnapshot ();

    // Lazy init subscriber receptors map on the pipe
    if ( pipe.subscriberReceptors == null ) {
      pipe.subscriberReceptors = new IdentityHashMap <> ();
    }

    Set < FsSubscriber < E > > activeSet = Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < E > sub : currentSubs ) {
      activeSet.add ( sub );
    }

    pipe.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < E > subscriber : currentSubs ) {
      if ( !pipe.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < E > registrar = new FsRegistrar <> ();
        subscriber.activate ( pipe.subject (), registrar );
        pipe.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    pipe.rebuildDispatch ();
    pipe.builtVersion = subscriberVersion;
  }

  // ─── Source<E, Conduit<E>> ───

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
