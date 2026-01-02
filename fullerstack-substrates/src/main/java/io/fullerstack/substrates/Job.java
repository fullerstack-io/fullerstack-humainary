package io.fullerstack.substrates;

import java.util.function.Consumer;

/**
 * Abstract job for circuit execution.
 * Has intrusive 'next' pointer for transit queue (cascade emissions).
 */
abstract class Job {

  /** Next job in intrusive transit queue (single-threaded access only). */
  Job next;

  abstract void run ();
}

/**
 * Concrete job for emit operations.
 */
final class EmitJob extends Job {
  private final Consumer < ? > consumer;
  private final Object         emission;

  EmitJob ( Consumer < ? > consumer, Object emission ) {
    this.consumer = consumer;
    this.emission = emission;
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  void run () {
    ( (Consumer < Object >) consumer ).accept ( emission );
  }
}

/**
 * Job wrapping a Runnable for simple tasks.
 */
final class RunnableJob extends Job {
  private final Runnable task;

  RunnableJob ( Runnable task ) {
    this.task = task;
  }

  @Override
  void run () {
    task.run ();
  }
}
