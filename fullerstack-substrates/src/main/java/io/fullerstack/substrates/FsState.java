package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.stream.Stream;

/// An immutable collection of slots using raw array for performance.
/// Stack semantics: most recent slot at end (highest index).
@Provided
final class FsState
  implements State {

  /// Empty slots array - shared to avoid allocation.
  private static final Slot < ? >[] EMPTY_SLOTS = new Slot < ? >[0];

  /// Shared singleton for empty state - internal use only (e.g., Subject.state()).
  /// Note: Cortex.state() must use create() to comply with @New annotation contract.
  static final FsState EMPTY = new FsState ( EMPTY_SLOTS );

  /// Raw array - most recent at index (length-1). Never null, never mutated.
  private final Slot < ? >[] slots;

  /// Private constructor.
  private FsState ( Slot < ? >[] slots ) {
    this.slots = slots;
  }

  /// Factory method to create new empty State - required by @New annotation contract.
  static FsState create () {
    return new FsState ( EMPTY_SLOTS );
  }

  @Override
  public Iterator < Slot < ? > > iterator () {
    // Return in most-recent-first order (reverse iteration: high index to low)
    return new Iterator <> () {
      private int index = slots.length - 1;

      @Override
      public boolean hasNext () {
        return index >= 0;
      }

      @Override
      public Slot < ? > next () {
        if ( index < 0 ) throw new java.util.NoSuchElementException ();
        return slots[index--];
      }
    };
  }

  @Override
  public Spliterator < Slot < ? > > spliterator () {
    // Return a SIZED spliterator in most-recent-first order (reverse of array)
    int len = slots.length;
    if ( len == 0 ) {
      return java.util.Spliterators.emptySpliterator ();
    }
    // Create reversed array for spliterator
    Slot < ? >[] reversed = new Slot < ? >[len];
    for ( int i = 0; i < len; i++ ) {
      reversed[i] = slots[len - 1 - i];
    }
    return java.util.Spliterators.spliterator (
      reversed,
      java.util.Spliterator.ORDERED | java.util.Spliterator.SIZED | java.util.Spliterator.IMMUTABLE
    );
  }

  @New
  @NotNull
  @Override
  public State compact () {
    // Early exit if no duplicates possible
    int len = slots.length;
    if ( len <= 1 ) return this;
    // For small arrays, use simple O(n²) scan - faster than HashMap overhead
    // Scan from most recent (end) to oldest, keeping first occurrence
    // Use identity comparison for Name (interned) and Class
    Slot < ? >[] result = new Slot < ? >[len];
    int count = 0;
    outer:
    for ( int i = len - 1; i >= 0; i-- ) {
      Slot < ? > candidate = slots[i];
      Name candidateName = candidate.name ();
      Class < ? > candidateType = candidate.type ();
      // Check if we already have this (name, type) in result
      for ( int j = 0; j < count; j++ ) {
        if ( result[j].name () == candidateName && result[j].type () == candidateType ) {
          continue outer; // Skip duplicate
        }
      }
      result[count++] = candidate;
    }
    // Early exit if no duplicates were found
    if ( count == len ) return this;
    // Create trimmed array
    Slot < ? >[] trimmed = new Slot < ? >[count];
    System.arraycopy ( result, 0, trimmed, 0, count );
    return new FsState ( trimmed );
  }

  private State addSlot ( Slot < ? > slot ) {
    int len = slots.length;
    // Fast path: adding to empty state - just wrap the single slot
    if ( len == 0 ) {
      return new FsState ( new Slot < ? >[]{slot} );
    }
    // If the most recent slot is equal (same name, type, and value), return this
    // Use identity for Name (interned) and Class
    Slot < ? > last = slots[len - 1];
    if ( last.name () == slot.name () &&
      last.type () == slot.type () &&
      Objects.equals ( last.value (), slot.value () ) ) {
      return this;
    }
    // Copy with one extra slot - System.arraycopy is JVM intrinsic
    Slot < ? >[] newSlots = new Slot < ? >[len + 1];
    System.arraycopy ( slots, 0, newSlots, 0, len );
    newSlots[len] = slot;
    return new FsState ( newSlots );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, int value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Integer >) (Class < ? >) int.class ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, long value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Long >) (Class < ? >) long.class ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, float value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Float >) (Class < ? >) float.class ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, double value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Double >) (Class < ? >) double.class ) );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, boolean value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Boolean >) (Class < ? >) boolean.class ) );
  }

  @New
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull String value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, String.class ) );
  }

  @New
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull Name value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, Name.class ) );
  }

  @New
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull State value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, State.class ) );
  }

  @New
  @NotNull
  @Override
  public State state ( @NotNull Slot < ? > slot ) {
    Objects.requireNonNull ( slot, "slot must not be null" );
    return addSlot ( slot );
  }

  @New
  @NotNull
  @Override
  public State state ( @NotNull Enum < ? > value ) {
    Objects.requireNonNull ( value, "value must not be null" );
    // Name is derived from the enum's declaring class canonical name (uses . not $)
    Class < ? > declClass = value.getDeclaringClass ();
    String canonical = declClass.getCanonicalName ();
    String className = canonical != null ? canonical : declClass.getName ();
    Name slotName = cortex ().name ( className );
    // Value is a Name using the full hierarchical path: DeclaringClass.enumConstant
    Name slotValue = FsName.fromEnum ( value );
    return addSlot ( new FsSlot <> ( slotName, slotValue, Name.class ) );
  }

  @Override
  public Stream < Slot < ? > > stream () {
    // Return in most-recent-first order (reverse of internal list)
    return java.util.stream.StreamSupport.stream (
      spliterator (),
      false
    );
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < T > T value ( Slot < T > slot ) {
    // Search in reverse for most recent - use identity for Name (interned)
    Name targetName = slot.name ();
    Class < T > targetType = slot.type ();
    for ( int i = slots.length - 1; i >= 0; i-- ) {
      Slot < ? > s = slots[i];
      if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
        return (T) s.value ();
      }
    }
    return slot.value ();
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < T > Stream < T > values ( Slot < ? extends T > slot ) {
    Objects.requireNonNull ( slot, "slot must not be null" );
    // Use identity for Name (interned)
    // Fast path: empty state returns empty stream
    int len = slots.length;
    if ( len == 0 ) {
      return Stream.empty ();
    }
    Name targetName = slot.name ();
    Class < ? extends T > targetType = slot.type ();
    // Fast path: single slot - check and return single-element or empty stream
    if ( len == 1 ) {
      Slot < ? > s = slots[0];
      if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
        return Stream.of ( (T) s.value () );
      }
      return Stream.empty ();
    }
    // Count matches first to size array exactly - also enables SIZED stream
    int count = 0;
    for ( int i = 0; i < len; i++ ) {
      Slot < ? > s = slots[i];
      if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
        count++;
      }
    }
    if ( count == 0 ) {
      return Stream.empty ();
    }
    // Collect matches into exactly-sized array in most-recent-first order (reverse iteration)
    Object[] matches = new Object[count];
    int idx = 0;
    for ( int i = len - 1; i >= 0; i-- ) {
      Slot < ? > s = slots[i];
      if ( s.name () == targetName && targetType.isAssignableFrom ( s.type () ) ) {
        matches[idx++] = s.value ();
      }
    }
    // Arrays.stream creates SIZED|SUBSIZED spliterator - count() is O(1)
    return (Stream < T >) java.util.Arrays.stream ( matches );
  }

}
