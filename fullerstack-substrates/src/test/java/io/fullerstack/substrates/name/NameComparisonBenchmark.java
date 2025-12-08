package io.fullerstack.substrates.name;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Name;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark comparing NanoName (flat cache) vs TreeName (tree structure).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NameComparisonBenchmark {

  // Pre-created names for hot-path tests
  private Name nanoRoot;
  private Name nanoParent;
  private TreeName treeRoot;
  private TreeName treeParent;

  @Setup(Level.Trial)
  public void setup() {
    // Create base names for chaining tests
    nanoRoot = Substrates.cortex().name("benchmark");
    nanoParent = nanoRoot.name("level1").name("level2");

    treeRoot = TreeName.root("benchmark");
    treeParent = treeRoot.child("level1").child("level2");
  }

  // =========================================================================
  // Chaining benchmarks - the hot path
  // =========================================================================

  @Benchmark
  public Name nano_chain_single() {
    return nanoParent.name("child");
  }

  @Benchmark
  public TreeName tree_chain_single() {
    return treeParent.child("child");
  }

  @Benchmark
  public Name nano_chain_three() {
    return nanoRoot.name("a").name("b").name("c");
  }

  @Benchmark
  public TreeName tree_chain_three() {
    return treeRoot.child("a").child("b").child("c");
  }

  @Benchmark
  public Name nano_chain_deep() {
    return nanoRoot.name("l1").name("l2").name("l3").name("l4").name("l5");
  }

  @Benchmark
  public TreeName tree_chain_deep() {
    return treeRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  // =========================================================================
  // Parse benchmarks
  // =========================================================================

  @Benchmark
  public Name nano_parse_simple() {
    return Substrates.cortex().name("simple");
  }

  @Benchmark
  public TreeName tree_parse_simple() {
    return TreeName.parse("simple");
  }

  @Benchmark
  public Name nano_parse_path() {
    return Substrates.cortex().name("kafka.broker.messages");
  }

  @Benchmark
  public TreeName tree_parse_path() {
    return TreeName.parse("kafka.broker.messages");
  }

  @Benchmark
  public Name nano_parse_deep() {
    return Substrates.cortex().name("a.b.c.d.e.f");
  }

  @Benchmark
  public TreeName tree_parse_deep() {
    return TreeName.parse("a.b.c.d.e.f");
  }

  // =========================================================================
  // Depth benchmarks
  // =========================================================================

  @Benchmark
  public int nano_depth() {
    return nanoParent.depth();
  }

  @Benchmark
  public int tree_depth() {
    return treeParent.depth();
  }

  // =========================================================================
  // Enclosure (parent) benchmarks
  // =========================================================================

  @Benchmark
  public void nano_enclosure(Blackhole bh) {
    bh.consume(nanoParent.enclosure());
  }

  @Benchmark
  public void tree_enclosure(Blackhole bh) {
    bh.consume(treeParent.enclosure());
  }

  // =========================================================================
  // Iteration benchmarks
  // =========================================================================

  @Benchmark
  public void nano_iterate_hierarchy(Blackhole bh) {
    nanoParent.forEach(bh::consume);
  }

  @Benchmark
  public void tree_iterate_hierarchy(Blackhole bh) {
    treeParent.forEach(bh::consume);
  }

  // =========================================================================
  // Compare benchmarks
  // =========================================================================

  @Benchmark
  public int nano_compare_same() {
    return ((Comparable<Name>) nanoParent).compareTo(nanoParent);
  }

  @Benchmark
  public int tree_compare_same() {
    return treeParent.compareTo(treeParent);
  }

  // =========================================================================
  // Main runner
  // =========================================================================

  public static void main(String[] args) throws java.lang.Exception {
    Options opt = new OptionsBuilder()
        .include(NameComparisonBenchmark.class.getSimpleName())
        .build();
    new Runner(opt).run();
  }
}
