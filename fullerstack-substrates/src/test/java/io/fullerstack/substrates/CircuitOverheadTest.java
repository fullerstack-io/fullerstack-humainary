package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Pipe;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Micro-benchmark to isolate circuit overhead components.
 * Run with: mvn test -Dtest=CircuitOverheadTest
 */
public class CircuitOverheadTest {

  private static final int WARMUP = 100_000;
  private static final int ITERATIONS = 1_000_000;

  public static void main(String[] args) throws Exception {
    // Allow JIT warmup
    Thread.sleep(100);
    System.out.println("=== Circuit Overhead Isolation Test ===\n");

    // Warmup JIT
    warmup();

    // Test 1: Raw atomic getAndSet (queue operation only)
    testAtomicGetAndSet();

    // Test 2: Job allocation only
    testJobAllocation();

    // Test 3: Thread.currentThread() check
    testThreadCheck();

    // Test 4: Volatile read
    testVolatileRead();

    // Test 5: Full emit path (allocation + atomic + thread check)
    testFullEmit();

    // Test 6: park/unpark overhead
    testParkUnpark();

    // Test 7: Producer only (no consumer coordination)
    testProducerOnly();

    // Test 8: Batched emit (like JMH)
    testBatchedEmit();

  }

  private static void warmup() {
    Circuit circuit = cortex().circuit(cortex().name("warmup"));
    Pipe<Integer> pipe = circuit.pipe(v -> {});
    for (int i = 0; i < WARMUP; i++) {
      pipe.emit(i);
    }
    circuit.await();
    circuit.close();
  }

  private static void testAtomicGetAndSet() {
    AtomicReference<Object> ref = new AtomicReference<>(new Object());
    Object sentinel = new Object();

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      ref.getAndSet(sentinel);
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("1. AtomicRef.getAndSet:  %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testJobAllocation() {
    Object[] holder = new Object[1]; // Prevent escape analysis optimization

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      holder[0] = new EmitJob(v -> {}, i);
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("2. EmitJob allocation:   %.2f ns/op (holder: %s)%n",
        (double) elapsed / ITERATIONS, holder[0] != null);
  }

  private static void testThreadCheck() {
    Thread current = Thread.currentThread();
    Thread[] holder = new Thread[1];

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      holder[0] = Thread.currentThread();
      if (holder[0] == current) {
        // cascade path
      }
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("3. Thread.currentThread: %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testVolatileRead() {
    AtomicLong counter = new AtomicLong();

    long start = System.nanoTime();
    long sum = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      if (running) {
        sum += counter.get();
      }
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("4. Volatile read:        %.2f ns/op (sum: %d)%n",
        (double) elapsed / ITERATIONS, sum);
  }

  private static void testFullEmit() {
    Circuit circuit = cortex().circuit(cortex().name("test"));
    AtomicLong counter = new AtomicLong();
    Pipe<Integer> pipe = circuit.pipe(v -> counter.incrementAndGet());

    // Measure from external thread (not circuit thread)
    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      pipe.emit(i);
    }
    long elapsed = System.nanoTime() - start;

    circuit.await();
    circuit.close();

    System.out.printf("5. Full emit (external): %.2f ns/op (processed: %d)%n",
        (double) elapsed / ITERATIONS, counter.get());
  }

  private static void testParkUnpark() throws Exception {
    Thread current = Thread.currentThread();
    Thread worker = Thread.ofVirtual().start(() -> {
      for (int i = 0; i < ITERATIONS; i++) {
        LockSupport.park();
      }
    });

    // Give worker time to start
    Thread.sleep(10);

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      LockSupport.unpark(worker);
      Thread.onSpinWait(); // Give worker time to park again
    }
    long elapsed = System.nanoTime() - start;

    worker.interrupt();
    worker.join(100);

    System.out.printf("6. Unpark (virtual):     %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  // Need to make this a field to use volatile
  private static volatile boolean running = true;

  /** Test just producer side - no consumer coordination */
  private static void testProducerOnly() {
    // Use FsCircuit directly to measure just submit() without consumer processing
    FsSubject<Circuit> subject = new FsSubject<>(cortex().name("producer-only"), null, Circuit.class);
    FsCircuit circuit = new FsCircuit(subject);
    AtomicLong counter = new AtomicLong();

    // Create pipe but don't care about processing
    java.util.function.Consumer<Integer> receiver = v -> counter.incrementAndGet();

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      // Simulate just the submit path
      circuit.submit(new EmitJob(receiver, i));
    }
    long elapsed = System.nanoTime() - start;

    circuit.close();

    System.out.printf("7. Producer only:        %.2f ns/op (queued: %d)%n",
        (double) elapsed / ITERATIONS, ITERATIONS);
  }

  /** Test batched emit like JMH does */
  private static void testBatchedEmit() {
    Circuit circuit = cortex().circuit(cortex().name("batched"));
    AtomicLong counter = new AtomicLong();
    Pipe<Integer> pipe = circuit.pipe(v -> counter.incrementAndGet());

    int batchSize = 1000;
    int batches = ITERATIONS / batchSize;

    long start = System.nanoTime();
    for (int b = 0; b < batches; b++) {
      for (int i = 0; i < batchSize; i++) {
        pipe.emit(i);
      }
      // Don't await between batches - just keep pushing
    }
    long elapsed = System.nanoTime() - start;

    circuit.await();
    circuit.close();

    System.out.printf("8. Batched emit:         %.2f ns/op (processed: %d)%n",
        (double) elapsed / ITERATIONS, counter.get());
  }

}
