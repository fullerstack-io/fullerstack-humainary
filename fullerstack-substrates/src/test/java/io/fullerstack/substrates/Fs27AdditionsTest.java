package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests for the smaller 2.7 surface additions whose dedicated coverage
/// was missing from the per-feature test files. Covers:
///   - Circuit.pipe(Name, Receptor) — named receptor pipe
///   - Fiber.every(Duration)         — time-based gating
///   - Fiber.pipe(Cell)              — terminate Fiber into Cell
///   - Flow.pipe(Cell)               — terminate Flow into Cell
class Fs27AdditionsTest {

  // ── Circuit.pipe(Name, Receptor) ────────────────────────────────────────

  @Test
  @DisplayName("Circuit.pipe(Name, Receptor) — pipe subject reflects supplied name")
  void namedReceptorPipeReflectsName() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Name n = cortex.name("my.named.pipe");
      Pipe<Integer> pipe = circuit.pipe(n, (Receptor<Integer>) v -> { /* no-op */ });

      assertThat((Object) pipe.subject().name()).isEqualTo(n);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Circuit.pipe(Name, Receptor) — emissions reach the receptor")
  void namedReceptorPipeReceivesEmits() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      List<Integer> seen = new ArrayList<>();
      Pipe<Integer> pipe = circuit.pipe(
          cortex.name("test.sink"),
          (Receptor<Integer>) seen::add);

      pipe.emit(1);
      pipe.emit(2);
      pipe.emit(3);
      circuit.await();

      assertThat(seen).containsExactly(1, 2, 3);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Circuit.pipe(null, Receptor) and pipe(Name, null) throw NPE")
  void namedReceptorPipeNullArgs() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      assertThatThrownBy(() -> circuit.pipe(null, (Receptor<Integer>) v -> { }))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> circuit.pipe(cortex.name("x"), (Receptor<Integer>) null))
          .isInstanceOf(NullPointerException.class);
    } finally {
      circuit.close();
    }
  }

  // ── Fiber.every(Duration) ───────────────────────────────────────────────

  @Test
  @DisplayName("Fiber.every(Duration) — first emit always passes; subsequent gated")
  void everyDurationGates() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Fiber<Integer> fiber = cortex.<Integer>fiber(Integer.class).every(Duration.ofMillis(50));
      List<Integer> emitted = new ArrayList<>();
      Pipe<Integer> sink = circuit.pipe((Receptor<Integer>) emitted::add);
      Pipe<Integer> in = fiber.pipe(sink);

      // First always passes; the next two are within 50ms and should be gated.
      in.emit(1);
      in.emit(2);
      in.emit(3);
      circuit.await();
      assertThat(emitted).containsExactly(1);

      // Wait past the gate; next emit should pass.
      Thread.sleep(60);
      in.emit(4);
      circuit.await();
      assertThat(emitted).containsExactly(1, 4);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Fiber.every(Duration) — zero / negative duration throws")
  void everyDurationValidation() {
    Cortex cortex = Substrates.cortex();
    assertThatThrownBy(() -> cortex.<Integer>fiber(Integer.class).every(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cortex.<Integer>fiber(Integer.class).every(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cortex.<Integer>fiber(Integer.class).every((Duration) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Fiber.every(Duration) — state per materialization")
  void everyDurationStatePerMaterialization() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Fiber<Integer> fiber = cortex.<Integer>fiber(Integer.class).every(Duration.ofMillis(50));
      // Two independent materializations; each tracks its own lastEmitNanos.
      List<Integer> outA = new ArrayList<>();
      List<Integer> outB = new ArrayList<>();
      Pipe<Integer> inA = fiber.pipe(circuit.pipe((Receptor<Integer>) outA::add));
      Pipe<Integer> inB = fiber.pipe(circuit.pipe((Receptor<Integer>) outB::add));

      inA.emit(1);   // first to A — passes
      inB.emit(10);  // first to B — passes (independent of A)
      inA.emit(2);   // gated
      inB.emit(20);  // gated
      circuit.await();

      assertThat(outA).containsExactly(1);
      assertThat(outB).containsExactly(10);
    } finally {
      circuit.close();
    }
  }

  // ── Fiber.pipe(Cell) ────────────────────────────────────────────────────

  @Test
  @DisplayName("Fiber.pipe(Cell) — fiber operator chain feeds the cell")
  void fiberPipeToCell() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Cell<Integer> cell = circuit.cell(0);
      // Fiber that only passes positive numbers
      Fiber<Integer> fiber = cortex.<Integer>fiber(Integer.class).guard((Integer n) -> n > 0);
      Pipe<Integer> in = fiber.pipe(cell);

      in.emit(5);
      in.emit(-3);   // guarded out
      in.emit(7);
      circuit.await();

      assertThat(cell.get()).isEqualTo(7);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Fiber.pipe(null Cell) throws NPE")
  void fiberPipeCellNullThrows() {
    Cortex cortex = Substrates.cortex();
    Fiber<Integer> fiber = cortex.fiber(Integer.class);
    assertThatThrownBy(() -> fiber.pipe((Cell<Integer>) null))
        .isInstanceOf(NullPointerException.class);
  }

  // ── Flow.pipe(Cell) ─────────────────────────────────────────────────────

  @Test
  @DisplayName("Flow.pipe(Cell) — flow operator chain feeds the cell")
  void flowPipeToCell() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Cell<String> cell = circuit.cell("initial");
      Flow<Integer, String> flow = cortex.<Integer>flow(Integer.class)
          .map((Integer n) -> "v=" + n);
      Pipe<Integer> in = flow.pipe(cell);

      in.emit(42);
      circuit.await();

      assertThat(cell.get()).isEqualTo("v=42");
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Flow.pipe(null Cell) throws NPE")
  void flowPipeCellNullThrows() {
    Cortex cortex = Substrates.cortex();
    Flow<Integer, Integer> flow = cortex.flow(Integer.class);
    assertThatThrownBy(() -> flow.pipe((Cell<Integer>) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Flow.pipe(Cell) composes with scan upstream")
  void flowScanIntoCell() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Cell<Integer> cell = circuit.cell(0);
      Flow<Integer, Integer> flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0, Integer::sum, s -> s);   // running sum
      Pipe<Integer> in = flow.pipe(cell);

      in.emit(1);
      in.emit(2);
      in.emit(3);
      circuit.await();

      // Cell holds the latest projection: cumulative sum = 6
      assertThat(cell.get()).isEqualTo(6);
    } finally {
      circuit.close();
    }
  }

  // ── Cross-feature: cell + scan + window ─────────────────────────────────

  @Test
  @DisplayName("End-to-end: window → cell")
  void windowIntoCell() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Cell<Integer> sumCell = circuit.cell(0);
      // Take a 3-element window, sum it, write to cell
      Flow<Integer, Integer> flow = cortex.<Integer>flow(Integer.class)
          .window(3)
          .map((Substrates.Window<Integer> w) -> w.reduce(0, Integer::sum));
      Pipe<Integer> in = flow.pipe(sumCell);

      AtomicInteger lastSize = new AtomicInteger();
      // (just emit enough to fill the window once)
      in.emit(10);
      in.emit(20);
      in.emit(30);
      circuit.await();

      assertThat(sumCell.get()).isEqualTo(60);

      in.emit(40);   // window: [20, 30, 40] = 90
      circuit.await();
      assertThat(sumCell.get()).isEqualTo(90);
    } finally {
      circuit.close();
    }
  }
}
