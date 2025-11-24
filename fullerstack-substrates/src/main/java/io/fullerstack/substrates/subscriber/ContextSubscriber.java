package io.fullerstack.substrates.subscriber;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.subject.ContextualSubject;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Contextual implementation of Substrates.Subscriber interface.
 * <p>
 * Provides a subscriber with identity (via Subject) that wraps a callback function.
 * The callback is invoked when new channels are created in subscribed sources.
 * <p>
 * Features:
 * <ul>
 * <li>Lazy Subject creation - only created if subject() is called</li>
 * <li>Sequential ID generation - fast, no UUID overhead</li>
 * <li>Minimal allocations - just callback storage</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 * Subscriber&lt;Integer&gt; subscriber = cortex.subscriber(
 *   name,
 *   (subject, registrar) -> {
 *     registrar.register(conduit.percept(subject.name()));
 *   }
 * );
 * </pre>
 *
 * @param <E> the emission type
 */
public class ContextSubscriber<E> implements Subscriber<E> {

  private final Name name;  // Store for lazy Subject creation
  private final BiConsumer<Subject<Channel<E>>, Registrar<E>> callback;
  private Subject<Subscriber<E>> subscriberSubject;  // Lazy - created only if subject() called

  /**
   * Creates a Subscriber with a callback function.
   *
   * @param name    the name for the subscriber
   * @param handler the callback function
   * @throws NullPointerException if name or handler is null
   */
  public ContextSubscriber(Name name, BiConsumer<Subject<Channel<E>>, Registrar<E>> handler) {
    this.name = Objects.requireNonNull(name, "Subscriber name cannot be null");
    this.callback = Objects.requireNonNull(handler, "Callback handler cannot be null");
    // Note: Subject creation deferred until subject() is actually called
  }

  /**
   * Creates a Subscriber that looks up Pipes from a Lookup.
   *
   * @param name   the name for the subscriber
   * @param lookup the lookup to retrieve pipes from
   * @throws NullPointerException if name or lookup is null
   */
  public ContextSubscriber(Name name, Lookup<? extends Pipe<E>> lookup) {
    this.name = Objects.requireNonNull(name, "Subscriber name cannot be null");
    Objects.requireNonNull(lookup, "Lookup cannot be null");
    this.callback = (subject, registrar) -> {
      Pipe<E> pipe = lookup.percept(subject.name());
      if (pipe != null) {
        registrar.register(pipe);
      }
    };
  }

  @Override
  public Subject<Subscriber<E>> subject() {
    // Lazy creation - only create Subject if actually requested
    if (subscriberSubject == null) {
      subscriberSubject = createSubject();
    }
    return subscriberSubject;
  }

  @SuppressWarnings("unchecked")
  private Subject<Subscriber<E>> createSubject() {
    return new ContextualSubject<>(
      name,
      (Class<Subscriber<E>>) (Class<?>) Subscriber.class
    );
  }

  /**
   * Returns the callback for this subscriber.
   * The runtime (RoutingConduit) calls this to get the callback.
   *
   * @return the callback that handles (Subject, Registrar) notifications
   */
  public BiConsumer<Subject<Channel<E>>, Registrar<E>> getCallback() {
    return callback;
  }

  @Override
  public void close() {
    // No resources to close
  }
}
