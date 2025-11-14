package io.fullerstack.substrates.benchmark;

import io.humainary.substrates.api.Substrates;
import static io.humainary.substrates.api.Substrates.*;

public class QuickCircuitBench {
    public static void main(String[] args) {
        Cortex cortex = cortex();
        Name name = cortex.name("test");
        
        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 5000; i++) {
            Circuit c = cortex.circuit(name);
            c.close();
        }
        
        // Measure
        int iterations = 10000;
        System.out.println("Measuring " + iterations + " iterations...");
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Circuit c = cortex.circuit(name);
            c.close();
        }
        long end = System.nanoTime();
        
        long avgNs = (end - start) / iterations;
        System.out.println("\n=== RESULTS ===");
        System.out.println("Circuit creation + close: " + avgNs + " ns/op");
        System.out.println("Humainary reference: 271 ns/op");
        System.out.println("Before optimization: 419,922 ns/op");
        System.out.println("Improvement vs before: " + String.format("%.1f", 419922.0 / avgNs) + "x faster");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNs / 271.0) + "x");
    }
}
