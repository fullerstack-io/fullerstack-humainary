package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Composer;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.*;

/**
 * Benchmark for Cortex operations matching Humainary's CortexOps benchmarks.
 */
public class CortexOperationsBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int OPERATIONS_PER_ITERATION = 100_000;

    private final Cortex cortex = Substrates.cortex();

    @Test
    public void benchmarkCircuitCreation() {
        System.out.println("\n=== Circuit Creation Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                Circuit circuit = cortex.circuit();
                circuit.close();
            }
            long duration = (System.nanoTime() - start) / 1_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < 1_000; i++) {
                Circuit circuit = cortex.circuit();
                circuit.close();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / 1_000;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 280.612 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 280.612);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkPipeEmptyCreation() {
        System.out.println("\n=== Pipe Empty Creation Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                cortex.pipe();
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                cortex.pipe();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 0.443 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 0.443);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkPipeObserverCreation() {
        System.out.println("\n=== Pipe Receptor Creation Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                cortex.pipe(s -> {});
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                cortex.pipe(s -> {});
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 5.713 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 5.713);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkScopeCreation() {
        System.out.println("\n=== Scope Creation Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                Scope scope = cortex.scope();
                scope.close();
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                Scope scope = cortex.scope();
                scope.close();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / 10_000;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 8.758 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 8.758);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkStateEmpty() {
        System.out.println("\n=== State Empty Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                cortex.state();
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                cortex.state();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 0.441 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 0.441);
        System.out.println("====================\n");
    }
}
