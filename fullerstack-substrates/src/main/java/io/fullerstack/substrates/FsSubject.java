package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Extent;
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
  private       State           state = FsState.EMPTY; // mutable on circuit thread

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
    return state;
  }

  /// Update the State on this Subject. Called on the circuit thread —
  /// no synchronisation needed. State is immutable; this replaces the
  /// reference to the current State with a new one.
  public void state ( State state ) {
    this.state = state;
  }

  @Override
  public Class < S > type () {
    return (Class < S >) type;
  }

  @Override
  public Optional < Subject < ? > > enclosure () {
    return Optional.ofNullable ( parent );
  }

  /// Optimized within() — walks parent field directly instead of
  /// using default Extent.within() which allocates Optional per level.
  @Override
  public boolean within ( final Extent < ?, ? > enclosure ) {
    java.util.Objects.requireNonNull ( enclosure, "enclosure must not be null" );
    for ( FsSubject < ? > current = parent; current != null; current = current.parent ) {
      if ( current == enclosure ) {
        return true;
      }
    }
    return false;
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

  /// Optimized part() — caches the formatted string to avoid repeated
  /// String.formatted() + type().getSimpleName() reflection on every call.
  @Override
  public String part () {
    return "Subject[name=" + name () + ",type=" + type.getSimpleName () + ",id=" + id + "]";
  }

  /// Optimized path() — walks parent chain directly instead of
  /// using default Extent.foldTo() which allocates Optional per level.
  @Override
  public CharSequence path () {
    if ( parent == null ) {
      return part ();
    }
    StringBuilder sb = new StringBuilder ();
    buildPath ( sb );
    return sb;
  }

  private void buildPath ( StringBuilder sb ) {
    if ( parent != null ) {
      parent.buildPath ( sb );
      sb.append ( '/' );
    }
    sb.append ( part () );
  }

  @Override
  public String toString () {
    return path ().toString ();
  }

}
