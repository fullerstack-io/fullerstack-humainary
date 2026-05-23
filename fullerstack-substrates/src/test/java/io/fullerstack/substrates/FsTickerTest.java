package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Fault;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests for the 2.8 Ticker primitive (SPEC §11.5).
class FsTickerTest {

  /// Emits a sequence of at least N values, then asserts the contract:
  /// gap-free, monotonic, starting from 0.
  @Test
  @DisplayName("Ticker emits monotonic gap-free Long sequence starting at 0")
  void monotonicGapFreeSequence() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      final int target = 5;
      CountDownLatch latch = new CountDownLatch(target);
      List<Long> received = new CopyOnWriteArrayList<>();
      Pipe<Long> sink = circuit.pipe(v -> {
        received.add(v);
        latch.countDown();
      });
      Ticker ticker = circuit.ticker(Duration.ofMillis(20), sink);

      assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
      ticker.close();
      circuit.await();

      assertThat(received).hasSizeGreaterThanOrEqualTo(target);
      List<Long> head = received.subList(0, target);
      for (int i = 0; i < target; i++) {
        assertThat(head.get(i)).isEqualTo((long) i);
      }
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Ticker has a Subject parented under the Circuit")
  void tickerSubjectHierarchy() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      Ticker ticker = circuit.ticker(cortex.name("my.ticker"),
          Duration.ofSeconds(1), sink);
      assertThat((Object) ticker.subject()).isNotNull();
      assertThat((Object) ticker.subject().name()).isEqualTo(cortex.name("my.ticker"));
      ticker.close();
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Closing the ticker stops further emissions")
  void closeStopsEmissions() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      List<Long> received = new CopyOnWriteArrayList<>();
      Pipe<Long> sink = circuit.pipe(received::add);
      Ticker ticker = circuit.ticker(Duration.ofMillis(15), sink);
      // Let a few ticks fire.
      Thread.sleep(80);
      ticker.close();
      circuit.await();
      int sizeAtClose = received.size();
      // Wait long enough for several more ticks if the ticker were still alive.
      Thread.sleep(150);
      circuit.await();
      // Allow at most one straggler tick already submitted to the ingress queue
      // before close (SPEC §11.5: in-flight ticks may still be delivered).
      assertThat(received.size()).isLessThanOrEqualTo(sizeAtClose + 1);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Closing the circuit stops the ticker")
  void circuitCloseStopsTicker() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    List<Long> received = new CopyOnWriteArrayList<>();
    Pipe<Long> sink = circuit.pipe(received::add);
    circuit.ticker(Duration.ofMillis(15), sink);
    Thread.sleep(60);
    int sizeBeforeClose = received.size();
    circuit.close();
    Thread.sleep(120);
    // After circuit close, no new ticks should be processed (the scheduler is
    // shutdown and any post-close emit calls are rejected by the circuit).
    assertThat(received.size()).isLessThanOrEqualTo(sizeBeforeClose + 1);
  }

  @Test
  @DisplayName("Failure in the target receptor does not stop the ticker")
  void receptorThrowDoesNotStopTicker() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      final int target = 3;
      CountDownLatch latch = new CountDownLatch(target);
      List<Long> received = new CopyOnWriteArrayList<>();
      Pipe<Long> sink = circuit.pipe(v -> {
        received.add(v);
        latch.countDown();
        if (v == 0L) throw new RuntimeException("boom");
      });
      Ticker ticker = circuit.ticker(Duration.ofMillis(20), sink);
      assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
      ticker.close();
      circuit.await();
      // Sequence remains gap-free across the throw — SPEC §11.5 + §15.4.
      assertThat(received.subList(0, target)).containsExactly(0L, 1L, 2L);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Zero interval rejected with IllegalArgumentException")
  void zeroIntervalRejected() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      assertThatThrownBy(() -> circuit.ticker(Duration.ZERO, sink))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Negative interval rejected with IllegalArgumentException")
  void negativeIntervalRejected() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      assertThatThrownBy(() -> circuit.ticker(Duration.ofMillis(-1), sink))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Null arguments rejected with NullPointerException")
  void nullArgsRejected() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      assertThatThrownBy(() -> circuit.ticker(null, sink))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> circuit.ticker(Duration.ofMillis(10), null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> circuit.ticker(null, Duration.ofMillis(10), sink))
          .isInstanceOf(NullPointerException.class);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("ticker on a closed circuit signals Fault")
  void tickerOnClosedCircuitFaults() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    Pipe<Long> sink = circuit.pipe(v -> { });
    circuit.close();
    assertThatThrownBy(() -> circuit.ticker(Duration.ofMillis(10), sink))
        .isInstanceOf(Fault.class);
  }

  @Test
  @DisplayName("closeAwait blocks until ticker stops")
  void closeAwaitBlocks() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      Ticker ticker = circuit.ticker(Duration.ofMillis(10), sink);
      Thread.sleep(30);
      ticker.closeAwait();
      // After closeAwait returns, no further work should be in flight.
      // We can't assert ticker state directly (it's anchored), but the
      // call should have returned without throwing.
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Multiple tickers on one circuit have independent sequences")
  void multipleTickersIndependent() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      final int target = 3;
      CountDownLatch latchA = new CountDownLatch(target);
      CountDownLatch latchB = new CountDownLatch(target);
      List<Long> a = new CopyOnWriteArrayList<>();
      List<Long> b = new CopyOnWriteArrayList<>();
      Pipe<Long> sinkA = circuit.pipe(v -> { a.add(v); latchA.countDown(); });
      Pipe<Long> sinkB = circuit.pipe(v -> { b.add(v); latchB.countDown(); });
      Ticker tA = circuit.ticker(cortex.name("a"), Duration.ofMillis(20), sinkA);
      Ticker tB = circuit.ticker(cortex.name("b"), Duration.ofMillis(20), sinkB);
      assertThat(latchA.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(latchB.await(2, TimeUnit.SECONDS)).isTrue();
      tA.close();
      tB.close();
      circuit.await();
      assertThat(a.subList(0, target)).containsExactly(0L, 1L, 2L);
      assertThat(b.subList(0, target)).containsExactly(0L, 1L, 2L);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Mean interval converges to the requested interval")
  void meanIntervalConvergence() throws InterruptedException {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      final long intervalMs = 25L;
      final int target = 8;
      CountDownLatch latch = new CountDownLatch(target);
      List<Long> stamps = new CopyOnWriteArrayList<>();
      Pipe<Long> sink = circuit.pipe(v -> {
        stamps.add(System.nanoTime());
        latch.countDown();
      });
      Ticker ticker = circuit.ticker(Duration.ofMillis(intervalMs), sink);
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.close();
      circuit.await();

      // Mean inter-arrival time, in ms. Generous bounds — CI is noisy.
      long deltaSum = 0;
      for (int i = 1; i < target; i++) {
        deltaSum += stamps.get(i) - stamps.get(i - 1);
      }
      double meanMs = (deltaSum / (double) (target - 1)) / 1_000_000.0;
      assertThat(meanMs).isBetween(intervalMs * 0.5, intervalMs * 2.0);
    } finally {
      circuit.close();
    }
  }

  @Test
  @DisplayName("Ticker close is idempotent")
  void closeIsIdempotent() {
    Cortex cortex = Substrates.cortex();
    Circuit circuit = cortex.circuit();
    try {
      Pipe<Long> sink = circuit.pipe(v -> { });
      Ticker ticker = circuit.ticker(Duration.ofMillis(10), sink);
      ticker.close();
      ticker.close();   // must be no-op
      ticker.close();   // still no-op
    } finally {
      circuit.close();
    }
  }
}
