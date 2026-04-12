package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Receptor;

/// Tests for the @New annotation contract.
///
/// @New indicates a method allocates a new instance on each call.
/// @New(conditional=true) indicates a method MAY return an existing instance.
///
/// Key annotated methods:
/// - Cortex.circuit(), flow(), scope(), slot(), state() — always new
/// - Circuit.conduit(), pipe(Receptor), subscriber() — always new
/// - Circuit.pipe(Pipe) — conditional (may reuse)

@DisplayName ( "@New Annotation Contract Tests" )
class NewAnnotationContractTest {

  @Nested
  @DisplayName ( "Cortex @New factories" )
  class CortexNew {

    @Test
    @DisplayName ( "cortex.circuit() returns distinct instances" )
    void circuit_alwaysNew () {
      Cortex cortex = cortex ();
      var a = cortex.circuit ();
      var b = cortex.circuit ();
      try {
        assertNotSame ( a, b, "@New: each circuit() call must return a new instance" );
      } finally {
        a.close ();
        b.close ();
      }
    }

    @Test
    @DisplayName ( "cortex.circuit(name) returns distinct instances for same name" )
    void circuitNamed_alwaysNew () {
      Cortex cortex = cortex ();
      var name = cortex.name ( "test" );
      var a = cortex.circuit ( name );
      var b = cortex.circuit ( name );
      try {
        assertNotSame ( a, b, "@New: same name still creates distinct circuits" );
      } finally {
        a.close ();
        b.close ();
      }
    }

    @Test
    @DisplayName ( "cortex.scope() returns distinct instances" )
    void scope_alwaysNew () {
      Cortex cortex = cortex ();
      var a = cortex.scope ();
      var b = cortex.scope ();
      try {
        assertNotSame ( a, b, "@New: each scope() call must return a new instance" );
      } finally {
        a.close ();
        b.close ();
      }
    }

    @Test
    @DisplayName ( "cortex.state() returns distinct instances" )
    void state_alwaysNew () {
      Cortex cortex = cortex ();
      var a = cortex.state ();
      var b = cortex.state ();
      assertNotSame ( a, b, "@New: each state() call must return a new instance" );
    }

    @Test
    @DisplayName ( "cortex.flow() returns distinct instances" )
    void flow_alwaysNew () {
      Cortex cortex = cortex ();
      var a = cortex.flow ( Integer.class );
      var b = cortex.flow ( Integer.class );
      assertNotSame ( a, b, "@New: each flow() call must return a new instance" );
    }

    @Test
    @DisplayName ( "cortex.slot() returns distinct instances" )
    void slot_alwaysNew () {
      Cortex cortex = cortex ();
      var name = cortex.name ( "s" );
      var a = cortex.slot ( name, 0 );
      var b = cortex.slot ( name, 0 );
      assertNotSame ( a, b, "@New: each slot() call must return a new instance" );
    }
  }

  @Nested
  @DisplayName ( "Circuit @New factories" )
  class CircuitNew {

    @Test
    @DisplayName ( "circuit.conduit(Class) returns distinct instances" )
    void conduit_alwaysNew () {
      var circuit = cortex ().circuit ();
      try {
        var a = circuit.conduit ( Integer.class );
        var b = circuit.conduit ( Integer.class );
        assertNotSame ( a, b, "@New: each conduit() call must return a new instance" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.pipe(Receptor) returns distinct instances" )
    void pipeReceptor_alwaysNew () {
      var circuit = cortex ().circuit ();
      try {
        Receptor < Integer > noop = v -> {};
        var a = circuit.pipe ( noop );
        var b = circuit.pipe ( noop );
        assertNotSame ( a, b, "@New: each pipe(Receptor) call must return a new instance" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.subscriber() returns distinct instances" )
    void subscriber_alwaysNew () {
      var circuit = cortex ().circuit ();
      try {
        var name = cortex ().name ( "sub" );
        var a = circuit.subscriber ( name, ( s, r ) -> {} );
        var b = circuit.subscriber ( name, ( s, r ) -> {} );
        assertNotSame ( a, b, "@New: each subscriber() call must return a new instance" );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "Pipe @New" )
  class PipeNew {

    @Test
    @DisplayName ( "pipe.pipe(flow) returns distinct instances" )
    void pipeFlow_alwaysNew () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        var base = conduit.get ( cortex.name ( "p" ) );
        var flow = cortex.flow ( Integer.class ).guard ( v -> v > 0 );
        var a = base.pipe ( flow );
        var b = base.pipe ( flow );
        assertNotSame ( a, b, "@New: pipe.pipe(flow) must return new instances" );
      } finally {
        circuit.close ();
      }
    }
  }
}
