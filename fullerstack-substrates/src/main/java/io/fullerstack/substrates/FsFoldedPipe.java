package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/**
 * Pipe implementation for the folded circuit model.
 *
 * <p>This pipe delegates emission handling to the circuit, which decides whether to link (cascade)
 * or enqueue to ingress based on whether we're on the circuit thread.
 *
 * @param <E> the emission type
 */
public final class FsFoldedPipe<E> implements Pipe<E> {

  private final Subject<Pipe<E>> subject;
  private final FsFoldedCircuit circuit;
  private final Consumer<E> receiver;

  /**
   * Creates a new folded pipe.
   *
   * @param subject the subject identity (passed through, not created)
   * @param circuit the circuit that owns this pipe
   * @param receiver the consumer that receives emissions
   */
  public FsFoldedPipe(Subject<Pipe<E>> subject, FsFoldedCircuit circuit, Consumer<E> receiver) {
    this.subject = subject;
    this.circuit = circuit;
    this.receiver = receiver;
  }

  @Override
  public Subject<Pipe<E>> subject() {
    return subject;
  }

  /**
   * Returns the receiver for this pipe (used by Flow for chain optimization).
   *
   * @return the consumer that receives emissions
   */
  Consumer<E> receiver() {
    return receiver;
  }

  @Override
  public void emit(E emission) {
    circuit.emit(emission, receiver);
  }
}
