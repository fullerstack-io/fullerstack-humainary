package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Sift;

/**
 * Tests to verify the @Fluent annotation contract from Substrates API.
 *
 * <p>The @Fluent annotation indicates that a method returns `this` (the same instance) to enable
 * fluent method chaining. This is a critical contract that ensures:
 *
 * <ul>
 *   <li>The method returns the same instance it was called on
 *   <li>Method chaining is supported (e.g., flow.diff().limit(10).sample(5))
 *   <li>No new wrapper objects are created for each chained call
 * </ul>
 *
 * <p>@Fluent annotated methods are found in:
 *
 * <ul>
 *   <li>{@link Flow} - diff(), guard(), limit(), peek(), reduce(), replace(), sample(), sift(),
 *       skip()
 *   <li>{@link Sift} - above(), below(), high(), low(), max(), min(), range()
 * </ul>
 */
@DisplayName("@Fluent Annotation Contract Tests")
class FluentAnnotationContractTest {

  private Circuit circuit;
  private Name testName;

  @BeforeEach
  void setUp() {
    testName = cortex().name("test");
    circuit = cortex().circuit(testName);
  }

  @AfterEach
  void tearDown() {
    if (circuit != null) {
      circuit.close();
    }
  }

  // ============================================================
  // Flow @Fluent methods
  // ============================================================

  @Nested
  @DisplayName("Flow @Fluent methods")
  class FlowFluentMethods {

    @Test
    @DisplayName("Flow.diff() returns same instance")
    void diff_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.diff();
                assertSame(original, result, "diff() must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.diff(initial) returns same instance")
    void diffWithInitial_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.diff(0);
                assertSame(original, result, "diff(initial) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.guard(predicate) returns same instance")
    void guard_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.guard(v -> v > 0);
                assertSame(original, result, "guard(predicate) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.guard(initial, predicate) returns same instance")
    void guardWithInitial_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.guard(0, (prev, curr) -> !prev.equals(curr));
                assertSame(
                    original, result, "guard(initial, predicate) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.limit(int) returns same instance")
    void limitInt_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.limit(10);
                assertSame(original, result, "limit(int) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.limit(long) returns same instance")
    void limitLong_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.limit(100L);
                assertSame(original, result, "limit(long) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.peek(receptor) returns same instance")
    void peek_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.peek(v -> {});
                assertSame(original, result, "peek(receptor) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.reduce(initial, operator) returns same instance")
    void reduce_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.reduce(0, Integer::sum);
                assertSame(
                    original, result, "reduce(initial, operator) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.replace(transformer) returns same instance")
    void replace_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.replace(v -> v * 2);
                assertSame(original, result, "replace(transformer) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.sample(int) returns same instance")
    void sampleInt_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.sample(5);
                assertSame(original, result, "sample(int) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.sample(double) returns same instance")
    void sampleDouble_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.sample(0.5);
                assertSame(original, result, "sample(double) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.skip(n) returns same instance")
    void skip_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.skip(5);
                assertSame(original, result, "skip(n) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Flow.sift(comparator, configurer) returns same instance")
    void sift_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result = flow.sift(Comparator.naturalOrder(), sift -> sift.high());
                assertSame(
                    original,
                    result,
                    "sift(comparator, configurer) must return same Flow instance");
              });
    }

    @Test
    @DisplayName("Fluent method chaining works correctly")
    void fluentChaining_worksCorrectly() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result =
                    flow.diff().limit(100).sample(10).skip(5).peek(v -> {}).replace(v -> v * 2);
                assertSame(original, result, "Chained fluent methods must return same instance");
              });
    }
  }

  // ============================================================
  // Sift @Fluent methods
  // ============================================================

  @Nested
  @DisplayName("Sift @Fluent methods")
  class SiftFluentMethods {

    @Test
    @DisplayName("Sift.above(lower) returns same instance")
    void above_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.above(10);
                      assertSame(original, result, "above(lower) must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.below(upper) returns same instance")
    void below_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.below(100);
                      assertSame(original, result, "below(upper) must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.high() returns same instance")
    void high_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.high();
                      assertSame(original, result, "high() must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.low() returns same instance")
    void low_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.low();
                      assertSame(original, result, "low() must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.max(max) returns same instance")
    void max_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.max(100);
                      assertSame(original, result, "max(max) must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.min(min) returns same instance")
    void min_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.min(0);
                      assertSame(original, result, "min(min) must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift.range(lower, upper) returns same instance")
    void range_returnsSameInstance() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result = sift.range(10, 100);
                      assertSame(
                          original, result, "range(lower, upper) must return same Sift instance");
                    });
              });
    }

    @Test
    @DisplayName("Sift fluent method chaining works correctly")
    void siftChaining_worksCorrectly() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                flow.sift(
                    Comparator.naturalOrder(),
                    sift -> {
                      Sift<Integer> original = sift;
                      Sift<Integer> result =
                          sift.min(0).max(100).above(10).below(90).high().low().range(20, 80);
                      assertSame(
                          original, result, "Chained sift methods must return same instance");
                    });
              });
    }
  }

  // ============================================================
  // Combined Flow and Sift @Fluent tests
  // ============================================================

  @Nested
  @DisplayName("Combined Flow and Sift @Fluent")
  class CombinedFluentTests {

    @Test
    @DisplayName("Complex fluent chain with sift maintains identity")
    void complexChain_maintainsIdentity() {
      Conduit<Pipe<Integer>, Integer> conduit =
          circuit.conduit(
              cortex().name("conduit"),
              Composer.pipe(),
              flow -> {
                Flow<Integer> original = flow;
                Flow<Integer> result =
                    flow.diff()
                        .limit(1000)
                        .sift(
                            Comparator.naturalOrder(),
                            sift -> {
                              Sift<Integer> siftOriginal = sift;
                              Sift<Integer> siftResult = sift.min(0).max(100).high();
                              assertSame(
                                  siftOriginal,
                                  siftResult,
                                  "Sift chain must return same instance");
                            })
                        .sample(10)
                        .skip(5);
                assertSame(original, result, "Flow chain with sift must return same instance");
              });
    }
  }
}
