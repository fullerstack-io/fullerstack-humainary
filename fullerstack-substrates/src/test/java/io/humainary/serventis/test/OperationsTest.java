package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Operations;
import io.humainary.serventis.sdk.Operations.Sign;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Operations instrument: BEGIN/END action bracketing.

@DisplayName ( "Serventis Operations" )
class OperationsTest {

  @Test
  @DisplayName ( "Sign enum has BEGIN and END constants with non-empty names" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly ( Sign.BEGIN, Sign.END );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "operation.begin() / operation.end() emit BEGIN and END signs in order" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "ops.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var operation = Operations.of ( conduit.get ( cortex.name ( "op1" ) ) );
      operation.begin ();
      operation.end ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.BEGIN, Sign.END );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "operation.sign(Sign) emits the given sign" )
  void signMethodEmits () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "ops.sign" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var operation = Operations.of ( conduit.get ( cortex.name ( "op2" ) ) );
      operation.sign ( Sign.BEGIN );
      operation.sign ( Sign.END );
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.BEGIN, Sign.END );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Operations.pool(conduit) returns a non-null Pool" )
  void poolFactoryReturnsPool () {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Operations.pool ( conduit );
      assertThat ( pool ).isNotNull ();
      assertThat ( pool.get ( cortex.name ( "any" ) ) ).isNotNull ();
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "pool.get(sameName) emits through the same underlying pipe" )
  void poolSameNameSamePipe () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Operations.pool ( conduit );
      final var name = cortex.name ( "op.shared" );

      final List < Sign > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "ops.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var op1 = pool.get ( name );
      final var op2 = pool.get ( name );
      op1.begin ();
      op2.end ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.BEGIN, Sign.END );
    } finally {
      circuit.close ();
    }
  }

}
