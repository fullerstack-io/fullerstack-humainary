package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Pipe;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FsCellSmokeTest {

  @Test
  void cellAcceptsEmitsAndPublishesViaVolatile() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit(cortex.name("smoke"));
    try {
      Cell<Double> cell = circuit.cell(42.0);
      assertThat(cell.get()).isEqualTo(42.0);   // seeded

      cell.pipe().emit(99.0);
      circuit.await();
      assertThat(cell.get()).isEqualTo(99.0);   // updated after circuit drain
    } finally {
      circuit.close();
    }
  }

  @Test
  void cellLazyPerSubjectInsideCascade_holonPattern() {
    // Mimics our holon pattern: cellFor(n) is called inside outputFn of a
    // per-subject subscriber callback, creating a Cell lazily for each new
    // subject. The receiver then emits to that cell's pipe.
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit(cortex.name("smoke3"));
    try {
      java.util.Map<io.humainary.substrates.api.Substrates.Name, Cell<Integer>> cells = new java.util.concurrent.ConcurrentHashMap<>();
      Conduit<Integer> in = circuit.conduit(cortex.name("input"), Integer.class);
      in.subscribe(circuit.subscriber(
          cortex.name("scan-like"),
          (subj, reg) -> {
            final io.humainary.substrates.api.Substrates.Name n = subj.name();
            final Cell<Integer> c = cells.computeIfAbsent(n, k -> circuit.cell(0));
            final Pipe<Integer> out = c.pipe();
            reg.register((Integer v) -> out.emit(v * 10));
          }));

      in.get(cortex.name("X")).emit(5);
      in.get(cortex.name("X")).emit(7);
      circuit.await();

      assertThat(cells.get(cortex.name("X"))).isNotNull();
      assertThat(cells.get(cortex.name("X")).get()).isEqualTo(70);
    } finally {
      circuit.close();
    }
  }

  @Test
  void cellCreatedInsideCascadeStillReceivesEmits() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit(cortex.name("smoke2"));
    try {
      // Mimic the backtest pattern: a subscriber on conduit creates a Cell
      // lazily on first emission, captures its pipe, then emits to it.
      Conduit<Double> in = circuit.conduit(cortex.name("input"), Double.class);
      AtomicReference<Cell<String>> cellRef = new AtomicReference<>();
      in.subscribe(circuit.subscriber(
          cortex.name("dispatcher"),
          (subj, reg) -> {
            // First time this subject is registered: create the cell + capture pipe.
            Cell<String> created = circuit.cell("init");
            cellRef.set(created);
            Pipe<String> out = created.pipe();
            reg.register((Double v) -> out.emit("got " + v));
          }));

      // Drive an emit through the input. The subscriber callback creates the cell
      // and emits into it — cell must end up with "got 1.0".
      in.get(cortex.name("subj-x")).emit(1.0);
      circuit.await();

      assertThat(cellRef.get()).isNotNull();
      assertThat(cellRef.get().get()).isEqualTo("got 1.0");
    } finally {
      circuit.close();
    }
  }
}
