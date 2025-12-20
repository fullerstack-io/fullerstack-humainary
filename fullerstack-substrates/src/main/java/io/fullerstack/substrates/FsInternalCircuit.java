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

  /**
   * Enqueues a value to the ingress queue for async processing.
   *
   * <p>This method queues the emission to the circuit's ingress queue for processing
   * by the circuit thread. The pipe's deliver() method will be called on the circuit thread.
   *
   * @param pipe the pipe that is emitting
   * @param value the value to emit
   * @param <E> the emission type
   */
  <E> void enqueue(FsAsyncPipe<E> pipe, Object value);
}
