package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Pipe;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Micro-benchmark to profile emit path components.
 * Run with: mvn exec:java -Dexec.mainClass="io.fullerstack.substrates.EmitPathProfileTest" -Dexec.classpathScope=test
 */
public class EmitPathProfileTest {

  private static final int WARMUP = 100_000;
  private static final int ITERATIONS = 10_000_000;

  public static void main(String[] args) throws Exception {
    System.out.println("=== Emit Path Profile ===\n");

    // Warmup
    warmup();
    Thread.sleep(100);

    // Test components
    testThreadCurrentThread();
    testThreadIdentityCheck();
    testVolatileRead();
    testAtomicGetAndSet();
    testVolatileWrite();
    testFullSubmitPath();
    testFullEmitPath();
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

  private static void testThreadCurrentThread() {
    Thread[] holder = new Thread[1];

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      holder[0] = Thread.currentThread();
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("Thread.currentThread():     %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testThreadIdentityCheck() {
    Thread current = Thread.currentThread();
    Thread other = Thread.ofVirtual().unstarted(() -> {});
    int[] holder = new int[1];

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      if (Thread.currentThread() == other) {
        holder[0]++;
      }
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("Thread identity check:      %.2f ns/op (holder: %d)%n",
        (double) elapsed / ITERATIONS, holder[0]);
  }

  private static volatile boolean running = true;

  private static void testVolatileRead() {
    int[] holder = new int[1];

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      if (running) {
        holder[0]++;
      }
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("Volatile boolean read:      %.2f ns/op (holder: %d)%n",
        (double) elapsed / ITERATIONS, holder[0]);
  }

  private static void testAtomicGetAndSet() {
    AtomicReference<Object> ref = new AtomicReference<>(new Object());
    Object obj = new Object();
    Object[] holder = new Object[1];

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      holder[0] = ref.getAndSet(obj);
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("AtomicRef.getAndSet:        %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testVolatileWrite() {
    Job job = new EmitJob(v -> {}, 42);
    Job prev = new EmitJob(v -> {}, 0);

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      job.next = prev;
    }
    long elapsed = System.nanoTime() - start;

    System.out.printf("Volatile job.next write:    %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testFullSubmitPath() {
    FsSubject<Circuit> subject = new FsSubject<>(cortex().name("submit-test"), null, Circuit.class);
    FsCircuit circuit = new FsCircuit(subject);
    Consumer<Integer> receiver = v -> {};

    // Pre-create jobs to isolate submit path
    Job[] jobs = new Job[ITERATIONS];
    for (int i = 0; i < ITERATIONS; i++) {
      jobs[i] = new EmitJob(receiver, i);
    }

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      circuit.submit(jobs[i]);
    }
    long elapsed = System.nanoTime() - start;

    circuit.close();

    System.out.printf("submit() only (pre-alloc):  %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }

  private static void testFullEmitPath() {
    Circuit circuit = cortex().circuit(cortex().name("emit-test"));
    Pipe<Integer> pipe = circuit.pipe(v -> {});

    long start = System.nanoTime();
    for (int i = 0; i < ITERATIONS; i++) {
      pipe.emit(i);
    }
    long elapsed = System.nanoTime() - start;

    circuit.await();
    circuit.close();

    System.out.printf("Full emit() path:           %.2f ns/op%n", (double) elapsed / ITERATIONS);
  }
}
