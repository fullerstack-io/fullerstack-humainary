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

  /// Action to run on close (unsubscribe from source).
  private final Runnable onClose;

  /// User-supplied close callback — fires exactly once when subscription terminates.
  private final java.util.function.Consumer < ? super Subscription > onCloseCallback;

  /// Whether this subscription has been closed.
  private volatile boolean closed;

  /// Creates a new subscription with lazy subject creation.
  ///
  /// @param name   the name for the subscription subject
  /// @param parent the parent subject for hierarchy
  /// @param onClose action to run when subscription is closed (internal cleanup)
  FsSubscription ( Name name, FsSubject < ? > parent, Runnable onClose ) {
    this ( name, parent, onClose, null );
  }

  /// Creates a new subscription with lazy subject creation and an onClose callback.
  ///
  /// @param name            the name for the subscription subject
  /// @param parent          the parent subject for hierarchy
  /// @param onClose         action to run when subscription is closed (internal cleanup)
  /// @param onCloseCallback user-supplied callback fired exactly once on termination, or null
  FsSubscription ( Name name, FsSubject < ? > parent, Runnable onClose,
                   java.util.function.Consumer < ? super Subscription > onCloseCallback ) {
    this.name = name;
    this.parent = parent;
    this.onClose = onClose;
    this.onCloseCallback = onCloseCallback;
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
      if ( onCloseCallback != null ) {
        try {
          onCloseCallback.accept ( this );
        } catch ( Exception e ) {
          // SPEC §15.4: onClose exceptions do not propagate to circuit dispatch loop
        }
      }
    }
  }

}
