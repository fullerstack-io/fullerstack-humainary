package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscription;

/// **FsCell** — circuit-owned state cell with safe publication.
///
/// Implements `Substrates.Cell<E>` (new in 2.7). A cell holds the latest
/// value accepted through its update pipe. Updates are processed in the
/// owning circuit's worker context; reads observe the latest published
/// value via a volatile field.
///
/// ## Construction
///
/// Internally a Cell is wired as `Conduit<E> → subscriber → volatile slot`.
/// The conduit's "self" pipe (`conduit.get(name)`) becomes the cell's
/// public update pipe; the subscriber receives every emission on the
/// circuit thread and stores it in the volatile slot.
///
/// ## Safe publication
///
/// The `value` field is `volatile` — after an update has been processed
/// by the owning circuit's worker, subsequent reads ordered after that
/// processing observe the published reference. If `E` is mutable, callers
/// must treat published values as immutable snapshots.
@SuppressWarnings ( "unchecked" )
public final class FsCell < E > implements Cell < E > {

  private final Subject < Cell < E > > subject;
  private final FsConduit < E >        conduit;
  private final Pipe < E >             pipe;
  private final Subscription           subscription;

  private volatile E value;

  public FsCell ( FsSubject < ? > parent, Name name, FsCircuit circuit, E initial ) {
    this.subject = (Subject < Cell < E > >) (Subject < ? >) new FsSubject <> ( name, parent, Cell.class );
    this.value   = initial;
    this.conduit = new FsConduit <> ( parent, name, circuit );
    this.pipe    = conduit.get ( name );
    this.subscription = conduit.subscribe (
        circuit.subscriber (
            name.name ( "cell.subscriber" ),
            (subj, reg) -> reg.register ( (E v) -> {
              if ( v != null ) value = v;
            } )
        )
    );
  }

  @NotNull
  @Override
  public Subject < Cell < E > > subject () {
    return subject;
  }

  @Override
  public E get () {
    return value;
  }

  @NotNull
  @Override
  public Pipe < E > pipe () {
    return pipe;
  }

  /// Release the internal subscription. Called by the owning circuit on close.
  void closeInternal () {
    subscription.close ();
  }
}
