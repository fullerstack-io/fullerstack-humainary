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
/// The channel has two roles:
/// - **Ingress receiver**: implements Consumer<Object>. Stored in the
///   ingress queue. On dequeue, checks version and dispatches.
/// - **Dispatch builder**: after rebuild, sets the dispatch Consumer
///   which is used directly on the transit hot path. The dispatch
///   already includes STEM propagation if applicable, so transit
///   cascades can call dispatch directly without going through the
///   channel — bypassing the version check (which the spec guarantees
///   is stable mid-cascade per §5.4.1 + §7.6.2).
///
/// The dispatch is Consumer<Object> throughout — same type as the
/// transit queue, the flow chain, and the registrar's stored receivers.
/// No lambda wrappers on the hot path.
final class FsChannel < E > implements Receptor < E >, Consumer < Object > {

  private final Subject < Pipe < E > > subject;
  private final FsCircuit              circuit;
  private final FsHub < E >            hub;
  private final boolean                stem;
  private final FsConduit < E >        conduit;

  /// The upstream pipe — what conduit.get(name) returns.
  final FsPipe < E > pipe;

  /// Downstream dispatch — receptors only, no STEM. Used by ingress receive()
  /// (which adds version check + STEM externally) and by dispatchStem when
  /// walking ancestors (must NOT trigger ancestor's STEM walk too).
  Consumer < Object > dispatch;

  /// Transit-side cascade dispatch — receptors + STEM (if applicable).
  /// Submitted directly to transit by fiber/flow terminals to bypass the
  /// version check on the cascade hot path. For non-STEM channels this is
  /// the same reference as `dispatch`. For STEM channels it's a wrapper
  /// that fires receptors then walks STEM.
  Consumer < Object > cascadeDispatch;

  /// Version this channel was last built at.
  int builtVersion = -1;

  /// Per-subscriber receptor registrations — lazy, circuit-thread only.
  Map < FsSubscriber < E >, List < Consumer < Object > > > subscriberReceptors;

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

  // ─── Ingress receiver ───

  @Override
  @SuppressWarnings ( "unchecked" )
  public void accept ( Object o ) {
    receive ( (E) o );
  }

  // ─── Dispatch (ingress path — version check) ───
  //
  // Called only from the ingress drain. The version check fires here because
  // an ingress emission may follow a subscriber change (which is itself
  // queued in ingress order). Per §5.4.1 relation 3 + §7.6.2, no subscriber
  // change can interleave during a cascade, so transit-side dispatch goes
  // straight to `dispatch` and skips this method entirely.

  @Override
  @jdk.internal.vm.annotation.ForceInline
  public void receive ( E emission ) {
    if ( builtVersion != hub.subscriberVersion ) rebuild ();
    Consumer < Object > d = dispatch;
    if ( d != null ) d.accept ( emission );
    if ( stem ) dispatchStem ( emission );
  }

  /// STEM — propagate to ancestor channels' dispatch (no version checks here;
  /// rebuild is driven from receive() before any transit can run).
  private void dispatchStem ( E emission ) {
    Name n = subject.name ();
    while ( n.enclosure ().isPresent () ) {
      n = n.enclosure ().get ();
      FsChannel < E > ancestor = conduit.channel ( n );
      if ( ancestor != null ) {
        if ( ancestor.builtVersion != hub.subscriberVersion ) ancestor.rebuild ();
        Consumer < Object > d = ancestor.dispatch;
        if ( d != null ) d.accept ( emission );
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
        subscriberReceptors.put ( subscriber, registrar.consumers () );
      }
    }

    // Build receptor-only dispatch — used by ingress receive() and by
    // dispatchStem when walking ancestors.
    List < Consumer < Object > > all = new ArrayList <> ();
    for ( List < Consumer < Object > > list : subscriberReceptors.values () ) {
      all.addAll ( list );
    }

    if ( all.isEmpty () ) {
      dispatch = null;
    } else if ( all.size () == 1 ) {
      dispatch = all.getFirst ();
    } else {
      Consumer < Object >[] arr = all.toArray ( new Consumer[0] );
      dispatch = v -> {
        for ( int i = 0, len = arr.length; i < len; i++ ) arr[i].accept ( v );
      };
    }

    // Build cascadeDispatch — what transit cascade terminals submit.
    // Same as dispatch for non-STEM channels; wraps in STEM walk for STEM channels.
    if ( stem ) {
      final Consumer < Object > base = dispatch;
      cascadeDispatch = v -> {
        if ( base != null ) base.accept ( v );
        @SuppressWarnings ( "unchecked" )
        E e = (E) v;
        dispatchStem ( e );
      };
    } else {
      cascadeDispatch = dispatch;
    }

    builtVersion = hub.subscriberVersion;
    circuit.recordRebuild ();
  }
}
