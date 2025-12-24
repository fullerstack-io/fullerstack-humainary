package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/**
 * Pipe implementation that routes all emissions through a circuit's queue.
 *
 * <p>The subject is passed in, not created. For chained pipes, the subject flows through from the
 * source (e.g., channel). This avoids allocating new subjects for wrapper pipes.
 *
 * <p>When chaining to another FsPipe, we store a direct reference to enable the continuation
 * fast-path optimization - avoiding queue operations for simple chains.
 *
 * @param <E> the emission type
 */
public final class FsPipe<E> implements Pipe<E> {

  private final Subject<Pipe<E>> subject;
  private final FsInternalCircuit circuit;
  private final Consumer<E> receiver;
  private final FsPipe<?> downstream;  // non-null when chained to another FsPipe

  /**
   * Creates a new pipe that routes through the circuit.
   *
   * @param subject the subject identity (passed through, not created)
   * @param circuit the circuit that owns this pipe
   * @param receiver the consumer that receives emissions
   */
  public FsPipe(Subject<Pipe<E>> subject, FsInternalCircuit circuit, Consumer<E> receiver) {
    this.subject = subject;
    this.circuit = circuit;
    this.receiver = receiver;
    this.downstream = null;
  }

  /**
   * Creates a new pipe chained to another FsPipe (enables continuation fast-path).
   *
   * @param subject the subject identity
   * @param circuit the circuit that owns this pipe
   * @param downstream the downstream FsPipe to chain to
   */
  public FsPipe(Subject<Pipe<E>> subject, FsInternalCircuit circuit, FsPipe<E> downstream) {
    this.subject = subject;
    this.circuit = circuit;
    this.downstream = downstream;
    this.receiver = null;  // Not used when downstream is set
  }

  @Override
  public Subject<Pipe<E>> subject() {
    return subject;
  }

  /**
   * Returns the terminal receiver for this pipe chain.
   * Used by FsFlow to avoid double-enqueue when wrapping pipes in flow operators.
   *
   * @return the terminal consumer that receives emissions
   */
  Consumer<E> terminalReceiver() {
    // Walk downstream chain to find terminal receiver
    FsPipe<?> terminal = this;
    while (terminal.downstream != null) {
      terminal = terminal.downstream;
    }
    @SuppressWarnings("unchecked")
    Consumer<E> result = (Consumer<E>) terminal.receiver;
    return result;
  }

  @Override
  public void emit(E emission) {
    // Reject emissions after circuit is closed
    if (!circuit.isRunning()) {
      return;
    }
    // Resolve downstream chain to get terminal receiver
    FsPipe<?> terminal = this;
    while (terminal.downstream != null) {
      terminal = terminal.downstream;
    }
    // Enqueue receiver and value (circuit creates Task record)
    circuit.enqueue(terminal.receiver, emission);
  }
}
