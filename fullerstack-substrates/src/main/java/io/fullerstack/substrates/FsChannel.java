package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// A named port in a conduit that provides a pipe for emission.
///
/// Channels serve as named entry points into a conduit's processing pipeline.
/// Each channel has a unique Subject with an associated Name, and emissions
/// to the channel are routed through the conduit's Flow pipeline to registered
/// subscribers.
///
/// Channels are pooled by name within a conduit (`@Tenure INTERNED`).
/// The channel holds per-channel subscriber state and implements `Receptor`
/// for direct emission dispatch on the circuit thread.
///
/// @param <E> the class type of emitted value
/// @see FsConduit
@Provided
final class FsChannel < E > implements Channel < E >, Receptor < E > {

  /// The subject identity for this channel.
  private final Subject < Channel < E > > subject;

  /// The circuit that owns this channel.
  private final FsCircuit circuit;

  /// The emission consumer for routing emissions through the pipeline.
  /// Non-final: for conduit-managed channels, set after construction
  /// to break the circular dependency (channel wraps itself via ReceptorReceiver).
  Consumer < E > router;

  /// The owning conduit (null for standalone channels, e.g. FsCell).
  private final FsConduit < ?, E > conduit;

  /// Cached pipe subject - all pipes from this channel share the same identity.
  /// Lazy initialized on first pipe() call.
  private volatile Subject < Pipe < E > > cachedPipeSubject;

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber state — circuit-thread only (no synchronization needed)
  // ─────────────────────────────────────────────────────────────────────────────

  /// Receptors per subscriber — for proper unsubscribe handling.
  final Map < FsSubscriber < E >, List < Receptor < ? super E > > > subscriberReceptors = new IdentityHashMap <> ();

  /// Cached flat array for fast iteration (rebuilt when subscribers change).
  Receptor < ? super E >[] receptors;

  /// Dirty flag — start dirty to force first rebuild.
  boolean dirty = true;

  /// Version tracking for FsTap — tracks which subscriber version this channel
  /// was last built against. Uses -1 as sentinel to force initial rebuild.
  int builtVersion = -1;

  /// Creates a conduit-managed channel with subscriber support.
  ///
  /// @param subject the subject identity for this channel
  /// @param circuit the circuit that owns this channel
  /// @param conduit the owning conduit for subscriber rebuild
  /// @param router the consumer that routes emissions to subscribers
  FsChannel ( Subject < Channel < E > > subject, FsCircuit circuit, FsConduit < ?, E > conduit, Consumer < E > router ) {
    this.subject = subject;
    this.circuit = circuit;
    this.conduit = conduit;
    this.router = router;
  }

  /// Creates a standalone channel (no conduit subscriber management).
  /// Used by FsCell where channels have their own routing logic.
  ///
  /// @param subject the subject identity for this channel
  /// @param circuit the circuit that owns this channel
  /// @param router the consumer that routes emissions to subscribers
  FsChannel ( Subject < Channel < E > > subject, FsCircuit circuit, Consumer < E > router ) {
    this ( subject, circuit, null, router );
  }

  /// Returns the subject identity of this channel.
  @Override
  public Subject < Channel < E > > subject () {
    return subject;
  }

  /// Returns the cached pipe subject, creating it lazily if needed.
  /// All pipes from this channel share the same subject identity.
  private Subject < Pipe < E > > pipeSubject () {
    Subject < Pipe < E > > cached = cachedPipeSubject;
    if ( cached == null ) {
      cached = new FsSubject <> ( subject.name (), (FsSubject < ? >) subject, Pipe.class );
      cachedPipeSubject = cached;
    }
    return cached;
  }

  /// Returns a new pipe for emitting to this channel.
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Pipe < E > pipe () {
    return circuit.createPipe ( subject.name (), (FsSubject < ? >) subject, (Consumer < Object >) (Consumer < ? >) router );
  }

  /// Returns a new pipe with custom flow configuration.
  /// Configurer is invoked eagerly; exceptions are wrapped in Substrates.Exception.
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Pipe < E > pipe ( @NotNull Configurer < Flow < E > > configurer ) {
    try {
      Subject < Pipe < E > > ps = pipeSubject ();
      Pipe < E > basePipe = circuit.createPipe ( ps.name (), (FsSubject < ? >) subject, (Consumer < Object >) (Consumer < ? >) router );
      FsFlow < E > flow = new FsFlow <> ( ps, circuit, basePipe );
      configurer.configure ( flow );
      return flow.pipe ();
    } catch ( FsException e ) {
      throw e;
    } catch ( RuntimeException e ) {
      throw new FsException ( "Flow configuration failed", e );
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Receptor — emission dispatch on circuit thread
  // ─────────────────────────────────────────────────────────────────────────────

  /// Receives an emission on the circuit thread.
  /// Checks dirty flag and triggers lazy rebuild before dispatching
  /// to all registered subscriber receptors.
  @Override
  public void receive ( E emission ) {
    if ( dirty || conduit.subscribersDirty ) {
      conduit.rebuildChannelPipes ( this );
      dirty = false;
    }
    Receptor < ? super E >[] r = receptors;
    if ( r == null ) return;
    for ( int i = 0, len = r.length; i < len; i++ ) {
      r[i].receive ( emission );
    }
  }

  /// Rebuilds the flat receptors array from the subscriberReceptors map.
  @SuppressWarnings ( "unchecked" )
  void rebuildReceptorsArray () {
    List < Receptor < ? super E > > all = new ArrayList <> ();
    for ( List < Receptor < ? super E > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }
    receptors = all.isEmpty () ? null : all.toArray ( new Receptor[0] );
  }
}
