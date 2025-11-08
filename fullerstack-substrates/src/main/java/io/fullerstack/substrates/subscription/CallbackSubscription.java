package io.fullerstack.substrates.subscription;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.id.UuidIdentifier;
import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;

import java.util.Objects;

/**
 * Implementation of Substrates.Subscription for managing subscriber lifecycle.
 * <p>
 * < p >Subscription is returned from Source.subscribe() and allows unsubscribing
 * by calling close(). Each subscription has a unique ID and subject.
 *
 * @see Subscription
 * @see Source
 * @see Subscriber
 */
public class CallbackSubscription implements Subscription {

  private final    Id                       subscriptionId;
  private final    Subject < Subscription > subscriptionSubject;
  private final    Runnable                 onClose;
  private volatile boolean                  closed = false;

  /**
   * Creates a Subscription with the given close handler and parent Subject.
   *
   * @param onClose       runnable to execute when close() is called
   * @param parentSubject the parent Subject (from the Source being subscribed to)
   * @throws NullPointerException if onClose or parentSubject is null
   */
  public CallbackSubscription ( Runnable onClose, Subject < ? > parentSubject ) {
    this.onClose = Objects.requireNonNull ( onClose, "onClose cannot be null" );
    Objects.requireNonNull ( parentSubject, "parentSubject cannot be null" );
    this.subscriptionId = UuidIdentifier.generate ();
    this.subscriptionSubject = new ContextualSubject <> (
      subscriptionId,
      InternedName.of ( "subscription" ).name ( subscriptionId.toString () ),
      LinkedState.empty (),
      Subscription.class,
      parentSubject  // Parent Subject for hierarchy
    );
  }

  @Override
  public Subject subject () {
    return subscriptionSubject;
  }

  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      onClose.run ();
    }
  }
}
