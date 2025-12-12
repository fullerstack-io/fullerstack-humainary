package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;

/**
 * Internal interface for circuit implementations to support FsConduit/FsCell and unified pipes.
 *
 * <p>This interface exposes the internal methods needed by FsConduit, FsCell, and FsCircuitPipe to
 * function correctly with any Circuit implementation.
 */
public interface FsInternalCircuit extends Circuit {

  /** Submits a task to be executed on the circuit thread. */
  void submit(Runnable task);

  /** Returns true if the circuit is still running. */
  boolean isRunning();

  /**
   * Emits a value through the given pipe. The circuit implementation handles queueing and
   * thread-safe delivery.
   *
   * @param pipe the pipe that is emitting
   * @param value the value to emit
   * @param <E> the emission type
   */
  <E> void emit(FsCircuitPipe<E> pipe, Object value);

  /**
   * Creates a subject for a pipe with the given name.
   *
   * @param name the pipe name (may be null for anonymous pipes)
   * @param <E> the emission type
   * @return a new subject for the pipe
   */
  <E> Subject<Pipe<E>> createPipeSubject(Name name);
}
