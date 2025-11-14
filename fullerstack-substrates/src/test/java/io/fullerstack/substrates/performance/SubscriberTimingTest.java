package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.Composer.pipe;

/**
 * Manual timing test to identify subscribe bottlenecks.
 */
public class SubscriberTimingTest implements Substrates {

  @Test
  void measureSubscriberCreationSteps() {
    var cortex = Substrates.cortex();
    var circuit = cortex.circuit();
    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(pipe());
    var name = cortex.name("test");

    int iterations = 100_000;

    System.out.println("\n=== Subscriber Creation Timing (averaged over " + iterations + " iterations) ===\n");

    // Warmup
    for (int i = 0; i < 10_000; i++) {
      var subscriber = cortex.<Integer>subscriber(name, (_, _) -> {});
      conduit.subscribe(subscriber);
    }

    // Measure cortex.name() overhead
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      cortex.name("test");
    }
    long nameTime = (System.nanoTime() - start) / iterations;
    System.out.println("1. cortex.name(\"test\"): " + nameTime + " ns");

    // Measure lambda creation overhead
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      java.util.function.BiConsumer<Subject<Channel<Integer>>, Registrar<Integer>> lambda = (_, _) -> {};
    }
    long lambdaTime = (System.nanoTime() - start) / iterations;
    System.out.println("2. Lambda creation (_, _) -> {}: " + lambdaTime + " ns");

    // Measure cortex.subscriber() - creates ContextSubscriber
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      cortex.<Integer>subscriber(name, (_, _) -> {});
    }
    long subscriberTime = (System.nanoTime() - start) / iterations;
    System.out.println("3. cortex.subscriber(name, lambda): " + subscriberTime + " ns");

    // Measure conduit.subscribe() - adds to list + creates Subscription
    var subscriber = cortex.<Integer>subscriber(name, (_, _) -> {});
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      conduit.subscribe(subscriber);
    }
    long subscribeTime = (System.nanoTime() - start) / iterations;
    System.out.println("4. conduit.subscribe(subscriber): " + subscribeTime + " ns");

    // Measure full operation (subscriber creation + subscribe)
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      var sub = cortex.<Integer>subscriber(name, (_, _) -> {});
      conduit.subscribe(sub);
    }
    long totalTime = (System.nanoTime() - start) / iterations;
    System.out.println("\nTOTAL (create + subscribe): " + totalTime + " ns");

    System.out.println("\n=== Breakdown ===");
    System.out.println("Subscriber creation overhead: " + (subscriberTime - nameTime - lambdaTime) + " ns");
    System.out.println("Subscribe operation overhead: " + subscribeTime + " ns");
    System.out.println("Unaccounted overhead: " + (totalTime - subscriberTime - subscribeTime) + " ns");

    System.out.println("\n=== Analysis ===");
    System.out.println("If subscriber was reused:");
    System.out.println("  Time per subscribe: " + subscribeTime + " ns");
    System.out.println("  " + (totalTime / subscribeTime) + "x faster\n");

    circuit.close();
  }

  @Test
  void measureAllocationSources() {
    var cortex = Substrates.cortex();
    var circuit = cortex.circuit();
    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(pipe());
    var name = cortex.name("test");

    System.out.println("\n=== Object Allocation Analysis ===\n");

    // Force GC
    System.gc();
    Thread.yield();

    long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    int iterations = 10_000;
    for (int i = 0; i < iterations; i++) {
      var subscriber = cortex.<Integer>subscriber(name, (_, _) -> {});
      conduit.subscribe(subscriber);
    }

    System.gc();
    Thread.yield();

    long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long totalAlloc = memAfter - memBefore;
    long avgAlloc = totalAlloc / iterations;

    System.out.println("Total memory allocated: " + totalAlloc + " bytes");
    System.out.println("Average per operation: " + avgAlloc + " bytes");
    System.out.println("JMH reported: ~505 bytes/op");
    System.out.println("\nExpected allocations:");
    System.out.println("  ContextSubscriber object: ~32 bytes");
    System.out.println("  BiConsumer lambda: ~32 bytes");
    System.out.println("  CallbackSubscription: ~32 bytes");
    System.out.println("  Name (if not cached): ~40 bytes");
    System.out.println("  ArrayList growth: ~variable");
    System.out.println("  Total minimum: ~136 bytes\n");

    circuit.close();
  }
}
