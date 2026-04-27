package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Trends;
import io.humainary.serventis.sdk.Trends.Sign;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Trends instrument: pattern detection (Nelson rules).

@DisplayName ( "Serventis Trends" )
class TrendsTest {

  @Test
  @DisplayName ( "Sign enum exposes STABLE/DRIFT/SPIKE/CYCLE/CHAOS with non-empty names" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly (
      Sign.STABLE, Sign.DRIFT, Sign.SPIKE, Sign.CYCLE, Sign.CHAOS
    );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "every semantic method emits the corresponding sign" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "trend.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var trend = Trends.of ( conduit.get ( cortex.name ( "trend1" ) ) );
      trend.stable ();
      trend.drift ();
      trend.spike ();
      trend.cycle ();
      trend.chaos ();
      circuit.await ();

      assertThat ( captured ).containsExactly (
        Sign.STABLE, Sign.DRIFT, Sign.SPIKE, Sign.CYCLE, Sign.CHAOS
      );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "trend.sign(Sign) emits the given sign" )
  void signMethodEmits () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final List < Sign > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "trend.sign" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var trend = Trends.of ( conduit.get ( cortex.name ( "trend2" ) ) );
      trend.sign ( Sign.SPIKE );
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.SPIKE );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Trends.pool(conduit) returns a working pool" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Trends.pool ( conduit );
      assertThat ( pool ).isNotNull ();

      final List < Sign > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "trend.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "trend.pool" ) ).chaos ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.CHAOS );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "pool.get(sameName) emits through the same pipe" )
  void poolSameNameShared () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Sign.class );
      final var pool = Trends.pool ( conduit );
      final var name = cortex.name ( "trend.shared" );

      final List < Sign > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "trend.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).stable ();
      pool.get ( name ).drift ();
      circuit.await ();

      assertThat ( captured ).containsExactly ( Sign.STABLE, Sign.DRIFT );
    } finally {
      circuit.close ();
    }
  }

}
