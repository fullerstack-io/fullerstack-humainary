package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// Internal channel — manages per-name subscriber receptors and emission dispatch.
///
/// In Substrates 2.0 there is no public Channel interface. Channels are an internal
/// implementation detail of conduits. Each channel represents a named emission point
/// within a conduit and holds:
/// - A subject (now Subject<Pipe<E>> instead of Subject<Channel<E>>)
/// - A router (emission consumer)
/// - Per-subscriber receptor mappings for lazy subscriber activation
///
/// @param <E> the emission type
final class FsChannel < E > implements Receptor < E > {

  /// The subject identity for this channel (now typed as Pipe subject).
  private final Subject < Pipe < E > > subject;

  /// The circuit that owns this channel.
  private final FsCircuit circuit;

  /// The emission consumer for routing emissions through the pipeline.
  /// Non-final: set after construction to break circular dependency.
  Consumer < E > router;

  /// The owning conduit (null for standalone channels).
  private final FsConduit < E > conduit;

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber state — circuit-thread only (no synchronization needed)
  // ─────────────────────────────────────────────────────────────────────────────

  /// Receptors per subscriber — for proper unsubscribe handling.
  final Map < FsSubscriber < E >, List < Receptor < ? super E > > > subscriberReceptors = new IdentityHashMap <> ();

  /// Cached flat array for fast iteration (rebuilt when subscribers change).
  Receptor < ? super E >[] receptors;

  /// Dirty flag — start dirty to force first rebuild.
  boolean dirty = true;

  /// Version tracking for FsTap.
  int builtVersion = -1;

  FsChannel ( Subject < Pipe < E > > subject, FsCircuit circuit, FsConduit < E > conduit, Consumer < E > router ) {
    this.subject = subject;
    this.circuit = circuit;
    this.conduit = conduit;
    this.router  = router;
  }

  FsChannel ( Subject < Pipe < E > > subject, FsCircuit circuit, Consumer < E > router ) {
    this ( subject, circuit, null, router );
  }

  Subject < Pipe < E > > subject () {
    return subject;
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Receptor — emission dispatch on circuit thread
  // ─────────────────────────────────────────────────────────────────────────────

  @Override
  public void receive ( E emission ) {
    if ( dirty || ( conduit != null && conduit.subscribersDirty ) ) {
      if ( conduit != null ) {
        conduit.rebuildChannelPipes ( this );
      }
      dirty = false;
    }
    Receptor < ? super E >[] r = receptors;
    if ( r == null ) return;
    for ( int i = 0, len = r.length; i < len; i++ ) {
      r[i].receive ( emission );
    }
  }

  @SuppressWarnings ( "unchecked" )
  void rebuildReceptorsArray () {
    List < Receptor < ? super E > > all = new ArrayList <> ();
    for ( List < Receptor < ? super E > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }
    receptors = all.isEmpty () ? null : all.toArray ( new Receptor[0] );
  }

}
