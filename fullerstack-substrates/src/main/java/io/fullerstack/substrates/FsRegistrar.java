package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;

import java.util.ArrayList;
import java.util.List;

/// A registrar that collects receptors during subscriber activation.
///
/// Registered pipes always go through pipe.emit() to preserve the
/// transit-queue async boundary. This prevents stack overflow in
/// cyclic emission patterns (subscriber re-emits to same conduit)
/// by ensuring each re-emission is enqueued rather than inlined.
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
    // For same-circuit FsPipes, register the receiver directly.
    // The receiver is a ReceptorAdapter wrapping the flow chain (or channel).
    // When the channel dispatches, it calls receiver.accept(v) which runs
    // the flow chain and enqueues the output to transit — one queue hop total.
    // This is safe because the channel dispatch already runs on the circuit
    // thread, and the receiver's accept() will submitTransit for cascades.
    // Cross-circuit pipes still go through pipe.emit() for correct routing.
    if ( pipe instanceof FsPipe < ? > fsPipe && fsPipe.isOnCircuitThread () ) {
      @SuppressWarnings ( "unchecked" )
      Receptor < ? super E > r = (Receptor < ? super E >) (Receptor < ? >) fsPipe.receiver ();
      receptors.add ( r );
    } else {
      receptors.add ( pipe::emit );
    }
  }
}
