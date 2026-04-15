package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;

import java.util.ArrayList;
import java.util.List;

/// Registrar — collects receptors during subscriber activation.
///
/// Registered pipes execute on the circuit thread during dispatch.
/// pipe::emit is used as the receptor — the pipe's emit() routes
/// correctly (transit for circuit thread, ingress for external).
///
/// Enforces the @Temporal contract: register() only valid during callback.
@Provided
public final class FsRegistrar < E > implements Registrar < E > {

  private final List < Receptor < ? super E > > receptors = new ArrayList <> ();
  private boolean closed;

  @Override
  public void register ( Receptor < ? super E > receptor ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    receptors.add ( receptor );
  }

  @Override
  public void register ( Pipe < ? super E > pipe ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // Registered downstream pipes execute synchronously during dispatch (§6.3).
    // For FsPipe: call the receiver directly (no queue hop).
    // For unknown pipes: fall back to pipe::emit.
    if ( pipe instanceof FsPipe < ? > fp ) {
      var receiver = fp.receiver ();
      receptors.add ( v -> receiver.accept ( v ) );
    } else {
      receptors.add ( pipe::emit );
    }
  }

  public List < Receptor < ? super E > > receptors () {
    closed = true;
    return receptors;
  }
}
