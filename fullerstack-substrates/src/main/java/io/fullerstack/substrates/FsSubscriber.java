package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/// A subscriber that holds a callback to be invoked when channels are activated.
///
/// Subscribers are created via Circuit.subscriber(name, callback) and passed
/// to Source.subscribe(). When a channel receives its first emission, the
/// callback is invoked with the channel's subject and a registrar for
/// registering pipes to receive emissions.
///
/// @param <E> the emission type
public final class FsSubscriber < E >
  implements Subscriber < E > {

  /// The subject identity for this subscriber.
  private final Subject < Subscriber < E > > subject;

  /// The callback invoked when a channel is activated.
  private final BiConsumer < Subject < Channel < E > >, Registrar < E > > callback;

  /// Whether this subscriber has been closed.
  private volatile boolean closed;

  /// Subscriptions created for this subscriber (to close when subscriber closes).
  private final CopyOnWriteArrayList < Subscription > subscriptions = new CopyOnWriteArrayList <> ();

  /// Creates a new subscriber with the given subject and callback.
  ///
  /// @param subject the subject identity for this subscriber
  /// @param callback the callback to invoke when channels are activated
  public FsSubscriber (
    Subject < Subscriber < E > > subject,
    BiConsumer < Subject < Channel < E > >, Registrar < E > > callback
  ) {
    this.subject = subject;
    this.callback = callback;
  }

  /// Returns the subject identity of this subscriber.
  @Override
  public Subject < Subscriber < E > > subject () {
    return subject;
  }

  /// Invokes the callback for a channel activation.
  public void activate ( Subject < Channel < E > > channelSubject, Registrar < E > registrar ) {
    if ( !closed ) {
      callback.accept ( channelSubject, registrar );
    }
  }

  /// Tracks a subscription created for this subscriber.
  void trackSubscription ( Subscription subscription ) {
    if ( !closed ) {
      subscriptions.add ( subscription );
    } else {
      // Already closed, close the new subscription immediately
      subscription.close ();
    }
  }

  /// Closes this subscriber and all its tracked subscriptions.
  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      // Close all tracked subscriptions
      for ( Subscription subscription : subscriptions ) {
        subscription.close ();
      }
      subscriptions.clear ();
    }
  }

}
