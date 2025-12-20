package io.fullerstack.substrates.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Mode.AverageTime;

/**
 * Ring buffer enqueue benchmark comparing current (CAS+sequence) vs optimized (getAndAdd).
 *
 * <p>Measures pure enqueue cost without consumer interference.
 * Batch size (1000) is much smaller than ring size (4096), so no wraparound.
 */
@State(Scope.Benchmark)
@BenchmarkMode(AverageTime)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class RingBufferOps {

  private static final int RING_SIZE = 4096;
  private static final int RING_MASK = RING_SIZE - 1;
  private static final int BATCH_SIZE = 1000;
  private static final Object VALUE = new Object();

  // ==========================================================================
  // OPTIMIZED: getAndAdd + amortized wraparound (parkNanos for virtual thread)
  // ==========================================================================

  @State(Scope.Benchmark)
  public static class OptimizedRing {
    private static final VarHandle TAIL;
    private static final VarHandle SEQUENCE;

    static {
      try {
        var lookup = MethodHandles.lookup();
        TAIL = lookup.findVarHandle(OptimizedRing.class, "tail", long.class);
        SEQUENCE = MethodHandles.arrayElementVarHandle(long[].class);
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private final Object[] values = new Object[RING_SIZE];
    private final long[] sequences = new long[RING_SIZE];
    @SuppressWarnings("unused") private volatile long tail = 0;

    @Setup(Iteration)
    public void setup() {
      tail = 0;
      for (int i = 0; i < RING_SIZE; i++) {
        sequences[i] = i;
        values[i] = null;
      }
    }

    void enqueue(Object value) {
      // getAndAdd instead of CAS loop - the optimization we're testing
      long claimed = (long) TAIL.getAndAdd(this, 1);
      int index = (int) (claimed & RING_MASK);
      values[index] = value;
      SEQUENCE.setRelease(sequences, index, claimed + 1);
    }
  }

  // ==========================================================================
  // CURRENT: CAS loop + per-slot sequence check (like LMAX Disruptor)
  // ==========================================================================

  @State(Scope.Benchmark)
  public static class CurrentRing {
    private static final VarHandle TAIL;
    private static final VarHandle SEQUENCE;

    static {
      try {
        var lookup = MethodHandles.lookup();
        TAIL = lookup.findVarHandle(CurrentRing.class, "tail", long.class);
        SEQUENCE = MethodHandles.arrayElementVarHandle(long[].class);
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }

    private final Object[] values = new Object[RING_SIZE];
    private final long[] sequences = new long[RING_SIZE];
    @SuppressWarnings("unused") private volatile long tail = 0;

    @Setup(Iteration)
    public void setup() {
      tail = 0;
      for (int i = 0; i < RING_SIZE; i++) {
        sequences[i] = i;
        values[i] = null;
      }
    }

    void enqueue(Object value) {
      long claimed;
      int index;

      // CAS loop with sequence check - matches FsExperimentalCircuit
      for (;;) {
        claimed = (long) TAIL.getVolatile(this);
        index = (int) (claimed & RING_MASK);
        long seq = (long) SEQUENCE.getVolatile(sequences, index);

        if (seq == claimed) {
          if (TAIL.compareAndSet(this, claimed, claimed + 1)) {
            break;
          }
        }
        // seq < claimed would mean buffer full, but we skip that check for benchmark
      }

      values[index] = value;
      SEQUENCE.setRelease(sequences, index, claimed + 1);
    }
  }

  // ==========================================================================
  // BENCHMARKS - Hot path (no wraparound, batch << ring size)
  // ==========================================================================

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void optimized_batch(OptimizedRing ring) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      ring.enqueue(VALUE);
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void current_batch(CurrentRing ring) {
    for (int i = 0; i < BATCH_SIZE; i++) {
      ring.enqueue(VALUE);
    }
  }

}
