package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/**
 * Async pipe implementation that routes emissions through a circuit's queue.
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
public final class FsAsyncPipe<E> extends FsSubstrate<Pipe<E>> implements Pipe<E> {

  private final FsInternalCircuit circuit;
  private final Consumer<E> receiver;

  /**
   * Creates a new async pipe.
   *
   * @param parent the parent subject (circuit's subject)
   * @param name the pipe name (null for anonymous pipes)
   * @param circuit the circuit that owns this pipe
   * @param receiver the consumer that receives emissions
   */
  public FsAsyncPipe(FsSubject<?> parent, Name name, FsInternalCircuit circuit, Consumer<E> receiver) {
    super(parent, name);
    this.circuit = circuit;
    this.receiver = receiver;
  }

  @Override
  protected Class<?> type() {
    return Pipe.class;
  }

  @Override
  public Subject<Pipe<E>> subject() {
    return lazySubject();
  }

  @Override
  public void emit(E emission) {
    circuit.enqueue(this, emission);
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

  /**
   * Returns the circuit that owns this pipe.
   *
   * @return the owning circuit
   */
  public FsInternalCircuit circuit() {
    return circuit;
  }
}
