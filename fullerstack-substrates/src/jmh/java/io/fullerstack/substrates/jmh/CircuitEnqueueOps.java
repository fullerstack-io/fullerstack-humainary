package io.fullerstack.substrates.jmh;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

/**
 * Benchmark for optimized circuit enqueue path.
 * Measures submission cost - consumer drains asynchronously.
 */
@BenchmarkMode(AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CircuitEnqueueOps {

  private static final int BATCH_SIZE = 1000;
  private static final int VALUE = 42;

  @org.openjdk.jmh.annotations.State(Scope.Benchmark)
  public static class CircuitState {
    Cortex cortex;
    Circuit circuit;
    Pipe<Integer> pipe;

    @Setup(Trial)
    public void setupTrial() {
      cortex = Substrates.cortex();
    }

    @Setup(Iteration)
    public void setupIteration() {
      circuit = cortex.circuit(cortex.name("bench"));
      pipe = circuit.pipe(Receptor.of(Integer.class));
    }

    @TearDown(Iteration)
    public void tearDown() {
      circuit.close();  // Don't await - just close
    }
  }

  // ============================================================
  // SINGLE PRODUCER
  // ============================================================

  @Benchmark
  @Threads(1)
  @OperationsPerInvocation(BATCH_SIZE)
  public void sp_batch(CircuitState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(VALUE);
    }
  }

  // ============================================================
  // MULTI PRODUCER (4 threads)
  // ============================================================

  @Benchmark
  @Threads(4)
  @OperationsPerInvocation(BATCH_SIZE)
  public void mp_batch(CircuitState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(VALUE);
    }
  }
}
