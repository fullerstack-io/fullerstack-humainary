package io.fullerstack.substrates.name;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Name;
import org.junit.jupiter.api.Test;

/**
 * Performance verification test for NanoName with sorted array children cache.
 */
class NamePerformanceTest {

  @Test
  void testNameInterning() {
    Name root = cortex().name("benchmark");

    // Create same child twice
    Name child1 = root.name("test");
    Name child2 = root.name("test");

    // Should be the same instance (interned via NanoName cache)
    assertSame(child1, child2, "Names should be interned");

    // Segments should be equal (same string content)
    String seg1 = child1.part();
    String seg2 = child2.part();
    assertEquals(seg1, seg2, "Segments should be equal");
    // They should actually be the same instance since it's the same NanoName
    assertSame(seg1, seg2, "Segments from same NanoName should be same instance");
  }

  @Test
  void testChainPerformance() {
    Name root = cortex().name("benchmark");

    // Pre-create child names so they exist in cache
    Name child1 = root.name("child1");
    Name parent = root.name("level1").name("level2");
    Name parentChild = parent.name("child1");
    Name deep = root.name("a").name("b").name("c").name("d").name("e");
    Name deepChild = deep.name("child1");

    // Extensive warmup to trigger JIT compilation
    for (int i = 0; i < 100_000; i++) {
      root.name("child1");
      parent.name("child1");
      deep.name("child1");
      parent.compareTo(parent);
    }

    // Measure chain_single (cached child lookup)
    int iterations = 1_000_000;
    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      root.name("child1");
    }
    long chainSingle = (System.nanoTime() - start) / iterations;

    // chain_three (cached child lookup at depth 3)
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      parent.name("child1");
    }
    long chainThree = (System.nanoTime() - start) / iterations;

    // chain_deep (cached child lookup at depth 6)
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      deep.name("child1");
    }
    long chainDeep = (System.nanoTime() - start) / iterations;

    // compare_same (identity comparison)
    start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      parent.compareTo(parent);
    }
    long compareSame = (System.nanoTime() - start) / iterations;

    System.out.println("=== NanoName Performance ===");
    System.out.println("chain_single:  " + chainSingle + " ns/op");
    System.out.println("chain_three:   " + chainThree + " ns/op");
    System.out.println("chain_deep:    " + chainDeep + " ns/op");
    System.out.println("compare_same:  " + compareSame + " ns/op");

    // Performance targets (relaxed for CI environments)
    // In JMH benchmarks we see ~10ns, but simple loops run slower
    assertTrue(chainSingle < 100, "chain_single should be < 100ns, was: " + chainSingle);
    assertTrue(chainThree < 100, "chain_three should be < 100ns, was: " + chainThree);
    assertTrue(chainDeep < 100, "chain_deep should be < 100ns, was: " + chainDeep);
    assertTrue(compareSame < 20, "compare_same should be < 20ns, was: " + compareSame);
  }
}
