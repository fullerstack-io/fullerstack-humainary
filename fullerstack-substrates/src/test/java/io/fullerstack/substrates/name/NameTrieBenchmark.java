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
 * Comprehensive JMH benchmark comparing Name implementations (9 tests each):
 * - NanoName (current Fullerstack) - flat ConcurrentHashMap
 * - TreeName - ConcurrentHashMap per node (trie)
 * - DoubleArrayName - Double Array Trie
 * - HotName - Height Optimized Trie (sorted arrays by hash)
 * - ArrayName - Sorted array with binary search
 * - IdentityName - IdentityHashMap with interned strings
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class NameTrieBenchmark {

  // Pre-created names for hot-path tests (depth 3)
  private Name nanoParent;
  private TreeName treeParent;
  private DoubleArrayName doubleArrayParent;
  private HotName hotParent;
  private ArrayName arrayParent;
  private IdentityName identityParent;

  // Root names for chaining tests
  private Name nanoRoot;
  private TreeName treeRoot;
  private DoubleArrayName doubleArrayRoot;
  private HotName hotRoot;
  private ArrayName arrayRoot;
  private IdentityName identityRoot;

  @Setup(Level.Trial)
  public void setup() {
    // Create root names
    nanoRoot = Substrates.cortex().name("benchmark");
    treeRoot = TreeName.root("benchmark");
    doubleArrayRoot = DoubleArrayName.root("benchmark");
    hotRoot = HotName.root("benchmark");
    arrayRoot = ArrayName.root("benchmark");
    identityRoot = IdentityName.root("benchmark");

    // Create parent names at depth 3 for chaining tests
    nanoParent = nanoRoot.name("level1").name("level2");
    treeParent = treeRoot.child("level1").child("level2");
    doubleArrayParent = doubleArrayRoot.child("level1").child("level2");
    hotParent = hotRoot.child("level1").child("level2");
    arrayParent = arrayRoot.child("level1").child("level2");
    identityParent = identityRoot.child("level1").child("level2");
  }

  // =========================================================================
  // 1. Chain single child (the hot path)
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
  public DoubleArrayName doubleArray_chain_single() {
    return doubleArrayParent.child("child");
  }

  @Benchmark
  public HotName hot_chain_single() {
    return hotParent.child("child");
  }

  @Benchmark
  public ArrayName array_chain_single() {
    return arrayParent.child("child");
  }

  @Benchmark
  public IdentityName identity_chain_single() {
    return identityParent.child("child");
  }

  // =========================================================================
  // 2. Chain three levels
  // =========================================================================

  @Benchmark
  public Name nano_chain_three() {
    return nanoRoot.name("a").name("b").name("c");
  }

  @Benchmark
  public TreeName tree_chain_three() {
    return treeRoot.child("a").child("b").child("c");
  }

  @Benchmark
  public DoubleArrayName doubleArray_chain_three() {
    return doubleArrayRoot.child("a").child("b").child("c");
  }

  @Benchmark
  public HotName hot_chain_three() {
    return hotRoot.child("a").child("b").child("c");
  }

  @Benchmark
  public ArrayName array_chain_three() {
    return arrayRoot.child("a").child("b").child("c");
  }

  @Benchmark
  public IdentityName identity_chain_three() {
    return identityRoot.child("a").child("b").child("c");
  }

  // =========================================================================
  // 3. Chain deep (5 levels)
  // =========================================================================

  @Benchmark
  public Name nano_chain_deep() {
    return nanoRoot.name("l1").name("l2").name("l3").name("l4").name("l5");
  }

  @Benchmark
  public TreeName tree_chain_deep() {
    return treeRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  @Benchmark
  public DoubleArrayName doubleArray_chain_deep() {
    return doubleArrayRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  @Benchmark
  public HotName hot_chain_deep() {
    return hotRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  @Benchmark
  public ArrayName array_chain_deep() {
    return arrayRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  @Benchmark
  public IdentityName identity_chain_deep() {
    return identityRoot.child("l1").child("l2").child("l3").child("l4").child("l5");
  }

  // =========================================================================
  // 4. Parse simple (single segment)
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
  public DoubleArrayName doubleArray_parse_simple() {
    return DoubleArrayName.parse("simple");
  }

  @Benchmark
  public HotName hot_parse_simple() {
    return HotName.parse("simple");
  }

  @Benchmark
  public ArrayName array_parse_simple() {
    return ArrayName.parse("simple");
  }

  @Benchmark
  public IdentityName identity_parse_simple() {
    return IdentityName.parse("simple");
  }

  // =========================================================================
  // 5. Parse path (3 segments)
  // =========================================================================

  @Benchmark
  public Name nano_parse_path() {
    return Substrates.cortex().name("kafka.broker.messages");
  }

  @Benchmark
  public TreeName tree_parse_path() {
    return TreeName.parse("kafka.broker.messages");
  }

  @Benchmark
  public DoubleArrayName doubleArray_parse_path() {
    return DoubleArrayName.parse("kafka.broker.messages");
  }

  @Benchmark
  public HotName hot_parse_path() {
    return HotName.parse("kafka.broker.messages");
  }

  @Benchmark
  public ArrayName array_parse_path() {
    return ArrayName.parse("kafka.broker.messages");
  }

  @Benchmark
  public IdentityName identity_parse_path() {
    return IdentityName.parse("kafka.broker.messages");
  }

  // =========================================================================
  // 6. Parse deep (6 segments)
  // =========================================================================

  @Benchmark
  public Name nano_parse_deep() {
    return Substrates.cortex().name("a.b.c.d.e.f");
  }

  @Benchmark
  public TreeName tree_parse_deep() {
    return TreeName.parse("a.b.c.d.e.f");
  }

  @Benchmark
  public DoubleArrayName doubleArray_parse_deep() {
    return DoubleArrayName.parse("a.b.c.d.e.f");
  }

  @Benchmark
  public HotName hot_parse_deep() {
    return HotName.parse("a.b.c.d.e.f");
  }

  @Benchmark
  public ArrayName array_parse_deep() {
    return ArrayName.parse("a.b.c.d.e.f");
  }

  @Benchmark
  public IdentityName identity_parse_deep() {
    return IdentityName.parse("a.b.c.d.e.f");
  }

  // =========================================================================
  // 7. Depth access
  // =========================================================================

  @Benchmark
  public int nano_depth() {
    return nanoParent.depth();
  }

  @Benchmark
  public int tree_depth() {
    return treeParent.depth();
  }

  @Benchmark
  public int doubleArray_depth() {
    return doubleArrayParent.depth();
  }

  @Benchmark
  public int hot_depth() {
    return hotParent.depth();
  }

  @Benchmark
  public int array_depth() {
    return arrayParent.depth();
  }

  @Benchmark
  public int identity_depth() {
    return identityParent.depth();
  }

  // =========================================================================
  // 8. Enclosure (parent) access
  // =========================================================================

  @Benchmark
  public void nano_enclosure(Blackhole bh) {
    bh.consume(nanoParent.enclosure());
  }

  @Benchmark
  public void tree_enclosure(Blackhole bh) {
    bh.consume(treeParent.enclosure());
  }

  @Benchmark
  public void doubleArray_enclosure(Blackhole bh) {
    bh.consume(doubleArrayParent.enclosure());
  }

  @Benchmark
  public void hot_enclosure(Blackhole bh) {
    bh.consume(hotParent.enclosure());
  }

  @Benchmark
  public void array_enclosure(Blackhole bh) {
    bh.consume(arrayParent.enclosure());
  }

  @Benchmark
  public void identity_enclosure(Blackhole bh) {
    bh.consume(identityParent.enclosure());
  }

  // =========================================================================
  // 9. Compare same (identity check)
  // =========================================================================

  @Benchmark
  @SuppressWarnings("unchecked")
  public int nano_compare_same() {
    return ((Comparable<Name>) nanoParent).compareTo(nanoParent);
  }

  @Benchmark
  public int tree_compare_same() {
    return treeParent.compareTo(treeParent);
  }

  @Benchmark
  public int doubleArray_compare_same() {
    return doubleArrayParent.compareTo(doubleArrayParent);
  }

  @Benchmark
  public int hot_compare_same() {
    return hotParent.compareTo(hotParent);
  }

  @Benchmark
  public int array_compare_same() {
    return arrayParent.compareTo(arrayParent);
  }

  @Benchmark
  public int identity_compare_same() {
    return identityParent.compareTo(identityParent);
  }

  // =========================================================================
  // Main runner
  // =========================================================================

  public static void main(String[] args) throws java.lang.Exception {
    Options opt = new OptionsBuilder()
        .include(NameTrieBenchmark.class.getSimpleName())
        .build();
    new Runner(opt).run();
  }
}
