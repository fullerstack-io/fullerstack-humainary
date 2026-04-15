package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Routing;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// Channel — the per-name dispatch point on the routing path.
///
/// Cortex → Circuit → Conduit → **Channel** → Pipe → Receptor
///
/// Implements Receptor<E> and Consumer<Object> — it IS what the
/// transit/ingress queue stores. On dequeue, the circuit calls
/// channel.accept(v) which routes to receive(v).
///
/// receive(v) checks the hub's subscriber version. If stale, rebuilds
/// the downstream receptor list by invoking subscriber callbacks.
/// After rebuild (or if version matches), dispatches to receptors.
///
/// The channel creates and owns its pipe — the upstream entry point
/// that users hold. The pipe enqueues [channel, v] to the circuit.
final class FsChannel < E > implements Receptor < E >, Consumer < Object > {

  private final Subject < Pipe < E > > subject;
  private final FsCircuit              circuit;
  private final FsHub < E >            hub;
  private final boolean                stem;
  private final FsConduit < E >        conduit;

  /// The upstream pipe — what conduit.get(name) returns.
  final FsPipe < E > pipe;

  /// Downstream dispatch — a single Receptor that handles all cases.
  /// For one subscriber: the receptor directly.
  /// For multiple: a composed receptor that loops.
  /// Null before first rebuild.
  Receptor < ? super E > dispatch;

  /// Transit-compatible dispatch — same as dispatch but typed as Consumer<Object>.
  /// Used by flow terminals for cascade re-entry: submitTransit(transitDispatch, v).
  /// Bypasses the channel entirely on the transit hot path — no version check.
  Consumer < Object > transitDispatch;

  /// Version this channel was last built at.
  int builtVersion = -1;

  /// Per-subscriber receptor registrations — lazy, circuit-thread only.
  Map < FsSubscriber < E >, List < Receptor < ? super E > > > subscriberReceptors;

  FsChannel (
    Subject < Pipe < E > > subject,
    FsCircuit circuit,
    FsHub < E > hub,
    FsConduit < E > conduit,
    Routing routing
  ) {
    this.subject = subject;
    this.circuit = circuit;
    this.hub = hub;
    this.conduit = conduit;
    this.stem = routing == Routing.STEM;
    this.pipe = new FsPipe <> ( this, circuit );
  }

  Subject < Pipe < E > > subject () {
    return subject;
  }

  // ─── Queue receiver ───

  @Override
  @SuppressWarnings ( "unchecked" )
  public void accept ( Object o ) {
    receive ( (E) o );
  }

  // ─── Dispatch (hot path) ───

  @Override
  @jdk.internal.vm.annotation.ForceInline
  public void receive ( E emission ) {
    if ( builtVersion != hub.subscriberVersion ) rebuild ();
    Receptor < ? super E > d = dispatch;
    if ( d != null ) d.receive ( emission );
    if ( stem ) dispatchStem ( emission );
  }

  /// STEM — propagate to ancestor channels.
  private void dispatchStem ( E emission ) {
    Name n = subject.name ();
    while ( n.enclosure ().isPresent () ) {
      n = n.enclosure ().get ();
      FsChannel < E > ancestor = conduit.channel ( n );
      if ( ancestor != null ) {
        if ( ancestor.builtVersion != hub.subscriberVersion ) ancestor.rebuild ();
        Receptor < ? super E > d = ancestor.dispatch;
        if ( d != null ) d.receive ( emission );
      }
    }
  }

  // ─── Rebuild (cold path) ───

  @SuppressWarnings ( "unchecked" )
  private void rebuild () {
    FsSubscriber < E >[] currentSubs = hub.ensureSnapshot ();

    if ( subscriberReceptors == null ) {
      subscriberReceptors = new IdentityHashMap <> ();
    }

    Set < FsSubscriber < E > > activeSet = Collections.newSetFromMap ( new IdentityHashMap <> () );
    for ( FsSubscriber < E > sub : currentSubs ) {
      activeSet.add ( sub );
    }

    subscriberReceptors.keySet ().removeIf ( sub -> !activeSet.contains ( sub ) );

    for ( FsSubscriber < E > subscriber : currentSubs ) {
      if ( !subscriberReceptors.containsKey ( subscriber ) ) {
        FsRegistrar < E > registrar = new FsRegistrar <> ();
        subscriber.activate ( subject, registrar );
        subscriberReceptors.put ( subscriber, registrar.receptors () );
      }
    }

    // Build dispatch receptor
    List < Receptor < ? super E > > all = new ArrayList <> ();
    for ( List < Receptor < ? super E > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }

    if ( all.isEmpty () ) {
      dispatch = null;
    } else if ( all.size () == 1 ) {
      dispatch = all.getFirst ();
    } else {
      @SuppressWarnings ( "unchecked" )
      Receptor < ? super E >[] arr = all.toArray ( new Receptor[0] );
      dispatch = v -> {
        for ( int i = 0, len = arr.length; i < len; i++ ) arr[i].receive ( v );
      };
    }

    // Build transit dispatch — wraps dispatch as Consumer<Object> for transit queue.
    // This is what cascade re-entry submits. Bypasses channel entirely.
    Receptor < ? super E > d = dispatch;
    if ( d != null ) {
      transitDispatch = o -> d.receive ( (E) o );
    } else {
      transitDispatch = null;
    }

    builtVersion = hub.subscriberVersion;
  }
}
