package io.fullerstack.substrates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Structural test for the marker-class invariant documented in
/// {@code FsCircuit.java}. Each marker class must have {@link Object} as its
/// direct superclass — adding a shared base reintroduces a bimorphic profile
/// on the dispatch hot path and regresses
/// {@code PipeOps.async_emit_batch_await} from ~22 ns to ~30+ ns.
@DisplayName ( "FsCircuit marker classes must remain structurally distinct" )
class FsCircuitMarkerInvariantTest {

  @Test
  @DisplayName ( "AwaitMarker has Object as direct superclass" )
  void awaitMarkerIsFlat () {
    assertSame ( Object.class, FsCircuit.AwaitMarker.class.getSuperclass () );
  }

  @Test
  @DisplayName ( "CloseMarker has Object as direct superclass" )
  void closeMarkerIsFlat () {
    assertSame ( Object.class, FsCircuit.CloseMarker.class.getSuperclass () );
  }

  @Test
  @DisplayName ( "CircuitJob has Object as direct superclass" )
  void circuitJobIsFlat () {
    assertSame ( Object.class, FsCircuit.CircuitJob.class.getSuperclass () );
  }

  @Test
  @DisplayName ( "ReceptorAdapter has Object as direct superclass" )
  void receptorAdapterIsFlat () {
    assertSame ( Object.class, FsCircuit.ReceptorAdapter.class.getSuperclass () );
  }

  @Test
  @DisplayName ( "marker classes are distinct types — no merging" )
  void markersAreDistinct () {
    Class < ? >[] markers = {
      FsCircuit.AwaitMarker.class,
      FsCircuit.CloseMarker.class,
      FsCircuit.CircuitJob.class,
      FsCircuit.ReceptorAdapter.class
    };
    assertEquals ( 4, java.util.Set.of ( markers ).size (),
      "marker classes must be distinct — merging any two reintroduces a bimorphic profile on the dispatch hot path" );
  }
}
