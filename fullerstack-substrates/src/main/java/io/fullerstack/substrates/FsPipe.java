package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;

import jdk.internal.vm.annotation.Stable;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Pipe implementation with direct circuit emission.
 *
 * <p>Routes emissions directly to circuit:
 * <ul>
 *   <li>External threads → circuit.submitIngress()
 *   <li>Worker thread → circuit.submitTransit()
 * </ul>
 */
@Provided
public final class FsPipe < E > implements Pipe < E > {

  @Stable private final Consumer < Object > receiver;
  @Stable private final FsCircuit           circuit;
  @Stable private final Thread              worker;
  @Stable private final Name                name;
  @Stable private final FsSubject < ? >     parentSubject;

  private volatile Subject < Pipe < E > > subject;

  FsPipe (
    Name name,
    FsCircuit circuit,
    FsSubject < ? > parentSubject,
    Consumer < Object > receiver
  ) {
    this.receiver = receiver;
    this.circuit = circuit;
    this.worker = circuit.worker ();
    this.name = name;
    this.parentSubject = parentSubject;
  }

  @Override
  public final Subject < Pipe < E > > subject () {
    Subject < Pipe < E > > s = subject;
    if ( s == null ) {
      Name actualName = ( name != null ) ? name : parentSubject.name ();
      s = new FsSubject <> ( actualName, parentSubject, Pipe.class );
      subject = s;
    }
    return s;
  }

  final Name name () {
    return name;
  }

  final FsCircuit circuit () {
    return circuit;
  }

  final Consumer < Object > receiver () {
    return receiver;
  }

  /**
   * Returns true if caller is on this pipe's circuit thread.
   * Used to determine if receiver can be extracted safely (same circuit)
   * or if emit() must be used (cross-circuit).
   */
  @jdk.internal.vm.annotation.ForceInline
  final boolean isOnCircuitThread () {
    return Thread.currentThread () == worker;
  }

  /// Creates a new pipe that applies flow processing before emitting to this pipe.
  /// Each call materialises an independent operator chain from the immutable flow.
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < I > Pipe < I > pipe ( @NotNull Flow < I, E > flow ) {
    requireNonNull ( flow, "flow must not be null" );
    FsFlow < I, E > fsFlow = (FsFlow < I, E >) flow;
    // Flow terminal re-enters this inlet's emit(), which enqueues to
    // the circuit (transit if on circuit thread, ingress if external).
    // This is the queue boundary for cyclic stack safety.
    Consumer < I > chain = fsFlow.materialise ( v -> emit ( v ) );
    @SuppressWarnings ( "unchecked" )
    Consumer < Object > outletReceiver = (Consumer < Object >) (Consumer < ? >) new FsCircuit.ReceptorAdapter <> ( chain::accept );
    return new FsOutletPipe <> ( name, circuit, parentSubject, outletReceiver );
  }

  @Override
  @jdk.internal.vm.annotation.ForceInline
  public final void emit ( @NotNull final E emission ) {
    requireNonNull ( emission, "emission must not be null" );
    if ( circuit.closed ) return;
    if ( isOnCircuitThread () ) {
      circuit.submitTransit ( receiver, emission );
    } else {
      circuit.submitIngress ( receiver, emission );
    }
  }
}
