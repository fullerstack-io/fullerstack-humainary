package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.humainary.substrates.api.Substrates.cortex;
import static java.util.Objects.requireNonNull;

/// Conduit — pools named channels and manages subscriptions.
///
/// Pool side: maps names to channels. conduit.get(name) returns channel.pipe.
/// Source side: delegates to Hub for subscriber management.
@Provided
public final class FsConduit < E > extends FsSubstrate < Conduit < E > > implements Conduit < E > {

  private final FsCircuit  circuit;
  private final Routing    routing;
  final FsHub < E >        hub;

  /// Channels by name — copy-on-write for thread-safe reads.
  private volatile Map < Name, FsChannel < E > > channels;

  /// Last lookup cache.
  private Name              lastLookupName;
  private FsChannel < E >   lastLookupChannel;

  public FsConduit ( FsSubject < ? > parent, Name name, FsCircuit circuit ) {
    this ( parent, name, circuit, Routing.PIPE );
  }

  public FsConduit ( FsSubject < ? > parent, Name name, FsCircuit circuit, Routing routing ) {
    super ( parent, name );
    this.circuit = circuit;
    this.routing = routing;
    this.hub = new FsHub <> ();
  }

  @Override
  protected Class < ? > type () {
    return Conduit.class;
  }

  @Override
  public Subject < Conduit < E > > subject () {
    return lazySubject ();
  }

  /// Returns the channel for the given name, or null if not yet created.
  FsChannel < E > channel ( Name name ) {
    Map < Name, FsChannel < E > > map = channels;
    return map != null ? map.get ( name ) : null;
  }

  // ─── Pool<Pipe<E>> ───

  @NotNull
  @Override
  public Pipe < E > get ( @NotNull Name name ) {
    Name last = lastLookupName;
    if ( last != null && name == last ) {
      return lastLookupChannel.pipe;
    }
    Map < Name, FsChannel < E > > map = channels;
    if ( map != null ) {
      FsChannel < E > cached = map.get ( name );
      if ( cached != null ) {
        lastLookupName = name;
        lastLookupChannel = cached;
        return cached.pipe;
      }
    }
    return createChannel ( name ).pipe;
  }

  @NotNull
  @Override
  public < U > Pool < U > pool ( @NotNull Function < ? super Pipe < E >, ? extends U > fn ) {
    return new FsDerivedPool <> ( this, fn );
  }

  /// 2.3: derived pool for flow preprocessing.
  /// Each pipe `get(name)` returned by the derived pool emits T; the flow
  /// transforms T → E before reaching this conduit's pipes.
  @NotNull
  @Override
  public < T > Pool < Pipe < T > > pool ( @NotNull Flow < T, E > flow ) {
    requireNonNull ( flow );
    if ( !( flow instanceof FsFlow < T, E > fsFlow ) ) {
      throw new IllegalArgumentException ( "flow must be an FsFlow instance" );
    }
    return new FsDerivedPool <> ( this, p -> fsFlow.pipe ( p ) );
  }

  /// 2.3: derived pool for fiber preprocessing.
  /// Each pipe `get(name)` returned by the derived pool applies the fiber
  /// before reaching this conduit's pipe (same emission type).
  @NotNull
  @Override
  public Pool < Pipe < E > > pool ( @NotNull Fiber < E > fiber ) {
    requireNonNull ( fiber );
    if ( !( fiber instanceof FsFiber < E > fsFiber ) ) {
      throw new IllegalArgumentException ( "fiber must be an FsFiber instance" );
    }
    return new FsDerivedPool <> ( this, p -> fsFiber.pipe ( p ) );
  }

  @SuppressWarnings ( "unchecked" )
  private FsChannel < E > createChannel ( Name name ) {
    synchronized ( this ) {
      if ( channels == null ) {
        channels = new IdentityHashMap <> ();
      }
      FsChannel < E > cached = channels.get ( name );
      if ( cached != null ) {
        lastLookupName = name;
        lastLookupChannel = cached;
        return cached;
      }

      FsSubject < Pipe < E > > pipeSubject = new FsSubject <> ( name, (FsSubject < ? >) lazySubject (), Pipe.class );

      FsChannel < E > channel = new FsChannel <> ( pipeSubject, circuit, hub, this, routing );

      Map < Name, FsChannel < E > > newMap = new IdentityHashMap <> ( channels );
      newMap.put ( name, channel );
      channels = newMap;

      lastLookupName = name;
      lastLookupChannel = channel;

      return channel;
    }
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

    circuit.submitIngress ( new FsCircuit.ReceptorAdapter < Object > ( _ -> hub.addSubscriber ( fs ) ), null );

    return subscription;
  }

  private void enqueueUnsubscribe ( FsSubscriber < E > subscriber ) {
    circuit.submitIngress ( new FsCircuit.ReceptorAdapter < Object > ( _ -> hub.removeSubscriber ( subscriber ) ), null );
  }

  @New
  @NotNull
  @Override
  public Reservoir < E > reservoir () {
    FsSubject < Reservoir < E > > resSubject = new FsSubject <> ( cortex ().name ( "reservoir" ),
      (FsSubject < ? >) lazySubject (), Reservoir.class );
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

  /// 2.3: tap with flow transformation (E → T).
  @New
  @NotNull
  @Override
  public < T > Tap < T > tap ( @NotNull Flow < E, T > flow ) {
    requireNonNull ( flow, "flow must not be null" );
    return tap ( target -> flow.pipe ( target ) );
  }

  /// 2.3: type-preserving tap with fiber per-emission processing.
  @New
  @NotNull
  @Override
  public Tap < E > tap ( @NotNull Fiber < E > fiber ) {
    requireNonNull ( fiber, "fiber must not be null" );
    return tap ( (Function < Pipe < E >, Pipe < E > >) target -> fiber.pipe ( target ) );
  }
}
