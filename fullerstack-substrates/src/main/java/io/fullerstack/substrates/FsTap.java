package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Queued;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Source;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Tap;

/// A transformed view of a source's emissions (Substrates 2.0).
///
/// In 2.0, tap is created via `source.tap(fn)` where fn is
/// `Function<Pipe<T>, Pipe<E>>`. The function receives a target pipe (T)
/// and returns a source-compatible pipe (E) that the source will emit into.
/// The function is called per-channel on first emission.
///
/// @param <T> the transformed (output) emission type
@Provided
final class FsTap < T > implements Tap < T > {

  private final Subject < Tap < T > > subject;
  private final FsCircuit              circuit;
  private final Subscription           sourceSubscription;

  /// Channels by name for T-typed emissions to tap subscribers.
  private volatile Map < Name, FsChannel < T > > channels = new IdentityHashMap <> ();

  private          ArrayList < FsSubscriber < T > > subscribersList;
  private volatile FsSubscriber < T >[]             subscribersSnapshot;
  private final    Object                           subscriberLock = new Object ();
  private volatile int                              version        = 0;
  private volatile boolean                          closed         = false;

  /// Creates a tap with the 2.0 pipe-function pattern.
  ///
  /// @param parent the parent subject
  /// @param name the tap's name
  /// @param sourceConduit the source to tap into
  /// @param circuit the owning circuit
  /// @param fn the function that receives a target Pipe<T> and returns a source-side Pipe<E>
  @SuppressWarnings ( "unchecked" )
  < E > FsTap (
    FsSubject < ? > parent,
    Name name,
    Source < E, ? > source,
    FsCircuit circuit,
    Function < Pipe < T >, Pipe < E > > fn ) {

    this.subject = new FsSubject <> ( name, parent, Tap.class );
    this.circuit = circuit;

    // Subscribe to source conduit. For each channel emission:
    // 1. Get or create a tap channel for T-typed output
    // 2. Apply fn to get the source-side pipe (only on first emission per channel)
    // 3. Route source emissions through fn's pipeline to tap subscribers
    FsSubscriber < E > tapSubscriber = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "tap.subscriber" ), (FsSubject < ? >) subject, Subscriber.class ),
      ( pipeSubject, registrar ) -> {
        Name channelName = pipeSubject.name ();

        // Create tap-side channel for T output (lazy init)
        FsChannel < T > tapChannel = getOrCreateChannel ( channelName );

        // Create a target pipe that routes to tap subscribers
        Pipe < T > targetPipe = new Pipe <> () {
          @Override
          public void emit ( T emission ) {
            if ( closed ) return;
            if ( tapChannel.builtVersion != version ) {
              rebuildChannelPipes ( tapChannel );
            }
            Consumer < T > router = tapChannel.router;
            if ( router != null ) router.accept ( emission );
          }

          @Override
          public Subject < Pipe < T > > subject () {
            return tapChannel.subject ();
          }

          @Override
          @SuppressWarnings ( "unchecked" )
          public < I > Pipe < I > pipe ( Flow < I, T > flow ) {
            FsFlow < I, T > fsFlow = (FsFlow < I, T >) flow;
            java.util.function.Consumer < I > chain = fsFlow.materialise ( this::emit );
            return circuit.createPipe ( channelName, (FsSubject < ? >) tapChannel.subject (),
              (java.util.function.Consumer < Object >) (java.util.function.Consumer < ? >) new FsCircuit.ReceptorAdapter <> ( chain::accept ) );
          }
        };

        // Apply fn: gives us a Pipe<E> that the source will emit into
        Pipe < E > sourcePipe = fn.apply ( targetPipe );

        // Register the source pipe to receive emissions from this channel
        registrar.register ( sourcePipe );
      } );

    this.sourceSubscription = source.subscribe ( tapSubscriber );
  }

  private FsChannel < T > getOrCreateChannel ( Name name ) {
    FsChannel < T > ch = channels.get ( name );
    if ( ch != null ) return ch;
    synchronized ( this ) {
      ch = channels.get ( name );
      if ( ch != null ) return ch;
      @SuppressWarnings ( "unchecked" )
      Subject < Pipe < T > > tapPipeSubject = new FsSubject <> ( name, (FsSubject < ? >) subject, Pipe.class );
      ch = new FsChannel <> ( tapPipeSubject, circuit, (Consumer < T >) null );
      Map < Name, FsChannel < T > > copy = new IdentityHashMap <> ( channels );
      copy.put ( name, ch );
      channels = copy;
      return ch;
    }
  }

  private static final FsSubscriber < ? >[] EMPTY_SUBSCRIBERS = new FsSubscriber < ? >[0];

  @SuppressWarnings ( "unchecked" )
  private FsSubscriber < T >[] getSubscribersSnapshot () {
    FsSubscriber < T >[] snapshot = subscribersSnapshot;
    if ( snapshot == null ) {
      synchronized ( subscriberLock ) {
        snapshot = subscribersSnapshot;
        if ( snapshot == null ) {
          snapshot = ( subscribersList == null || subscribersList.isEmpty () )
                     ? (FsSubscriber < T >[]) EMPTY_SUBSCRIBERS
                     : subscribersList.toArray ( new FsSubscriber[0] );
          subscribersSnapshot = snapshot;
        }
      }
    }
    return snapshot;
  }

  private void rebuildChannelPipes ( FsChannel < T > channel ) {
    FsSubscriber < T >[] currentSubs = getSubscribersSnapshot ();

    java.util.Set < FsSubscriber < T > > activeSet = java.util.Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < T > sub : currentSubs ) activeSet.add ( sub );

    channel.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < T > subscriber : currentSubs ) {
      if ( !channel.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < T > registrar = new FsRegistrar <> ();
        subscriber.activate ( channel.subject (), registrar );
        channel.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }
    channel.rebuildReceptorsArray ();

    // Build router: direct receptor dispatch
    if ( channel.receptors != null ) {
      channel.router = value -> {
        Receptor < ? super T >[] r = channel.receptors;
        if ( r != null ) {
          for ( int i = 0, len = r.length; i < len; i++ ) r[i].receive ( value );
        }
      };
    } else {
      channel.router = null;
    }

    channel.builtVersion = version;
  }

  @Override
  public Subject < Tap < T > > subject () {
    return subject;
  }

  @New
  @NotNull
  @Override
  public Subscription subscribe (
    @NotNull Subscriber < T > subscriber,
    @NotNull @Queued Consumer < ? super Subscription > onClose ) {
    java.util.Objects.requireNonNull ( subscriber );
    if ( closed ) throw new IllegalStateException ( "Tap is closed" );

    FsSubscriber < T > fs = (FsSubscriber < T >) subscriber;

    synchronized ( subscriberLock ) {
      if ( subscribersList == null ) subscribersList = new ArrayList <> ();
      subscribersList.add ( fs );
      subscribersSnapshot = null;
    }
    version++;

    return new FsSubscription ( subscriber.subject ().name (),
      (FsSubject < ? >) subject, () -> unsubscribe ( fs ), onClose );
  }

  private void unsubscribe ( FsSubscriber < T > subscriber ) {
    synchronized ( subscriberLock ) {
      if ( subscribersList != null ) {
        subscribersList.remove ( subscriber );
        subscribersSnapshot = null;
      }
    }
    version++;
  }

  @New
  @NotNull
  @Override
  public Reservoir < T > reservoir () {
    FsSubject < Reservoir < T > > resSubject = new FsSubject <> ( cortex ().name ( "reservoir" ), (FsSubject < ? >) subject,
      Reservoir.class );
    FsReservoir < T > reservoir = new FsReservoir <> ( resSubject );
    FsSubscriber < T > sub = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "reservoir.subscriber" ), resSubject, Subscriber.class ),
      ( pipeSubject, registrar ) -> registrar.register ( emission -> reservoir.capture ( emission, pipeSubject ) ) );
    subscribe ( sub );
    return reservoir;
  }

  @Override
  public < U > Tap < U > tap ( @NotNull Function < Pipe < U >, Pipe < T > > fn ) {
    java.util.Objects.requireNonNull ( fn );
    return new FsTap <> ( (FsSubject < ? >) subject, cortex ().name ( "tap" ), this, circuit, fn );
  }

  @Override
  @Idempotent
  @Queued
  public void close () {
    if ( closed ) return;
    closed = true;
    sourceSubscription.close ();
  }

}
