package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Registrar — collects consumers during subscriber activation.
///
/// Everything is Consumer<Object> on the hot path — no lambda wrappers
/// between the channel dispatch and the flow chain.
///
/// Enforces the @Temporal contract: register() only valid during callback.
@Provided
public final class FsRegistrar < E > implements Registrar < E > {

  private final List < Consumer < Object > > consumers = new ArrayList <> ();
  private boolean closed;

  @Override
  @SuppressWarnings ( "unchecked" )
  public void register ( Receptor < ? super E > receptor ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // Wrap Receptor in Consumer<Object> — this is the cold path (subscriber callback)
    consumers.add ( v -> receptor.receive ( (E) v ) );
  }

  @Override
  public void register ( Pipe < ? super E > pipe ) {
    if ( closed ) throw new IllegalStateException ( "Registrar is closed — register() only valid during callback" );
    // For FsPipe: store the receiver directly — it's already Consumer<Object>.
    // No lambda wrapper on the hot path.
    // For unknown pipes: wrap pipe::emit.
    if ( pipe instanceof FsPipe < ? > fp ) {
      consumers.add ( fp.receiver () );
    } else {
      consumers.add ( v -> pipe.emit ( (E) v ) );
    }
  }

  /// Returns the registered consumers and closes this registrar.
  public List < Consumer < Object > > consumers () {
    closed = true;
    return consumers;
  }

  /// Legacy accessor — returns consumers as receptors for compatibility.
  @SuppressWarnings ( "unchecked" )
  public List < Receptor < ? super E > > receptors () {
    closed = true;
    List < Receptor < ? super E > > result = new ArrayList <> ();
    for ( Consumer < Object > c : consumers ) {
      result.add ( v -> c.accept ( v ) );
    }
    return result;
  }
}
