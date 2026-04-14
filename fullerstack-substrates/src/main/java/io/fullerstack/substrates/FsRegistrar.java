package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;

import java.util.ArrayList;
import java.util.List;

/// Registrar — collects receptors during subscriber activation.
///
/// Registered pipes execute synchronously during dispatch via accept().
/// For flow-composed pipes, accept() runs the flow chain on the circuit
/// thread. For named pipes, accept() does version check + dispatch —
/// but the flow terminal handles cascade re-entry via submitTransit.
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
  @SuppressWarnings ( "unchecked" )
  public void register ( Pipe < ? super E > pipe ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // Registered downstream pipes execute synchronously during dispatch (§6.3).
    // pipe.accept() runs synchronously on the circuit thread:
    // - Flow pipes: runs flow chain, terminal -> submitTransit for cascade
    // - Receptor pipes: calls receptor directly
    // - Named pipes: version check + dispatch (cyclic re-entry via transit)
    if ( pipe instanceof FsPipe < ? > fp ) {
      receptors.add ( v -> fp.accept ( v ) );
    } else {
      receptors.add ( pipe::emit );
    }
  }

  public List < Receptor < ? super E > > receptors () {
    closed = true;
    return receptors;
  }
}
