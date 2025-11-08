// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SlotTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  @Test
  void testBooleanSlotProperties () {

    final var slotName = cortex.name ( "slot.bool.test" );
    final var slot = cortex.slot ( slotName, true );

    assertEquals ( slotName, slot.name () );
    assertEquals ( boolean.class, slot.type () );
    assertTrue ( slot.value () );

  }

  @Test
  void testDifferentSlotsSameName () {

    final var sharedName = cortex.name ( "slot.shared.name" );

    final var intSlot = cortex.slot ( sharedName, 42 );
    final var stringSlot = cortex.slot ( sharedName, "test" );

    assertEquals ( sharedName, intSlot.name () );
    assertEquals ( sharedName, stringSlot.name () );

    assertEquals ( int.class, intSlot.type () );
    assertEquals ( String.class, stringSlot.type () );

    assertEquals ( 42, intSlot.value () );
    assertEquals ( "test", stringSlot.value () );

  }

  @Test
  void testDoubleSlotProperties () {

    final var slotName = cortex.name ( "slot.double.test" );
    final var slot = cortex.slot ( slotName, 2.718281828 );

    assertEquals ( slotName, slot.name () );
    assertEquals ( double.class, slot.type () );
    assertEquals ( 2.718281828, slot.value (), 0.00001 );

  }

  @Test
  void testEnumSlotAsTemplateWithFallback () {

    final var template = cortex.slot ( TestStatus.INACTIVE );
    final var emptyState = cortex.state ();

    assertEquals ( cortex.name ( TestStatus.INACTIVE ), emptyState.value ( template ) );

  }

  @Test
  void testEnumSlotAsTemplateWithOverride () {

    final var enumSlot = cortex.slot ( TestStatus.ACTIVE );

    final var state = cortex.state ()
      .state ( enumSlot.name (), cortex.name ( "OVERRIDDEN" ) );

    assertEquals ( cortex.name ( "OVERRIDDEN" ), state.value ( enumSlot ) );

  }

  @Test
  void testEnumSlotDifferentEnumsSameName () {

    final var status1 = cortex.slot ( TestStatus.ACTIVE );
    final var status2 = cortex.slot ( TestStatus.INACTIVE );

    assertEquals ( cortex.name ( TestStatus.ACTIVE ), status1.value () );
    assertEquals ( cortex.name ( TestStatus.INACTIVE ), status2.value () );

  }

  @Test
  void testEnumSlotInState () {

    final var statusSlot = cortex.slot ( TestStatus.ACTIVE );

    final var state = cortex.state ()
      .state ( statusSlot );

    assertEquals ( cortex.name ( TestStatus.ACTIVE ), state.value ( statusSlot ) );

  }

  @Test
  void testEnumSlotMultipleEnumsInState () {

    final var statusSlot = cortex.slot ( TestStatus.ACTIVE );
    final var prioritySlot = cortex.slot ( Priority.HIGH );

    final var state = cortex.state ()
      .state ( statusSlot )
      .state ( prioritySlot );

    assertEquals ( cortex.name ( TestStatus.ACTIVE ), state.value ( statusSlot ) );
    assertEquals ( cortex.name ( Priority.HIGH ), state.value ( prioritySlot ) );

  }

  @Test
  void testEnumSlotNameDerivedFromEnum () {

    final var slot = cortex.slot ( TestStatus.INACTIVE );
    final var expectedName = cortex.name ( TestStatus.INACTIVE.getDeclaringClass () );

    assertEquals ( expectedName, slot.name () );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testEnumSlotNullGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.slot ( null )
    );

  }

  @Test
  void testEnumSlotProperties () {

    final var slot = cortex.slot ( TestStatus.ACTIVE );

    assertNotNull ( slot.name () );
    assertEquals ( Name.class, slot.type () );
    assertEquals ( cortex.name ( TestStatus.ACTIVE ), slot.value () );

  }

  @Test
  void testEnumSlotTypeIsName () {

    final var slot = cortex.slot ( Priority.HIGH );

    assertEquals ( Name.class, slot.type () );
    assertFalse ( slot.type ().isPrimitive () );

  }

  @Test
  void testEnumSlotValueIsEnumName () {

    final var activeSlot = cortex.slot ( TestStatus.ACTIVE );
    final var pendingSlot = cortex.slot ( TestStatus.PENDING );

    assertEquals ( cortex.name ( TestStatus.ACTIVE ), activeSlot.value () );
    assertEquals ( cortex.name ( TestStatus.PENDING ), pendingSlot.value () );

  }

  @Test
  void testFloatSlotProperties () {

    final var slotName = cortex.name ( "slot.float.test" );
    final var slot = cortex.slot ( slotName, 3.14f );

    assertEquals ( slotName, slot.name () );
    assertEquals ( float.class, slot.type () );
    assertEquals ( 3.14f, slot.value (), 0.001f );

  }

  @Test
  void testIntSlotProperties () {

    final var slotName = cortex.name ( "slot.int.test" );
    final var slot = cortex.slot ( slotName, 42 );

    assertEquals ( slotName, slot.name () );
    assertEquals ( int.class, slot.type () );
    assertEquals ( 42, slot.value () );

  }

  @Test
  void testLongSlotProperties () {

    final var slotName = cortex.name ( "slot.long.test" );
    final var slot = cortex.slot ( slotName, 123456789L );

    assertEquals ( slotName, slot.name () );
    assertEquals ( long.class, slot.type () );
    assertEquals ( 123456789L, slot.value () );

  }

  @Test
  void testNameSlotProperties () {

    final var slotName = cortex.name ( "slot.name.test" );
    final var slotValue = cortex.name ( "slot.value.nested" );
    final var slot = cortex.slot ( slotName, slotValue );

    assertEquals ( slotName, slot.name () );
    assertEquals ( Name.class, slot.type () );
    assertEquals ( slotValue, slot.value () );

  }

  @Test
  void testSlotAsStateTemplate () {

    final var counterName = cortex.name ( "slot.template.counter" );
    final var template = cortex.slot ( counterName, 0 );

    final var state = cortex.state ()
      .state ( counterName, 10 )
      .state ( counterName, 20 );

    assertEquals ( 20, state.value ( template ) );

  }

  @Test
  void testSlotInStateMatching () {

    final var metricName = cortex.name ( "slot.matching.metric" );

    final var intSlot = cortex.slot ( metricName, 0 );
    final var longSlot = cortex.slot ( metricName, 0L );

    final var state = cortex.state ()
      .state ( metricName, 100 )
      .state ( metricName, 200L );

    assertEquals ( 100, state.value ( intSlot ) );
    assertEquals ( 200L, state.value ( longSlot ) );

  }

  @Test
  void testSlotNameIdentity () {

    final var name1 = cortex.name ( "slot.identity.test" );
    final var name2 = cortex.name ( "slot.identity.test" );
    final var nameFromParts = cortex.name ( List.of ( "slot", "identity", "test" ) );

    final var slot = cortex.slot ( name1, 42 );

    assertSame ( name1, name2 );
    assertSame ( name1, nameFromParts );
    assertSame ( name1, slot.name () );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSlotNullNameGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.slot ( null, 42 )
    );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSlotNullValueGuardForReferenceTypes () {

    final var slotName = cortex.name ( "slot.null.guard" );

    assertThrows (
      NullPointerException.class,
      () -> cortex.slot ( slotName, (String) null )
    );

    assertThrows (
      NullPointerException.class,
      () -> cortex.slot ( slotName, (Name) null )
    );

    assertThrows (
      NullPointerException.class,
      () -> cortex.slot ( slotName, (State) null )
    );

  }

  @Test
  void testSlotPrimitiveTypeReflection () {

    final var boolName = cortex.name ( "slot.primitive.bool" );
    final var intName = cortex.name ( "slot.primitive.int" );
    final var longName = cortex.name ( "slot.primitive.long" );
    final var floatName = cortex.name ( "slot.primitive.float" );
    final var doubleName = cortex.name ( "slot.primitive.double" );

    assertTrue ( cortex.slot ( boolName, true ).type ().isPrimitive () );
    assertTrue ( cortex.slot ( intName, 0 ).type ().isPrimitive () );
    assertTrue ( cortex.slot ( longName, 0L ).type ().isPrimitive () );
    assertTrue ( cortex.slot ( floatName, 0f ).type ().isPrimitive () );
    assertTrue ( cortex.slot ( doubleName, 0.0 ).type ().isPrimitive () );

  }

  @Test
  void testSlotReferenceTypeReflection () {

    final var stringName = cortex.name ( "slot.reference.string" );
    final var nameName = cortex.name ( "slot.reference.name" );
    final var stateName = cortex.name ( "slot.reference.state" );

    assertFalse ( cortex.slot ( stringName, "" ).type ().isPrimitive () );
    assertFalse ( cortex.slot ( nameName, cortex.name ( "test" ) ).type ().isPrimitive () );
    assertFalse ( cortex.slot ( stateName, cortex.state () ).type ().isPrimitive () );

  }

  @Test
  void testSlotTemplateForReferenceTypes () {

    final var nameSlot = cortex.name ( "slot.template.name" );
    final var stateSlot = cortex.name ( "slot.template.state" );

    final var storedName = cortex.name ( "slot.template.stored" );
    final var storedState = cortex.state ().state ( cortex.name ( "nested.template" ), 1 );

    final var state = cortex.state ()
      .state ( nameSlot, storedName )
      .state ( stateSlot, storedState );

    assertEquals (
      storedName,
      state.value ( cortex.slot ( nameSlot, cortex.name ( "fallback" ) ) )
    );

    assertEquals (
      storedState,
      state.value ( cortex.slot ( stateSlot, cortex.state () ) )
    );

  }

  @Test
  void testSlotTemplateWithFallback () {

    final var missingName = cortex.name ( "slot.template.missing" );
    final var template = cortex.slot ( missingName, 999 );

    final var emptyState = cortex.state ();

    assertEquals ( 999, emptyState.value ( template ) );

  }

  @Test
  void testSlotValueImmutability () {

    final var slotName = cortex.name ( "slot.immutable.value" );
    final var slot = cortex.slot ( slotName, 100 );

    assertEquals ( 100, slot.value () );
    assertEquals ( 100, slot.value () );
    assertEquals ( 100, slot.value () );

  }

  @Test
  void testSlotWithDifferentValues () {

    final var slotName = cortex.name ( "slot.different.values" );

    final var slot1 = cortex.slot ( slotName, 100 );
    final var slot2 = cortex.slot ( slotName, 200 );

    assertEquals ( slotName, slot1.name () );
    assertEquals ( slotName, slot2.name () );

    assertEquals ( 100, slot1.value () );
    assertEquals ( 200, slot2.value () );

  }

  @Test
  void testStateSlotProperties () {

    final var slotName = cortex.name ( "slot.state.test" );
    final var slotValue = cortex.state ().state ( cortex.name ( "nested" ), 99 );
    final var slot = cortex.slot ( slotName, slotValue );

    assertEquals ( slotName, slot.name () );
    assertEquals ( State.class, slot.type () );
    assertEquals ( slotValue, slot.value () );

  }

  @Test
  void testStringSlotProperties () {

    final var slotName = cortex.name ( "slot.string.test" );
    final var slot = cortex.slot ( slotName, "hello" );

    assertEquals ( slotName, slot.name () );
    assertEquals ( String.class, slot.type () );
    assertEquals ( "hello", slot.value () );

  }

  enum TestStatus {
    ACTIVE,
    INACTIVE,
    PENDING
  }

  enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

}
