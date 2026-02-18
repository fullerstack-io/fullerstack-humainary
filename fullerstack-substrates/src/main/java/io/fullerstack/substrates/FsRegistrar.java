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
  public void register ( Pipe < ? super E > pipe ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // Always go through pipe.emit() to preserve the transit-queue async
    // boundary. This ensures cyclic patterns (subscriber re-emits to same
    // conduit) use queue-based iteration rather than stack recursion.
    // pipe.emit() routes correctly: submitTransit for same circuit thread,
    // submitIngress for cross-circuit threads.
    receptors.add ( pipe::emit );
  }
}
