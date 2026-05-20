package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;

/// **FsCell** — circuit-owned state cell with safe publication.
///
/// Implements `Substrates.Cell<E>` (new in 2.7). A cell holds the latest
/// value accepted through its update pipe; the volatile slot guarantees
/// safe publication after the owning circuit processes each emission.
///
/// ## Implementation
///
/// The cell's pipe is a direct **receptor pipe** (`circuit.pipe(Receptor)`)
/// whose receptor simply writes to the volatile slot. No conduit / channel /
/// subscriber machinery is needed — Cell's contract is "the latest emit
/// becomes the value." Any thread can `cell.pipe().emit(v)` and any thread
/// can `cell.get()`; the receptor runs on the circuit thread so the slot
/// is always updated under deterministic ordering.
@SuppressWarnings ( "unchecked" )
public final class FsCell < E > implements Cell < E > {

  private final Subject < Cell < E > > subject;
  private final Pipe < E >             pipe;

  private volatile E value;

  public FsCell ( FsSubject < ? > parent, Name name, FsCircuit circuit, E initial ) {
    this.subject = (Subject < Cell < E > >) (Subject < ? >) new FsSubject <> ( name, parent, Cell.class );
    this.value   = initial;
    this.pipe    = circuit.pipe ( (E emission) -> {
      if ( emission != null ) value = emission;
    } );
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
}
