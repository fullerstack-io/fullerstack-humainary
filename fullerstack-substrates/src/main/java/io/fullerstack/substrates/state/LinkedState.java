package io.fullerstack.substrates.state;

import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.slot.TypedSlot;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Implementation of Substrates.State for storing typed values.
 * <p>
 * < p >< b >Immutable Design:</b > State objects are immutable. Each call to {@code state()}
 * returns a NEW State instance with the slot appended. This allows duplicate names
 * to exist in the slot list.
 * <p>
 * < p >< b >Type Matching:</b > Per William Louth's design, "A State stores the type with
 * the name, only matching when both are exact matches." This allows the same name to
 * hold different types simultaneously:
 * < pre >
 * State state = cortex.state()
 * .state(name("port"), 8080)        // Integer
 * .state(name("port"), "HTTP/1.1"); // String (does NOT override Integer!)
 * <p>
 * Integer port = state.value(slot(name("port"), 0));     // 8080
 * String protocol = state.value(slot(name("port"), "")); // "HTTP/1.1"
 * </pre >
 * <p>
 * < p >< b >Duplicate Handling:</b > Uses a List internally, which allows multiple slots
 * with the same (name, type) pair. Call {@code compact()} to remove duplicates, keeping
 * the last occurrence of each (name, type) pair.
 * <p>
 * < p >< b >Pattern:</b > Builder pattern with override support:
 * < pre >
 * State config = cortex.state()
 * .state(name("timeout"), 30)   // Default value
 * .state(name("timeout"), 60)   // Override (both exist until compact)
 * .compact();                   // Deduplicate (keeps last: 60)
 * </pre >
 *
 * @see State
 */
public class LinkedState implements State {
  private final List < Slot < ? > > slots;

  /**
   * Creates an empty State.
   */
  public LinkedState () {
    this.slots = new ArrayList <> ();
  }

  /**
   * Private constructor for creating new State with slots.
   */
  private LinkedState ( List < Slot < ? > > slots ) {
    this.slots = new ArrayList <> ( slots );
  }

  @Override
  public State compact () {
    // Remove duplicates, keeping last occurrence of each (name, type) pair
    // Result must be in reverse chronological order (most recent first)
    Map < NameTypePair, Slot < ? > > latest = new LinkedHashMap <> ();

    // Iterate from end to start, only keeping first seen (which is latest occurrence)
    java.util.ListIterator < Slot < ? > > it = slots.listIterator ( slots.size () );
    while ( it.hasPrevious () ) {
      Slot < ? > slot = it.previous ();
      latest.putIfAbsent ( new NameTypePair ( slot.name (), slot.type () ), slot );
    }

    // LinkedHashMap preserves reverse iteration order, so values are already in reverse chronological order
    return new LinkedState ( new ArrayList <> ( latest.values () ) );
  }

  /**
   * Composite key for deduplication: (name, type) pair.
   * State matches slots by both name AND type.
   */
  private record NameTypePair( Name name, Class < ? > type ) {
  }

  /**
   * Core method to add a slot to this state.
   * Returns this instance if an equal slot (same name, type, value) already exists.
   * Otherwise returns a new state with the slot appended.
   */
  private State addSlot ( Slot < ? > slot ) {
    // Check if equal slot already exists
    for ( Slot < ? > existing : slots ) {
      if ( existing.name ().equals ( slot.name () ) &&
        existing.type ().equals ( slot.type () ) &&
        java.util.Objects.equals ( existing.value (), slot.value () ) ) {
        return this;  // Idempotency: return same instance
      }
    }

    // Create new state with slot appended
    LinkedState newState = new LinkedState ( this.slots );
    newState.slots.add ( slot );
    return newState;
  }

  @Override
  public State state ( Name name, int value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, long value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, float value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, double value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, boolean value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, String value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    java.util.Objects.requireNonNull ( value, "String value cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, Name value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    java.util.Objects.requireNonNull ( value, "Name value cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Name name, State value ) {
    java.util.Objects.requireNonNull ( name, "Name cannot be null" );
    java.util.Objects.requireNonNull ( value, "State value cannot be null" );
    return addSlot ( TypedSlot.of ( name, value ) );
  }

  @Override
  public State state ( Enum < ? > value ) {
    java.util.Objects.requireNonNull ( value, "Enum value cannot be null" );

    // Per Humainary API: Store enum using its declaring class as the name,
    // and the enum value as a hierarchical Name: className.enumConstant
    // Convert $ to . for proper hierarchical names
    String className = value.getDeclaringClass ().getName ().replace ( '$', '.' );
    Name enumClassName = InternedName.of ( className );
    // Append just the enum constant name (not the full Enum path)
    Name enumValueName = InternedName.of ( className ).name ( value.name () );

    return addSlot ( TypedSlot.of ( enumClassName, enumValueName, Name.class ) );
  }

  @Override
  public State state ( Slot < ? > slot ) {
    java.util.Objects.requireNonNull ( slot, "Slot cannot be null" );

    // Special case: Only check if equal slot is at the END (most recent position)
    // This allows "moving" a slot to the most recent position by re-adding it
    if ( !slots.isEmpty () ) {
      Slot < ? > last = slots.get ( slots.size () - 1 );
      if ( last.name ().equals ( slot.name () ) &&
        last.type ().equals ( slot.type () ) &&
        java.util.Objects.equals ( last.value (), slot.value () ) ) {
        return this;  // Already at most recent position
      }
    }

    // Append slot (allows duplicates until compact())
    LinkedState newState = new LinkedState ( this.slots );
    newState.slots.add ( slot );
    return newState;
  }

  @Override
  public Stream < Slot < ? > > stream () {
    // Return slots in reverse chronological order (most recent first)
    List < Slot < ? > > reversed = new ArrayList <> ( slots );
    java.util.Collections.reverse ( reversed );
    return reversed.stream ();
  }

  @Override
  public < T > T value ( Slot < T > slot ) {
    // API: "Returns the value of a slot matching the specified slot
    //      or the value of the specified slot when not found"
    // Start with fallback value from query slot
    T result = slot.value ();

    // Search for matching name AND type, keep updating with each match (last one wins)
    // Per article: "A State stores the type with the name, only matching when both are exact matches"
    for ( Slot < ? > s : slots ) {
      if ( s.name ().equals ( slot.name () ) && typesMatch ( slot.type (), s.type () ) ) {
        @SuppressWarnings ( "unchecked" )
        T value = ( (Slot < T >) s ).value ();
        result = value;  // Keep updating (last occurrence wins)
      }
    }

    // Returns slot.value() fallback if name and type not found
    return result;
  }

  @Override
  public < T > Stream < T > values ( Slot < ? extends T > slot ) {
    // Return ALL values with this name AND type in reverse chronological order (most recent first)
    // Per article: "A State stores the type with the name, only matching when both are exact matches"
    // Collect matching values, then reverse to get most recent first
    List < T > values = new ArrayList <> ();
    for ( Slot < ? > s : slots ) {
      if ( s.name ().equals ( slot.name () ) && typesMatch ( slot.type (), s.type () ) ) {
        @SuppressWarnings ( "unchecked" )
        Slot < T > typed = (Slot < T >) s;
        values.add ( typed.value () );
      }
    }
    // Reverse to get most recent first (slots are in chronological order)
    java.util.Collections.reverse ( values );
    return values.stream ();
  }

  /**
   * Check if types match for slot lookup.
   * Handles both exact matches and interface/subclass relationships.
   *
   * @param queryType  the type being queried (e.g., State.class)
   * @param storedType the type stored in the slot (e.g., LinkedState.class)
   * @return true if types are compatible
   */
  private boolean typesMatch ( Class < ? > queryType, Class < ? > storedType ) {
    return queryType.equals ( storedType ) || queryType.isAssignableFrom ( storedType );
  }

  @Override
  public Iterator < Slot < ? > > iterator () {
    // Return slots in reverse chronological order (most recent first)
    List < Slot < ? > > reversed = new ArrayList <> ( slots );
    java.util.Collections.reverse ( reversed );
    return reversed.iterator ();
  }

  @Override
  public java.util.Spliterator < Slot < ? > > spliterator () {
    // Return slots in reverse chronological order with exact size
    List < Slot < ? > > reversed = new ArrayList <> ( slots );
    java.util.Collections.reverse ( reversed );
    return reversed.spliterator ();
  }

  // Factory methods for creating states
  public static State empty () {
    return new LinkedState ();
  }

  public static State of ( Name name, int value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, long value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, float value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, double value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, boolean value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, String value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, Name value ) {
    return new LinkedState ().state ( name, value );
  }

  public static State of ( Name name, State value ) {
    return new LinkedState ().state ( name, value );
  }

  @Override
  public String toString () {
    return "State[slots=" + slots.size () + "]";
  }
}
