package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Window;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/// **FsWindow** — array-backed view over a stable emission set.
///
/// Implements `Substrates.Window<E>` (new in 2.6). A Window is callback-scoped:
/// instances are only valid during the callback that received them. Restriction
/// operations (`prefix`, `suffix`, `skip`, `trim`, `slice`, `reverse`) produce
/// new view instances over the **same underlying buffer** — no value copies.
///
/// ## Strided view representation
///
/// `(buffer, start, length, reversed)`:
/// - `buffer` — the underlying Object[] holding values
/// - `start`  — physical offset of the leftmost value in this view's bounds
/// - `length` — number of values visible through this view
/// - `reversed` — when true, encounter order traverses [start+length-1 .. start]
///                instead of [start .. start+length-1]
///
/// Encounter-order index `i` maps to physical buffer index:
/// - reversed=false: `buffer[start + i]`
/// - reversed=true:  `buffer[start + length - 1 - i]`
///
/// Restriction operations compute the equivalent (start, length, reversed)
/// for the requested sub-view; no element copies, allocation only of the
/// new FsWindow record.
@SuppressWarnings ( "unchecked" )
final class FsWindow < E > implements Window < E > {

  private final Object[] buffer;
  private final int      start;
  private final int      length;
  private final boolean  reversed;

  FsWindow ( Object[] buffer, int start, int length, boolean reversed ) {
    this.buffer   = buffer;
    this.start    = start;
    this.length   = length;
    this.reversed = reversed;
  }

  /// Encounter-order accessor.
  private E at ( int idx ) {
    if ( reversed ) {
      return (E) buffer[ start + length - 1 - idx ];
    }
    return (E) buffer[ start + idx ];
  }

  // ─── Terminal operations ──────────────────────────────────────────────────

  @Override
  public int size () {
    return length;
  }

  @Override
  public boolean isEmpty () {
    return length == 0;
  }

  @NotNull
  @Override
  public E first () {
    if ( length == 0 ) throw new NoSuchElementException ( "empty window" );
    return at ( 0 );
  }

  @NotNull
  @Override
  public E last () {
    if ( length == 0 ) throw new NoSuchElementException ( "empty window" );
    return at ( length - 1 );
  }

  @Override
  public void forEach ( @NotNull Consumer < ? super E > action ) {
    requireNonNull ( action, "action" );
    for ( int i = 0; i < length; i++ ) action.accept ( at ( i ) );
  }

  @Override
  public boolean all ( @NotNull Predicate < ? super E > predicate ) {
    requireNonNull ( predicate, "predicate" );
    for ( int i = 0; i < length; i++ ) {
      if ( !predicate.test ( at ( i ) ) ) return false;
    }
    return true;
  }

  @Override
  public boolean any ( @NotNull Predicate < ? super E > predicate ) {
    requireNonNull ( predicate, "predicate" );
    for ( int i = 0; i < length; i++ ) {
      if ( predicate.test ( at ( i ) ) ) return true;
    }
    return false;
  }

  @Override
  public boolean none ( @NotNull Predicate < ? super E > predicate ) {
    requireNonNull ( predicate, "predicate" );
    for ( int i = 0; i < length; i++ ) {
      if ( predicate.test ( at ( i ) ) ) return false;
    }
    return true;
  }

  @Override
  public int count ( @NotNull Predicate < ? super E > predicate ) {
    requireNonNull ( predicate, "predicate" );
    int c = 0;
    for ( int i = 0; i < length; i++ ) {
      if ( predicate.test ( at ( i ) ) ) c++;
    }
    return c;
  }

  @Override
  public < R > R fold ( @NotNull R seed,
                        @NotNull BiFunction < ? super R, ? super E, ? extends R > op ) {
    requireNonNull ( seed, "seed" );
    requireNonNull ( op,   "op" );
    R acc = seed;
    for ( int i = 0; i < length; i++ ) acc = op.apply ( acc, at ( i ) );
    return acc;
  }

  @Override
  public E reduce ( @NotNull E identity, @NotNull BinaryOperator < E > op ) {
    requireNonNull ( identity, "identity" );
    requireNonNull ( op,       "op" );
    E acc = identity;
    for ( int i = 0; i < length; i++ ) acc = op.apply ( acc, at ( i ) );
    return acc;
  }

  // ─── Restriction operations (return views; no value copies) ────────────────

  @NotNull
  @Override
  public Window < E > prefix ( int count ) {
    if ( count < 0 ) throw new IllegalArgumentException ( "count must be >= 0" );
    if ( count >= length ) return this;
    // First `count` elements of encounter order.
    if ( reversed ) {
      // Reversed encounter visits buffer[start+length-1 .. start]; the first
      // `count` of that is buffer[start+length-count .. start+length-1].
      return new FsWindow <> ( buffer, start + length - count, count, true );
    }
    return new FsWindow <> ( buffer, start, count, false );
  }

  @NotNull
  @Override
  public Window < E > suffix ( int count ) {
    if ( count < 0 ) throw new IllegalArgumentException ( "count must be >= 0" );
    if ( count >= length ) return this;
    // Last `count` elements of encounter order.
    if ( reversed ) {
      // Last `count` of reversed = buffer[start .. start+count-1].
      return new FsWindow <> ( buffer, start, count, true );
    }
    return new FsWindow <> ( buffer, start + length - count, count, false );
  }

  @NotNull
  @Override
  public Window < E > skip ( int count ) {
    if ( count < 0 ) throw new IllegalArgumentException ( "count must be >= 0" );
    if ( count == 0 ) return this;
    if ( count >= length ) return new FsWindow <> ( buffer, start, 0, reversed );
    // Drop first `count` of encounter order.
    if ( reversed ) {
      // Reversed: dropping first `count` = dropping last `count` of buffer.
      return new FsWindow <> ( buffer, start, length - count, true );
    }
    return new FsWindow <> ( buffer, start + count, length - count, false );
  }

  @NotNull
  @Override
  public Window < E > trim ( int count ) {
    if ( count < 0 ) throw new IllegalArgumentException ( "count must be >= 0" );
    if ( count == 0 ) return this;
    if ( count >= length ) return new FsWindow <> ( buffer, start, 0, reversed );
    // Drop last `count` of encounter order.
    if ( reversed ) {
      // Reversed: dropping last `count` = dropping first `count` of buffer.
      return new FsWindow <> ( buffer, start + count, length - count, true );
    }
    return new FsWindow <> ( buffer, start, length - count, false );
  }

  @NotNull
  @Override
  public Window < E > slice ( int offset, int count ) {
    if ( offset < 0 ) throw new IllegalArgumentException ( "offset must be >= 0" );
    if ( count  < 0 ) throw new IllegalArgumentException ( "count must be >= 0" );
    if ( offset >= length ) return new FsWindow <> ( buffer, start, 0, reversed );
    final int effective = Math.min ( count, length - offset );
    if ( offset == 0 && effective == length ) return this;
    // Take `effective` values starting at encounter-order index `offset`.
    if ( reversed ) {
      // Reversed encounter at logical index `offset` is buffer[start + length - 1 - offset].
      // The slice's last element (logical offset + effective - 1) is
      // buffer[start + length - 1 - (offset + effective - 1)] = buffer[start + length - offset - effective].
      // So physical range = [start + length - offset - effective .. start + length - offset - 1]
      return new FsWindow <> ( buffer, start + length - offset - effective, effective, true );
    }
    return new FsWindow <> ( buffer, start + offset, effective, false );
  }

  @NotNull
  @Override
  public Window < E > reverse () {
    return new FsWindow <> ( buffer, start, length, !reversed );
  }
}
