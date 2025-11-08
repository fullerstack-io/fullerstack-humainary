package io.fullerstack.substrates.subscriber;

import io.humainary.substrates.api.Substrates.*;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Strategy interface for different Subscriber implementation patterns.
 * <p>
 * < p >Sealed hierarchy ensures type-safe exhaustive handling of the two subscriber strategies:
 * < ul >
 * < li >{@link FunctionStrategy} - Uses a BiConsumer to dynamically create pipes</li >
 * < li >{@link PoolStrategy} - Uses a Pool to look up pre-existing pipes</li >
 * </ul >
 *
 * @param < E > the emission type
 * @see FunctionalSubscriber
 * @see FunctionStrategy
 * @see PoolStrategy
 */
sealed interface SubscriberStrategy < E >
  permits FunctionStrategy, PoolStrategy {

  /**
   * Applies this strategy to register pipes for a subject.
   *
   * @param subject   the Subject of the Channel that emitted
   * @param registrar the registrar to use for pipe registration
   */
  void apply ( Subject < Channel < E > > subject, Registrar < E > registrar );
}

/**
 * Function-based subscriber strategy.
 * <p>
 * < p >Uses a BiConsumer that receives the emitting subject and a registrar,
 * allowing dynamic pipe creation based on the subject's characteristics.
 * <p>
 * < p >Example:
 * < pre >
 * new FunctionStrategy&lt;&gt;((subject, registrar) -&gt; {
 * if (subject.name().value().contains("error")) {
 * registrar.register(value -&gt; errorLogger.log(value));
 * } else {
 * registrar.register(value -&gt; System.out.println(value));
 * }
 * });
 * </pre >
 *
 * @param <       E > the emission type
 * @param handler the BiConsumer that handles subject registration
 */
record FunctionStrategy < E >( BiConsumer < Subject < Channel < E > >, Registrar < E > > handler )
  implements SubscriberStrategy < E > {

  public FunctionStrategy {
    Objects.requireNonNull ( handler, "Handler cannot be null" );
  }

  @Override
  public void apply ( Subject < Channel < E > > subject, Registrar < E > registrar ) {
    handler.accept ( subject, registrar );
  }
}

/**
 * Pool-based subscriber strategy.
 * <p>
 * < p >Uses a Pool to look up pre-existing pipes by subject name.
 * This is useful when you have a fixed set of pipes indexed by name.
 * <p>
 * < p >Example:
 * < pre >
 * Pool&lt;Pipe&lt;String&gt;&gt; pipePool = name -&gt; preCreatedPipes.get(name);
 * new PoolStrategy&lt;&gt;(pipePool);
 * </pre >
 *
 * @param <    E > the emission type
 * @param pool the pool of pipes indexed by subject name
 */
record PoolStrategy < E >( Pool < ? extends Pipe < E > > pool )
  implements SubscriberStrategy < E > {

  public PoolStrategy {
    Objects.requireNonNull ( pool, "Pool cannot be null" );
  }

  @Override
  public void apply ( Subject < Channel < E > > subject, Registrar < E > registrar ) {
    Pipe < E > pipe = pool.get ( subject.name () );
    if ( pipe != null ) {
      registrar.register ( pipe );
    }
  }
}
