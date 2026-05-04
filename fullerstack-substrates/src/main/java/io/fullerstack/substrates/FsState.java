package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;

/// An immutable collection of slots stored most-recent-first.
/// Index 0 is the most recently added slot.
@Provided
final class FsState implements State {

  private static final Slot < ? >[] EMPTY_ARRAY = new Slot < ? >[0];

  /// Shared singleton for empty state - internal use only (e.g.,
  /// Subject.state()).
  /// Note: Cortex.state() must use create() to comply with @New annotation
  /// contract.
  static final FsState EMPTY = new FsState ( EMPTY_ARRAY, 0 );

  /// Slots in most-recent-first order. Never null, never mutated.
  private final Slot < ? >[] slots;

  /// Number of valid elements in slots array.
  private final int size;

  /// Lazily-cached compacted slots array + size. Volatile for safe publication.
  /// Same pattern as String.hashCode — a deterministic lazy cache that doesn't
  /// break effective immutability. The compact() method computes this once and
  /// returns a fresh FsState wrapping it on subsequent calls (the @New contract
  /// requires a new instance per call, so we can't cache the FsState itself —
  /// only the underlying array + size).
  ///
  /// Sentinel: if cachedCompactSlots == slots (this state's own array), it
  /// means the state is already compact and compact() should return `this`.
  private volatile Slot < ? >[] cachedCompactSlots;
  private          int           cachedCompactSize;

  /// Private constructor.
  private FsState ( Slot < ? >[] slots, int size ) {
    this.slots = slots;
    this.size = size;
  }

  /// Factory method to create new empty State - required by @New annotation
  /// contract.
  static FsState create () {
    return new FsState ( EMPTY_ARRAY, 0 );
  }

  @Override
  public Iterator < Slot < ? > > iterator () {
    return Spliterators.iterator ( spliterator () );
  }

  @Override
  public Spliterator < Slot < ? > > spliterator () {
    return Arrays.spliterator ( slots, 0, size );
  }

  /// Removed from State interface in 2.0 — kept as FsState-local utility.
  public State compact () {
    // Fast path: cached result from a previous call on this state.
    // The volatile read gives us safe publication; cachedCompactSize is
    // published through the volatile slots reference via happens-before.
    Slot < ? >[] cached = cachedCompactSlots;
    if ( cached != null ) {
      // Sentinel: own slots array means "already compact, return this"
      if ( cached == slots )
        return this;
      // Otherwise wrap the cached dedup'd array in a fresh FsState
      // (@New contract requires a new instance per call).
      return new FsState ( cached, cachedCompactSize );
    }
    // Cold path: compute the compaction once and memoise
    final int len = size;
    if ( len <= 1 ) {
      cachedCompactSize = len;
      cachedCompactSlots = slots; // sentinel
      return this;
    }
    var result = new Slot < ? >[len];
    int idx = 0;
    outer:
    for ( int i = 0; i < len; i++ ) {
      Name name = slots[i].name ();
      Class < ? > type = slots[i].type ();
      for ( int j = 0; j < idx; j++ ) {
        if ( result[j].name () == name && result[j].type () == type )
          continue outer;
      }
      result[idx++] = slots[i];
    }
    if ( idx == len ) {
      cachedCompactSize = len;
      cachedCompactSlots = slots; // sentinel
      return this;
    }
    cachedCompactSize = idx;
    cachedCompactSlots = result; // volatile publish (happens-after size write)
    return new FsState ( result, idx );
  }

  private State addSlot ( Slot < ? > slot ) {
    // Idempotent: if most recent slot has same name/type/value, return this
    if ( size > 0
      && slots[0].name () == slot.name ()
      && slots[0].type () == slot.type ()
      && Objects.equals ( slots[0].value (), slot.value () ) ) {
      return this;
    }
    var arr = new Slot < ? >[size + 1];
    arr[0] = slot;
    System.arraycopy ( slots, 0, arr, 1, size );
    return new FsState ( arr, size + 1 );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, int value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Integer >) (Class < ? >) int.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, long value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Long >) (Class < ? >) long.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, float value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Float >) (Class < ? >) float.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, double value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Double >) (Class < ? >) double.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public State state ( @NotNull Name name, boolean value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    return addSlot ( new FsSlot <> ( name, value, (Class < Boolean >) (Class < ? >) boolean.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull String value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, String.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull Name value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, Name.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public State state ( @NotNull Name name, @NotNull State value ) {
    Objects.requireNonNull ( name, "name must not be null" );
    Objects.requireNonNull ( value, "value must not be null" );
    return addSlot ( new FsSlot <> ( name, value, State.class ) );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public State state ( @NotNull Slot < ? > slot ) {
    Objects.requireNonNull ( slot, "slot must not be null" );
    return addSlot ( slot );
  }

  @New ( conditional = true )
  @NotNull
  @Override
  public State state ( @NotNull Enum < ? > value ) {
    Objects.requireNonNull ( value, "value must not be null" );
    Name slotName = cortex ().name ( value.getDeclaringClass () );
    Name slotValue = FsName.fromEnum ( value );
    return addSlot ( new FsSlot <> ( slotName, slotValue, Name.class ) );
  }

  @Override
  public Stream < Slot < ? > > stream () {
    return Arrays.stream ( slots, 0, size );
  }

  /// Type-match check: == for exact match (common case, avoids reflective call),
  /// fall back to isAssignableFrom for subtypes.
  private static boolean typeMatches ( Class < ? > target, Class < ? > actual ) {
    return target == actual || target.isAssignableFrom ( actual );
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < T > T value ( Slot < T > slot ) {
    Name targetName = slot.name ();
    Class < T > targetType = slot.type ();
    for ( int i = 0; i < size; i++ ) {
      if ( slots[i].name () == targetName && typeMatches ( targetType, slots[i].type () ) ) {
        return (T) slots[i].value ();
      }
    }
    return slot.value ();
  }

  /// Cached values() result for a single (slot → matches) binding.
  /// Saves the filter loop on repeated calls with the same slot argument.
  private volatile Slot < ? > cachedValuesSlot;
  private          Object[]   cachedValuesMatches;
  private          int        cachedValuesCount;

  /// Removed from State interface in 2.0 — kept as FsState-local utility.
  @SuppressWarnings ( "unchecked" )
  public < T > Stream < T > values ( Slot < ? extends T > slot ) {
    Objects.requireNonNull ( slot, "slot must not be null" );
    // Fast path: if same slot as last call, reuse the cached matches array.
    // Returns a fresh Stream (streams aren't reusable), backed by the same data.
    if ( slot == cachedValuesSlot ) {
      int c = cachedValuesCount;
      if ( c == 0 ) return Stream.empty ();
      return (Stream < T >) Arrays.stream ( cachedValuesMatches, 0, c );
    }
    // Cold path: filter and cache
    Name targetName = slot.name ();
    Class < ? extends T > targetType = slot.type ();
    Object[] matches = new Object[size];
    int idx = 0;
    for ( int i = 0; i < size; i++ ) {
      if ( slots[i].name () == targetName && typeMatches ( targetType, slots[i].type () ) ) {
        matches[idx++] = slots[i].value ();
      }
    }
    cachedValuesCount   = idx;
    cachedValuesMatches = matches;
    cachedValuesSlot    = slot; // volatile publish (happens-after count + matches)
    if ( idx == 0 )
      return Stream.empty ();
    return (Stream < T >) Arrays.stream ( matches, 0, idx );
  }

}
