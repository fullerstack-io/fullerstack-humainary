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

/// Inlet pipe — the external-facing pipe returned by conduit.get() and circuit.pipe().
///
/// emit() always enqueues to ingress (no thread check). External threads
/// call this to submit emissions to the circuit.
///
/// Each inlet has a paired transit pipe that shares the same receiver.
/// pipe.pipe(flow) materialises the flow terminal to call the transit pipe,
/// enabling zero-check cascade re-entry on the circuit thread.
@Provided
public final class FsPipe < E > implements Pipe < E > {

  @Stable private final Consumer < Object > receiver;
  @Stable private final FsCircuit           circuit;
  @Stable private final Name                name;
  @Stable private final FsSubject < ? >     parentSubject;

  /// Paired transit pipe — emit() always submitTransit. No checks.
  /// Used by flow terminals for circuit-thread re-entry.
  @Stable final FsTransitPipe < E > transitPipe;

  private volatile Subject < Pipe < E > > subject;

  FsPipe (
    Name name,
    FsCircuit circuit,
    FsSubject < ? > parentSubject,
    Consumer < Object > receiver
  ) {
    this.receiver = receiver;
    this.circuit = circuit;
    this.name = name;
    this.parentSubject = parentSubject;
    this.transitPipe = new FsTransitPipe <> ( receiver, circuit );
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

  /// Creates an outlet pipe with flow processing.
  /// Flow terminal calls the paired transit pipe (submitTransit, no checks).
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < I > Pipe < I > pipe ( @NotNull Flow < I, E > flow ) {
    requireNonNull ( flow, "flow must not be null" );
    FsFlow < I, E > fsFlow = (FsFlow < I, E >) flow;
    // Flow terminal targets the transit pipe — direct submitTransit,
    // no null/closed/thread checks. Safe because the flow only runs
    // on the circuit thread (dispatched there via channel rebuild).
    FsTransitPipe < E > tp = transitPipe;
    Consumer < I > chain = fsFlow.materialise ( v -> tp.emit ( v ) );
    // Outlet wraps the flow chain — runs synchronously on circuit thread
    Consumer < Object > outletReceiver = (Consumer < Object >) (Consumer < ? >) new FsCircuit.ReceptorAdapter <> ( chain::accept );
    // Return an inlet wrapping the outlet — external callers hit ingress,
    // circuit dequeues and calls the outlet on the circuit thread.
    return new FsPipe <> ( name, circuit, parentSubject, outletReceiver );
  }

  /// Inlet emit — always enqueues to ingress. No thread check.
  @Override
  public final void emit ( @NotNull final E emission ) {
    requireNonNull ( emission, "emission must not be null" );
    if ( circuit.closed ) return;
    circuit.submitIngress ( receiver, emission );
  }
}
