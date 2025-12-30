package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.function.Consumer;

/**
 * Pipe implementation that delegates to circuit for async emissions.
 *
 * <p>Cascade emissions (on circuit thread) call receiver directly.
 * Async emissions submit a job to the circuit's queue.
 *
 * @param <E> the emission type
 */
@Provided
public final class FsPipe < E > implements Pipe < E > {

  private final Subject < Pipe < E > > subject;
  private final FsCircuit              circuit;
  private final Consumer < E >         receiver;

  public FsPipe ( Subject < Pipe < E > > subject, FsCircuit circuit, Consumer < E > receiver ) {
    this.subject = subject;
    this.circuit = circuit;
    this.receiver = receiver;
  }

  @Override
  public Subject < Pipe < E > > subject () {
    return subject;
  }

  /**
   * Returns the receiver for this pipe (used by Flow for chain optimization).
   *
   * @return the consumer that receives emissions
   */
  Consumer < E > receiver () {
    return receiver;
  }

  @Override
  public void emit ( @NotNull E emission ) {
    circuit.submit ( new EmitJob ( receiver, emission ) );
  }
}
