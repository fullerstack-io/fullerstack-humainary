package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Circuit;
import java.util.function.Consumer;

/**
 * Internal interface for circuit implementations to support FsConduit/FsCell and async pipes.
 *
 * <p>This interface exposes the internal methods needed by FsConduit, FsCell, and FsPipe to
 * function correctly with any Circuit implementation.
 */
public interface FsInternalCircuit extends Circuit {

  /** Returns true if the circuit is still running. */
  boolean isRunning();

  /** Returns true if the current thread is the circuit's processing thread. */
  boolean isCircuitThread();

  /**
   * Enqueues a task for execution on the circuit thread.
   *
   * <p>If called from the circuit thread, adds to transit queue (cascading). If called from
   * external thread, adds to ingress queue.
   *
   * @param receiver the consumer to invoke with the value
   * @param value the value to pass to the receiver
   */
  void enqueue(Consumer<?> receiver, Object value);

}
