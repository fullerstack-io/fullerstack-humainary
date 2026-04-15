package io.fullerstack.substrates;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.humainary.substrates.api.Substrates.cortex;
import static java.util.Objects.requireNonNull;

/// A transformed view of a source's emissions (Substrates 2.0).
@Provided
final class FsTap < T > implements Tap < T > {

  private final Subject < Tap < T > > subject;
  private final FsCircuit              circuit;
  private final Subscription           sourceSubscription;

  /// Lightweight per-name dispatch for tap channels.
  static final class TapChannel < T > {
    final Subject < Pipe < T > > subject;
    Receptor < ? super T >       dispatch;
    int                          builtVersion = -1;
    Map < FsSubscriber < T >, List < Receptor < ? super T > > > subscriberReceptors;

    TapChannel ( Subject < Pipe < T > > subject ) {
      this.subject = subject;
    }
  }

  private volatile Map < Name, TapChannel < T > > channels = new IdentityHashMap <> ();

  private          ArrayList < FsSubscriber < T > > subscribersList;
  private volatile FsSubscriber < T >[]             subscribersSnapshot;
  private final    Object                           subscriberLock = new Object ();
  private volatile int                              version        = 0;
  private volatile boolean                          closed         = false;

  @SuppressWarnings ( "unchecked" )
  < E > FsTap (
    FsSubject < ? > parent,
    Name name,
    Source < E, ? > source,
    FsCircuit circuit,
    Function < Pipe < T >, Pipe < E > > fn ) {

    this.subject = new FsSubject <> ( name, parent, Tap.class );
    this.circuit = circuit;

    FsSubscriber < E > tapSubscriber = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "tap.subscriber" ), (FsSubject < ? >) subject, Subscriber.class ),
      ( pipeSubject, registrar ) -> {
        Name channelName = pipeSubject.name ();
        TapChannel < T > tapChannel = getOrCreateChannel ( channelName );

        Pipe < T > targetPipe = new Pipe <> () {
          @Override
          public void emit ( T emission ) {
            if ( closed ) return;
            if ( tapChannel.builtVersion != version ) {
              rebuildTapChannel ( tapChannel );
            }
            Receptor < ? super T > d = tapChannel.dispatch;
            if ( d != null ) d.receive ( emission );
          }

          @Override
          public Subject < Pipe < T > > subject () {
            return tapChannel.subject;
          }

          @Override
          @SuppressWarnings ( "unchecked" )
          public < I > Pipe < I > pipe ( Flow < I, T > flow ) {
            FsFlow < I, T > fsFlow = (FsFlow < I, T >) flow;
            Consumer < I > chain = fsFlow.materialise ( this::emit );
            return new FsPipe <> ( (Consumer < Object >) (Consumer < ? >) chain, circuit );
          }
        };

        Pipe < E > sourcePipe = fn.apply ( targetPipe );
        registrar.register ( sourcePipe );
      } );

    this.sourceSubscription = source.subscribe ( tapSubscriber );
  }

  private TapChannel < T > getOrCreateChannel ( Name name ) {
    TapChannel < T > ch = channels.get ( name );
    if ( ch != null ) return ch;
    synchronized ( this ) {
      ch = channels.get ( name );
      if ( ch != null ) return ch;
      @SuppressWarnings ( "unchecked" )
      Subject < Pipe < T > > tapPipeSubject = new FsSubject <> ( name, (FsSubject < ? >) subject, Pipe.class );
      ch = new TapChannel <> ( tapPipeSubject );
      Map < Name, TapChannel < T > > copy = new IdentityHashMap <> ( channels );
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

  @SuppressWarnings ( "unchecked" )
  private void rebuildTapChannel ( TapChannel < T > ch ) {
    FsSubscriber < T >[] currentSubs = getSubscribersSnapshot ();

    if ( ch.subscriberReceptors == null ) {
      ch.subscriberReceptors = new IdentityHashMap <> ();
    }

    Set < FsSubscriber < T > > activeSet = Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < T > sub : currentSubs ) activeSet.add ( sub );

    ch.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < T > subscriber : currentSubs ) {
      if ( !ch.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < T > registrar = new FsRegistrar <> ();
        subscriber.activate ( ch.subject, registrar );
        ch.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    List < Receptor < ? super T > > all = new ArrayList <> ();
    for ( List < Receptor < ? super T > > list : ch.subscriberReceptors.values () ) {
      all.addAll ( list );
    }
    if ( all.isEmpty () ) {
      ch.dispatch = null;
    } else if ( all.size () == 1 ) {
      ch.dispatch = all.getFirst ();
    } else {
      Receptor < ? super T >[] arr = all.toArray ( new Receptor[0] );
      ch.dispatch = v -> {
        for ( int i = 0, len = arr.length; i < len; i++ ) arr[i].receive ( v );
      };
    }

    ch.builtVersion = version;
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
    requireNonNull ( subscriber );
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
    requireNonNull ( fn );
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
