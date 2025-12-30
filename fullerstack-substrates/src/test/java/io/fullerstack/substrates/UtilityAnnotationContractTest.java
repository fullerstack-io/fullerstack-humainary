package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Slot;

/**
 * Tests to verify the @Utility annotation contract from Substrates API.
 *
 * <p>The @Utility annotation indicates a type or method that serves a utility purpose. This means:
 *
 * <ul>
 *   <li>The type provides helper functionality (factory methods, conversions, etc.)
 *   <li>Static utility methods on interfaces
 *   <li>Types that are primarily containers for utility functions
 * </ul>
 *
 * <p>@Utility annotated elements in Substrates API include:
 *
 * <ul>
 *   <li>CortexProvider - SPI utility for loading Cortex implementation
 *   <li>Composer.pipe() methods - Factory methods for creating Composer instances
 *   <li>Receptor.of() methods - Factory methods for creating Receptor instances
 *   <li>Slot interface - Utility type for key-value pairs
 *   <li>Closure interface - Utility for lazy resource creation
 * </ul>
 */
@DisplayName ( "@Utility Annotation Contract Tests" )
class UtilityAnnotationContractTest {

  private Circuit circuit;

  @BeforeEach
  void setUp () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );
  }

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  // ============================================================
  // Composer.pipe() @Utility methods
  // ============================================================

  @Nested
  @DisplayName ( "Composer.pipe() @Utility methods" )
  class ComposerPipeUtility {

    @Test
    @DisplayName ( "Composer.pipe() creates valid Composer" )
    void composerPipe_createsValidComposer () {
      var composer = Composer. < String > pipe ();
      assertNotNull ( composer, "Composer.pipe() must return a valid Composer" );
    }

    @Test
    @DisplayName ( "Composer.pipe() can be used to create Conduit" )
    void composerPipe_canCreateConduit () {
      Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), Composer.pipe () );

      assertNotNull ( conduit, "Conduit created with Composer.pipe() must not be null" );
      assertNotNull ( conduit.subject (), "Conduit must have a subject" );
    }

    @Test
    @DisplayName ( "Composer.pipe(configurer) creates Composer with flow configuration" )
    void composerPipeConfigurer_createsComposerWithConfig () {
      var composer = Composer. < Integer > pipe ( flow -> flow.diff ().limit ( 100 ) );
      assertNotNull ( composer, "Composer.pipe(configurer) must return a valid Composer" );

      Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), composer );
      assertNotNull ( conduit, "Conduit created with configured Composer must not be null" );
    }

    @Test
    @DisplayName ( "Composer.pipe(type) creates typed Composer" )
    void composerPipeType_createsTypedComposer () {
      var composer = Composer.pipe ( Integer.class );
      assertNotNull ( composer, "Composer.pipe(type) must return a valid Composer" );

      Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), composer );
      assertNotNull ( conduit, "Conduit created with typed Composer must not be null" );
    }

    @Test
    @DisplayName ( "Composer.pipe() works with different element types" )
    void composerPipe_worksWithDifferentTypes () {
      // String
      Conduit < Pipe < String >, String > stringConduit =
        circuit.conduit ( cortex ().name ( "strings" ), Composer.pipe () );
      assertNotNull ( stringConduit );

      // Integer
      Conduit < Pipe < Integer >, Integer > intConduit =
        circuit.conduit ( cortex ().name ( "integers" ), Composer.pipe () );
      assertNotNull ( intConduit );

      // Custom record type
      record Event( String name, int value ) { }
      Conduit < Pipe < Event >, Event > eventConduit =
        circuit.conduit ( cortex ().name ( "events" ), Composer.pipe () );
      assertNotNull ( eventConduit );
    }
  }

  // ============================================================
  // Receptor.of() @Utility methods
  // ============================================================

  @Nested
  @DisplayName ( "Receptor.of() @Utility methods" )
  class ReceptorOfUtility {

    @Test
    @DisplayName ( "Receptor.of(type, receptor) creates typed Receptor" )
    void receptorOf_createsTypedReceptor () {
      Receptor < Integer > receptor = Receptor.of ( Integer.class, v -> {
      } );
      assertNotNull ( receptor, "Receptor.of(type, receptor) must return a valid Receptor" );
    }

    @Test
    @DisplayName ( "Receptor.of(type) creates no-op Receptor" )
    void receptorOfType_createsNoOpReceptor () {
      Receptor < Integer > receptor = Receptor.of ( Integer.class );
      assertNotNull ( receptor, "Receptor.of(type) must return a valid Receptor" );

      // Should not throw when receiving values
      assertDoesNotThrow ( () -> receptor.receive ( 42 ), "No-op receptor must accept values" );
    }

    @Test
    @DisplayName ( "Receptor.of() creates generic no-op Receptor" )
    void receptorOf_createsGenericNoOpReceptor () {
      Receptor < Object > receptor = Receptor.of ();
      assertNotNull ( receptor, "Receptor.of() must return a valid Receptor" );

      // Should not throw when receiving any value
      assertDoesNotThrow ( () -> receptor.receive ( "string" ), "No-op receptor must accept values" );
      assertDoesNotThrow ( () -> receptor.receive ( 42 ), "No-op receptor must accept values" );
    }

    @Test
    @DisplayName ( "Receptor utility methods can be used with circuit.pipe()" )
    void receptor_canBeUsedWithCircuitPipe () {
      Receptor < Integer > receptor = Receptor.of ( Integer.class, v -> {
      } );
      Pipe < Integer > pipe = circuit.pipe ( receptor );
      assertNotNull ( pipe, "Pipe created with utility Receptor must not be null" );
    }
  }

  // ============================================================
  // Slot @Utility type
  // ============================================================

  @Nested
  @DisplayName ( "Slot @Utility type" )
  class SlotUtility {

    @Test
    @DisplayName ( "Slot contains name, value, and type" )
    void slot_containsNameValueType () {
      Name slotName = cortex ().name ( "key" );
      Slot < Integer > slot = cortex ().slot ( slotName, 42 );

      assertNotNull ( slot.name (), "Slot must have name" );
      assertNotNull ( slot.value (), "Slot must have value" );
      assertNotNull ( slot.type (), "Slot must have type" );

      assertEquals ( slotName, slot.name (), "Slot name must match" );
      assertEquals ( 42, slot.value (), "Slot value must match" );
      assertEquals ( int.class, slot.type (), "Slot type must be primitive int" );
    }

    @Test
    @DisplayName ( "Slot works with primitive types" )
    void slot_worksWithPrimitiveTypes () {
      Name name = cortex ().name ( "test" );

      Slot < Integer > intSlot = cortex ().slot ( name, 42 );
      assertEquals ( int.class, intSlot.type () );

      Slot < Long > longSlot = cortex ().slot ( name, 42L );
      assertEquals ( long.class, longSlot.type () );

      Slot < Double > doubleSlot = cortex ().slot ( name, 3.14 );
      assertEquals ( double.class, doubleSlot.type () );

      Slot < Float > floatSlot = cortex ().slot ( name, 3.14f );
      assertEquals ( float.class, floatSlot.type () );

      Slot < Boolean > boolSlot = cortex ().slot ( name, true );
      assertEquals ( boolean.class, boolSlot.type () );
    }

    @Test
    @DisplayName ( "Slot works with object types" )
    void slot_worksWithObjectTypes () {
      Name name = cortex ().name ( "test" );

      Slot < String > stringSlot = cortex ().slot ( name, "value" );
      assertEquals ( String.class, stringSlot.type () );

      Slot < Name > nameSlot = cortex ().slot ( name, cortex ().name ( "value" ) );
      assertEquals ( Name.class, nameSlot.type () );
    }

    @Test
    @DisplayName ( "Slot can be used with State" )
    void slot_canBeUsedWithState () {
      Name name = cortex ().name ( "key" );
      Slot < Integer > slot = cortex ().slot ( name, 42 );

      var state = cortex ().state ().state ( slot );
      assertNotNull ( state, "State must accept Slot" );

      // Value should be retrievable
      Integer value = state.value ( slot );
      assertEquals ( 42, value, "State value must match Slot value" );
    }

    @Test
    @DisplayName ( "Slot from enum creates Name-typed slot" )
    void slot_fromEnumCreatesNameSlot () {
      Slot < Name > enumSlot = cortex ().slot ( TestEnum.VALUE_ONE );

      assertNotNull ( enumSlot, "Enum slot must not be null" );
      assertNotNull ( enumSlot.name (), "Enum slot must have name" );
      assertEquals ( Name.class, enumSlot.type (), "Enum slot type must be Name" );
    }
  }

  // ============================================================
  // CortexProvider @Utility (implicit through cortex())
  // ============================================================

  @Nested
  @DisplayName ( "CortexProvider @Utility" )
  class CortexProviderUtility {

    @Test
    @DisplayName ( "cortex() uses CortexProvider SPI to load implementation" )
    void cortex_usesCortexProviderSPI () {
      // The fact that cortex() returns a working Cortex proves
      // that CortexProvider loaded our FsCortexProvider implementation
      var cortex = cortex ();
      assertNotNull ( cortex, "CortexProvider must load Cortex implementation" );

      // Create and verify a circuit to ensure full functionality
      Circuit c = cortex.circuit ( cortex.name ( "spi-test" ) );
      try {
        assertNotNull ( c.subject (), "Loaded Cortex must create functional circuits" );
      } finally {
        c.close ();
      }
    }

    @Test
    @DisplayName ( "CortexProvider loads singleton Cortex" )
    void cortexProvider_loadsSingletonCortex () {
      var cortex1 = cortex ();
      var cortex2 = cortex ();
      assertSame ( cortex1, cortex2, "CortexProvider must return singleton Cortex" );
    }
  }

  // Test enum for enum-based slot tests
  enum TestEnum {
    VALUE_ONE,
    VALUE_TWO
  }
}
