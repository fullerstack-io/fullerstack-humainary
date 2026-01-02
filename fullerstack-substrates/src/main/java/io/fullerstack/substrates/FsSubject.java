package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Id;
import io.humainary.substrates.api.Substrates.Identity;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Substrate;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/// The identity of a substrate.
/// Supports null name for anonymous subjects - delegates to parent's name.
@Identity
@Provided
@SuppressWarnings ( {"unchecked"} )
public final class FsSubject < S extends Substrate < S > > implements Subject < S > {

  /// Counter for unique subject IDs - cheaper than UUID.randomUUID() (~5ns vs
  /// ~300ns).
  private static final AtomicLong ID_COUNTER = new AtomicLong ();

  /// Simple Id wrapper with non-recursive toString().
  private record FsId( long value ) implements Id {
    @Override
    public String toString () {
      return String.valueOf ( value );
    }
  }

  private final Name            name; // null for anonymous subjects
  private final FsSubject < ? > parent;
  private final Class < ? >     type;
  private final FsId            id; // unique id

  /// Creates a root subject with the given name and type.
  public FsSubject ( Name name, Class < ? > type ) {
    this.name = name;
    this.parent = null;
    this.type = type;
    this.id = new FsId ( ID_COUNTER.getAndIncrement () );
  }

  /// Creates a child subject with the given name, parent, and type.
  /// If name is null, this subject delegates name() to parent.
  public FsSubject ( Name name, FsSubject < ? > parent, Class < ? > type ) {
    this.name = name;
    this.parent = parent;
    this.type = type;
    this.id = new FsId ( ID_COUNTER.getAndIncrement () );
  }

  @Override
  public Id id () {
    return id;
  }

  @Override
  public Name name () {
    // Anonymous subjects delegate to parent's name
    return name != null ? name : parent.name ();
  }

  @Override
  public State state () {
    return FsState.EMPTY;
  }

  @Override
  public Class < S > type () {
    return (Class < S >) type;
  }

  @Override
  public Optional < Subject < ? > > enclosure () {
    return Optional.ofNullable ( parent );
  }

  @Override
  public int compareTo ( Subject < ? > other ) {
    if ( this == other ) return 0;
    if ( other instanceof FsSubject < ? > fs ) {
      return Long.compare ( this.id.value (), fs.id.value () );
    }
    return Subject.super.compareTo ( other );
  }

  /// Finds the circuit ancestor in the subject hierarchy.
  /// Returns null if no Circuit type ancestor is found.
  public FsSubject < ? > findCircuitAncestor () {
    FsSubject < ? > current = this;
    while ( current != null ) {
      if ( current.type == Circuit.class ) {
        return current;
      }
      current = current.parent;
    }
    return null;
  }

  @Override
  public String toString () {
    return path ().toString ();
  }

}
