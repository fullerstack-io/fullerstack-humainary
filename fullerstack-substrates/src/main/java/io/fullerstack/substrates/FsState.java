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

  private static final Slot < ? > [] EMPTY_ARRAY = new Slot < ? > [ 0 ];

  /// Shared singleton for empty state - internal use only (e.g.,
  /// Subject.state()).
  /// Note: Cortex.state() must use create() to comply with @New annotation
  /// contract.
  static final FsState EMPTY = new FsState ( EMPTY_ARRAY, 0 );

  /// Slots in most-recent-first order. Never null, never mutated.
  private final Slot < ? > [] slots;

  /// Number of valid elements in slots array.
  private final int size;

  /// Private constructor.
  private FsState ( Slot < ? > [] slots, int size ) {
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

  @New
  @NotNull
  @Override
  public State compact () {
    final int len = size;
    if ( len <= 1 )
      return this;
    var result = new Slot < ? > [ len ];
    int idx = 0;
    outer:
    for ( int i = 0; i < len; i++ ) {
      Name name = slots[ i ].name ();
      Class < ? > type = slots[ i ].type ();
      for ( int j = 0; j < idx; j++ ) {
        if ( result[ j ].name () == name && result[ j ].type () == type )
          continue outer;
      }
      result[ idx++ ] = slots[ i ];
    }
    if ( idx == len )
      return this;
    return new FsState ( result, idx );
  }

  private State addSlot ( Slot < ? > slot ) {
    // Idempotent: if most recent slot has same name/type/value, return this
    if ( size > 0
         && slots[ 0 ].name () == slot.name ()
         && slots[ 0 ].type () == slot.type ()
         && Objects.equals ( slots[ 0 ].value (), slot.value () ) ) {
      return this;
    }
    var arr = new Slot < ? > [ size + 1 ];
    arr[ 0 ] = slot;
    System.arraycopy ( slots, 0, arr, 1, size );
    return new FsState ( arr, size + 1 );
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
    Class < ? > declClass = value.getDeclaringClass ();
    String canonical = declClass.getCanonicalName ();
    String className = canonical != null ? canonical : declClass.getName ();
    Name slotName = cortex ().name ( className );
    Name slotValue = FsName.fromEnum ( value );
    return addSlot ( new FsSlot <> ( slotName, slotValue, Name.class ) );
  }

  @Override
  public Stream < Slot < ? > > stream () {
    return Arrays.stream ( slots, 0, size );
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < T > T value ( Slot < T > slot ) {
    Name targetName = slot.name ();
    Class < T > targetType = slot.type ();
    for ( int i = 0; i < size; i++ ) {
      if ( slots[ i ].name () == targetName && targetType.isAssignableFrom ( slots[ i ].type () ) ) {
        return (T) slots[ i ].value ();
      }
    }
    return slot.value ();
  }

  @Override
  @SuppressWarnings ( "unchecked" )
  public < T > Stream < T > values ( Slot < ? extends T > slot ) {
    Objects.requireNonNull ( slot, "slot must not be null" );
    Name targetName = slot.name ();
    Class < ? extends T > targetType = slot.type ();
    Object[] matches = new Object[ size ];
    int idx = 0;
    for ( int i = 0; i < size; i++ ) {
      if ( slots[ i ].name () == targetName && targetType.isAssignableFrom ( slots[ i ].type () ) ) {
        matches[ idx++ ] = slots[ i ].value ();
      }
    }
    if ( idx == 0 )
      return Stream.empty ();
    return (Stream < T >) Arrays.stream ( matches, 0, idx );
  }

}
