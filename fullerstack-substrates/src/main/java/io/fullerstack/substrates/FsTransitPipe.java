package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.function.Consumer;

/// Transit pipe — circuit-thread-only pipe for cascade re-entry.
///
/// emit() always enqueues to transit. No null check, no closed check,
/// no thread check. Used by flow terminals on inlet pipes to re-enter
/// the circuit's processing loop for cyclic stack safety.
///
/// Not exposed to external code — only used internally by flow materialisation.
final class FsTransitPipe < E > implements Pipe < E > {

  private final Consumer < Object > receiver;
  private final FsCircuit           circuit;

  FsTransitPipe ( Consumer < Object > receiver, FsCircuit circuit ) {
    this.receiver = receiver;
    this.circuit = circuit;
  }

  /// Transit emit — always submitTransit. No checks.
  @Override
  @jdk.internal.vm.annotation.ForceInline
  public void emit ( @NotNull E emission ) {
    circuit.submitTransit ( receiver, emission );
  }

  @Override
  public Subject < Pipe < E > > subject () {
    throw new UnsupportedOperationException ( "Transit pipe has no subject" );
  }

  @New
  @NotNull
  @Override
  public < I > Pipe < I > pipe ( @NotNull Flow < I, E > flow ) {
    throw new UnsupportedOperationException ( "Transit pipe does not support pipe(flow)" );
  }
}
