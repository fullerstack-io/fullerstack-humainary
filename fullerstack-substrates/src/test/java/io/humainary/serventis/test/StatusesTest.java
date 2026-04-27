package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Statuses;
import io.humainary.serventis.sdk.Statuses.Dimension;
import io.humainary.serventis.sdk.Statuses.Sign;
import io.humainary.serventis.sdk.Statuses.Signal;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Statuses instrument: 7 operational signs by
/// TENTATIVE/MEASURED/CONFIRMED confidence dimension.

@DisplayName ( "Serventis Statuses" )
class StatusesTest {

  @Test
  @DisplayName ( "Sign enum lists all 7 operational states" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly (
      Sign.CONVERGING, Sign.STABLE, Sign.DIVERGING, Sign.ERRATIC,
      Sign.DEGRADED,   Sign.DEFECTIVE, Sign.DOWN
    );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "Dimension is an ordered Spectrum: TENTATIVE < MEASURED < CONFIRMED" )
  void dimensionSpectrumOrdered () {
    assertThat ( Dimension.values () ).containsExactly (
      Dimension.TENTATIVE, Dimension.MEASURED, Dimension.CONFIRMED
    );
    assertThat ( Dimension.TENTATIVE.ordinal () ).isLessThan ( Dimension.MEASURED.ordinal () );
    assertThat ( Dimension.MEASURED.ordinal () ).isLessThan ( Dimension.CONFIRMED.ordinal () );
  }

  @Test
  @DisplayName ( "every semantic method emits a Signal with matching sign and dimension" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final List < Signal > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "status.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var status = Statuses.of ( conduit.get ( cortex.name ( "status1" ) ) );
      status.converging ( Dimension.TENTATIVE );
      status.stable     ( Dimension.MEASURED );
      status.diverging  ( Dimension.CONFIRMED );
      status.erratic    ( Dimension.MEASURED );
      status.degraded   ( Dimension.MEASURED );
      status.defective  ( Dimension.CONFIRMED );
      status.down       ( Dimension.CONFIRMED );
      status.signal     ( Sign.STABLE, Dimension.CONFIRMED );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.CONVERGING, Dimension.TENTATIVE ),
        new Signal ( Sign.STABLE,     Dimension.MEASURED ),
        new Signal ( Sign.DIVERGING,  Dimension.CONFIRMED ),
        new Signal ( Sign.ERRATIC,    Dimension.MEASURED ),
        new Signal ( Sign.DEGRADED,   Dimension.MEASURED ),
        new Signal ( Sign.DEFECTIVE,  Dimension.CONFIRMED ),
        new Signal ( Sign.DOWN,       Dimension.CONFIRMED ),
        new Signal ( Sign.STABLE,     Dimension.CONFIRMED )
      );

      for ( final var s : captured ) {
        assertThat ( s.sign () ).isNotNull ();
        assertThat ( s.dimension () ).isNotNull ();
      }
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Statuses.pool(conduit) returns a working pool" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final var pool = Statuses.pool ( conduit );
      assertThat ( pool ).isNotNull ();

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "status.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "status.pool" ) ).stable ( Dimension.CONFIRMED );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.STABLE, Dimension.CONFIRMED )
      );
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
      final var conduit = circuit.conduit ( Signal.class );
      final var pool = Statuses.pool ( conduit );
      final var name = cortex.name ( "status.shared" );

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "status.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).degraded ( Dimension.TENTATIVE );
      pool.get ( name ).down     ( Dimension.CONFIRMED );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.DEGRADED, Dimension.TENTATIVE ),
        new Signal ( Sign.DOWN,     Dimension.CONFIRMED )
      );
    } finally {
      circuit.close ();
    }
  }

}
