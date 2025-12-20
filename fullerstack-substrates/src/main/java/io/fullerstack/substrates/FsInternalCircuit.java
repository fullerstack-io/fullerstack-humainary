package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Circuit;

/**
 * Internal interface for circuit implementations to support FsConduit/FsCell and async pipes.
 *
 * <p>This interface exposes the internal methods needed by FsConduit, FsCell, and FsAsyncPipe to
 * function correctly with any Circuit implementation.
 */
public interface FsInternalCircuit extends Circuit {

  /** Submits a task to be executed on the circuit thread. */
  void submit(Runnable task);

  /** Returns true if the circuit is still running. */
  boolean isRunning();

  /** Returns true if the current thread is the circuit's processing thread. */
  boolean isCircuitThread();

  /**
   * Enqueues a value to the ingress queue for async processing.
   *
   * <p>This method queues the emission to the circuit's ingress queue for processing by the
   * circuit thread. The pipe's deliver() method will be called on the circuit thread.
   *
   * @param pipe the pipe that is emitting
   * @param value the value to emit
   * @param <E> the emission type
   */
  <E> void enqueue(FsAsyncPipe<E> pipe, Object value);

  /**
   * Adds a task to the transit queue for cascading (depth-first) processing.
   *
   * <p>This method should only be called from the circuit thread. Tasks added here have priority
   * over the ingress queue, ensuring depth-first processing of cascading emissions.
   *
   * @param task the task to cascade
   */
  void cascade(Runnable task);
}
