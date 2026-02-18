package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import io.humainary.substrates.api.Substrates.Tap;

/// A transformed view of a conduit's emissions.
///
/// Tap mirrors the channel structure of its source conduit but transforms
/// emissions from type E to type T using a mapper function. Channels are
/// created automatically as subjects appear in the source conduit.
///
/// @param <E> the source emission type
/// @param <T> the transformed emission type
@Provided
final class FsTap < E, T > implements Tap < T > {

  private final Subject < Tap < T > >               subject;
  private final FsCircuit                           circuit;
  private final Function < ? super E, ? extends T > mapper;
  private final Configurer < Flow < T > >           flowConfigurer;
  private final Subscription                        sourceSubscription;

  /// Channels by name - mirrors source conduit's channel structure.
  /// Copy-on-write for lock-free reads; writes synchronize on 'this'.
  private volatile Map < Name, FsChannel < T > > channels = new IdentityHashMap <> ();

  /// Subscribers to this tap.
  private          ArrayList < FsSubscriber < T > > subscribersList;
  private volatile FsSubscriber < T >[]             subscribersSnapshot;
  private final    Object                           subscriberLock = new Object ();
  private volatile int                              version        = 0;
  private volatile boolean                          closed         = false;

  /// Creates a tap that transforms emissions from a source conduit.
  @SuppressWarnings ( "unchecked" )
  < P extends io.humainary.substrates.api.Substrates.Percept > FsTap (
    FsSubject < ? > parent,
    Name name,
    FsConduit < P, E > sourceConduit,
    FsCircuit circuit,
    Function < ? super E, ? extends T > mapper,
    Configurer < Flow < T > > flowConfigurer ) {
    this.subject = new FsSubject <> ( name, parent, Tap.class );
    this.circuit = circuit;
    this.mapper = mapper;
    this.flowConfigurer = flowConfigurer;

    // Subscribe to source conduit to receive emissions
    FsSubscriber < E > tapSubscriber = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "tap.subscriber" ), (FsSubject < ? >) subject, Subscriber.class ),
      ( channelSubject, registrar ) -> registrar.register ( emission -> handleSourceEmission ( channelSubject, emission ) ) );

    this.sourceSubscription = sourceConduit.subscribe ( tapSubscriber );
  }

  /// Handle emission from source conduit - transform and deliver to tap subscribers.
  private void handleSourceEmission ( Subject < Channel < E > > sourceChannelSubject, E emission ) {
    if ( closed ) return;

    Name channelName = sourceChannelSubject.name ();
    FsChannel < T > channel = channels.get ( channelName );

    if ( channel == null ) {
      // Lazy init channel (copy-on-write)
      synchronized ( this ) {
        channel = channels.get ( channelName );
        if ( channel == null ) {
          Subject < Channel < T > > tapChannelSubject = new FsSubject <> ( channelName, (FsSubject < ? >) subject, Channel.class );
          channel = new FsChannel <> ( tapChannelSubject, circuit, (Consumer < T >) null );
          Map < Name, FsChannel < T > > newChannels = new IdentityHashMap <> ( channels );
          newChannels.put ( channelName, channel );
          channels = newChannels;
        }
      }
    }

    // Rebuild if subscriber version has changed
    if ( channel.builtVersion != version ) {
      rebuildChannelPipes ( channel );
    }

    // Transform and deliver through router
    T transformed = mapper.apply ( emission );

    Consumer < T > router = channel.router;
    if ( router != null ) {
      router.accept ( transformed );
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
          if ( subscribersList == null || subscribersList.isEmpty () ) {
            snapshot = (FsSubscriber < T >[]) EMPTY_SUBSCRIBERS;
          } else {
            snapshot = subscribersList.toArray ( new FsSubscriber[0] );
          }
          subscribersSnapshot = snapshot;
        }
      }
    }
    return snapshot;
  }

  private void rebuildChannelPipes ( FsChannel < T > channel ) {
    FsSubscriber < T >[] currentSubs = getSubscribersSnapshot ();

    java.util.Set < FsSubscriber < T > > activeSet = java.util.Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < T > sub : currentSubs ) {
      activeSet.add ( sub );
    }

    channel.subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < T > subscriber : currentSubs ) {
      if ( !channel.subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < T > registrar = new FsRegistrar <> ();
        subscriber.activate ( channel.subject (), registrar );
        channel.subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    channel.rebuildReceptorsArray ();

    // Build router: flow pipeline wrapping receptor dispatch, or direct dispatch
    if ( flowConfigurer != null && channel.receptors != null ) {
      // Direct receptor dispatch consumer (downstream of flow pipeline)
      Consumer < T > downstream = value -> {
        Receptor < ? super T >[] r = channel.receptors;
        if ( r != null ) {
          for ( int i = 0, len = r.length; i < len; i++ ) {
            r[i].receive ( value );
          }
        }
      };

      // Dummy pipe targeting the downstream consumer
      @SuppressWarnings ( "unchecked" )
      Pipe < T > targetPipe = new Pipe < T > () {
        @Override
        public void emit ( T emission ) {
          downstream.accept ( emission );
        }

        @Override
        public Subject < Pipe < T > > subject () {
          return (Subject < Pipe < T > >) (Subject < ? >) channel.subject ();
        }
      };

      FsFlow < T > flow = new FsFlow <> ( channel.subject ().name (), circuit, targetPipe );
      try {
        flowConfigurer.configure ( flow );
      } catch ( FsException e ) {
        throw e;
      } catch ( RuntimeException e ) {
        throw new FsException ( "Flow configuration failed", e );
      }
      channel.router = flow.consumer ();
    } else if ( channel.receptors != null ) {
      // No flow — direct receptor dispatch
      channel.router = value -> {
        Receptor < ? super T >[] r = channel.receptors;
        if ( r != null ) {
          for ( int i = 0, len = r.length; i < len; i++ ) {
            r[i].receive ( value );
          }
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
  public Subscription subscribe ( @NotNull Subscriber < T > subscriber ) {
    if ( closed ) {
      throw new IllegalStateException ( "Tap is closed" );
    }

    FsSubscriber < T > fs = (FsSubscriber < T >) subscriber;

    synchronized ( subscriberLock ) {
      if ( subscribersList == null ) {
        subscribersList = new ArrayList <> ();
      }
      subscribersList.add ( fs );
      subscribersSnapshot = null;
    }
    version++;

    Subscription subscription = new FsSubscription ( subscriber.subject ().name (),
      (FsSubject < ? >) subject, () -> unsubscribe ( fs ) );

    return subscription;
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
      new FsSubject <> ( cortex ().name ( "reservoir.subscriber" ), resSubject, Subscriber.class ), ( channelSubject,
                                                                                                      registrar ) -> registrar.register (
      emission -> reservoir.capture ( emission, channelSubject ) ) );
    subscribe ( sub );
    return reservoir;
  }

  @Override
  public void close () {
    if ( closed ) return;
    closed = true;
    sourceSubscription.close ();
  }
}
