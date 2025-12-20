package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/**
 * A simple pipe that delegates emit to a consumer.
 *
 * <p>Subject is created lazily on first call to {@link #subject()} via the base class.
 *
 * @param <E> the class type of emitted value
 */
public final class FsConsumerPipe<E> extends FsSubstrate<Pipe<E>> implements Pipe<E> {

  private final Consumer<E> consumer;

  /**
   * Creates a new consumer pipe with lazy subject creation.
   *
   * @param parent the parent subject (for hierarchy)
   * @param name the pipe name (may be null for anonymous pipes)
   * @param consumer the consumer that receives emissions
   */
  public FsConsumerPipe(FsSubject<?> parent, Name name, Consumer<E> consumer) {
    super(parent, name);
    this.consumer = consumer;
  }

  @Override
  protected Class<?> type() {
    return Pipe.class;
  }

  @Override
  public Subject<Pipe<E>> subject() {
    return lazySubject();
  }

  @Override
  public void emit(E emission) {
    consumer.accept(emission);
  }
}
