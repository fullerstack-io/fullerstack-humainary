package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Cortex;

/// Tests for the @NotNull annotation contract.
///
/// @NotNull parameters must reject null with NullPointerException.
/// @NotNull return types must never return null.
///
/// Tests cover key factory methods and emission paths.

@DisplayName ( "@NotNull Annotation Contract Tests" )
class NotNullAnnotationContractTest {

  @Nested
  @DisplayName ( "Cortex @NotNull parameters" )
  class CortexNotNull {

    @Test
    @DisplayName ( "cortex.name(null) throws NPE" )
    void name_nullThrows () {
      assertThrows ( NullPointerException.class,
        () -> cortex ().name ( (String) null ) );
    }

    @Test
    @DisplayName ( "cortex.circuit(null) throws NPE" )
    void circuit_nullThrows () {
      assertThrows ( NullPointerException.class,
        () -> cortex ().circuit ( null ) );
    }

    @Test
    @DisplayName ( "cortex.scope(null) throws NPE" )
    void scope_nullThrows () {
      assertThrows ( NullPointerException.class,
        () -> cortex ().scope ( null ) );
    }
  }

  @Nested
  @DisplayName ( "Circuit @NotNull parameters" )
  class CircuitNotNull {

    @Test
    @DisplayName ( "circuit.conduit(null, class) throws NPE" )
    void conduit_nullName () {
      var circuit = cortex ().circuit ();
      try {
        assertThrows ( NullPointerException.class,
          () -> circuit.conduit ( null, Integer.class ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.conduit(name, null) throws NPE" )
    void conduit_nullClass () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        assertThrows ( NullPointerException.class,
          () -> circuit.conduit ( cortex.name ( "c" ), null ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.pipe(null Receptor) throws NPE" )
    void pipe_nullReceptor () {
      var circuit = cortex ().circuit ();
      try {
        assertThrows ( NullPointerException.class,
          () -> circuit.pipe ( (io.humainary.substrates.api.Substrates.Receptor < ? >) null ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.subscriber(null, callback) throws NPE" )
    void subscriber_nullName () {
      var circuit = cortex ().circuit ();
      try {
        assertThrows ( NullPointerException.class,
          () -> circuit.subscriber ( null, ( s, r ) -> {} ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.subscriber(name, null) throws NPE" )
    void subscriber_nullCallback () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        assertThrows ( NullPointerException.class,
          () -> circuit.subscriber ( cortex.name ( "s" ), null ) );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "Pipe @NotNull parameters" )
  class PipeNotNull {

    @Test
    @DisplayName ( "pipe.emit(null) throws NPE" )
    void emit_nullThrows () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        var pipe = conduit.get ( cortex.name ( "p" ) );
        assertThrows ( NullPointerException.class, () -> pipe.emit ( null ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "fiber.pipe(null) throws NPE" )
    void fiberPipe_nullThrows () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var fiber = cortex.fiber ( Integer.class );
        assertThrows ( NullPointerException.class, () -> fiber.pipe ( null ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.pipe(null) throws NPE" )
    void flowPipe_nullThrows () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var flow = cortex.flow ( Integer.class );
        assertThrows ( NullPointerException.class, () -> flow.pipe ( null ) );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "@NotNull return values" )
  class ReturnNotNull {

    @Test
    @DisplayName ( "cortex() never returns null" )
    void cortex_notNull () {
      assertNotNull ( cortex () );
    }

    @Test
    @DisplayName ( "cortex.circuit() never returns null" )
    void circuit_notNull () {
      var c = cortex ().circuit ();
      try {
        assertNotNull ( c );
      } finally {
        c.close ();
      }
    }

    @Test
    @DisplayName ( "cortex.name() never returns null" )
    void name_notNull () {
      assertNotNull ( cortex ().name ( "test" ) );
    }

    @Test
    @DisplayName ( "cortex.state() never returns null" )
    void state_notNull () {
      assertNotNull ( cortex ().state () );
    }

    @Test
    @DisplayName ( "cortex.current() never returns null" )
    void current_notNull () {
      assertNotNull ( cortex ().current () );
    }

    @Test
    @DisplayName ( "cortex.flow() never returns null" )
    void flow_notNull () {
      assertNotNull ( cortex ().flow ( Integer.class ) );
    }

    @Test
    @DisplayName ( "circuit.subject() never returns null" )
    void circuitSubject_notNull () {
      var c = cortex ().circuit ();
      try {
        assertNotNull ( c.subject () );
      } finally {
        c.close ();
      }
    }
  }
}
