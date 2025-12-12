package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/// A simple pipe that delegates emit to a consumer.
///
/// @param <E> the class type of emitted value
public final class FsConsumerPipe<E> implements Pipe<E> {

  private final Subject<Pipe<E>> subject;
  private final Consumer<E> consumer;

  public FsConsumerPipe(Subject<Pipe<E>> subject, Consumer<E> consumer) {
    this.subject = subject;
    this.consumer = consumer;
  }

  @Override
  public Subject<Pipe<E>> subject() {
    return subject;
  }

  @Override
  public void emit(E emission) {
    consumer.accept(emission);
  }
}
