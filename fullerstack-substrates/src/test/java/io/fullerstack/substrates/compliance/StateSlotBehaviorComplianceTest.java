package io.fullerstack.substrates.compliance;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compliance tests for State and Slot interfaces verifying documented behaviors from Substrates.java {@snippet} examples.
 * <p>
 * **Documented Behaviors Being Verified:**
 * <ul>
 *   <li>**State Creation**: Empty state and builder pattern</li>
 *   <li>**Slot Types**: All primitive and reference types</li>
 *   <li>**Immutability**: State.state() returns new instance</li>
 *   <li>**Value Retrieval**: Accessing values by slot</li>
 *   <li>**State Compaction**: Removing duplicates, keeping latest</li>
 *   <li>**Enum Slots**: Special enum slot behavior</li>
 * </ul>
 */
class StateSlotBehaviorComplianceTest {

  /**
   * **Documented Behavior**: State creation and immutability
   * <p>
   * From {@snippet}:
   * <pre>
   *   State empty = cortex.state();
   *   State withSlot = empty.state(name, value);
   *   // empty != withSlot (immutable)
   * </pre>
   */
  @Test
  void testStateImmutability_StateMethodReturnsNewInstance() {
    State empty = cortex().state();
    State withSlot = empty.state(cortex().name("key"), "value");

    // Verify: Different instances (immutable)
    assertThat(withSlot).isNotSameAs(empty);
  }

  /**
   * **Documented Behavior**: Value retrieval using slot
   * <p>
   * From {@snippet}:
   * <pre>
   *   Slot<Integer> slot = cortex.slot(name, 42);
   *   State state = cortex.state().state(name, 42);
   *   Integer value = state.value(slot);  // → 42
   * </pre>
   */
  @Test
  void testValueRetrieval_UsingSlot() {
    Name key = cortex().name("count");
    State state = cortex().state().state(key, 42);

    Slot<Integer> slot = cortex().slot(key, 0);  // Default value 0
    Integer value = state.value(slot);

    // Verify: Correct value retrieved
    assertThat(value).isEqualTo(42);
  }

  /**
   * **Documented Behavior**: Multiple state updates
   * <p>
   * From {@snippet}:
   * <pre>
   *   State state = cortex.state()
   *     .state(name1, value1)
   *     .state(name2, value2)
   *     .state(name3, value3);
   * </pre>
   */
  @Test
  void testMultipleStateUpdates_ChainCorrectly() {
    State state = cortex().state()
      .state(cortex().name("a"), 1)
      .state(cortex().name("b"), 2)
      .state(cortex().name("c"), 3);

    // Verify: All values present
    assertThat(state.value(cortex().slot(cortex().name("a"), 0))).isEqualTo(1);
    assertThat(state.value(cortex().slot(cortex().name("b"), 0))).isEqualTo(2);
    assertThat(state.value(cortex().slot(cortex().name("c"), 0))).isEqualTo(3);
  }

  /**
   * **Documented Behavior**: State compaction removes duplicates
   * <p>
   * From {@snippet}:
   * <pre>
   *   State state = cortex.state()
   *     .state(name, 1)
   *     .state(name, 2)
   *     .state(name, 3);
   *   State compact = state.compact();
   *   // compact has only latest value (3)
   * </pre>
   */
  @Test
  void testStateCompaction_KeepsLatestValue() {
    Name key = cortex().name("counter");

    State state = cortex().state()
      .state(key, 1)
      .state(key, 2)
      .state(key, 3);

    State compact = state.compact();

    // Verify: Only latest value (3) retained
    assertThat(compact.value(cortex().slot(key, 0))).isEqualTo(3);
  }

  /**
   * **Documented Behavior**: Slot fallback values
   * <p>
   * From {@snippet}:
   * <pre>
   *   Slot<Integer> slot = cortex.slot(name, 99);  // fallback = 99
   *   State empty = cortex.state();
   *   Integer value = empty.value(slot);  // → 99 (fallback)
   * </pre>
   */
  @Test
  void testSlotFallback_UsedWhenValueNotInState() {
    Slot<Integer> slot = cortex().slot(cortex().name("missing"), 99);
    State empty = cortex().state();

    Integer value = empty.value(slot);

    // Verify: Fallback value returned
    assertThat(value).isEqualTo(99);
  }

  /**
   * **Documented Behavior**: All primitive slot types
   * <p>
   * From {@snippet}:
   * <pre>
   *   cortex.slot(name, true);     // Boolean
   *   cortex.slot(name, 42);       // Integer
   *   cortex.slot(name, 42L);      // Long
   *   cortex.slot(name, 3.14);     // Double
   *   cortex.slot(name, 3.14f);    // Float
   *   cortex.slot(name, "text");   // String
   * </pre>
   */
  @Test
  void testAllSlotTypes_PrimitiveAndReference() {
    State state = cortex().state()
      .state(cortex().name("bool"), true)
      .state(cortex().name("int"), 42)
      .state(cortex().name("long"), 42L)
      .state(cortex().name("double"), 3.14)
      .state(cortex().name("float"), 3.14f)
      .state(cortex().name("string"), "text");

    // Verify: All types work
    assertThat(state.value(cortex().slot(cortex().name("bool"), false))).isTrue();
    assertThat(state.value(cortex().slot(cortex().name("int"), 0))).isEqualTo(42);
    assertThat(state.value(cortex().slot(cortex().name("long"), 0L))).isEqualTo(42L);
    assertThat(state.value(cortex().slot(cortex().name("double"), 0.0))).isEqualTo(3.14);
    assertThat(state.value(cortex().slot(cortex().name("float"), 0.0f))).isEqualTo(3.14f);
    assertThat(state.value(cortex().slot(cortex().name("string"), ""))).isEqualTo("text");
  }

  /**
   * **Documented Behavior**: Enum slots have special behavior
   * <p>
   * From {@snippet}:
   * <pre>
   *   Slot<Name> slot = cortex.slot(Status.ACTIVE);
   *   // Slot name = enum class name
   *   // Slot value = enum fully qualified name
   * </pre>
   */
  @Test
  void testEnumSlot_SpecialBehavior() {
    enum Status { ACTIVE, INACTIVE }

    Slot<Name> slot = cortex().slot(Status.ACTIVE);

    // Verify: Slot name is enum class
    assertThat(slot.name().toString()).contains("Status");

    // Verify: Slot value is enum name
    Name enumValue = slot.value();
    assertThat(enumValue.toString()).contains("ACTIVE");
  }

  /**
   * **Documented Behavior**: State streaming and iteration
   * <p>
   * From {@snippet}:
   * <pre>
   *   State state = cortex.state()...;
   *   state.stream().forEach(slot -> ...);
   *   state.iterator().forEachRemaining(slot -> ...);
   * </pre>
   */
  @Test
  void testStateIteration_StreamAndIterator() {
    State state = cortex().state()
      .state(cortex().name("a"), 1)
      .state(cortex().name("b"), 2)
      .state(cortex().name("c"), 3);

    // Verify: Stream works
    long streamCount = state.stream().count();
    assertThat(streamCount).isEqualTo(3);

    // Verify: Iterator works
    int iteratorCount = 0;
    var iterator = state.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      iteratorCount++;
    }
    assertThat(iteratorCount).isEqualTo(3);
  }

  /**
   * **Documented Behavior**: Name slot type
   * <p>
   * From {@snippet}:
   * <pre>
   *   Slot<Name> nameSlot = cortex.slot(cortex.name("key"), cortex.name("default"));
   *   State state = cortex.state().state(cortex.name("key"), cortex.name("value"));
   *   Name value = state.value(nameSlot);  // → "value"
   * </pre>
   */
  @Test
  void testNameSlotType() {
    Name key = cortex().name("config");
    Name defaultValue = cortex().name("default");
    Name actualValue = cortex().name("production");

    Slot<Name> slot = cortex().slot(key, defaultValue);
    State state = cortex().state().state(key, actualValue);

    Name value = state.value(slot);

    // Verify: Name value retrieved correctly
    assertThat(value.toString()).isEqualTo("production");
  }

  /**
   * **Documented Behavior**: State slot type (nested state)
   * <p>
   * From {@snippet}:
   * <pre>
   *   Slot<State> stateSlot = cortex.slot(name, cortex.state());
   *   State nested = cortex.state().state(name, value);
   *   State parent = cortex.state().state(name, nested);
   * </pre>
   */
  @Test
  void testStateSlotType_NestedState() {
    Name key = cortex().name("nested");

    State nestedState = cortex().state()
      .state(cortex().name("inner"), 42);

    State parentState = cortex().state()
      .state(key, nestedState);

    Slot<State> slot = cortex().slot(key, cortex().state());
    State retrievedNested = parentState.value(slot);

    // Verify: Nested state retrieved correctly
    assertThat(retrievedNested.value(cortex().slot(cortex().name("inner"), 0))).isEqualTo(42);
  }
}
