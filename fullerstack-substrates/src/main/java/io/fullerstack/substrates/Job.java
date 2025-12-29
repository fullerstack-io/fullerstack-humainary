package io.fullerstack.substrates;

import java.util.function.Consumer;

/**
 * Abstract job with intrusive next pointer for queue linkage.
 * Used by both ingress (MPSC) and transit (FIFO) queues.
 */
abstract class Job {
  volatile Job next;

  abstract void run();
}

/**
 * Concrete job for emit operations.
 */
final class EmitJob extends Job {
  private final Consumer<?> consumer;
  private final Object emission;

  EmitJob(Consumer<?> consumer, Object emission) {
    this.consumer = consumer;
    this.emission = emission;
  }

  @Override
  @SuppressWarnings("unchecked")
  void run() {
    ((Consumer<Object>) consumer).accept(emission);
  }
}
