package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Registrar;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/// A subscriber that holds a callback to be invoked when channels are activated.
///
/// Subscribers are created via Circuit.subscriber(name, callback) and passed
/// to Source.subscribe(). When a channel receives its first emission, the
/// callback is invoked with the channel's subject and a registrar for
/// registering pipes to receive emissions.
///
/// Closing a subscriber sets the closed flag. Subsequent channel activations
/// see closed=true and skip callback invocation. Lazy rebuild on next emission
/// detects no pipes registered and cleans up the channel state.
///
/// @param <E> the emission type
@Provided
public final class FsSubscriber < E > implements Subscriber < E > {

  /// The subject identity for this subscriber.
  private final Subject < Subscriber < E > > subject;

  /// The callback invoked when a channel is activated.
  private final BiConsumer < Subject < Channel < E > >, Registrar < E > > callback;

  /// Whether this subscriber has been closed.
  /// Volatile: read by activate() on circuit thread, written by close() from any thread.
  private volatile boolean closed;

  /// Tracked subscriptions — closed on subscriber.close().
  /// Guarded by synchronization: written by trackSubscription() from any thread,
  /// read and cleared by close() from any thread.
  private List < Subscription > subscriptions;

  /// Creates a new subscriber with the given subject and callback.
  ///
  /// @param subject the subject identity for this subscriber
  /// @param callback the callback to invoke when channels are activated
  public FsSubscriber ( Subject < Subscriber < E > > subject, BiConsumer < Subject < Channel < E > >, Registrar < E > > callback ) {
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

  /// Tracks a subscription so it will be closed when this subscriber is closed.
  synchronized void trackSubscription ( Subscription subscription ) {
    if ( closed ) {
      subscription.close ();
      return;
    }
    if ( subscriptions == null ) {
      subscriptions = new ArrayList <> ();
    }
    subscriptions.add ( subscription );
  }

  /// Closes this subscriber. Closes all tracked subscriptions and
  /// subsequent activations become no-ops.
  @Idempotent
  @Override
  public synchronized void close () {
    if ( closed ) return;
    closed = true;
    if ( subscriptions != null ) {
      for ( Subscription s : subscriptions ) {
        s.close ();
      }
      subscriptions = null;
    }
  }

}
