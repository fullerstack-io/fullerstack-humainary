package io.fullerstack.substrates.performance;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.*;

/**
 * Performance analysis test for Conduit.percept() hot-path.
 * <p>
 * Measures time spent in different stages of percept() to identify bottlenecks:
 * 1. Cache lookup
 * 2. Channel creation
 * 3. Composer invocation
 * 4. Cache insertion
 * 5. Subscriber notification
 */
public class PerceptPerformanceTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int TEST_ITERATIONS = 10000;

    // Simple test percept that wraps a Channel
    static class TestPercept implements Percept {
        private final Channel<String> channel;

        TestPercept(Channel<String> channel) {
            this.channel = channel;
        }

        public Subject subject() {
            return channel.subject();
        }
    }

    @Test
    public void testPerceptPerformance() {
        // Disable timing in RoutingConduit for accurate measurement
        System.setProperty("substrates.timing", "false");
        System.out.println("=== Percept Performance Analysis (No Internal Timing) ===\n");

        // Create circuit and conduit with simple String percepts
        Circuit circuit = cortex().circuit(cortex().name("perf-test"));
        Conduit<TestPercept, String> conduit = circuit.conduit(
            cortex().name("test-conduit"),
            (Composer<String, TestPercept>) TestPercept::new
        );

        // Warmup phase
        System.out.println("Warming up JIT...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            TestPercept p = conduit.percept(cortex().name("warmup-" + i));
        }

        System.out.println("Warmup complete. Starting measurements...\n");

        // Test 1: First access (cache miss) - measures full percept() cost
        long[] firstAccessTimes = new long[TEST_ITERATIONS];
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            TestPercept p = conduit.percept(cortex().name("percept-" + i));
            long end = System.nanoTime();
            firstAccessTimes[i] = end - start;
        }

        // Test 2: Repeated access (cache hit) - measures cache lookup cost
        Name cachedName = cortex().name("cached-percept");
        TestPercept cached = conduit.percept(cachedName); // Prime the cache

        long[] cachedAccessTimes = new long[TEST_ITERATIONS];
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            TestPercept p = conduit.percept(cachedName);
            long end = System.nanoTime();
            cachedAccessTimes[i] = end - start;
        }

        // Test 3: Name creation overhead
        long[] nameCreationTimes = new long[TEST_ITERATIONS];
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            Name name = cortex().name("test-" + i);
            long end = System.nanoTime();
            nameCreationTimes[i] = end - start;
        }

        // Calculate statistics
        Stats firstAccessStats = calculateStats(firstAccessTimes);
        Stats cachedAccessStats = calculateStats(cachedAccessTimes);
        Stats nameCreationStats = calculateStats(nameCreationTimes);

        // Report results
        System.out.println("Results (based on " + TEST_ITERATIONS + " iterations):\n");

        System.out.println("1. First Access (cache miss - full percept() cost):");
        printStats(firstAccessStats);
        System.out.println();

        System.out.println("2. Cached Access (cache hit - lookup only):");
        printStats(cachedAccessStats);
        System.out.println();

        System.out.println("3. Name Creation (cortex().name() overhead):");
        printStats(nameCreationStats);
        System.out.println();

        // Calculate derived metrics
        long channelPlusComposerCost = firstAccessStats.median - cachedAccessStats.median;
        System.out.println("Derived Metrics:");
        System.out.println("  Channel + Composer cost: " + channelPlusComposerCost + " ns");
        System.out.println("  Cache lookup cost: " + cachedAccessStats.median + " ns");
        System.out.println();

        // Compare to Humainary target (1.9 ns for cached access)
        System.out.println("Performance vs Humainary Alpha SPI:");
        System.out.println("  Humainary target (from JMH): ~1.9 ns");
        System.out.println("  Our cached access: " + cachedAccessStats.median + " ns");
        System.out.println("  Gap: " + String.format("%.1fx", cachedAccessStats.median / 1.9) + " slower");
        System.out.println();

        // Recommendations
        System.out.println("Analysis:");
        if (cachedAccessStats.median > 10) {
            System.out.println("  ⚠ Cache lookup is slow (>10ns). Consider:");
            System.out.println("    - Use identity-based Name comparison (avoid equals/hashCode)");
            System.out.println("    - Optimize ConcurrentHashMap access pattern");
        }
        if (channelPlusComposerCost > 100) {
            System.out.println("  ⚠ Channel+Composer cost is high (>100ns). Consider:");
            System.out.println("    - Pre-allocate Channel objects");
            System.out.println("    - Optimize EmissionChannel constructor");
            System.out.println("    - Check if Composer is doing unnecessary work");
        }
        if (nameCreationStats.median > 5) {
            System.out.println("  ⚠ Name creation is slow (>5ns). Consider:");
            System.out.println("    - Cache frequently-used names");
            System.out.println("    - Optimize Name interning");
        }

        circuit.close();
    }

    private static class Stats {
        long min, max, median, p95, p99;
        double mean, stddev;
    }

    private Stats calculateStats(long[] times) {
        Stats stats = new Stats();

        // Sort for percentiles
        long[] sorted = times.clone();
        java.util.Arrays.sort(sorted);

        stats.min = sorted[0];
        stats.max = sorted[sorted.length - 1];
        stats.median = sorted[sorted.length / 2];
        stats.p95 = sorted[(int)(sorted.length * 0.95)];
        stats.p99 = sorted[(int)(sorted.length * 0.99)];

        // Calculate mean
        long sum = 0;
        for (long time : times) {
            sum += time;
        }
        stats.mean = (double)sum / times.length;

        // Calculate stddev
        double variance = 0;
        for (long time : times) {
            double diff = time - stats.mean;
            variance += diff * diff;
        }
        stats.stddev = Math.sqrt(variance / times.length);

        return stats;
    }

    private void printStats(Stats stats) {
        System.out.println("  Min:    " + stats.min + " ns");
        System.out.println("  Median: " + stats.median + " ns");
        System.out.println("  Mean:   " + String.format("%.1f", stats.mean) + " ns");
        System.out.println("  P95:    " + stats.p95 + " ns");
        System.out.println("  P99:    " + stats.p99 + " ns");
        System.out.println("  Max:    " + stats.max + " ns");
        System.out.println("  StdDev: " + String.format("%.1f", stats.stddev) + " ns");
    }
}
