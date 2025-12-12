package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscription;

/// A cancellable handle representing an active subscription to a source.
///
/// Subscription is returned by [Source#subscribe(Subscriber)] and allows
/// the subscriber to cancel interest in future events. Each subscription has its
/// own identity (via [Substrate]) and can be closed to unregister.
///
/// ## Lifecycle
///
/// A subscription progresses through these states:
/// 1. **Active**: Created by `subscribe()`, subscriber receives callbacks
/// 2. **Closed**: After `close()` is called, no more callbacks occur
///
/// @see FsConduit#subscribe(Subscriber)
/// @see FsSubscriber
public final class FsSubscription
  implements Subscription {

  /// The subject identity for this subscription.
  private final Subject < Subscription > subject;

  /// Action to run on close.
  private final Runnable onClose;

  /// Whether this subscription has been closed.
  private volatile boolean closed;

  /// Creates a new subscription with the given subject and close action.
  ///
  /// @param subject the subject identity for this subscription
  /// @param onClose action to run when subscription is closed
  public FsSubscription ( Subject < Subscription > subject, Runnable onClose ) {
    this.subject = subject;
    this.onClose = onClose;
  }

  /// Returns the subject identity of this subscription.
  ///
  /// @return the subject of this subscription
  @Override
  public Subject < Subscription > subject () {
    return subject;
  }

  /// Closes this subscription, unregistering from the source.
  /// Idempotent - repeated calls have no effect.
  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      onClose.run ();
    }
  }

}
