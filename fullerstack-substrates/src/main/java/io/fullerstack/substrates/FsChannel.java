package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Routing;
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
final class FsChannel < E > implements Receptor < E >, Consumer < Object > {

  /// The subject identity for this channel (now typed as Pipe subject).
  private final Subject < Pipe < E > > subject;

  /// The circuit that owns this channel.
  private final FsCircuit circuit;

  /// The emission consumer for routing emissions through the pipeline.
  /// Non-final: set after construction to break circular dependency.
  Consumer < E > router;

  /// The owning conduit (null for standalone channels).
  private final FsConduit < E > conduit;

  /// Cached routing flag — avoids conduit null check + routing() call on every emission.
  private final boolean stem;

  // ─────────────────────────────────────────────────────────────────────────────
  // Subscriber state — circuit-thread only (no synchronization needed)
  // ─────────────────────────────────────────────────────────────────────────────

  /// Receptors per subscriber — for proper unsubscribe handling.
  final Map < FsSubscriber < E >, List < Receptor < ? super E > > > subscriberReceptors = new IdentityHashMap <> ();

  /// Cached flat array for fast iteration (rebuilt when subscribers change).
  Receptor < ? super E >[] receptors;

  /// Version this channel was last built at — starts at -1 to force first rebuild.
  int builtVersion = -1;

  FsChannel ( Subject < Pipe < E > > subject, FsCircuit circuit, FsConduit < E > conduit, Consumer < E > router ) {
    this.subject = subject;
    this.circuit = circuit;
    this.conduit = conduit;
    this.router  = router;
    this.stem    = conduit != null && conduit.routing () == Routing.STEM;
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

  /// Pre-computed fast dispatch — single receptor for the common case,
  /// avoids array read + loop on every emission. Updated by rebuildReceptorsArray().
  Receptor < ? super E > fastDispatch;

  @Override
  @SuppressWarnings ( "unchecked" )
  public void accept ( Object o ) {
    receive ( (E) o );
  }

  @Override
  public void receive ( E emission ) {
    if ( conduit != null && builtVersion != conduit.subscriberVersion ) {
      conduit.rebuildChannelPipes ( this );
    }
    Receptor < ? super E > fast = fastDispatch;
    if ( fast != null ) {
      fast.receive ( emission );
    }
    // STEM routing: propagate emission upward through name hierarchy
    if ( stem ) {
      Name name = subject.name ();
      while ( name.enclosure ().isPresent () ) {
        name = name.enclosure ().get ();
        FsChannel < E > ancestor = conduit.channel ( name );
        if ( ancestor != null ) {
          ancestor.receiveLocal ( emission );
        }
      }
    }
  }

  /// Dispatch to this channel's receptors only (no STEM propagation).
  /// Used by STEM routing to avoid infinite recursion on ancestor channels.
  void receiveLocal ( E emission ) {
    if ( conduit != null && builtVersion != conduit.subscriberVersion ) {
      conduit.rebuildChannelPipes ( this );
    }
    Receptor < ? super E > fast = fastDispatch;
    if ( fast != null ) {
      fast.receive ( emission );
    }
  }

  @SuppressWarnings ( "unchecked" )
  void rebuildReceptorsArray () {
    List < Receptor < ? super E > > all = new ArrayList <> ();
    for ( List < Receptor < ? super E > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }
    receptors = all.isEmpty () ? null : all.toArray ( new Receptor[0] );
    // Pre-compute fast dispatch: single receptor avoids array access,
    // multi-receptor builds a composed receptor.
    if ( all.isEmpty () ) {
      fastDispatch = null;
    } else if ( all.size () == 1 ) {
      // Unwrap ReceptorAdapter to eliminate one virtual dispatch
      @SuppressWarnings ( "unchecked" )
      Receptor < ? super E > r = all.getFirst ();
      if ( r instanceof FsCircuit.ReceptorAdapter < ? > a ) {
        fastDispatch = (Receptor < ? super E >) a.receptor;
      } else {
        fastDispatch = r;
      }
    } else {
      Receptor < ? super E >[] arr = receptors;
      fastDispatch = v -> {
        for ( int i = 0, len = arr.length; i < len; i++ ) arr[i].receive ( v );
      };
    }
  }

}
