package io.fullerstack.substrates.benchmarks;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

import io.fullerstack.substrates.FsName;
import io.fullerstack.substrates.FsSubject;
import io.fullerstack.substrates.disruptor.FsDisruptorCircuit;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Subject;
import org.openjdk.jmh.annotations.*;

/**
 * Benchmark for FsDisruptorCircuit implementation.
 *
 * <p>Tests the LMAX Disruptor-inspired circuit with single-producer optimization.
 */
@State(Scope.Benchmark)
@BenchmarkMode(AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class DisruptorBenchmark {

  private static final int VALUE = 42;
  private static final int BATCH_SIZE = 1000;

  private Name name;
  private Circuit circuit;
  private Pipe<Integer> pipe;
  private Pipe<Integer> chained;

  @Setup(Trial)
  public void setupTrial() {
    name = FsName.parse("test");
  }

  @Setup(Iteration)
  public void setupIteration() {
    Subject<Circuit> subject = new FsSubject<>(name, Circuit.class);
    circuit = new FsDisruptorCircuit(subject);
    Pipe<Integer> sink = circuit.pipe(Receptor.of(Integer.class));
    pipe = circuit.pipe(Receptor.of(Integer.class));
    Pipe<Integer> intermediate = circuit.pipe(sink);
    chained = circuit.pipe(intermediate);
    circuit.await();
  }

  @TearDown(Iteration)
  public void tearDownIteration() {
    circuit.close();
  }

  @Benchmark
  public void emit_single() {
    pipe.emit(VALUE);
  }

  @Benchmark
  public void emit_single_await() {
    pipe.emit(VALUE);
    circuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void emit_batch() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      pipe.emit(VALUE + i);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void emit_batch_await() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      pipe.emit(VALUE + i);
    }
    circuit.await();
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void emit_chained_await() {
    for (int i = 0; i < BATCH_SIZE; i++) {
      chained.emit(VALUE + i);
    }
    circuit.await();
  }
}
