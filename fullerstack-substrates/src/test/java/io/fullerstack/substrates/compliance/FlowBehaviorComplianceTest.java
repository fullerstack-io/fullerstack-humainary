package io.fullerstack.substrates.compliance;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.humainary.substrates.api.Substrates.Composer;
import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compliance tests for Flow interface verifying documented behaviors from Substrates.java {@snippet} examples.
 * <p>
 * **Documented Behaviors Being Verified:**
 * <ul>
 *   <li>**guard()**: Filtering emissions based on predicate</li>
 *   <li>**limit()**: Capping emission count</li>
 *   <li>**replace()**: Mapping/transforming values</li>
 *   <li>**reduce()**: Accumulating state</li>
 *   <li>**diff()**: Change detection (deduplicate consecutive)</li>
 *   <li>**sample()**: Periodic sampling (every nth emission)</li>
 *   <li>**skip()**: Skipping initial emissions</li>
 *   <li>**sift()**: Comparator-based filtering</li>
 *   <li>**Chaining**: Multiple transformations compose correctly</li>
 * </ul>
 */
class FlowBehaviorComplianceTest {

  private Circuit circuit;

  @AfterEach
  void cleanup() {
    if (circuit != null) {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: guard() filters emissions
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.guard(value -> value > 0)
   *   pipe.emit(-5);  // filtered
   *   pipe.emit(10);  // passes
   * </pre>
   */
  @Test
  void testGuard_FiltersEmissionsBasedOnPredicate() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe(
        (Flow<Integer> flow) -> flow.guard((Integer value) -> value > 0)
      )
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(-5);
    pipe.emit(10);
    pipe.emit(-3);
    pipe.emit(20);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(10, 20);
  }

  /**
   * **Documented Behavior**: limit() caps emission count
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.limit(3)
   *   pipe.emit(1);  // passes (1/3)
   *   pipe.emit(2);  // passes (2/3)
   *   pipe.emit(3);  // passes (3/3)
   *   pipe.emit(4);  // blocked
   * </pre>
   */
  @Test
  void testLimit_CapsEmissionCount() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(3);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.limit(3))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(1);
    pipe.emit(2);
    pipe.emit(3);
    pipe.emit(4);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(1, 2, 3);
  }

  /**
   * **Documented Behavior**: replace() transforms values
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.replace(n -> n * 2)
   *   pipe.emit(5);   // → 10
   *   pipe.emit(10);  // → 20
   * </pre>
   */
  @Test
  void testReplace_TransformsValues() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.replace((Integer n) -> n * 2))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(5);
    pipe.emit(10);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(10, 20);
  }

  /**
   * **Documented Behavior**: reduce() accumulates state
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.reduce(0, Integer::sum)
   *   pipe.emit(1);  // → 1 (0+1)
   *   pipe.emit(2);  // → 3 (1+2)
   *   pipe.emit(3);  // → 6 (3+3)
   * </pre>
   */
  @Test
  void testReduce_AccumulatesState() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(3);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.reduce(0, Integer::sum))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("accumulator"));
    pipe.emit(1);
    pipe.emit(2);
    pipe.emit(3);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(1, 3, 6);
  }

  /**
   * **Documented Behavior**: diff() detects changes
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.diff()
   *   pipe.emit(1);  // passes (first)
   *   pipe.emit(1);  // filtered (duplicate)
   *   pipe.emit(2);  // passes (changed)
   * </pre>
   */
  @Test
  void testDiff_DetectsChanges() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(3);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.diff())
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(1);
    pipe.emit(1);
    pipe.emit(2);
    pipe.emit(2);
    pipe.emit(1);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(1, 2, 1);
  }

  /**
   * **Documented Behavior**: sample() passes every nth emission
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.sample(3)
   *   pipe.emit(1);  // filtered (1st)
   *   pipe.emit(2);  // filtered (2nd)
   *   pipe.emit(3);  // passes (3rd)
   * </pre>
   */
  @Test
  void testSample_PassesEveryNthEmission() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.sample(3))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(1);
    pipe.emit(2);
    pipe.emit(3);
    pipe.emit(4);
    pipe.emit(5);
    pipe.emit(6);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(3, 6);
  }

  /**
   * **Documented Behavior**: skip() skips initial emissions
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.skip(2)
   *   pipe.emit(1);  // skipped
   *   pipe.emit(2);  // skipped
   *   pipe.emit(3);  // passes
   * </pre>
   */
  @Test
  void testSkip_SkipsInitialEmissions() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.skip(2))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(1);
    pipe.emit(2);
    pipe.emit(3);
    pipe.emit(4);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(3, 4);
  }

  /**
   * **Documented Behavior**: sift() with comparator filtering
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.sift(Integer::compareTo, sift -> sift.above(5))
   *   pipe.emit(3);   // filtered (not above 5)
   *   pipe.emit(10);  // passes (above 5)
   * </pre>
   */
  @Test
  void testSift_ComparatorBasedFiltering() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe((Flow<Integer> flow) -> flow.sift(Integer::compareTo, sift -> sift.above(5)))
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(3);
    pipe.emit(5);
    pipe.emit(10);
    pipe.emit(2);
    pipe.emit(7);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(10, 7);
  }

  /**
   * **Documented Behavior**: Chained transformations compose correctly
   * <p>
   * From {@snippet}:
   * <pre>
   *   flow.guard(...).replace(...).limit(...)
   *   Transformations apply in order
   * </pre>
   */
  @Test
  void testChainedTransformations_ComposeCorrectly() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(3);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe(
        (Flow<Integer> flow) -> flow
          .guard((Integer n) -> n > 0)
          .replace((Integer n) -> n * 2)
          .limit(3)
      )
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<Integer> pipe = conduit.percept(cortex().name("source"));
    pipe.emit(-5);
    pipe.emit(1);
    pipe.emit(5);
    pipe.emit(-3);
    pipe.emit(7);
    pipe.emit(10);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(2, 10, 14);
  }
}
