package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Situations;
import io.humainary.serventis.sdk.Situations.Dimension;
import io.humainary.serventis.sdk.Situations.Sign;
import io.humainary.serventis.sdk.Situations.Signal;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Situations instrument: NORMAL/WARNING/CRITICAL by
/// CONSTANT/VARIABLE/VOLATILE variability.

@DisplayName ( "Serventis Situations" )
class SituationsTest {

  @Test
  @DisplayName ( "Sign enum lists NORMAL, WARNING, CRITICAL" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly ( Sign.NORMAL, Sign.WARNING, Sign.CRITICAL );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "Dimension is a Spectrum with CONSTANT < VARIABLE < VOLATILE ordinals" )
  void dimensionSpectrumOrdered () {
    assertThat ( Dimension.values () ).containsExactly (
      Dimension.CONSTANT, Dimension.VARIABLE, Dimension.VOLATILE
    );
    assertThat ( Dimension.CONSTANT.ordinal () ).isLessThan ( Dimension.VARIABLE.ordinal () );
    assertThat ( Dimension.VARIABLE.ordinal () ).isLessThan ( Dimension.VOLATILE.ordinal () );
  }

  @Test
  @DisplayName ( "every semantic method emits a Signal carrying matching sign and dimension" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final List < Signal > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "situ.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var situation = Situations.of ( conduit.get ( cortex.name ( "situ1" ) ) );
      situation.normal ( Dimension.CONSTANT );
      situation.warning ( Dimension.VARIABLE );
      situation.critical ( Dimension.VOLATILE );
      situation.signal ( Sign.WARNING, Dimension.CONSTANT );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.NORMAL,   Dimension.CONSTANT ),
        new Signal ( Sign.WARNING,  Dimension.VARIABLE ),
        new Signal ( Sign.CRITICAL, Dimension.VOLATILE ),
        new Signal ( Sign.WARNING,  Dimension.CONSTANT )
      );

      // Each Signal carries both sign() and dimension()
      for ( final var s : captured ) {
        assertThat ( s.sign () ).isNotNull ();
        assertThat ( s.dimension () ).isNotNull ();
      }
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Situations.pool(conduit) returns a non-null Pool that produces working instruments" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final var pool = Situations.pool ( conduit );
      assertThat ( pool ).isNotNull ();

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "situ.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "situ.pool" ) ).critical ( Dimension.VOLATILE );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.CRITICAL, Dimension.VOLATILE )
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
      final var pool = Situations.pool ( conduit );
      final var name = cortex.name ( "situ.shared" );

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "situ.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).normal ( Dimension.CONSTANT );
      pool.get ( name ).warning ( Dimension.VARIABLE );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.NORMAL,  Dimension.CONSTANT ),
        new Signal ( Sign.WARNING, Dimension.VARIABLE )
      );
    } finally {
      circuit.close ();
    }
  }

}
