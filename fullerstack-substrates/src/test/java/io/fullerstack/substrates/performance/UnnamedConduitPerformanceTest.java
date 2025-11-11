package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.Composer.pipe;

/**
 * Performance test for unnamed conduit creation.
 * Tests the fast path optimization that bypasses InternedName caching and ConduitSlot lookup.
 */
public class UnnamedConduitPerformanceTest {

    @Test
    public void testUnnamedConduitCreationPerformance() {
        Substrates.Cortex cortex = Substrates.cortex();
        Substrates.Circuit circuit = cortex.circuit();

        // Warmup (critical for JVM optimization)
        System.out.println("\n=== WARMUP PHASE ===");
        for (int warmup = 0; warmup < 5; warmup++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                circuit.conduit(pipe(Integer.class));
            }
            long duration = (System.nanoTime() - start) / 10000;
            System.out.printf("Warmup %d: %d ns/op%n", warmup + 1, duration);
        }

        // Measurement phase
        System.out.println("\n=== MEASUREMENT PHASE ===");
        int iterations = 100000;
        double[] results = new double[5];

        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                circuit.conduit(pipe(Integer.class));
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / iterations;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        // Calculate statistics
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (double result : results) {
            sum += result;
            if (result < min) min = result;
            if (result > max) max = result;
        }

        double avg = sum / results.length;

        System.out.println("\n=== FINAL RESULTS ===");
        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Min: %.3f ns/op%n", min);
        System.out.printf("Max: %.3f ns/op%n", max);
        System.out.println("====================\n");

        circuit.close();

        // Compare with baseline (5,321 ns/op before optimization)
        System.out.println("=== COMPARISON ===");
        System.out.println("Baseline (before fast path): 5,321 ns/op");
        System.out.printf("Current (with fast path): %.3f ns/op%n", avg);
        if (avg < 5321) {
            double improvement = ((5321 - avg) / 5321) * 100;
            System.out.printf("Improvement: %.1f%% faster%n", improvement);
        } else {
            double regression = ((avg - 5321) / 5321) * 100;
            System.out.printf("Regression: %.1f%% slower%n", regression);
        }
        System.out.println("==================\n");
    }
}
