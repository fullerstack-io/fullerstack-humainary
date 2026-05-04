package io.humainary.serventis.test;

import io.humainary.serventis.api.Serventis;
import io.humainary.serventis.sdk.Systems;
import io.humainary.serventis.sdk.Systems.Dimension;
import io.humainary.serventis.sdk.Systems.Sign;
import io.humainary.serventis.sdk.Systems.Signal;
import io.humainary.substrates.api.Substrates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Systems instrument: NORMAL/LIMIT/ALARM/FAULT by
/// SPACE/FLOW/LINK/TIME constraint type.

@DisplayName ( "Serventis Systems" )
class SystemsTest {

  @Test
  @DisplayName ( "Sign enum lists NORMAL, LIMIT, ALARM, FAULT" )
  void signEnumExhaustive () {
    final var values = Sign.values ();
    assertThat ( values ).containsExactly (
      Sign.NORMAL, Sign.LIMIT, Sign.ALARM, Sign.FAULT
    );
    for ( final var sign : values ) {
      assertThat ( sign.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "Dimension is a Category (unordered) listing SPACE/FLOW/LINK/TIME" )
  void dimensionIsCategory () {
    assertThat ( Dimension.values () ).containsExactly (
      Dimension.SPACE, Dimension.FLOW, Dimension.LINK, Dimension.TIME
    );
    // All Systems dimensions are Category (unordered) — verify the type
    for ( final var dim : Dimension.values () ) {
      assertThat ( dim ).isInstanceOf ( Serventis.Category.class );
      assertThat ( dim.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "every semantic method emits Signal carrying matching sign and dimension" )
  void singleInstrumentEmission () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final List < Signal > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "system.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var system = Systems.of ( conduit.get ( cortex.name ( "sys1" ) ) );
      system.normal ( Dimension.SPACE );
      system.limit  ( Dimension.FLOW );
      system.alarm  ( Dimension.LINK );
      system.fault  ( Dimension.TIME );
      system.signal ( Sign.NORMAL, Dimension.TIME );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.NORMAL, Dimension.SPACE ),
        new Signal ( Sign.LIMIT,  Dimension.FLOW ),
        new Signal ( Sign.ALARM,  Dimension.LINK ),
        new Signal ( Sign.FAULT,  Dimension.TIME ),
        new Signal ( Sign.NORMAL, Dimension.TIME )
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
  @DisplayName ( "Systems.pool(conduit) returns a working pool" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final var conduit = circuit.conduit ( Signal.class );
      final var pool = Systems.pool ( conduit );
      assertThat ( pool ).isNotNull ();

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "system.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "sys.pool" ) ).alarm ( Dimension.SPACE );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.ALARM, Dimension.SPACE )
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
      final var pool = Systems.pool ( conduit );
      final var name = cortex.name ( "sys.shared" );

      final List < Signal > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "system.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).fault  ( Dimension.LINK );
      pool.get ( name ).normal ( Dimension.SPACE );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal ( Sign.FAULT,  Dimension.LINK ),
        new Signal ( Sign.NORMAL, Dimension.SPACE )
      );
    } finally {
      circuit.close ();
    }
  }

}
