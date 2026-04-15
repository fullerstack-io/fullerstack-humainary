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

/// Pipe — the emission carrier.
///
/// Holds a receiver and a circuit. emit(v) enqueues [receiver, v]
/// to the circuit's queue. That's it.
///
/// The receiver is typically a Channel (for conduit pipes) but can be
/// any Consumer<Object> (flow receivers, receptor adapters).
///
/// pipe.pipe(flow) creates a new pipe whose receiver runs the flow
/// chain on the circuit thread, then delivers to the original target.
@Provided
public final class FsPipe < E > implements Pipe < E > {

  private final Consumer < Object > receiver;
  private final FsCircuit           circuit;

  private volatile Subject < Pipe < E > > subject;

  /// Conduit pipe constructor — receiver is a channel.
  FsPipe ( FsChannel < E > channel, FsCircuit circuit ) {
    this.receiver = channel;
    this.circuit = circuit;
  }

  /// General constructor — for flow pipes, circuit.pipe(receptor), etc.
  FsPipe ( Consumer < Object > receiver, FsCircuit circuit ) {
    this.receiver = receiver;
    this.circuit = circuit;
  }

  @Override
  public Subject < Pipe < E > > subject () {
    Subject < Pipe < E > > s = subject;
    if ( s == null ) {
      // Derive subject from receiver if it's a channel
      @SuppressWarnings ( "unchecked" )
      Subject < Pipe < E > > derived = ( receiver instanceof FsChannel < ? > ch )
        ? (Subject < Pipe < E > >) (Subject < ? >) ch.subject ()
        : new FsSubject <> ( null, null, Pipe.class );
      s = derived;
      subject = s;
    }
    return s;
  }

  Consumer < Object > receiver () {
    return receiver;
  }

  FsCircuit circuit () {
    return circuit;
  }

  // ─── Emit ───

  @Override
  @jdk.internal.vm.annotation.ForceInline
  public void emit ( @NotNull E emission ) {
    requireNonNull ( emission, "emission must not be null" );
    if ( circuit.closed ) return;
    if ( Thread.currentThread () == circuit.worker () ) {
      circuit.submitTransit ( receiver, emission );
    } else {
      circuit.submitIngress ( receiver, emission );
    }
  }

  // ─── Pipe composition ───

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < I > Pipe < I > pipe ( @NotNull Flow < I, E > flow ) {
    requireNonNull ( flow, "flow must not be null" );
    FsFlow < I, E > fsFlow = (FsFlow < I, E >) flow;
    // Flow terminal: if receiver is a channel (conduit pipe), use
    // submitTransit directly for cascade safety (flow runs on circuit thread).
    // Otherwise fall back to emit() for correct routing.
    Consumer < Object > target = receiver;
    FsCircuit c = circuit;
    Consumer < I > chain;
    if ( target instanceof FsChannel < ? > ch ) {
      // Conduit pipe: flow terminal reads channel.dispatch at re-entry.
      // dispatch IS Consumer<Object> — goes directly into transit, no wrapper.
      // Falls back to channel before first rebuild (dispatch is null).
      chain = fsFlow.materialise ( v -> {
        Consumer < Object > d = ch.dispatch;
        c.submitTransit ( d != null ? d : target, v );
      } );
    } else {
      // Non-conduit pipe: flow terminal delivers to receiver synchronously
      chain = fsFlow.materialise ( v -> target.accept ( v ) );
    }
    // The flow receiver: runs the flow chain on the circuit thread.
    Consumer < Object > flowReceiver = (Consumer < Object >) (Consumer < ? >) chain;
    return new FsPipe <> ( flowReceiver, circuit );
  }
}
