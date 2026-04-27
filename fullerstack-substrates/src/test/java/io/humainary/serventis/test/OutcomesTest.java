package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Outcomes.Sign;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Outcomes instrument: SUCCESS/FAIL binary verdict.

@DisplayName ( "Serventis Outcomes" )
class OutcomesTest {

  @Test
  @DisplayName ( "Sign enum has SUCCESS and FAIL constants with non-empty names" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly ( Sign.SUCCESS, Sign.FAIL );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "outcome.success() / outcome.fail() emit SUCCESS and FAIL in order" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "outcome.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var outcome = Outcomes.of ( conduit.get ( cortex.name ( "out1" ) ) );
      outcome.success ();
      outcome.fail ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.SUCCESS, Sign.FAIL );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "outcome.sign(Sign) emits the given sign" )
  void signMethodEmits () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "outcome.sign" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var outcome = Outcomes.of ( conduit.get ( cortex.name ( "out2" ) ) );
      outcome.sign ( Sign.FAIL );
      outcome.sign ( Sign.SUCCESS );
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.FAIL, Sign.SUCCESS );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Outcomes.pool(conduit) returns working pool" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Outcomes.pool ( conduit );
      assertThat ( pool ).isNotNull ();

      final List < Sign > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "outcome.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "out.pool" ) ).success ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.SUCCESS );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "pool.get(sameName) shares emission identity" )
  void poolSameNameSharedPipe () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Outcomes.pool ( conduit );
      final var name = cortex.name ( "out.shared" );

      final List < Sign > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "outcome.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).success ();
      pool.get ( name ).fail ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.SUCCESS, Sign.FAIL );
    } finally {
      circuit.close ();
    }
  }

}
