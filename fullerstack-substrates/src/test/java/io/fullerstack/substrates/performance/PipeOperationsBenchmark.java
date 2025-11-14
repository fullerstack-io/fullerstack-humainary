package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.humainary.substrates.api.Substrates.*;

/**
 * Benchmark for Pipe operations matching Humainary's PipeOps benchmarks.
 */
public class PipeOperationsBenchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int OPERATIONS_PER_ITERATION = 100_000;

    private final Cortex cortex = Substrates.cortex();

    @Test
    public void benchmarkEmitToEmptyPipe() {
        System.out.println("\n=== Emit to Empty Pipe Benchmark ===");

        Pipe<String> pipe = cortex.pipe();

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                pipe.emit("test");
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                pipe.emit("test");
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 0.445 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 0.445);
        System.out.println("====================\n");
    }

    @Test
    public void benchmarkEmitToObserverPipe() {
        System.out.println("\n=== Emit to Receptor Pipe Benchmark ===");

        AtomicInteger counter = new AtomicInteger();
        Pipe<String> pipe = cortex.pipe(s -> counter.incrementAndGet());

        // Warmup
        for (int w = 0; w < WARMUP_ITERATIONS; w++) {
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) {
                pipe.emit("test");
            }
            long duration = (System.nanoTime() - start) / 10_000;
            System.out.printf("Warmup %d: %d ns/op%n", w + 1, duration);
        }

        // Measurement
        double[] results = new double[MEASUREMENT_ITERATIONS];
        for (int run = 0; run < MEASUREMENT_ITERATIONS; run++) {
            long start = System.nanoTime();
            for (int i = 0; i < OPERATIONS_PER_ITERATION; i++) {
                pipe.emit("test");
            }
            long duration = System.nanoTime() - start;
            results[run] = (double) duration / OPERATIONS_PER_ITERATION;
            System.out.printf("Run %d: %.3f ns/op%n", run + 1, results[run]);
        }

        double avg = 0;
        for (double r : results) avg += r;
        avg /= results.length;

        System.out.printf("Average: %.3f ns/op%n", avg);
        System.out.printf("Reference (Humainary): 0.640 ns/op%n");
        System.out.printf("Gap: %.1fx%n", avg / 0.640);
        System.out.println("====================\n");
    }
}
