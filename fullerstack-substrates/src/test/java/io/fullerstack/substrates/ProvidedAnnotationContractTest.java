package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.*;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.Slot;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify the @Provided annotation contract from Substrates API.
 *
 * <p>The @Provided annotation indicates a type that is exclusively provided by the runtime. This
 * means:
 *
 * <ul>
 *   <li>Users cannot directly implement these interfaces
 *   <li>Instances are created only through factory methods (cortex(), circuit(), etc.)
 *   <li>The runtime (SPI provider) is responsible for all implementations
 * </ul>
 *
 * <p>@Provided types in Substrates API include:
 *
 * <ul>
 *   <li>Cortex - Entry point provided by SPI
 *   <li>Circuit - Event orchestration hub
 *   <li>Conduit - Emission routing container
 *   <li>Channel - Named emission port
 *   <li>Cell - Hierarchical computation unit
 *   <li>Name - Hierarchical identifier
 *   <li>Subject - Substrate identity
 *   <li>State - Immutable slot collection
 *   <li>Scope - Resource lifecycle manager
 *   <li>Flow - Transformation pipeline
 *   <li>Sift - Filtering configuration
 *   <li>Closure - Resource factory
 *   <li>Capture - Emission capture
 *   <li>Current - Thread-local context
 *   <li>Subscriber, Subscription, Reservoir, etc.
 * </ul>
 *
 * <p>These tests verify that @Provided types:
 *
 * <ul>
 *   <li>Are properly instantiated through factory methods
 *   <li>Have correct type relationships
 *   <li>Work as expected without user implementation
 * </ul>
 */
@DisplayName ( "@Provided Annotation Contract Tests" )
class ProvidedAnnotationContractTest {

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
  // Cortex @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Cortex @Provided" )
  class CortexProvided {

    @Test
    @DisplayName ( "cortex() returns a Cortex implementation" )
    void cortex_returnsCortexImplementation () {
      Cortex c = cortex ();
      assertNotNull ( c );
      assertTrue ( c instanceof Cortex, "cortex() must return a Cortex implementation" );
    }

    @Test
    @DisplayName ( "cortex() returns same instance (singleton)" )
    void cortex_returnsSameInstance () {
      Cortex c1 = cortex ();
      Cortex c2 = cortex ();
      assertSame ( c1, c2, "cortex() should return same singleton instance" );
    }

    @Test
    @DisplayName ( "Cortex provides all required factory methods" )
    void cortex_providesFactoryMethods () {
      Cortex c = cortex ();

      // Test all factory methods return proper types
      assertNotNull ( c.circuit (), "Cortex must provide circuit()" );
      assertNotNull ( c.scope (), "Cortex must provide scope()" );
      assertNotNull ( c.state (), "Cortex must provide state()" );
      assertNotNull ( c.current (), "Cortex must provide current()" );
      assertNotNull ( c.name ( "test" ), "Cortex must provide name()" );
      assertNotNull ( c.slot ( c.name ( "slot" ), 42 ), "Cortex must provide slot()" );

      // Cleanup
      c.circuit ().close ();
      c.scope ().close ();
    }
  }

  // ============================================================
  // Circuit @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Circuit @Provided" )
  class CircuitProvided {

    @Test
    @DisplayName ( "circuit is a Circuit implementation" )
    void circuit_isCircuitImplementation () {
      assertTrue ( circuit instanceof Circuit, "Must be a Circuit implementation" );
    }

    @Test
    @DisplayName ( "Circuit provides all required methods" )
    void circuit_providesRequiredMethods () {
      assertNotNull ( circuit.subject (), "Circuit must provide subject()" );
      assertNotNull ( circuit.reservoir (), "Circuit must provide reservoir()" );
    }
  }

  // ============================================================
  // Conduit @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Conduit @Provided" )
  class ConduitProvided {

    @Test
    @DisplayName ( "circuit.conduit() returns Conduit implementation" )
    void conduit_isConduitImplementation () {
      Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), Composer.pipe () );
      assertTrue ( conduit instanceof Conduit, "Must be a Conduit implementation" );
    }

    @Test
    @DisplayName ( "Conduit provides all required methods" )
    void conduit_providesRequiredMethods () {
      Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), Composer.pipe () );

      assertNotNull ( conduit.subject (), "Conduit must provide subject()" );
      assertNotNull ( conduit.percept ( cortex ().name ( "pipe" ) ), "Conduit must provide percept()" );
    }
  }

  // ============================================================
  // Name @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Name @Provided" )
  class NameProvided {

    @Test
    @DisplayName ( "cortex().name() returns Name implementation" )
    void name_isNameImplementation () {
      Name name = cortex ().name ( "test" );
      assertTrue ( name instanceof Name, "Must be a Name implementation" );
    }

    @Test
    @DisplayName ( "Name provides all required methods" )
    void name_providesRequiredMethods () {
      Name name = cortex ().name ( "parent" );

      // Extension methods
      assertNotNull ( name.name ( cortex ().name ( "child" ) ), "Name must provide name(Name)" );
      assertNotNull ( name.name ( "child" ), "Name must provide name(String)" );
    }
  }

  // ============================================================
  // Subject @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Subject @Provided" )
  class SubjectProvided {

    @Test
    @DisplayName ( "circuit.subject() returns Subject implementation" )
    void subject_isSubjectImplementation () {
      Subject < Circuit > subject = circuit.subject ();
      assertTrue ( subject instanceof Subject, "Must be a Subject implementation" );
    }

    @Test
    @DisplayName ( "Subject provides all required methods" )
    void subject_providesRequiredMethods () {
      Subject < Circuit > subject = circuit.subject ();

      assertNotNull ( subject.id (), "Subject must provide id()" );
      assertNotNull ( subject.name (), "Subject must provide name()" );
      assertNotNull ( subject.state (), "Subject must provide state()" );
      assertNotNull ( subject.type (), "Subject must provide type()" );
    }
  }

  // ============================================================
  // State @Provided
  // ============================================================

  @Nested
  @DisplayName ( "State @Provided" )
  class StateProvided {

    @Test
    @DisplayName ( "cortex().state() returns State implementation" )
    void state_isStateImplementation () {
      State state = cortex ().state ();
      assertTrue ( state instanceof State, "Must be a State implementation" );
    }

    @Test
    @DisplayName ( "State provides all required methods" )
    void state_providesRequiredMethods () {
      State state = cortex ().state ();
      Name key = cortex ().name ( "key" );

      // Extension methods return State
      assertNotNull ( state.state ( key, 42 ), "State must provide state(Name, int)" );
      assertNotNull ( state.state ( key, 42L ), "State must provide state(Name, long)" );
      assertNotNull ( state.state ( key, 3.14f ), "State must provide state(Name, float)" );
      assertNotNull ( state.state ( key, 3.14 ), "State must provide state(Name, double)" );
      assertNotNull ( state.state ( key, true ), "State must provide state(Name, boolean)" );
      assertNotNull ( state.state ( key, "value" ), "State must provide state(Name, String)" );
      assertNotNull ( state.state ( key, cortex ().name ( "value" ) ), "State must provide state(Name, Name)" );
      assertNotNull ( state.compact (), "State must provide compact()" );
    }
  }

  // ============================================================
  // Scope @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Scope @Provided" )
  class ScopeProvided {

    @Test
    @DisplayName ( "cortex().scope() returns Scope implementation" )
    void scope_isScopeImplementation () {
      Scope scope = cortex ().scope ();
      try {
        assertTrue ( scope instanceof Scope, "Must be a Scope implementation" );
      } finally {
        scope.close ();
      }
    }

    @Test
    @DisplayName ( "Scope provides all required methods" )
    void scope_providesRequiredMethods () {
      Scope scope = cortex ().scope ( cortex ().name ( "scope" ) );
      try {
        assertNotNull ( scope.subject (), "Scope must provide subject()" );

        // Test register returns the same resource
        Circuit c = cortex ().circuit ( cortex ().name ( "nested" ) );
        Circuit registered = scope.register ( c );
        assertSame ( c, registered, "register() must return the same resource" );
      } finally {
        scope.close ();
      }
    }
  }

  // ============================================================
  // Slot @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Slot @Provided" )
  class SlotProvided {

    @Test
    @DisplayName ( "cortex().slot() returns Slot implementation" )
    void slot_isSlotImplementation () {
      Slot < Integer > slot = cortex ().slot ( cortex ().name ( "test" ), 42 );
      assertTrue ( slot instanceof Slot, "Must be a Slot implementation" );
    }

    @Test
    @DisplayName ( "Slot provides all required methods" )
    void slot_providesRequiredMethods () {
      Slot < Integer > slot = cortex ().slot ( cortex ().name ( "test" ), 42 );

      assertNotNull ( slot.name (), "Slot must provide name()" );
      assertNotNull ( slot.value (), "Slot must provide value()" );
      assertNotNull ( slot.type (), "Slot must provide type()" );
    }
  }

  // ============================================================
  // Subscriber @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Subscriber @Provided" )
  class SubscriberProvided {

    @Test
    @DisplayName ( "circuit.subscriber() returns Subscriber implementation" )
    void subscriber_isSubscriberImplementation () {
      Subscriber < State > subscriber =
        circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
        } );
      assertTrue ( subscriber instanceof Subscriber, "Must be a Subscriber implementation" );
    }

    @Test
    @DisplayName ( "Subscriber provides all required methods" )
    void subscriber_providesRequiredMethods () {
      Subscriber < State > subscriber =
        circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
        } );

      assertNotNull ( subscriber.subject (), "Subscriber must provide subject()" );
    }
  }

  // ============================================================
  // Subscription @Provided
  // ============================================================

  @Nested
  @DisplayName ( "Subscription @Provided" )
  class SubscriptionProvided {

    @Test
    @DisplayName ( "circuit.subscribe() returns Subscription implementation" )
    void subscription_isSubscriptionImplementation () {
      Subscriber < State > subscriber =
        circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
        } );
      Subscription subscription = circuit.subscribe ( subscriber );
      try {
        assertTrue ( subscription instanceof Subscription, "Must be a Subscription implementation" );
      } finally {
        subscription.close ();
      }
    }

    @Test
    @DisplayName ( "Subscription provides all required methods" )
    void subscription_providesRequiredMethods () {
      Subscriber < State > subscriber =
        circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
        } );
      Subscription subscription = circuit.subscribe ( subscriber );
      try {
        assertNotNull ( subscription.subject (), "Subscription must provide subject()" );
        // close() is also required (tested in Idempotent tests)
      } finally {
        subscription.close ();
      }
    }
  }
}
