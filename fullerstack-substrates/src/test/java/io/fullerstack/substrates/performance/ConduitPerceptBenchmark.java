package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.*;

/**
 * Benchmark for Conduit percept/get operations.
 *
 * Measures cache hit performance for repeated percept access.
 * Reference: Humainary conduit.get() = 3.7 ns/op
 */
public class ConduitPerceptBenchmark {

    @Test
    public void benchmarkPerceptCacheHit() {
        System.out.println("\n=== Conduit Percept Cache Hit Benchmark ===");

        Cortex cortex = Substrates.cortex();
        Circuit circuit = cortex.circuit(cortex.name("benchmark"));

        // Create conduit with simple pipe composer
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex.name("test"),
            channel -> cortex.pipe(String.class)
        );

        // Create names for testing
        Name name1 = cortex.name("entity-1");
        Name name2 = cortex.name("entity-2");
        Name name3 = cortex.name("entity-3");

        // Prime the cache
        Pipe<String> pipe1 = conduit.percept(name1);
        Pipe<String> pipe2 = conduit.percept(name2);
        Pipe<String> pipe3 = conduit.percept(name3);

        // Warmup - ensure JIT compilation
        System.out.println("Warming up...");
        for (int w = 0; w < 10000; w++) {
            conduit.percept(name1);
            conduit.percept(name2);
            conduit.percept(name3);
        }

        // Measure - cache hit path only
        int iterations = 100_000;
        System.out.println("Measuring " + iterations + " iterations (cache hits only)...");

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            conduit.percept(name1);
        }
        long end = System.nanoTime();

        long avgNs = (end - start) / iterations;

        System.out.println("\n=== CONDUIT PERCEPT CACHE HIT BENCHMARK ===");
        System.out.println("Current: " + avgNs + " ns/op");
        System.out.println("Humainary reference: 3.7 ns/op");
        System.out.println("Gap: " + String.format("%.1f", avgNs / 3.7) + "x");
        System.out.println("Overhead: " + (avgNs - 3.7) + " ns");
        System.out.println("==========================================\n");

        circuit.close();
    }

    @Test
    public void benchmarkPerceptWithDifferentNames() {
        System.out.println("\n=== Conduit Percept (Rotating Names) Benchmark ===");

        Cortex cortex = Substrates.cortex();
        Circuit circuit = cortex.circuit(cortex.name("benchmark"));

        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex.name("test"),
            channel -> cortex.pipe(String.class)
        );

        // Create 10 different names
        Name[] names = new Name[10];
        for (int i = 0; i < 10; i++) {
            names[i] = cortex.name("entity-" + i);
            conduit.percept(names[i]); // Prime cache
        }

        // Warmup
        System.out.println("Warming up...");
        for (int w = 0; w < 10000; w++) {
            for (Name name : names) {
                conduit.percept(name);
            }
        }

        // Measure
        int iterations = 100_000;
        System.out.println("Measuring " + iterations + " iterations (10 names rotating)...");

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            conduit.percept(names[i % 10]);
        }
        long end = System.nanoTime();

        long avgNs = (end - start) / iterations;

        System.out.println("\n=== RESULT ===");
        System.out.println("Current: " + avgNs + " ns/op");
        System.out.println("Humainary reference: 3.7 ns/op");
        System.out.println("Gap: " + String.format("%.1f", avgNs / 3.7) + "x");
        System.out.println("==============\n");

        circuit.close();
    }

    @Test
    public void profileNameHashCodeAndEquals() {
        System.out.println("\n=== Name HashCode and Equals Profiling ===");

        Cortex cortex = Substrates.cortex();

        // Create test names
        Name name1 = cortex.name("broker-1.topic-orders.partition-5");
        Name name2 = cortex.name("broker-1.topic-orders.partition-5");
        Name name3 = cortex.name("broker-2.topic-users.partition-3");

        // Warmup
        for (int w = 0; w < 10000; w++) {
            name1.hashCode();
            name1.equals(name2);
            name1.equals(name3);
        }

        // Measure hashCode
        int iterations = 1_000_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            name1.hashCode();
        }
        long end = System.nanoTime();
        long hashCodeNs = (end - start) / iterations;

        // Measure equals (identical)
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            name1.equals(name2);
        }
        end = System.nanoTime();
        long equalsIdenticalNs = (end - start) / iterations;

        // Measure equals (different)
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            name1.equals(name3);
        }
        end = System.nanoTime();
        long equalsDifferentNs = (end - start) / iterations;

        System.out.println("\n=== NAME OPERATION COSTS ===");
        System.out.println("hashCode(): " + hashCodeNs + " ns/op");
        System.out.println("equals() [identical]: " + equalsIdenticalNs + " ns/op");
        System.out.println("equals() [different]: " + equalsDifferentNs + " ns/op");
        System.out.println("============================\n");

        System.out.println("Analysis:");
        System.out.println("- ConcurrentHashMap.get() overhead: ~2-3 ns (base)");
        System.out.println("- hashCode() cost: " + hashCodeNs + " ns");
        System.out.println("- equals() cost: ~" + equalsIdenticalNs + " ns (cache hit)");
        System.out.println("- Total estimated: " + (2 + hashCodeNs + equalsIdenticalNs) + " ns");
        System.out.println("- Measured percept: TBD (run benchmarkPerceptCacheHit)");
        System.out.println();
    }
}
