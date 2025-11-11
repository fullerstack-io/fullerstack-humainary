package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.Composer.pipe;

/**
 * Performance test for named conduit creation.
 * Tests direct construction (no caching) vs previous ConduitSlot approach.
 */
public class NamedConduitPerformanceTest {

    @Test
    public void testNamedConduitCreationPerformance() {
        Substrates.Cortex cortex = Substrates.cortex();
        Substrates.Circuit circuit = cortex.circuit();
        Substrates.Name name = cortex.name("test-conduit");

        // Warmup (critical for JVM optimization)
        System.out.println("\n=== WARMUP PHASE (NAMED) ===");
        for (int warmup = 0; warmup < 5; warmup++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                circuit.conduit(name, pipe(Integer.class));
            }
            long duration = (System.nanoTime() - start) / 10000;
            System.out.printf("Warmup %d: %d ns/op%n", warmup + 1, duration);
        }

        // Measurement phase
        System.out.println("\n=== MEASUREMENT PHASE (NAMED) ===");
        int iterations = 100000;
        double[] results = new double[5];

        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                circuit.conduit(name, pipe(Integer.class));
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

        System.out.println("\n=== FINAL RESULTS (NAMED) ===");
        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Min: %.3f ns/op%n", min);
        System.out.printf("Max: %.3f ns/op%n", max);
        System.out.println("====================\n");

        circuit.close();

        // Compare with reference (21ns baseline)
        System.out.println("=== COMPARISON ===");
        System.out.println("Reference (William's Alpha SPI): 21 ns/op");
        System.out.printf("Current (no caching): %.3f ns/op%n", avg);
        double gap = avg / 21.0;
        System.out.printf("Gap: %.1fx slower than reference%n", gap);
        System.out.println("==================\n");
    }
}
