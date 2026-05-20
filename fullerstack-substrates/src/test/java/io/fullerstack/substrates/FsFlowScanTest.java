package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FsFlowScanTest {

  @Test
  @DisplayName("scan (state-only projection) — running sum with identity emit")
  void runningSum() {
    final List<Integer> out = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0,
                (sum, v) -> sum + v,
                s -> s);          // identity projection
      final var sink = circuit.<Integer>pipe(out::add);
      final var pipe = flow.pipe(sink);
      pipe.emit(1);
      pipe.emit(2);
      pipe.emit(3);
      pipe.emit(4);
      circuit.await();
      assertThat(out).containsExactly(1, 3, 6, 10);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("scan with type-changing projection")
  void typeChangingProjection() {
    final List<String> out = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0,
                (sum, v) -> sum + v,
                s -> "sum=" + s);
      final var sink = circuit.<String>pipe(out::add);
      final var pipe = flow.pipe(sink);
      pipe.emit(1);
      pipe.emit(2);
      pipe.emit(3);
      circuit.await();
      assertThat(out).containsExactly("sum=1", "sum=3", "sum=6");
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("scan with null projection drops emission")
  void nullProjectionDrops() {
    final AtomicInteger count = new AtomicInteger(0);
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0,
                (sum, v) -> sum + v,
                s -> s >= 5 ? s : null);    // suppress until sum >= 5
      final var sink = circuit.<Integer>pipe(v -> count.incrementAndGet());
      final var pipe = flow.pipe(sink);
      pipe.emit(1);  // sum=1, null → drop
      pipe.emit(2);  // sum=3, null → drop
      pipe.emit(3);  // sum=6, emit
      pipe.emit(4);  // sum=10, emit
      circuit.await();
      assertThat(count.get()).isEqualTo(2);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("scan with throwing step leaves state unchanged")
  void stepThrowLeavesStateUnchanged() {
    final List<Integer> out = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0,
                (sum, v) -> {
                  if (v < 0) throw new IllegalArgumentException("negative");
                  return sum + v;
                },
                s -> s);
      final var sink = circuit.<Integer>pipe(out::add);
      final var pipe = flow.pipe(sink);
      pipe.emit(1);    // sum=1, emit
      pipe.emit(-99);  // step throws, state unchanged, drop
      pipe.emit(2);    // sum=3 (prior state was 1), emit
      circuit.await();
      assertThat(out).containsExactly(1, 3);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("scan with input-aware projection")
  void inputAwareProjection() {
    final List<Integer> out = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      // State = running max; projection = current input - running max (always <= 0)
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> Integer.MIN_VALUE,
                (max, v) -> Math.max(max, v),
                (max, v) -> v - max);
      final var sink = circuit.<Integer>pipe(out::add);
      final var pipe = flow.pipe(sink);
      pipe.emit(5);   // max=5, 5-5=0
      pipe.emit(3);   // max=5, 3-5=-2
      pipe.emit(8);   // max=8, 8-8=0
      pipe.emit(2);   // max=8, 2-8=-6
      circuit.await();
      assertThat(out).containsExactly(0, -2, 0, -6);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("scan state allocated once per materialization (different chains independent)")
  void statePerMaterialization() {
    final List<Integer> a = new ArrayList<>();
    final List<Integer> b = new ArrayList<>();
    final var cortex = Substrates.cortex();
    final var circuit = cortex.circuit();
    try {
      final var flow = cortex.<Integer>flow(Integer.class)
          .scan(() -> 0, Integer::sum, s -> s);
      // Two independent pipes — each materialization allocates its own state slot.
      final var pipeA = flow.pipe(circuit.<Integer>pipe(a::add));
      final var pipeB = flow.pipe(circuit.<Integer>pipe(b::add));
      pipeA.emit(10);
      pipeB.emit(20);
      pipeA.emit(5);
      pipeB.emit(7);
      circuit.await();
      assertThat(a).containsExactly(10, 15);
      assertThat(b).containsExactly(20, 27);
    } finally {
      circuit.close();
    }
  }
}
