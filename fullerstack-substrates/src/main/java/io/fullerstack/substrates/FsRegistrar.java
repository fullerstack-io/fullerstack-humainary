package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;

import java.util.ArrayList;
import java.util.List;

/// A registrar that collects receptors during subscriber activation.
///
/// Registered pipes are stored via pipe::emit. For outlet pipes (created by
/// pipe.pipe(flow)), emit() dispatches synchronously on the circuit thread.
/// For inlet pipes (conduit pipes), emit() enqueues to the circuit queue.
/// Cyclic safety is provided by the flow terminal calling the inlet's emit,
/// not by the registrar.
///
/// The registrar enforces the @Temporal contract: register() may only
/// be called during the subscriber callback. After the callback completes,
/// the registrar is closed and subsequent register() calls throw
/// IllegalStateException.
///
/// @param <E> the emission type
@Provided
public final class FsRegistrar < E > implements Registrar < E > {

  /// Receptors registered by the subscriber.
  private final List < Receptor < ? super E > > receptors = new ArrayList <> ();

  /// Temporal enforcement flag — set after callback completes.
  private boolean closed;

  @Override
  public void register ( Receptor < ? super E > receptor ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    receptors.add ( receptor );
  }

  /// Returns the registered receptors and closes this registrar.
  public List < Receptor < ? super E > > receptors () {
    closed = true;
    return receptors;
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public void register ( Pipe < ? super E > pipe ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // Outlet pipes: unwrap to the inner receptor (flow chain).
    // Inlet pipes: pipe::emit enqueues to circuit (cyclic boundary).
    if ( pipe instanceof FsOutletPipe < ? > outlet
      && outlet.receiver () instanceof FsCircuit.ReceptorAdapter < ? > adapter ) {
      receptors.add ( (Receptor < ? super E >) adapter );
    } else {
      receptors.add ( pipe::emit );
    }
  }
}
