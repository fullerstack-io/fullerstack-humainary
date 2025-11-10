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
 * Compliance tests for Conduit interface verifying documented behaviors from Substrates.java {@snippet} examples.
 * <p>
 * **Documented Behaviors Being Verified:**
 * <ul>
 *   <li>**Dynamic Channel Creation**: get() creates channels on demand</li>
 *   <li>**Channel Routing**: Each channel routes to subscribers with correct Subject</li>
 *   <li>**Composer Application**: Transformations apply to all channels</li>
 *   <li>**Multiple Subscribers**: All subscribers receive emissions</li>
 *   <li>**Channel Caching**: Same name returns same channel instance</li>
 * </ul>
 */
class ConduitBehaviorComplianceTest {

  private Circuit circuit;

  @AfterEach
  void cleanup() {
    if (circuit != null) {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: Dynamic channel creation and routing
   * <p>
   * From {@snippet}:
   * <pre>
   *   Pipe sensor1 = conduit.percept("sensor-1");
   *   Pipe sensor2 = conduit.percept("sensor-2");
   *   sensor1.emit(100);  // → subscribers receive 100 from sensor-1
   *   sensor2.emit(200);  // → subscribers receive 200 from sensor-2
   * </pre>
   */
  @Test
  void testDynamicChannelCreation_AndRoutingToSubscribers() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    List<String> channelNames = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("sensors"),
      Composer.pipe()
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber"),
      (Subject<Channel<Integer>> subject, Registrar<Integer> registrar) -> {
        registrar.register(emission -> {
          received.add(emission);
          channelNames.add(subject.name().toString());
          latch.countDown();
        });
      }
    ));

    // Create dynamic channels
    Pipe<Integer> sensor1 = conduit.percept(cortex().name("sensor-1"));
    Pipe<Integer> sensor2 = conduit.percept(cortex().name("sensor-2"));

    sensor1.emit(100);
    sensor2.emit(200);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Correct routing with channel names
    assertThat(received).containsExactlyInAnyOrder(100, 200);
    assertThat(channelNames).containsExactlyInAnyOrder("sensor-1", "sensor-2");
  }

  /**
   * **Documented Behavior**: Composer transformations apply to all channels
   * <p>
   * From {@snippet}:
   * <pre>
   *   Conduit with doubling transformation
   *   All channels created from this conduit double their values
   * </pre>
   */
  @Test
  void testComposerTransformations_ApplyToAllChannels() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("metrics"),
      Composer.pipe(
        (Flow<Integer> path) -> path.replace((Integer n) -> n * 2)
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

    Pipe<Integer> cpu = conduit.percept(cortex().name("cpu"));
    Pipe<Integer> memory = conduit.percept(cortex().name("memory"));

    cpu.emit(50);
    memory.emit(25);

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Both values doubled
    assertThat(received).containsExactlyInAnyOrder(100, 50);
  }

  /**
   * **Documented Behavior**: Multiple subscribers receive same emissions
   * <p>
   * From {@snippet}:
   * <pre>
   *   conduit.subscribe(subscriber1);
   *   conduit.subscribe(subscriber2);
   *   pipe.emit("value");  // → both receive "value"
   * </pre>
   */
  @Test
  void testMultipleSubscribers_AllReceiveEmissions() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<String> subscriber1Received = new ArrayList<>();
    List<String> subscriber2Received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<String>, String> conduit = circuit.conduit(
      cortex().name("events"),
      Composer.pipe()
    );

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber1"),
      (Subject<Channel<String>> subject, Registrar<String> registrar) -> {
        registrar.register(emission -> {
          subscriber1Received.add(emission);
          latch.countDown();
        });
      }
    ));

    conduit.subscribe(cortex().subscriber(
      cortex().name("subscriber2"),
      (Subject<Channel<String>> subject, Registrar<String> registrar) -> {
        registrar.register(emission -> {
          subscriber2Received.add(emission);
          latch.countDown();
        });
      }
    ));

    Pipe<String> loginPipe = conduit.percept(cortex().name("login"));
    loginPipe.emit("user-123");

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Both subscribers received
    assertThat(subscriber1Received).containsExactly("user-123");
    assertThat(subscriber2Received).containsExactly("user-123");
  }

  /**
   * **Documented Behavior**: Channel caching - same name returns same instance
   * <p>
   * From {@snippet}:
   * <pre>
   *   Pipe sensor = conduit.percept("temp-1");
   *   Pipe same = conduit.percept("temp-1");
   *   // sensor == same
   * </pre>
   */
  @Test
  void testChannelCaching_SameNameReturnsSameInstance() {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("sensors"),
      Composer.pipe()
    );

    Pipe<Integer> sensor1 = conduit.percept(cortex().name("temp-1"));
    Pipe<Integer> sensor2 = conduit.percept(cortex().name("temp-1"));

    // Verify: Same instance
    assertThat(sensor1).isSameAs(sensor2);
  }

  /**
   * **Documented Behavior**: Flow-level transformations at conduit level
   * <p>
   * From {@snippet}:
   * <pre>
   *   circuit.conduit(..., Composer.pipe(), flow -> flow.guard(...).limit(...))
   *   Flow transformations apply to all channels
   * </pre>
   */
  @Test
  void testFlowLevelTransformations_ApplyAtConduitLevel() throws InterruptedException {
    circuit = cortex().circuit(cortex().name("test-circuit"));

    List<Integer> received = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
      cortex().name("data"),
      Composer.pipe(),
      (Flow<Integer> flow) -> flow
        .guard((Integer n) -> n > 0)
        .limit(2)
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
    pipe.emit(-5);   // Filtered by guard
    pipe.emit(100);  // Passes (1/2)
    pipe.emit(200);  // Passes (2/2)
    pipe.emit(300);  // Blocked by limit

    circuit.await();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

    // Verify: Guard and limit applied
    assertThat(received).containsExactly(100, 200);
  }
}
