package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Receptor;

/// Tests for the @Tenure annotation contract.
///
/// @Tenure documents instance retention semantics:
/// - INTERNED: pooled/cached — same key returns same instance
/// - EPHEMERAL: not retained — each creation is independent
/// - ANCHORED: lifetime tied to parent context
///
/// INTERNED types: Cortex, Current, Id, Name
/// EPHEMERAL types: Circuit, Conduit, Closure, Capture, Reservoir, Tap
/// ANCHORED types: Pipe, Scope, Slot, State, Subject, Subscriber, Subscription

@DisplayName ( "@Tenure Annotation Contract Tests" )
class TenureAnnotationContractTest {

  // ================================================================
  // INTERNED — same key must return same instance
  // ================================================================

  @Nested
  @DisplayName ( "@Tenure(INTERNED)" )
  class Interned {

    @Test
    @DisplayName ( "Cortex is a singleton" )
    void cortex_singleton () {
      assertSame ( cortex (), cortex (),
        "Cortex is INTERNED: cortex() must return the same instance" );
    }

    @Test
    @DisplayName ( "Name is interned by path" )
    void name_internedByPath () {
      Cortex cortex = cortex ();
      assertSame ( cortex.name ( "a.b.c" ), cortex.name ( "a.b.c" ),
        "Name is INTERNED: same path must return same instance" );
    }

    @Test
    @DisplayName ( "Name interning across construction methods" )
    void name_internedAcrossMethods () {
      Cortex cortex = cortex ();
      var parsed = cortex.name ( "x.y" );
      var chained = cortex.name ( "x" ).name ( "y" );
      assertSame ( parsed, chained,
        "Name is INTERNED: same logical name must be same instance" );
    }

    @Test
    @DisplayName ( "Current is interned per thread" )
    void current_internedPerThread () {
      Cortex cortex = cortex ();
      assertSame ( cortex.current (), cortex.current (),
        "Current is INTERNED: same thread must return same instance" );
    }
  }

  // ================================================================
  // EPHEMERAL — each creation is independent, not retained
  // ================================================================

  @Nested
  @DisplayName ( "@Tenure(EPHEMERAL)" )
  class Ephemeral {

    @Test
    @DisplayName ( "Circuit is ephemeral — each creation is distinct" )
    void circuit_ephemeral () {
      Cortex cortex = cortex ();
      var a = cortex.circuit ();
      var b = cortex.circuit ();
      try {
        assertNotSame ( a, b, "Circuit is EPHEMERAL: each call creates a new instance" );
      } finally {
        a.close ();
        b.close ();
      }
    }

    @Test
    @DisplayName ( "Circuit with same name is still ephemeral" )
    void circuit_sameName_ephemeral () {
      Cortex cortex = cortex ();
      var name = cortex.name ( "same" );
      var a = cortex.circuit ( name );
      var b = cortex.circuit ( name );
      try {
        assertNotSame ( a, b, "Circuit is EPHEMERAL: same name doesn't mean same instance" );
      } finally {
        a.close ();
        b.close ();
      }
    }

    @Test
    @DisplayName ( "Conduit is ephemeral — each creation is distinct" )
    void conduit_ephemeral () {
      var circuit = cortex ().circuit ();
      try {
        var a = circuit.conduit ( Integer.class );
        var b = circuit.conduit ( Integer.class );
        assertNotSame ( a, b, "Conduit is EPHEMERAL" );
      } finally {
        circuit.close ();
      }
    }
  }

  // ================================================================
  // ANCHORED — lifetime tied to parent context
  // ================================================================

  @Nested
  @DisplayName ( "@Tenure(ANCHORED)" )
  class Anchored {

    @Test
    @DisplayName ( "Pipe is anchored to conduit — same name returns same pipe" )
    void pipe_anchoredToConduit () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        var name = cortex.name ( "p" );
        var a = conduit.get ( name );
        var b = conduit.get ( name );
        assertSame ( a, b,
          "Pipe is ANCHORED: same name on same conduit must return same pipe" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "Pipe from different conduits are distinct" )
    void pipe_differentConduits_distinct () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var c1 = circuit.conduit ( Integer.class );
        var c2 = circuit.conduit ( Integer.class );
        var name = cortex.name ( "p" );
        assertNotSame ( c1.get ( name ), c2.get ( name ),
          "Pipes from different conduits must be distinct" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "State is anchored — chained state builds on parent" )
    void state_anchored () {
      Cortex cortex = cortex ();
      var base = cortex.state ();
      var name = cortex.name ( "k" );
      var extended = base.state ( name, 1 );
      assertNotSame ( base, extended,
        "State chain creates new anchored state" );
      assertEquals ( 1, extended.value ( cortex.slot ( name, 0 ) ) );
    }

    @Test
    @DisplayName ( "Subscription is anchored to conduit lifecycle" )
    void subscription_anchored () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        var sub = conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( (Receptor < Integer >) v -> {} )
          )
        );
        assertNotNull ( sub, "Subscription must be non-null while anchored" );
        assertNotNull ( sub.subject (), "Subscription subject must be non-null" );
      } finally {
        circuit.close ();
      }
    }
  }
}
