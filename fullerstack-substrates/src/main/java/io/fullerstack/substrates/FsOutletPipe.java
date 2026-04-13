package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/// Outlet pipe — runs on the circuit thread. emit() dispatches synchronously
/// to the receiver with no queue, no null check, no thread check.
///
/// Created by pipe.pipe(flow) for flow-processed pipes. The flow chain runs
/// synchronously on the circuit thread and delivers to the target's receiver.
///
/// If the receiver is an inlet pipe's dispatch, the emission flows through
/// synchronously. If it feeds back to a conduit pipe (inlet), that pipe's
/// emit() enqueues — the queue boundary is at the inlet, not here.
@Provided
final class FsOutletPipe < E > implements Pipe < E > {

  private final Consumer < Object > receiver;
  private final FsCircuit           circuit;
  private final Name                name;
  private final FsSubject < ? >     parentSubject;

  private volatile Subject < Pipe < E > > subject;

  FsOutletPipe (
    Name name,
    FsCircuit circuit,
    FsSubject < ? > parentSubject,
    Consumer < Object > receiver
  ) {
    this.receiver = receiver;
    this.circuit = circuit;
    this.name = name;
    this.parentSubject = parentSubject;
  }

  @Override
  public Subject < Pipe < E > > subject () {
    Subject < Pipe < E > > s = subject;
    if ( s == null ) {
      Name actualName = ( name != null ) ? name : parentSubject.name ();
      s = new FsSubject <> ( actualName, parentSubject, Pipe.class );
      subject = s;
    }
    return s;
  }

  /// Outlet emit — synchronous dispatch to receiver. No queue.
  /// Only called from the circuit thread (flow chains, channel dispatch).
  @Override
  @jdk.internal.vm.annotation.ForceInline
  @SuppressWarnings ( "unchecked" )
  public void emit ( @NotNull E emission ) {
    receiver.accept ( emission );
  }

  /// Creates another outlet pipe with flow processing.
  /// Terminal dispatches to this pipe's receiver synchronously.
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < I > Pipe < I > pipe ( @NotNull Flow < I, E > flow ) {
    requireNonNull ( flow, "flow must not be null" );
    FsFlow < I, E > fsFlow = (FsFlow < I, E >) flow;
    Consumer < I > chain = fsFlow.materialise ( v -> receiver.accept ( v ) );
    return new FsOutletPipe <> ( name, circuit, parentSubject,
      (Consumer < Object >) (Consumer < ? >) new FsCircuit.ReceptorAdapter <> ( chain::accept ) );
  }
}
