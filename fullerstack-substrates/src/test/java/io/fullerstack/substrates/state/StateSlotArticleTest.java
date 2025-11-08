package io.fullerstack.substrates.state;

import io.fullerstack.substrates.slot.TypedSlot;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests based on William Louth's article:
 * https://humainary.io/blog/observability-x-states-and-slots/
 * <p>
 * Each test verifies a specific scenario from the article to ensure
 * our implementation matches the design intent exactly.
 */
class StateSlotArticleTest {

  private Cortex cortex;
  private Name   XYZ;
  private Name   ABC;
  private Name   NAME;
  private Name   NODE;

  @BeforeEach
  void setUp () {
    cortex = cortex();
    XYZ = cortex.name ( "x.y.z" );
    ABC = cortex.name ( "a.b.c" );
    NAME = cortex.name ( "name" );
    NODE = cortex.name ( "node" );
  }

  /**
   * Article Scenario 1: Basic State Creation
   * <p>
   * From article:
   * var state = cortex
   * .state(XYZ, 1)
   * .state(ABC, 2);
   */
  @Test
  void scenario1_basicStateCreation () {
    State state = cortex.state ()
      .state ( XYZ, 1 )
      .state ( ABC, 2 );

    assertThat ( state.stream ().count () ).isEqualTo ( 2 );

    // Verify both values are present
    Slot < Integer > xyzSlot = cortex.slot ( XYZ, -1 );
    Slot < Integer > abcSlot = cortex.slot ( ABC, -1 );

    assertThat ( state.value ( xyzSlot ) ).isEqualTo ( 1 );
    assertThat ( state.value ( abcSlot ) ).isEqualTo ( 2 );
  }

  /**
   * Article Scenario 2: Slot Value Resolution with Fallback
   * <p>
   * From article:
   * var xyz = cortex.slot(XYZ, -1);
   * out.println(state.value(xyz)); // Prints 1
   */
  @Test
  void scenario2_slotValueResolutionWithFallback () {
    State state = cortex.state ().state ( XYZ, 1 );

    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );

    // Should find value in state
    assertThat ( state.value ( xyz ) ).isEqualTo ( 1 );
  }

  /**
   * Article Scenario 2b: Fallback When Not Found
   * <p>
   * Tests that fallback value is returned when name not in state.
   */
  @Test
  void scenario2b_fallbackWhenNotFound () {
    State state = cortex.state ().state ( ABC, 100 );

    // Query for XYZ (not in state) with fallback -1
    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );

    // Should return fallback value
    assertThat ( state.value ( xyz ) ).isEqualTo ( -1 );
  }

  /**
   * Article Scenario 3: State Modification (Immutability)
   * <p>
   * From article:
   * state = state.state(XYZ, 3);
   * out.println(state.value(xyz)); // Prints 3
   */
  @Test
  void scenario3_stateModificationImmutability () {
    State state = cortex.state ().state ( XYZ, 1 );

    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );
    assertThat ( state.value ( xyz ) ).isEqualTo ( 1 );

    // "Modify" by creating new state
    state = state.state ( XYZ, 3 );

    // New state has updated value
    assertThat ( state.value ( xyz ) ).isEqualTo ( 3 );
  }

  /**
   * Article Scenario 4: Type Matching - Same Name, Different Types
   * <p>
   * From article:
   * "A State stores the type with the name, only matching when both are exact matches.
   * The new slot has no impact with 3 printed as before in the following code because
   * the new slot type added is now of type String."
   * <p>
   * state = state.state(XYZ, "4");
   * out.println(state.value(xyz)); // Prints 3 (Integer, not "4")
   */
  @Test
  void scenario4_typeMatchingSameNameDifferentTypes () {
    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );

    State state = cortex.state ()
      .state ( XYZ, 3 )      // Integer
      .state ( XYZ, "4" );   // String - different type!

    // Query for Integer type - should return 3, NOT affected by String "4"
    assertThat ( state.value ( xyz ) ).isEqualTo ( 3 );

    // Query for String type - should return "4"
    Slot < String > xyzString = cortex.slot ( XYZ, "default" );
    assertThat ( state.value ( xyzString ) ).isEqualTo ( "4" );

    // Both values coexist because types differ
    assertThat ( state.stream ().count () ).isEqualTo ( 2 );
  }

  /**
   * Article Scenario 5: Compact State
   * <p>
   * From article:
   * cortex
   * .state(XYZ, 4)
   * .state(XYZ, 3)
   * .compact()
   * .values(xyz)
   * .forEach(out::print); // Prints 3
   */
  @Test
  void scenario5_compactState () {
    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );

    State state = cortex.state ()
      .state ( XYZ, 4 )
      .state ( XYZ, 3 )
      .compact ();

    // After compact, should have only last value
    List < Integer > values = state.values ( xyz ).collect ( Collectors.toList () );

    assertThat ( values ).containsExactly ( 3 );  // Only last value (3)
    assertThat ( state.stream ().count () ).isEqualTo ( 1 );  // Only 1 slot after compact
  }

  /**
   * Article Scenario 6: Multiple Values with Same (Name, Type)
   * <p>
   * From article:
   * "A State does not discard previously added slots. The value method returns
   * the most recently added value that matches the specified Slot."
   * <p>
   * state.values(xyz).forEach(out::println);
   * <p>
   * Note: LinkedState returns values in reverse chronological order (LIFO)
   */
  @Test
  void scenario6_multipleValuesWithSameNameAndType () {
    Slot < Integer > xyz = cortex.slot ( XYZ, -1 );

    State state = cortex.state ()
      .state ( XYZ, 1 )
      .state ( XYZ, 2 )
      .state ( XYZ, 3 );

    // value() returns LAST occurrence
    assertThat ( state.value ( xyz ) ).isEqualTo ( 3 );

    // values() returns ALL occurrences in reverse chronological order (most recent first)
    List < Integer > allValues = state.values ( xyz ).collect ( Collectors.toList () );
    assertThat ( allValues ).containsExactly ( 3, 2, 1 );
  }

  /**
   * Article Scenario 7: Stream All Slots
   * <p>
   * From article:
   * state.stream()
   * .forEach(
   * slot ->
   * out.printf(
   * "%s[%s]=%s%n",
   * slot.name(),
   * slot.type().getSimpleName(),
   * slot.value()
   * )
   * );
   * <p>
   * Note: LinkedState.stream() returns slots in reverse chronological order (LIFO)
   */
  @Test
  void scenario7_streamAllSlots () {
    State state = cortex.state ()
      .state ( XYZ, 42 )           // Integer (added first)
      .state ( ABC, "hello" )      // String (added second)
      .state ( XYZ, true );        // Boolean (added third - most recent)

    List < Slot < ? > > allSlots = state.stream ().collect ( Collectors.toList () );

    assertThat ( allSlots ).hasSize ( 3 );

    // Verify slot details - returned in reverse chronological order
    Slot < ? > slot1 = allSlots.get ( 0 );
    assertThat ( (Object) slot1.name () ).isEqualTo ( XYZ );
    assertThat ( (Object) slot1.type () ).isEqualTo ( boolean.class );  // Primitive type from literal
    assertThat ( (Object) slot1.value () ).isEqualTo ( true );

    Slot < ? > slot2 = allSlots.get ( 1 );
    assertThat ( (Object) slot2.name () ).isEqualTo ( ABC );
    assertThat ( (Object) slot2.type () ).isEqualTo ( String.class );
    assertThat ( (Object) slot2.value () ).isEqualTo ( "hello" );

    Slot < ? > slot3 = allSlots.get ( 2 );
    assertThat ( (Object) slot3.name () ).isEqualTo ( XYZ );
    assertThat ( (Object) slot3.type () ).isEqualTo ( int.class );  // Primitive type from literal
    assertThat ( (Object) slot3.value () ).isEqualTo ( 42 );
  }

  /**
   * Article Scenario 8: Nested State
   * <p>
   * From article:
   * var NAME = cortex.name("name");
   * var NODE = cortex.name("node");
   * state = cortex.state(
   * NODE,
   * cortex.state(
   * NAME,
   * "william"
   * )
   * );
   */
  @Test
  void scenario8_nestedState () {
    State innerState = cortex.state ().state ( NAME, "william" );
    State outerState = cortex.state ().state ( NODE, innerState );

    // Query for nested state
    Slot < State > nodeSlot = TypedSlot.of ( NODE, cortex.state (), State.class );
    State retrievedInner = outerState.value ( nodeSlot );

    // Verify nested state content
    Slot < String > nameSlot = cortex.slot ( NAME, "" );
    assertThat ( retrievedInner.value ( nameSlot ) ).isEqualTo ( "william" );
  }

  /**
   * Complex Scenario: Type Matching with Multiple Types for Same Name
   * <p>
   * Demonstrates that State can hold Integer, String, and Boolean
   * all with the same name, and each can be retrieved independently.
   */
  @Test
  void complexScenario_multipleTypesForSameName () {
    Name PORT = cortex.name ( "port" );

    State state = cortex.state ()
      .state ( PORT, 8080 )        // Integer port number
      .state ( PORT, "HTTP/1.1" )  // String protocol
      .state ( PORT, true );       // Boolean enabled flag

    // Each type can be retrieved independently
    Slot < Integer > portNumber = cortex.slot ( PORT, 0 );
    Slot < String > portProtocol = cortex.slot ( PORT, "" );
    Slot < Boolean > portEnabled = cortex.slot ( PORT, false );

    assertThat ( state.value ( portNumber ) ).isEqualTo ( 8080 );
    assertThat ( state.value ( portProtocol ) ).isEqualTo ( "HTTP/1.1" );
    assertThat ( state.value ( portEnabled ) ).isTrue ();

    // All three slots coexist
    assertThat ( state.stream ().count () ).isEqualTo ( 3 );
  }

  /**
   * Complex Scenario: Compact with Mixed Types
   * <p>
   * Verifies that compact() only removes duplicates with same (name, type) pair.
   */
  @Test
  void complexScenario_compactWithMixedTypes () {
    State state = cortex.state ()
      .state ( XYZ, 1 )      // Integer #1
      .state ( XYZ, 2 )      // Integer #2 (duplicate)
      .state ( XYZ, "a" )    // String #1 (different type)
      .state ( XYZ, "b" )    // String #2 (duplicate)
      .state ( XYZ, 3 );     // Integer #3 (duplicate)

    State compacted = state.compact ();

    // Should keep last Integer (3) and last String ("b")
    assertThat ( compacted.stream ().count () ).isEqualTo ( 2 );

    Slot < Integer > xyzInt = cortex.slot ( XYZ, -1 );
    Slot < String > xyzStr = cortex.slot ( XYZ, "" );

    assertThat ( compacted.value ( xyzInt ) ).isEqualTo ( 3 );
    assertThat ( compacted.value ( xyzStr ) ).isEqualTo ( "b" );
  }

  /**
   * Edge Case: Empty State with Fallback
   */
  @Test
  void edgeCase_emptyStateWithFallback () {
    State emptyState = cortex.state ();

    Slot < Integer > xyz = cortex.slot ( XYZ, 999 );

    // Should return fallback for empty state
    assertThat ( emptyState.value ( xyz ) ).isEqualTo ( 999 );
  }

  /**
   * Edge Case: Compact Empty State
   */
  @Test
  void edgeCase_compactEmptyState () {
    State emptyState = cortex.state ();
    State compacted = emptyState.compact ();

    assertThat ( compacted.stream ().count () ).isZero ();
  }
}
