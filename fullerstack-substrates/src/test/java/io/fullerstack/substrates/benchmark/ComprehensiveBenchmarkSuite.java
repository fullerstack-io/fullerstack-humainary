package io.fullerstack.substrates.benchmark;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.humainary.substrates.api.Substrates.cortex;

/**
 * Comprehensive benchmark suite comparing Fullerstack implementation against
 * William Louth's Humainary reference implementation numbers.
 * <p>
 * Reference Numbers (from William's Alpha SPI implementation):
 * - Circuit creation: ~271 ns/op
 * - Conduit creation: ~21 ns/op (unnamed)
 * - Percept (cache hit): ~1.9 ns/op
 * - Name creation: ~2.8 ns/op
 * - Subscriber creation + subscribe: ~505 bytes/op allocation
 * <p>
 * Goal: Measure where Fullerstack implementation stands vs reference.
 */
@DisplayName("Comprehensive Benchmark Suite - Fullerstack vs Humainary Reference")
public class ComprehensiveBenchmarkSuite {

    private static final int WARMUP_ITERATIONS = 10000;
    private static final int MEASUREMENT_ITERATIONS = 100000;

    @Test
    @DisplayName("1. Circuit Creation Benchmark")
    void benchmarkCircuitCreation() {
        System.out.println("\n=== CIRCUIT CREATION BENCHMARK ===");
        System.out.println("Reference (Humainary Alpha SPI): 271 ns/op");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Circuit c = cortex().circuit(cortex().name("warmup-" + i));
            c.close();
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            Circuit c = cortex().circuit(cortex().name("test-" + i));
            c.close();
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNanosPerOp / 271.0) + "x");
        System.out.println("================================\n");
    }

    @Test
    @DisplayName("2. Unnamed Conduit Creation Benchmark")
    void benchmarkUnnamedConduitCreation() {
        System.out.println("\n=== UNNAMED CONDUIT CREATION BENCHMARK ===");
        System.out.println("Reference (Humainary Alpha SPI): 21 ns/op");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("conduit-bench"));

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            circuit.conduit(Composer.pipe());
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            circuit.conduit(Composer.pipe());
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNanosPerOp / 21.0) + "x");

        circuit.close();
        System.out.println("==========================================\n");
    }

    @Test
    @DisplayName("3. Named Conduit Creation Benchmark")
    void benchmarkNamedConduitCreation() {
        System.out.println("\n=== NAMED CONDUIT CREATION BENCHMARK ===");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("named-conduit-bench"));

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            circuit.conduit(cortex().name("warmup-" + i), Composer.pipe());
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            circuit.conduit(cortex().name("test-" + i), Composer.pipe());
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");

        circuit.close();
        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("4. Percept (Channel Access) Benchmark - Cache Hit")
    void benchmarkPerceptCacheHit() {
        System.out.println("\n=== PERCEPT (CACHE HIT) BENCHMARK ===");
        System.out.println("Reference (Humainary Alpha SPI): 1.9 ns/op");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("percept-bench"));
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        Name entityName = cortex().name("entity-1");

        // Warmup - create the channel (cache it)
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            conduit.percept(entityName);
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement - cache hits only
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            conduit.percept(entityName);
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.1f", avgNanosPerOp) + " ns/op");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNanosPerOp / 1.9) + "x");

        circuit.close();
        System.out.println("=====================================\n");
    }

    @Test
    @DisplayName("5. Percept (Channel Access) Benchmark - Cache Miss")
    void benchmarkPerceptCacheMiss() {
        System.out.println("\n=== PERCEPT (CACHE MISS) BENCHMARK ===");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("percept-miss-bench"));
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        // Warmup with different names each time (all cache misses)
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            conduit.percept(cortex().name("warmup-" + i));
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement - all cache misses
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            conduit.percept(cortex().name("test-" + i));
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");

        circuit.close();
        System.out.println("======================================\n");
    }

    @Test
    @DisplayName("6. Name Creation Benchmark")
    void benchmarkNameCreation() {
        System.out.println("\n=== NAME CREATION BENCHMARK ===");
        System.out.println("Reference (Humainary Alpha SPI): 2.8 ns/op");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            cortex().name("warmup-" + i);
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            cortex().name("test-" + i);
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.1f", avgNanosPerOp) + " ns/op");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNanosPerOp / 2.8) + "x");
        System.out.println("===============================\n");
    }

    @Test
    @DisplayName("7. Subscriber Creation + Subscribe Benchmark")
    void benchmarkSubscriberCreation() {
        System.out.println("\n=== SUBSCRIBER CREATION + SUBSCRIBE BENCHMARK ===");
        System.out.println("Reference (Humainary): ~505 bytes/op allocation");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("subscriber-bench"));
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        List<String> sink = new ArrayList<>();

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Subscriber<String> sub = cortex().subscriber(
                cortex().name("sub-" + i),
                (subject, registrar) -> registrar.register(sink::add)
            );
            conduit.subscribe(sub);
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            Subscriber<String> sub = cortex().subscriber(
                cortex().name("test-" + i),
                (subject, registrar) -> registrar.register(sink::add)
            );
            conduit.subscribe(sub);
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");
        System.out.println("Note: Allocation measurement requires JMH profiler");

        circuit.close();
        System.out.println("=================================================\n");
    }

    @Test
    @DisplayName("8. Signal Emission Benchmark (Pipe.emit)")
    void benchmarkSignalEmission() {
        System.out.println("\n=== SIGNAL EMISSION BENCHMARK ===");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("emission-bench"));
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        Pipe<String> pipe = conduit.percept(cortex().name("emitter"));

        List<String> sink = new ArrayList<>();
        conduit.subscribe(
            cortex().subscriber(
                cortex().name("receiver"),
                (subject, registrar) -> registrar.register(sink::add)
            )
        );

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            pipe.emit("warmup");
        }
        circuit.await();
        sink.clear();
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            pipe.emit("test");
        }
        circuit.await();  // Wait for all emissions to process
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation: " + String.format("%.0f", avgNanosPerOp) + " ns/op");
        System.out.println("Emissions processed: " + sink.size());

        circuit.close();
        System.out.println("=================================\n");
    }

    @Test
    @DisplayName("9. Circuit.await() Synchronization Benchmark")
    void benchmarkCircuitAwait() {
        System.out.println("\n=== CIRCUIT.AWAIT() SYNCHRONIZATION BENCHMARK ===");
        System.out.println("Measurement iterations: " + MEASUREMENT_ITERATIONS);
        System.out.println();

        Circuit circuit = cortex().circuit(cortex().name("await-bench"));

        // Warmup
        System.out.print("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            circuit.await();
        }
        System.out.println(" done");

        // Force GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        // Measurement (circuit is idle, await should return immediately)
        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            circuit.await();
        }
        long endTime = System.nanoTime();

        long totalNanos = endTime - startTime;
        double avgNanosPerOp = (double) totalNanos / MEASUREMENT_ITERATIONS;

        System.out.println("Fullerstack implementation (idle circuit): " + String.format("%.0f", avgNanosPerOp) + " ns/op");
        System.out.println("Note: This is zero-latency synchronization (event-driven)");

        circuit.close();
        System.out.println("=================================================\n");
    }

    @Test
    @DisplayName("SUMMARY: Full Benchmark Results vs Humainary Reference")
    void printSummary() {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  BENCHMARK SUMMARY: Fullerstack vs Humainary Reference            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                    ║");
        System.out.println("║  Run individual benchmarks above to see detailed comparisons.     ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  Reference Numbers (William's Alpha SPI):                         ║");
        System.out.println("║    • Circuit creation: 271 ns/op                                  ║");
        System.out.println("║    • Unnamed conduit: 21 ns/op                                    ║");
        System.out.println("║    • Percept (cache hit): 1.9 ns/op                               ║");
        System.out.println("║    • Name creation: 2.8 ns/op                                     ║");
        System.out.println("║    • Subscriber: ~505 bytes/op allocation                         ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  Goal: Identify performance gaps and optimization opportunities   ║");
        System.out.println("║                                                                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
