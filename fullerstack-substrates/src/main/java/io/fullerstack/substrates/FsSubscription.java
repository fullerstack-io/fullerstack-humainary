package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Idempotent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Provided;
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
@Provided
public final class FsSubscription implements Subscription {

  /// Lazy subject - only allocated when subject() is called.
  /// Saves FsSubject + FsId + AtomicLong CAS per subscribe in the common case.
  private final    Name                     name;
  private final    FsSubject < ? >          parent;
  private volatile Subject < Subscription > subject;

  /// Action to run on close.
  private final Runnable onClose;

  /// Whether this subscription has been closed.
  private volatile boolean closed;

  /// Creates a new subscription with lazy subject creation.
  ///
  /// @param name   the name for the subscription subject
  /// @param parent the parent subject for hierarchy
  /// @param onClose action to run when subscription is closed
  FsSubscription ( Name name, FsSubject < ? > parent, Runnable onClose ) {
    this.name = name;
    this.parent = parent;
    this.onClose = onClose;
  }

  /// Returns the subject identity of this subscription (lazy creation).
  @Override
  public Subject < Subscription > subject () {
    Subject < Subscription > s = subject;
    if ( s == null ) {
      s = new FsSubject <> ( name, parent, Subscription.class );
      subject = s;
    }
    return s;
  }

  /// Closes this subscription, unregistering from the source.
  /// Idempotent - repeated calls have no effect.
  @Idempotent
  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      onClose.run ();
    }
  }

}
