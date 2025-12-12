package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/**
 * Unified pipe implementation for all circuit types.
 *
 * <p>This pipe works with any circuit that implements {@link FsInternalCircuit}. Key features:
 *
 * <ul>
 *   <li>Lazy subject - only created when {@link #subject()} is called
 *   <li>Direct deliver() method for circuit thread to call
 *   <li>Stores receiver as Consumer for efficient invocation
 *   <li>Aggressively final for JIT inlining
 * </ul>
 *
 * @param <E> the emission type
 */
public final class FsCircuitPipe<E> implements Pipe<E> {

  private final FsInternalCircuit circuit;
  private final Consumer<E> receiver;
  private final Name name; // null for anonymous pipes

  // Lazy subject - only created on demand
  private volatile Subject<Pipe<E>> subject;

  /**
   * Creates a new circuit pipe.
   *
   * @param circuit the circuit that owns this pipe
   * @param receiver the consumer that receives emissions
   * @param name the pipe name (null for anonymous pipes)
   */
  public FsCircuitPipe(FsInternalCircuit circuit, Consumer<E> receiver, Name name) {
    this.circuit = circuit;
    this.receiver = receiver;
    this.name = name;
  }

  @Override
  public Subject<Pipe<E>> subject() {
    Subject<Pipe<E>> s = subject;
    if (s == null) {
      synchronized (this) {
        s = subject;
        if (s == null) {
          s = subject = circuit.createPipeSubject(name);
        }
      }
    }
    return s;
  }

  @Override
  public void emit(E emission) {
    circuit.emit(this, emission);
  }

  /**
   * Delivers the emission directly to the receiver. Called by the circuit thread - no queuing. This
   * method is hot path and should inline well.
   *
   * @param value the value to deliver (will be cast to E)
   */
  @SuppressWarnings("unchecked")
  public void deliver(Object value) {
    receiver.accept((E) value);
  }

  /**
   * Returns the receiver consumer. Used by circuits that need direct access to the consumer.
   *
   * @return the receiver consumer
   */
  public Consumer<E> receiver() {
    return receiver;
  }
}
