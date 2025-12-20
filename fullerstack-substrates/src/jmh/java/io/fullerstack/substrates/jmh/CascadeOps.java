package io.fullerstack.substrates.jmh;

import static io.humainary.substrates.api.Substrates.*;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Pipe;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.*;

/**
 * Benchmarks for cascading emissions (transit queue processing).
 *
 * <p>Cascade = when processing an emission triggers another emission on the circuit thread. These
 * go through the transit queue (LIFO) rather than ingress queue (FIFO).
 */
@org.openjdk.jmh.annotations.State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(java.util.concurrent.TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CascadeOps implements Substrates {

  private static final int BATCH_SIZE = 1000;
  private static final int CASCADE_DEPTH = 5;

  private Cortex cortex;
  private Circuit circuit;
  private AtomicInteger counter;

  // Pipes for cascade testing
  private Pipe<Integer> cascadePipe1; // depth 1
  private Pipe<Integer> cascadePipe3; // depth 3
  private Pipe<Integer> cascadePipe5; // depth 5

  @Setup(org.openjdk.jmh.annotations.Level.Trial)
  public void setup() {
    cortex = cortex();
    counter = new AtomicInteger(0);
  }

  @TearDown(org.openjdk.jmh.annotations.Level.Trial)
  public void teardown() {
    // Circuit closed per iteration
  }

  @Setup(org.openjdk.jmh.annotations.Level.Iteration)
  public void setupIteration() {
    circuit = cortex.circuit(cortex.name("cascade-test"));

    // Create cascade chains where each stage emits to the next
    // This forces emissions through the transit queue

    // Depth 1: emit -> receptor (no cascade)
    cascadePipe1 = circuit.pipe(v -> counter.incrementAndGet());

    // Depth 3: emit -> cascade -> cascade -> receptor
    Pipe<Integer> end3 = circuit.pipe(v -> counter.incrementAndGet());
    Pipe<Integer> mid3 = circuit.pipe(v -> end3.emit(v));
    cascadePipe3 = circuit.pipe(v -> mid3.emit(v));

    // Depth 5: emit -> cascade x4 -> receptor
    Pipe<Integer> end5 = circuit.pipe(v -> counter.incrementAndGet());
    Pipe<Integer> mid5a = circuit.pipe(v -> end5.emit(v));
    Pipe<Integer> mid5b = circuit.pipe(v -> mid5a.emit(v));
    Pipe<Integer> mid5c = circuit.pipe(v -> mid5b.emit(v));
    cascadePipe5 = circuit.pipe(v -> mid5c.emit(v));

    circuit.await();
  }

  @TearDown(org.openjdk.jmh.annotations.Level.Iteration)
  public void teardownIteration() {
    circuit.close();
  }

  /// Baseline: single hop, no cascading.
  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void cascade_depth_1() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      cascadePipe1.emit(i);
    }
    circuit.await();
  }

  /// 3-level cascade: tests transit queue with moderate depth.
  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void cascade_depth_3() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      cascadePipe3.emit(i);
    }
    circuit.await();
  }

  /// 5-level cascade: tests transit queue with deeper nesting.
  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void cascade_depth_5() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      cascadePipe5.emit(i);
    }
    circuit.await();
  }
}
