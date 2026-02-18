package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.*;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Id;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
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
 * Tests to verify the @Tenure annotation contract from Substrates API.
 *
 * <p>
 * The @Tenure annotation describes the lifecycle and caching policy of a type:
 *
 * <ul>
 * <li><b>INTERNED (1)</b>: Pooled by key. Same key returns same instance.
 * Creator maintains reference.
 * <li><b>EPHEMERAL (2)</b>: Not cached. Each creation is independent. Only
 * caller's reference keeps it alive.
 * <li><b>ANCHORED (3)</b>: Depends on attachment. Standalone = ephemeral;
 * attached = retained by parent substrate.
 * </ul>
 */
@DisplayName ( "@Tenure Annotation Contract Tests" )
class TenureAnnotationContractTest {

  private Circuit circuit;
  private Name    testName;

  @BeforeEach
  void setUp () {
    testName = cortex ().name ( "test" );
    circuit = cortex ().circuit ( testName );
  }

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  // ============================================================
  // INTERNED types — pooled by key, same key returns same instance
  // ============================================================

  @Nested
  @DisplayName ( "INTERNED types — pooled by key" )
  class InternedTypes {

    @Test
    @DisplayName ( "Name: same string returns same instance (interned)" )
    void name_sameString_returnsSameInstance () {
      Name name1 = cortex ().name ( "interned.test" );
      Name name2 = cortex ().name ( "interned.test" );

      assertSame ( name1, name2,
        "Name must be interned: same path returns same instance" );
    }

    @Test
    @DisplayName ( "Name: different strings return different instances" )
    void name_differentString_returnsDifferentInstance () {
      Name name1 = cortex ().name ( "path.one" );
      Name name2 = cortex ().name ( "path.two" );

      assertNotSame ( name1, name2,
        "Names with different paths must be different instances" );
    }

    @Test
    @DisplayName ( "Cortex: same SPI returns same instance (singleton)" )
    void cortex_returnsSameInstance () {
      var cortex1 = cortex ();
      var cortex2 = cortex ();

      assertSame ( cortex1, cortex2,
        "Cortex must be interned: same SPI returns same instance" );
    }

    @Test
    @DisplayName ( "Channel: same name in conduit returns same percept (interned)" )
    void channel_sameName_returnsSameInstance () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ),
        Composer.pipe ()
      );

      Name channelName = cortex ().name ( "channel" );
      Pipe < Integer > pipe1 = conduit.percept ( channelName );
      Pipe < Integer > pipe2 = conduit.percept ( channelName );

      assertSame ( pipe1, pipe2,
        "Channel must be interned: same name returns same percept" );
    }

    @Test
    @DisplayName ( "Channel: different names in conduit return different percepts" )
    void channel_differentName_returnsDifferentInstance () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ),
        Composer.pipe ()
      );

      Pipe < Integer > pipe1 = conduit.percept ( cortex ().name ( "channel-a" ) );
      Pipe < Integer > pipe2 = conduit.percept ( cortex ().name ( "channel-b" ) );

      assertNotSame ( pipe1, pipe2,
        "Channels with different names must be different instances" );
    }

    @Test
    @DisplayName ( "Id: same subject returns same Id (stable identity)" )
    void id_sameSubject_returnsSameInstance () {
      Subject < Circuit > subject = circuit.subject ();
      Id id1 = subject.id ();
      Id id2 = subject.id ();

      assertSame ( id1, id2,
        "Id must be interned: same subject returns same Id" );
    }

    @Test
    @DisplayName ( "Current: same thread returns same instance" )
    void current_sameThread_returnsSameInstance () {
      var current1 = cortex ().current ();
      var current2 = cortex ().current ();

      assertSame ( current1, current2,
        "Current must be interned: same thread returns same instance" );
    }
  }

  // ============================================================
  // EPHEMERAL types — not cached, each creation is independent
  // ============================================================

  @Nested
  @DisplayName ( "EPHEMERAL types — each creation is independent" )
  class EphemeralTypes {

    @Test
    @DisplayName ( "Circuit: each creation returns new instance" )
    void circuit_eachCreation_returnsNewInstance () {
      Circuit circuit1 = cortex ().circuit ( cortex ().name ( "a" ) );
      Circuit circuit2 = cortex ().circuit ( cortex ().name ( "a" ) );

      try {
        assertNotSame ( circuit1, circuit2,
          "Circuit must be ephemeral: each creation returns new instance" );
      } finally {
        circuit1.close ();
        circuit2.close ();
      }
    }

    @Test
    @DisplayName ( "Conduit: each creation returns new instance" )
    void conduit_eachCreation_returnsNewInstance () {
      Conduit < Pipe < Integer >, Integer > conduit1 = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );
      Conduit < Pipe < Integer >, Integer > conduit2 = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      assertNotSame ( conduit1, conduit2,
        "Conduit must be ephemeral: each creation returns new instance" );
    }

    @Test
    @DisplayName ( "Reservoir: each creation returns new instance" )
    void reservoir_eachCreation_returnsNewInstance () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      Reservoir < Integer > reservoir1 = conduit.reservoir ();
      Reservoir < Integer > reservoir2 = conduit.reservoir ();

      assertNotSame ( reservoir1, reservoir2,
        "Reservoir must be ephemeral: each creation returns new instance" );

      reservoir1.close ();
      reservoir2.close ();
    }

    @Test
    @DisplayName ( "Tap: each creation returns new instance" )
    void tap_eachCreation_returnsNewInstance () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      var tap1 = conduit.tap ( v -> v.toString () );
      var tap2 = conduit.tap ( v -> v.toString () );

      assertNotSame ( tap1, tap2,
        "Tap must be ephemeral: each creation returns new instance" );

      tap1.close ();
      tap2.close ();
    }

    @Test
    @DisplayName ( "Capture: drain produces independent capture instances" )
    void capture_eachDrain_returnsIndependentCaptures () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      Reservoir < Integer > reservoir = conduit.reservoir ();

      conduit.percept ( cortex ().name ( "ch" ) ).emit ( 1 );
      circuit.await ();

      var drain1 = reservoir.drain ().toList ();
      var drain2 = reservoir.drain ().toList ();

      // Each drain() returns a new Stream — captures are independent
      assertNotSame ( drain1, drain2,
        "Each drain() must return independent results" );

      reservoir.close ();
    }
  }

  // ============================================================
  // ANCHORED types — ephemeral standalone, retained when attached
  // ============================================================

  @Nested
  @DisplayName ( "ANCHORED types — depends on attachment" )
  class AnchoredTypes {

    @Test
    @DisplayName ( "Pipe: standalone creation returns new instance (ephemeral)" )
    void pipe_standalone_isEphemeral () {
      Pipe < Integer > pipe1 = circuit.pipe ( Receptor.of ( Integer.class ) );
      Pipe < Integer > pipe2 = circuit.pipe ( Receptor.of ( Integer.class ) );

      assertNotSame ( pipe1, pipe2,
        "Standalone Pipe must be ephemeral: each creation returns new instance" );
    }

    @Test
    @DisplayName ( "Pipe: via channel is retained (interned by conduit)" )
    void pipe_viaChannel_isRetainedByConduit () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      Name channelName = cortex ().name ( "retained" );
      Pipe < Integer > pipe1 = conduit.percept ( channelName );
      Pipe < Integer > pipe2 = conduit.percept ( channelName );

      assertSame ( pipe1, pipe2,
        "Pipe via channel must be retained: same name returns same instance" );
    }

    @Test
    @DisplayName ( "Subscriber: each creation returns new instance" )
    void subscriber_eachCreation_isEphemeral () {
      Name subName = cortex ().name ( "sub" );

      Subscriber < Integer > sub1 = circuit.subscriber (
        subName,
        ( subject, registrar ) -> registrar.register ( Receptor.of ( Integer.class ) )
      );

      Subscriber < Integer > sub2 = circuit.subscriber (
        subName,
        ( subject, registrar ) -> registrar.register ( Receptor.of ( Integer.class ) )
      );

      assertNotSame ( sub1, sub2,
        "Subscriber must be ephemeral when standalone" );
    }

    @Test
    @DisplayName ( "Subscription: each subscribe() returns new instance" )
    void subscription_eachSubscribe_isIndependent () {
      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ), Composer.pipe ()
      );

      Subscriber < Integer > subscriber = circuit.subscriber (
        cortex ().name ( "sub" ),
        ( subject, registrar ) -> registrar.register ( Receptor.of ( Integer.class ) )
      );

      Subscription sub1 = conduit.subscribe ( subscriber );
      Subscription sub2 = conduit.subscribe ( subscriber );

      assertNotSame ( sub1, sub2,
        "Each subscribe() must return a new Subscription instance" );
    }

    @Test
    @DisplayName ( "State: each mutation creates new instance" )
    void state_eachMutation_createsNewInstance () {
      State state = cortex ().state ();
      Name slotName = cortex ().name ( "value" );

      State state1 = state.state ( slotName, 1 );
      State state2 = state.state ( slotName, 2 );

      assertNotSame ( state, state1,
        "State mutation must create new instance" );
      assertNotSame ( state1, state2,
        "Each State mutation must create new instance" );
    }

    @Test
    @DisplayName ( "Slot: each creation returns new instance" )
    void slot_eachCreation_isIndependent () {
      Name slotName = cortex ().name ( "counter" );

      Slot < Integer > slot1 = cortex ().slot ( slotName, 42 );
      Slot < Integer > slot2 = cortex ().slot ( slotName, 42 );

      assertNotSame ( slot1, slot2,
        "Each Slot creation must return a new instance" );
    }

    @Test
    @DisplayName ( "Subject: anchored to substrate, retained by circuit" )
    void subject_anchoredToCircuit_retainedByCircuit () {
      Subject < Circuit > subject1 = circuit.subject ();
      Subject < Circuit > subject2 = circuit.subject ();

      assertSame ( subject1, subject2,
        "Subject must be retained by its substrate (circuit)" );
    }

    @Test
    @DisplayName ( "Scope: each creation returns new instance (ephemeral standalone)" )
    void scope_eachCreation_isIndependent () {
      try ( Scope scope1 = cortex ().scope (); Scope scope2 = cortex ().scope () ) {

        assertNotSame ( scope1, scope2,
          "Each Scope creation must return a new instance" );
      }
    }

    @Test
    @DisplayName ( "Cell: each creation returns new instance (ephemeral standalone)" )
    void cell_eachCreation_isIndependent () {
      var cell1 = circuit.cell (
        Composer.pipe (),
        Composer.pipe (),
        (Receptor < Integer >) value -> {}
      );

      var cell2 = circuit.cell (
        Composer.pipe (),
        Composer.pipe (),
        (Receptor < Integer >) value -> {}
      );

      assertNotSame ( cell1, cell2,
        "Each Cell creation must return a new instance" );
    }
  }

  // ============================================================
  // Cross-cutting: ANCHORED attachment transitions
  // ============================================================

  @Nested
  @DisplayName ( "ANCHORED attachment transitions" )
  class AnchoredTransitions {

    @Test
    @DisplayName ( "Cell: children are anchored by parent (lookup returns same)" )
    void cell_children_anchoredByParent () {
      var parent = circuit.cell (
        cortex ().name ( "parent" ),
        Composer.pipe (),
        Composer.pipe (),
        (Receptor < Integer >) value -> {}
      );

      Name childName = cortex ().name ( "child" );

      // Lookup by name should return the same child (anchored/retained)
      var child1 = parent.percept ( childName );
      var child2 = parent.percept ( childName );

      assertSame ( child1, child2,
        "Cell children must be anchored: same name returns same child" );
    }

    @Test
    @DisplayName ( "Subject: different circuits have different subjects" )
    void subject_differentCircuits_differentSubjects () {
      Circuit circuit2 = cortex ().circuit ( cortex ().name ( "other" ) );

      try {
        assertNotSame ( circuit.subject (), circuit2.subject (),
          "Different circuits must have different subjects" );
      } finally {
        circuit2.close ();
      }
    }
  }
}
