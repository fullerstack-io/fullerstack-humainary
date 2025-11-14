package io.fullerstack.substrates.name;

import io.humainary.substrates.api.Substrates;
import org.junit.jupiter.api.Test;
import static io.humainary.substrates.api.Substrates.*;

public class EnumNameBenchmarkTest {
    
    enum TestEnum {
        FIRST, SECOND, THIRD
    }
    
    @Test
    public void benchmarkEnumNameCreation() {
        Cortex cortex = cortex();
        
        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 10000; i++) {
            cortex.name(TestEnum.SECOND);
        }
        
        // Measure
        int iterations = 100000;
        System.out.println("Measuring " + iterations + " iterations...");
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cortex.name(TestEnum.SECOND);
        }
        long end = System.nanoTime();
        
        long avgNs = (end - start) / iterations;
        System.out.println("\n=== ENUM NAME CREATION BENCHMARK ===");
        System.out.println("Current: " + avgNs + " ns/op");
        System.out.println("Humainary reference: 2.8 ns/op");
        System.out.println("Before optimization: 335 ns/op");
        System.out.println("Improvement vs before: " + String.format("%.1f", 335.0 / avgNs) + "x faster");
        System.out.println("Gap vs Humainary: " + String.format("%.1f", avgNs / 2.8) + "x");
        System.out.println("====================================\n");
    }
}
