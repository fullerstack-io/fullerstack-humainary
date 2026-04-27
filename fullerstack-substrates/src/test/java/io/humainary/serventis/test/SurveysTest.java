package io.humainary.serventis.test;

import io.humainary.serventis.sdk.Outcomes;
import io.humainary.serventis.sdk.Statuses;
import io.humainary.serventis.sdk.Surveys;
import io.humainary.serventis.sdk.Surveys.Dimension;
import io.humainary.serventis.sdk.Surveys.Signal;
import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Conduit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the Serventis Surveys instrument: collective assessment generic over
/// a foreign Sign type, qualified by DIVIDED/MAJORITY/UNANIMOUS agreement.

@DisplayName ( "Serventis Surveys" )
class SurveysTest {

  /// Conduit construction for a generic Signal<S> goes through a Class<?> cast,
  /// matching the pattern in the SurveyOps JMH benchmark — Java's erasure forces
  /// it because Signal.class loses the type parameter.

  @SuppressWarnings ( "unchecked" )
  private static < S extends Enum < S > & io.humainary.serventis.api.Serventis.Sign >
  Conduit < Signal < S > > signalConduit (
    final Substrates.Circuit circuit
  ) {
    return (Conduit < Signal < S > >) (Conduit < ? >) circuit.conduit ( Signal.class );
  }

  @Test
  @DisplayName ( "Dimension is an ordered Spectrum: DIVIDED < MAJORITY < UNANIMOUS" )
  void dimensionSpectrumOrdered () {
    assertThat ( Dimension.values () ).containsExactly (
      Dimension.DIVIDED, Dimension.MAJORITY, Dimension.UNANIMOUS
    );
    assertThat ( Dimension.DIVIDED.ordinal () ).isLessThan ( Dimension.MAJORITY.ordinal () );
    assertThat ( Dimension.MAJORITY.ordinal () ).isLessThan ( Dimension.UNANIMOUS.ordinal () );
    for ( final var d : Dimension.values () ) {
      assertThat ( d.name () ).isNotNull ().isNotEmpty ();
    }
  }

  @Test
  @DisplayName ( "survey.signal(sign, agreement) parameterised over Outcomes.Sign emits matching Signal" )
  void singleInstrumentEmissionOverOutcomes () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final Conduit < Signal < Outcomes.Sign > > conduit = signalConduit ( circuit );
      final List < Signal < Outcomes.Sign > > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "survey.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var survey =
        Surveys.of ( Outcomes.Sign.class, conduit.get ( cortex.name ( "survey1" ) ) );

      survey.signal ( Outcomes.Sign.SUCCESS, Dimension.UNANIMOUS );
      survey.signal ( Outcomes.Sign.FAIL,    Dimension.MAJORITY );
      survey.signal ( Outcomes.Sign.SUCCESS, Dimension.DIVIDED );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal <> ( Outcomes.Sign.SUCCESS, Dimension.UNANIMOUS ),
        new Signal <> ( Outcomes.Sign.FAIL,    Dimension.MAJORITY ),
        new Signal <> ( Outcomes.Sign.SUCCESS, Dimension.DIVIDED )
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
  @DisplayName ( "survey parameterised over Statuses.Sign emits a Statuses-flavoured Signal" )
  void singleInstrumentEmissionOverStatuses () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final Conduit < Signal < Statuses.Sign > > conduit = signalConduit ( circuit );
      final List < Signal < Statuses.Sign > > captured = new CopyOnWriteArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "survey.statuses.collect" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      final var survey =
        Surveys.of ( Statuses.Sign.class, conduit.get ( cortex.name ( "survey.statuses" ) ) );

      survey.signal ( Statuses.Sign.STABLE,    Dimension.UNANIMOUS );
      survey.signal ( Statuses.Sign.DEGRADED,  Dimension.MAJORITY );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal <> ( Statuses.Sign.STABLE,   Dimension.UNANIMOUS ),
        new Signal <> ( Statuses.Sign.DEGRADED, Dimension.MAJORITY )
      );
    } finally {
      circuit.close ();
    }
  }

  @Test
  @DisplayName ( "Surveys.pool(class, conduit) returns a working pool" )
  void poolFactory () throws Exception {
    final var cortex = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      final Conduit < Signal < Outcomes.Sign > > conduit = signalConduit ( circuit );
      final var pool = Surveys.pool ( Outcomes.Sign.class, conduit );
      assertThat ( pool ).isNotNull ();

      final List < Signal < Outcomes.Sign > > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "survey.pool.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( cortex.name ( "survey.pool" ) )
          .signal ( Outcomes.Sign.SUCCESS, Dimension.UNANIMOUS );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal <> ( Outcomes.Sign.SUCCESS, Dimension.UNANIMOUS )
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
      final Conduit < Signal < Outcomes.Sign > > conduit = signalConduit ( circuit );
      final var pool = Surveys.pool ( Outcomes.Sign.class, conduit );
      final var name = cortex.name ( "survey.shared" );

      final List < Signal < Outcomes.Sign > > captured = new CopyOnWriteArrayList <> ();
      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "survey.shared.sub" ),
          ( subject, registrar ) -> registrar.register ( captured::add )
        )
      );

      pool.get ( name ).signal ( Outcomes.Sign.SUCCESS, Dimension.MAJORITY );
      pool.get ( name ).signal ( Outcomes.Sign.FAIL,    Dimension.DIVIDED );
      circuit.await ();

      assertThat ( captured ).containsExactly (
        new Signal <> ( Outcomes.Sign.SUCCESS, Dimension.MAJORITY ),
        new Signal <> ( Outcomes.Sign.FAIL,    Dimension.DIVIDED )
      );
    } finally {
      circuit.close ();
    }
  }

}
