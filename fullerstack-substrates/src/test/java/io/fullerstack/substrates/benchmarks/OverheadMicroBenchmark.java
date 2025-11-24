package io.fullerstack.substrates.benchmarks;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmark to measure individual overhead components in async pipe emission.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class OverheadMicroBenchmark {

  private static final ThreadLocal<Boolean> threadLocal = ThreadLocal.withInitial(() -> Boolean.FALSE);
  private LinkedBlockingQueue<Runnable> queue;
  private Thread virtualThread;
  private volatile boolean running = true;

  @Setup
  public void setup() {
    queue = new LinkedBlockingQueue<>();
    virtualThread = Thread.startVirtualThread(() -> {
      while (running) {
        try {
          Runnable task = queue.poll();
          if (task != null) {
            task.run();
          } else {
            LockSupport.park();
          }
        } catch (Exception e) {
          // ignore
        }
      }
    });
  }

  @TearDown
  public void tearDown() throws InterruptedException {
    running = false;
    virtualThread.interrupt();
    virtualThread.join(1000);
  }

  @Benchmark
  public void measureThreadLocalLookup(Blackhole bh) {
    bh.consume(threadLocal.get());
  }

  @Benchmark
  public void measureTaskAllocation(Blackhole bh) {
    EmitTask task = new EmitTask(42);
    bh.consume(task);
  }

  @Benchmark
  public void measureQueueOffer(Blackhole bh) {
    Runnable task = () -> {};
    bh.consume(queue.offer(task));
  }

  @Benchmark
  public void measureUnpark() {
    LockSupport.unpark(virtualThread);
  }

  @Benchmark
  public void measureFullAsyncEmit(Blackhole bh) {
    // Simulate full async emit path
    if (Thread.currentThread() != virtualThread) {
      Runnable task = new EmitTask(42);
      queue.offer(task);
      LockSupport.unpark(virtualThread);
    }
    bh.consume(true);
  }

  private static class EmitTask implements Runnable {
    private final int value;

    EmitTask(int value) {
      this.value = value;
    }

    @Override
    public void run() {
      // no-op
    }
  }
}
