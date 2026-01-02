package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.function.Consumer;

/**
 * Pipe implementation that delegates to circuit for async emissions.
 *
 * <p>
 * Cascade emissions (on circuit thread) call receiver directly. Async emissions
 * submit a job to the circuit's queue.
 *
 * <p>
 * Subject is created lazily on first access to avoid AtomicLong overhead in hot path.
 *
 * @param <E>
 *            the emission type
 */
@Provided
public final class FsPipe < E > implements Pipe < E > {

  private final Name           name; // null for anonymous pipes
  private final FsCircuit      circuit;
  private final Consumer < E > receiver;

  /** Lazily-initialized subject (benign race - multiple creates are harmless). */
  private volatile Subject < Pipe < E > > subject;

  /** Primary constructor - lazy subject creation for hot path. */
  public FsPipe ( Name name, FsCircuit circuit, Consumer < E > receiver ) {
    this.name = name;
    this.circuit = circuit;
    this.receiver = receiver;
  }

  /** Secondary constructor - eager subject for when subject is pre-computed. */
  FsPipe ( Subject < Pipe < E > > subject, FsCircuit circuit, Consumer < E > receiver ) {
    this.name = subject.name ();
    this.circuit = circuit;
    this.receiver = receiver;
    this.subject = subject; // Already have it, no lazy creation needed
  }

  @Override
  public Subject < Pipe < E > > subject () {
    Subject < Pipe < E > > s = subject;
    if ( s == null ) {
      s = new FsSubject <> ( name, (FsSubject < ? >) circuit.subject (), Pipe.class );
      subject = s;
    }
    return s;
  }

  /**
   * Returns the name for this pipe (null for anonymous pipes).
   *
   * @return the pipe name, or null
   */
  Name name () {
    return name;
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
