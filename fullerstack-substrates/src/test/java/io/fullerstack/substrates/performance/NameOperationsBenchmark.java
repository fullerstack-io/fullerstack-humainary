package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.humainary.substrates.api.Substrates.Cortex;

/**
 * Benchmark for Name operations matching Humainary's NameOps benchmarks.
 */
public class NameOperationsBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int OPERATIONS_PER_ITERATION = 100_000;

    private final Cortex cortex = Substrates.cortex();

    @Test
    public void benchmarkNameFromString() {
        System.out.println("\n=== Name from String Benchmark ===");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                cortex.name("test.name.path");
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                cortex.name("test.name.path");
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 3.087 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 3.087);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkNameChaining() {
        System.out.println("\n=== Name Chaining Benchmark ===");

        Substrates.Name base = cortex.name("base");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                base.name("child");
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                base.name("child");
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 9.044 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 9.044);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkNameComparison() {
        System.out.println("\n=== Name Comparison Benchmark ===");

        Substrates.Name name1 = cortex.name("test.name.one");
        Substrates.Name name2 = cortex.name("test.name.two");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                name1.compareTo(name2);
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                name1.compareTo(name2);
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 33.085 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 33.085);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkNameDepth() {
        System.out.println("\n=== Name Depth Benchmark ===");

        Substrates.Name name = cortex.name("level1.level2.level3.level4");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                name.depth();
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                name.depth();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 1.644 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 1.644);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkNameFromIterable() {
        System.out.println("\n=== Name from Iterable Benchmark ===");

        List<String> segments = List.of("part1", "part2", "part3");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                cortex.name(segments);
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                cortex.name(segments);
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 11.477 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 11.477);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkNamePathGeneration() {
        System.out.println("\n=== Name Path Generation Benchmark ===");

        Substrates.Name name = cortex.name("level1.level2.level3.level4");

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                name.path();
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                name.path();
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 39.257 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 39.257);
        System.out.println("====================\n");
    }
}
