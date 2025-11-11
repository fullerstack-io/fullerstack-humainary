package io.fullerstack.substrates.benchmark;

import io.humainary.substrates.api.Substrates;

import static io.humainary.substrates.api.Substrates.Composer.pipe;

/**
 * Manual benchmark for unnamed conduit creation.
 * Measures performance improvement from fast path optimization.
 */
public class ManualConduitBenchmark {

    public static void main(String[] args) {
        Substrates.Cortex cortex = Substrates.cortex();
        Substrates.Circuit circuit = cortex.circuit();

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 10000; i++) {
            circuit.conduit(pipe(Integer.class));
        }

        // Measure
        int iterations = 100000;
        System.out.println("Measuring " + iterations + " iterations...");

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            circuit.conduit(pipe(Integer.class));
        }
        long end = System.nanoTime();

        long totalNanos = end - start;
        double avgNanos = (double) totalNanos / iterations;

        System.out.println("\n=== BENCHMARK RESULTS ===");
        System.out.println("Total time: " + totalNanos + " ns");
        System.out.println("Iterations: " + iterations);
        System.out.printf("Average: %.3f ns/op%n", avgNanos);
        System.out.println("=========================\n");

        circuit.close();
    }
}
