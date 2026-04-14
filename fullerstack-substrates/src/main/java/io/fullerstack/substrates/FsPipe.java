package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Routing;
import io.humainary.substrates.api.Substrates.Subject;

import jdk.internal.vm.annotation.Stable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/// Unified pipe implementation — the single pipe class for all roles.
///
/// The pipe's role depends on how it was created:
/// - **Conduit pipe** (`conduit.get(name)`): `emit()` enqueues to ingress.
///   `accept()` does version check + dispatches to registered receptors.
/// - **Flow pipe** (`pipe.pipe(flow)`): `emit()` enqueues to ingress.
///   `accept()` runs the flow chain synchronously on the circuit thread.
/// - **Receptor pipe** (`circuit.pipe(receptor)`): `emit()` enqueues to ingress.
///   `accept()` calls the receptor directly.
///
/// The transit queue stores the pipe itself as the `Consumer<Object>`.
/// This makes the drain loop monomorphic — always `FsPipe.accept()`.
@Provided
public final class FsPipe < E > implements Pipe < E >, Consumer < Object > {

  // ─── Identity (cold, @Stable) ───
  @Stable private final Name          name;
  @Stable private final FsCircuit     circuit;
  @Stable private final FsSubject < ? > parentSubject;

  // ─── Flow chain (for flow-composed pipes, null otherwise) ───
  @Stable private final Consumer < Object > flowChain;

  // ─── Named pipe dispatch (hot path, circuit-thread only) ───
  // Only used when this pipe is a conduit's named pipe (conduit != null).
  @Stable FsConduit < E > conduit;

  Receptor < ? super E > dispatch;
  int                     builtVersion = -1;
  boolean                 stem;

  // Subscriber bookkeeping — lazy, circuit-thread only
  Map < FsSubscriber < E >, List < Receptor < ? super E > > > subscriberReceptors;

  // ─── Subject (lazy) ───
  private volatile Subject < Pipe < E > > subject;

  /// Named pipe constructor — for conduit pipes.
  FsPipe ( Name name, FsCircuit circuit, FsSubject < ? > parentSubject ) {
    this.name = name;
    this.circuit = circuit;
    this.parentSubject = parentSubject;
    this.flowChain = null;
  }

  /// Receptor pipe constructor — for circuit.pipe(receptor).
  FsPipe ( Name name, FsCircuit circuit, FsSubject < ? > parentSubject, Receptor < ? super E > receptor ) {
    this.name = name;
    this.circuit = circuit;
    this.parentSubject = parentSubject;
    this.flowChain = null;
    this.dispatch = receptor;
  }

  /// Flow pipe constructor — for pipe.pipe(flow).
  @SuppressWarnings ( "unchecked" )
  private FsPipe ( Name name, FsCircuit circuit, FsSubject < ? > parentSubject, Consumer < Object > flowChain ) {
    this.name = name;
    this.circuit = circuit;
    this.parentSubject = parentSubject;
    this.flowChain = flowChain;
  }

  // ─── Identity ───

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

  final Name name () { return name; }

  final FsCircuit circuit () { return circuit; }

  // ─── Emit (entry path — called from any thread) ───

  @Override
  @jdk.internal.vm.annotation.ForceInline
  public void emit ( @NotNull E emission ) {
    requireNonNull ( emission, "emission must not be null" );
    if ( circuit.closed ) return;
    if ( Thread.currentThread () == circuit.worker () ) {
      circuit.submitTransit ( this, emission );
    } else {
      circuit.submitIngress ( this, emission );
    }
  }

  // ─── Accept (hot path — called by queue drain loop) ───

  /// Dispatches the emission. Called by the circuit thread when
  /// dequeuing from ingress or transit. Monomorphic call site —
  /// always FsPipe.accept().
  @Override
  @SuppressWarnings ( "unchecked" )
  public void accept ( Object o ) {
    // Flow-composed pipe: run the flow chain synchronously
    if ( flowChain != null ) {
      flowChain.accept ( o );
      return;
    }
    // Named conduit pipe: version check + dispatch
    if ( conduit != null && builtVersion != conduit.subscriberVersion ) {
      conduit.rebuildPipe ( this );
    }
    Receptor < ? super E > d = dispatch;
    if ( d != null ) {
      d.receive ( (E) o );
    }
    // STEM routing: propagate upward through name hierarchy
    if ( stem ) {
      dispatchStem ( (E) o );
    }
  }

  /// STEM dispatch — propagate to ancestor named pipes.
  private void dispatchStem ( E emission ) {
    Name n = name;
    while ( n.enclosure ().isPresent () ) {
      n = n.enclosure ().get ();
      FsPipe < E > ancestor = conduit.namedPipe ( n );
      if ( ancestor != null ) {
        // Dispatch to ancestor's receptors only (no further STEM propagation)
        if ( ancestor.conduit != null && ancestor.builtVersion != conduit.subscriberVersion ) {
          conduit.rebuildPipe ( ancestor );
        }
        Receptor < ? super E > d = ancestor.dispatch;
        if ( d != null ) d.receive ( emission );
      }
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
    // Flow terminal: for named conduit pipes, submit directly to transit
    // with the named pipe as the receiver (cascade safety, no checks).
    // For non-conduit pipes, fall back to emit() which routes through ingress.
    FsPipe < E > target = this;
    FsCircuit c = circuit;
    Consumer < I > chain;
    if ( conduit != null ) {
      chain = fsFlow.materialise ( v -> c.submitTransit ( target, v ) );
    } else {
      chain = fsFlow.materialise ( v -> target.emit ( v ) );
    }
    return new FsPipe <> ( name, circuit, parentSubject, (Consumer < Object >) (Consumer < ? >) chain );
  }

  // ─── Subscriber dispatch rebuild ───

  @SuppressWarnings ( "unchecked" )
  void rebuildDispatch () {
    if ( subscriberReceptors == null || subscriberReceptors.isEmpty () ) {
      dispatch = null;
      return;
    }
    List < Receptor < ? super E > > all = new ArrayList <> ();
    for ( List < Receptor < ? super E > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }
    if ( all.isEmpty () ) {
      dispatch = null;
    } else if ( all.size () == 1 ) {
      dispatch = all.getFirst ();
    } else {
      Receptor < ? super E >[] arr = all.toArray ( new Receptor[0] );
      dispatch = v -> {
        for ( int i = 0, len = arr.length; i < len; i++ ) arr[i].receive ( v );
      };
    }
  }
}
