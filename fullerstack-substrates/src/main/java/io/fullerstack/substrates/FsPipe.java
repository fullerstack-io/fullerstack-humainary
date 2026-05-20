package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
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

  /// 2.7: named-pipe constructor. Pre-seeds the subject with the given
  /// name + circuit-subject parent so diagnostic surfaces (logs, dumps,
  /// observatories) can identify this pipe by its caller-supplied name.
  @SuppressWarnings ( "unchecked" )
  FsPipe ( Consumer < Object > receiver, FsCircuit circuit, Name name, FsSubject < ? > parent ) {
    this.receiver = receiver;
    this.circuit = circuit;
    this.subject = (Subject < Pipe < E > >) (Subject < ? >) new FsSubject <> ( name, parent, Pipe.class );
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

  /// Routes the emission per SPEC §5.3 dual-queue model:
  /// - External threads → ingress queue (shared, MPSC)
  /// - Circuit/worker thread (cascade re-entry from within processing) → transit
  ///   queue (single-thread, takes priority over ingress)
  ///
  /// Cascade priority is the spec's mechanism for ensuring effects of an emission
  /// resolve before the next external input is processed.
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

}
