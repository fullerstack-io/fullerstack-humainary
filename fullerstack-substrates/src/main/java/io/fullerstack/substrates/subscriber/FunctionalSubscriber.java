package io.fullerstack.substrates.subscriber;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.id.UuidIdentifier;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Implementation of Substrates.Subscriber interface using the Strategy Pattern ().
 * <p>
 * < p >< b >Change:</b > Subscriber is now a marker interface with no methods. The BiConsumer
 * callback is stored internally and retrieved by the runtime when needed.
 * <p>
 * < p >This implementation supports two patterns via {@link SubscriberStrategy}:
 * < ul >
 * < li >< b >Function-based:</b > Uses a BiConsumer to handle (Subject, Registrar) callbacks</li >
 * < li >< b >Pool-based:</b > Uses a Pool to retrieve Pipes for specific Subjects</li >
 * </ul >
 * <p>
 * < p >< b >Function-Based Pattern:</b >
 * < pre >
 * Subscriber&lt;String&gt; subscriber = cortex.subscriber(
 * name,
 * (subject, registrar) -&gt; {
 * // Register pipes based on Subject
 * registrar.register(conduit.get(subject.name()));
 * }
 * );
 * </pre >
 * <p>
 * < p >< b >Pool-Based Pattern:</b >
 * < pre >
 * Pool&lt;Pipe&lt;String&gt;&gt; pipePool = name -&gt; conduit.get(name);
 * Subscriber&lt;String&gt; subscriber = cortex.subscriber(name, pipePool);
 * </pre >
 * <p>
 * < p >The runtime (RoutingConduit) retrieves the callback via {@link #getCallback()}
 * and invokes it when new Channels are created.
 *
 * @param < E > the emission type
 * @see Subscriber
 * @see Registrar
 * @see SubscriberStrategy
 * @see FunctionStrategy
 * @see PoolStrategy
 */
public class FunctionalSubscriber < E > implements Subscriber < E > {

  private final Subject < Subscriber < E > > subscriberSubject;
  private final SubscriberStrategy < E >     strategy;

  //  Store the callback internally since Subscriber interface has no methods
  private final BiConsumer < Subject < Channel < E > >, Registrar < E > > callback;

  /**
   * Creates a function-based Subscriber ().
   *
   * @param name    the name to be used by the subject assigned to the subscriber
   * @param handler the callback function that receives (Subject, Registrar)
   * @throws NullPointerException if name or handler is null
   */
  public FunctionalSubscriber ( Name name, BiConsumer < Subject < Channel < E > >, Registrar < E > > handler ) {
    Objects.requireNonNull ( name, "Subscriber name cannot be null" );
    Objects.requireNonNull ( handler, "Callback handler cannot be null" );
    this.subscriberSubject = createSubject ( name );
    this.strategy = new FunctionStrategy <> ( handler );
    this.callback = handler;  //  Store callback for runtime retrieval
  }

  /**
   * Creates a pool-based Subscriber ().
   * <p>
   * < p >When a Subject emits, this Subscriber retrieves a Pipe from the pool
   * using the Subject's name and registers it to receive the emission.
   *
   * @param name the name to be used by the subject assigned to the subscriber
   * @param pool the pool of Pipes keyed by Subject name
   * @throws NullPointerException if name or pool is null
   */
  public FunctionalSubscriber ( Name name, Pool < ? extends Pipe < E > > pool ) {
    Objects.requireNonNull ( name, "Subscriber name cannot be null" );
    Objects.requireNonNull ( pool, "Pipe pool cannot be null" );
    this.subscriberSubject = createSubject ( name );
    PoolStrategy < E > poolStrategy = new PoolStrategy <> ( pool );
    this.strategy = poolStrategy;
    //  Create callback from pool strategy
    this.callback = ( subject, registrar ) -> poolStrategy.apply ( subject, registrar );
  }

  @SuppressWarnings ( "unchecked" )
  private Subject < Subscriber < E > > createSubject ( Name name ) {
    return new ContextualSubject <> (
      UuidIdentifier.generate (),
      name,
      LinkedState.empty (),
      (Class < Subscriber < E > >) (Class < ? >) Subscriber.class
    );
  }

  @Override
  public Subject < Subscriber < E > > subject () {
    return subscriberSubject;
  }

  /**
   * Returns the callback for this subscriber (pattern).
   * <p>
   * < p >In , the Subscriber interface is a marker with no methods. The runtime
   * (RoutingConduit) retrieves the callback via this method and invokes it
   * when new Channels are created.
   *
   * @return the BiConsumer callback that handles (Subject, Registrar) notifications
   */
  public BiConsumer < Subject < Channel < E > >, Registrar < E > > getCallback () {
    return callback;
  }

  @Override
  public void close () {
    // No resources to close in this implementation
  }
}
