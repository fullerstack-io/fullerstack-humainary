package io.fullerstack.substrates.subscription;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.id.SequentialIdentifier;
import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;

import java.util.Objects;

/**
 * Implementation of Substrates.Subscription for managing subscriber lifecycle.
 * <p>
 * Subscription is returned from Source.subscribe() and allows unsubscribing
 * by calling close(). Each subscription has a unique ID and subject.
 * <p>
 * Features:
 * <ul>
 * <li>Lazy Subject creation - only created if subject() is called</li>
 * <li>Sequential ID generation - fast, no UUID overhead</li>
 * <li>Minimal allocations - just callback storage</li>
 * </ul>
 *
 * @see Subscription
 * @see Source
 * @see Subscriber
 */
public class CallbackSubscription implements Subscription {

  private final Runnable onClose;
  private final Subject<?> parentSubject;  // Store for lazy Subject creation
  private Subject<Subscription> subscriptionSubject;  // Lazy - created only if subject() called
  private boolean closed = false;  // No volatile needed - single-threaded circuit execution

  /**
   * Creates a Subscription with the given close handler and parent Subject.
   *
   * @param onClose       runnable to execute when close() is called
   * @param parentSubject the parent Subject (from the Source being subscribed to)
   * @throws NullPointerException if onClose or parentSubject is null
   */
  public CallbackSubscription ( Runnable onClose, Subject < ? > parentSubject ) {
    this.onClose = Objects.requireNonNull ( onClose, "onClose cannot be null" );
    this.parentSubject = Objects.requireNonNull ( parentSubject, "parentSubject cannot be null" );
    // Note: Subject creation deferred until subject() is actually called
  }

  @Override
  public Subject<Subscription> subject () {
    // Lazy creation - only create Subject if actually requested
    if (subscriptionSubject == null) {
      subscriptionSubject = createSubject();
    }
    return subscriptionSubject;
  }

  private Subject<Subscription> createSubject() {
    Id subscriptionId = SequentialIdentifier.generate();
    return new ContextualSubject<>(
      subscriptionId,
      InternedName.of("subscription").name(subscriptionId.toString()),
      LinkedState.empty(),
      Subscription.class,
      parentSubject  // Parent Subject for hierarchy
    );
  }

  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      onClose.run ();
    }
  }
}
