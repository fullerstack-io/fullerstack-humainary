package io.humainary.serventis.test;

import io.humainary.serventis.sdk.SignalSet;
import io.humainary.serventis.sdk.Statuses;
import io.humainary.serventis.sdk.Systems;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests for the SignalSet utility — a Sign x Dimension Cartesian lookup table
/// used internally by the two-dimensional instruments.

@DisplayName ( "Serventis SignalSet" )
class SignalSetTest {

  @Test
  @DisplayName ( "Factory creates a Signal from sign and dimension" )
  void factoryCreatesSignal () {
    final SignalSet.Factory < Statuses.Sign, Statuses.Dimension, Statuses.Signal > factory =
      Statuses.Signal::new;

    final var signal = factory.create ( Statuses.Sign.STABLE, Statuses.Dimension.CONFIRMED );
    assertThat ( signal ).isNotNull ();
    assertThat ( signal.sign () ).isEqualTo ( Statuses.Sign.STABLE );
    assertThat ( signal.dimension () ).isEqualTo ( Statuses.Dimension.CONFIRMED );
  }

  @Test
  @DisplayName ( "SignalSet pre-allocates every Sign x Dimension combination for Statuses" )
  void preallocatesAllStatusesCombinations () {
    final var signals =
      new SignalSet < Statuses.Sign, Statuses.Dimension, Statuses.Signal > (
        Statuses.Sign.class,
        Statuses.Dimension.class,
        Statuses.Signal::new
      );

    for ( final var sign : Statuses.Sign.values () ) {
      for ( final var dim : Statuses.Dimension.values () ) {
        final var signal = signals.get ( sign, dim );
        assertThat ( signal ).isNotNull ();
        assertThat ( signal.sign () ).isEqualTo ( sign );
        assertThat ( signal.dimension () ).isEqualTo ( dim );
      }
    }
  }

  @Test
  @DisplayName ( "SignalSet.get returns the same instance for the same key (zero-allocation lookup)" )
  void getReturnsCachedInstance () {
    final var signals =
      new SignalSet < Statuses.Sign, Statuses.Dimension, Statuses.Signal > (
        Statuses.Sign.class,
        Statuses.Dimension.class,
        Statuses.Signal::new
      );

    final var first  = signals.get ( Statuses.Sign.DEGRADED, Statuses.Dimension.MEASURED );
    final var second = signals.get ( Statuses.Sign.DEGRADED, Statuses.Dimension.MEASURED );

    // get() is a pure indexed lookup into a pre-allocated array — same instance every time
    assertThat ( second ).isSameAs ( first );
  }

  @Test
  @DisplayName ( "SignalSet works for Systems (4 signs x 4 dimensions = 16 combinations)" )
  void worksForSystems () {
    final var signals =
      new SignalSet < Systems.Sign, Systems.Dimension, Systems.Signal > (
        Systems.Sign.class,
        Systems.Dimension.class,
        Systems.Signal::new
      );

    int count = 0;
    for ( final var sign : Systems.Sign.values () ) {
      for ( final var dim : Systems.Dimension.values () ) {
        final var signal = signals.get ( sign, dim );
        assertThat ( signal.sign () ).isEqualTo ( sign );
        assertThat ( signal.dimension () ).isEqualTo ( dim );
        count++;
      }
    }
    assertThat ( count ).isEqualTo ( 16 );
  }

}
