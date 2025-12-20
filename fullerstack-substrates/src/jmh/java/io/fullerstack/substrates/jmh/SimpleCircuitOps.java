package io.fullerstack.substrates.jmh;

import io.fullerstack.substrates.FsAsyncPipe;
import io.fullerstack.substrates.FsSimpleCircuit;
import io.fullerstack.substrates.FsSubject;
import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Cortex;
import org.openjdk.jmh.annotations.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

/**
 * Benchmark for FsSimpleCircuit using ConcurrentLinkedQueue.
 */
@BenchmarkMode(AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class SimpleCircuitOps {

  private static final int BATCH_SIZE = 1000;
  private static final int VALUE = 42;

  @State(Scope.Benchmark)
  public static class CircuitState {
    Cortex cortex;
    FsSimpleCircuit circuit;
    FsAsyncPipe<Integer> pipe;

    @Setup(Trial)
    public void setupTrial() {
      cortex = Substrates.cortex();
    }

    @Setup(Iteration)
    public void setupIteration() {
      FsSubject<Circuit> subject = new FsSubject<>(cortex.name("bench"), null, Circuit.class);
      circuit = new FsSimpleCircuit(subject);
      pipe = new FsAsyncPipe<>(subject, null, circuit, v -> {});
    }

    @TearDown(Iteration)
    public void tearDown() {
      circuit.close();
    }
  }

  @Benchmark
  @Threads(1)
  @OperationsPerInvocation(BATCH_SIZE)
  public void sp_batch(CircuitState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(VALUE);
    }
  }

  @Benchmark
  @Threads(4)
  @OperationsPerInvocation(BATCH_SIZE)
  public void mp_batch(CircuitState s) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      s.pipe.emit(VALUE);
    }
  }
}
