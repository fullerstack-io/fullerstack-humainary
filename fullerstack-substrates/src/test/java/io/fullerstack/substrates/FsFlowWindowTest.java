package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsFlowWindowTest {

  @Test
  @DisplayName("window(int) emits a Window<O> on every input")
  void countWindow() {
    final List<List<Integer>> emitted = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
    final var flow = cortex.<Integer>flow(Integer.class).window(3);
    final var sink = circuit.<Window<Integer>>pipe(w -> {
      final List<Integer> snap = new ArrayList<>();
      w.forEach(snap::add);
      emitted.add(snap);
    });
    final var pipe = flow.pipe(sink);
    for (int i = 1; i <= 5; i++) pipe.emit(i);
    circuit.await();
    // Warm-up: each window sized 1, 2, 3 — then rolling fixed at 3.
    assertThat(emitted).hasSize(5);
    assertThat(emitted.get(0)).containsExactly(1);
    assertThat(emitted.get(1)).containsExactly(1, 2);
    assertThat(emitted.get(2)).containsExactly(1, 2, 3);
    assertThat(emitted.get(3)).containsExactly(2, 3, 4);
    assertThat(emitted.get(4)).containsExactly(3, 4, 5);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("window(int) — count must be positive")
  void countMustBePositive() {
    final var cortex = Substrates.cortex();
    assertThatThrownBy(() -> cortex.<Integer>flow(Integer.class).window(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cortex.<Integer>flow(Integer.class).window(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("window(Duration, int) — capacity bound applies")
  void durationWindowCapacity() {
    final List<Integer> sizes = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class).window(Duration.ofMinutes(10), 2);
      final var sink = circuit.<Window<Integer>>pipe(w -> sizes.add(w.size()));
      final var pipe = flow.pipe(sink);
      pipe.emit(1);
      pipe.emit(2);
      pipe.emit(3);
      pipe.emit(4);
      circuit.await();
      assertThat(sizes).containsExactly(1, 2, 2, 2);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("window operates inside a Flow chain (after map)")
  void afterMap() {
    final AtomicInteger lastSum = new AtomicInteger(0);
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .map((Integer n) -> n * 2)
          .window(3);
      final var sink = circuit.<Window<Integer>>pipe(w -> lastSum.set(w.fold(0, Integer::sum)));
      final var pipe = flow.pipe(sink);
      pipe.emit(1);  // [2]            sum=2
      pipe.emit(2);  // [2,4]          sum=6
      pipe.emit(3);  // [2,4,6]        sum=12
      pipe.emit(4);  // [4,6,8]        sum=18
      pipe.emit(5);  // [6,8,10]       sum=24
      circuit.await();
      assertThat(lastSum.get()).isEqualTo(24);
    } finally {
      circuit.close();
    }
  }
}
