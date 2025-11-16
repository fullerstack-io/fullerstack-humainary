package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Breakers.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Breakers;

/**
 * Demonstration of the Breakers API (PREVIEW) - Circuit Breaker Observability.
 * <p>
 * Breakers track circuit breaker state transitions for resilience observability.
 * <p>
 * Breaker Signs (6):
 * - CLOSE: Circuit closed, traffic flowing normally
 * - OPEN: Circuit opened, traffic blocked (fail fast)
 * - HALF_OPEN: Circuit testing recovery, limited traffic
 * - TRIP: Failure threshold exceeded, circuit breaking
 * - PROBE: Test request sent in half-open state
 * - RESET: Circuit manually reset to closed
 * <p>
 * Circuit Breaker State Machine:
 * <pre>
 * CLOSED ──[failures exceed threshold]──> OPEN
 *   ▲                                       │
 *   │                                       │
 *   │                                 [timeout expires]
 *   │                                       │
 *   │                                       ▼
 *   └──[success]── HALF_OPEN ◄──[test request]
 *       │
 *       └──[failure]──> OPEN
 * </pre>
 * <p>
 * Kafka Use Cases:
 * - Producer send circuit breaker (prevent backpressure cascade)
 * - Consumer lag circuit breaker (pause on overload)
 * - Broker connection circuit breaker
 * - Replication lag circuit breaker
 */
@DisplayName("Breakers API (PREVIEW) - Circuit Breaker Observability")
class BreakersApiDemoTest {

    private Circuit circuit;
    private Conduit<Breaker, Sign> breakers;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("breakers-demo"));
        breakers = circuit.conduit(
            cortex().name("breakers"),
            Breakers::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic circuit breaker lifecycle: CLOSE → TRIP → OPEN → HALF_OPEN → PROBE → CLOSE")
    void basicCircuitBreakerLifecycle() {
        Breaker circuitBreaker = breakers.percept(cortex().name("api.breaker"));

        List<Sign> timeline = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Normal lifecycle
        circuitBreaker.close();      // Initial state: closed
        circuitBreaker.trip();        // Threshold exceeded
        circuitBreaker.open();        // Circuit opened
        circuitBreaker.halfOpen();    // Timeout expired, test recovery
        circuitBreaker.probe();       // Send test request
        circuitBreaker.close();       // Recovery successful

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            Sign.CLOSE,
            Sign.TRIP,
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.PROBE,
            Sign.CLOSE
        );
    }

    @Test
    @DisplayName("Circuit breaker failure: HALF_OPEN → PROBE → OPEN (failure)")
    void circuitBreakerProbeFailure() {
        Breaker circuitBreaker = breakers.percept(cortex().name("failing.breaker"));

        List<Sign> timeline = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Probe fails, back to OPEN
        circuitBreaker.open();        // Circuit already open
        circuitBreaker.halfOpen();    // Try recovery
        circuitBreaker.probe();       // Test request
        circuitBreaker.open();        // Probe failed, back to OPEN

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.PROBE,
            Sign.OPEN
        );
    }

    @Test
    @DisplayName("Manual circuit reset: OPEN → RESET → CLOSE")
    void manualCircuitReset() {
        Breaker circuitBreaker = breakers.percept(cortex().name("manual.breaker"));

        List<Sign> timeline = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Operator manually resets circuit
        circuitBreaker.trip();        // Circuit trips
        circuitBreaker.open();        // Circuit opens
        circuitBreaker.reset();       // Operator resets
        circuitBreaker.close();       // Back to normal

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            Sign.TRIP,
            Sign.OPEN,
            Sign.RESET,
            Sign.CLOSE
        );
    }

    @Test
    @DisplayName("All 6 signs available")
    void allSignsAvailable() {
        Breaker circuitBreaker = breakers.percept(cortex().name("test.breaker"));

        List<Sign> observed = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(observed::add);
            }
        ));

        // ACT - Emit all signs
        circuitBreaker.close();
        circuitBreaker.trip();
        circuitBreaker.open();
        circuitBreaker.halfOpen();
        circuitBreaker.probe();
        circuitBreaker.reset();

        circuit.await();

        // ASSERT - All 6 signs emitted
        assertThat(observed).containsExactly(
            Sign.CLOSE,
            Sign.TRIP,
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.PROBE,
            Sign.RESET
        );

        // ASSERT - Enum has exactly 6 values
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(6);
        assertThat(allSigns).contains(
            Sign.CLOSE,
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.TRIP,
            Sign.PROBE,
            Sign.RESET
        );
    }

    @Test
    @DisplayName("Kafka producer send circuit breaker")
    void kafkaProducerSendCircuitBreaker() {
        Breaker producerBreaker = breakers.percept(cortex().name("producer-1.send.breaker"));

        List<Sign> sendEvents = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(sendEvents::add);
            }
        ));

        // ACT - Simulate producer send failures triggering circuit breaker
        producerBreaker.close();      // Initial: sending normally
        // ... multiple send failures occur ...
        producerBreaker.trip();        // Threshold exceeded (5 consecutive failures)
        producerBreaker.open();        // Circuit breaks - fail fast
        // ... 30 seconds later ...
        producerBreaker.halfOpen();    // Test recovery
        producerBreaker.probe();       // Try one send
        producerBreaker.close();       // Success - resume sending

        circuit.await();

        // ASSERT - Circuit breaker protected producer from cascading failures
        assertThat(sendEvents).containsExactly(
            Sign.CLOSE,
            Sign.TRIP,
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.PROBE,
            Sign.CLOSE
        );
    }

    @Test
    @DisplayName("Kafka consumer lag circuit breaker")
    void kafkaConsumerLagCircuitBreaker() {
        Breaker consumerBreaker = breakers.percept(cortex().name("consumer-group-1.lag.breaker"));

        List<Sign> lagEvents = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(lagEvents::add);
            }
        ));

        // ACT - Simulate consumer lag triggering circuit breaker (pause)
        consumerBreaker.close();      // Initial: consuming normally
        // ... lag increases to 50,000 messages ...
        consumerBreaker.trip();        // Lag threshold exceeded
        consumerBreaker.open();        // Pause consumer to prevent OOM
        // ... lag decreases to 10,000 messages ...
        consumerBreaker.halfOpen();    // Test partial resume
        consumerBreaker.probe();       // Resume 1 partition
        consumerBreaker.close();       // Lag stable - resume all

        circuit.await();

        // ASSERT - Circuit breaker prevented consumer overload
        assertThat(lagEvents).containsExactly(
            Sign.CLOSE,
            Sign.TRIP,
            Sign.OPEN,
            Sign.HALF_OPEN,
            Sign.PROBE,
            Sign.CLOSE
        );
    }

    @Test
    @DisplayName("Multiple breakers operate independently")
    void multipleBreakerOperateIndependently() {
        Breaker producerBreaker = breakers.percept(cortex().name("producer.breaker"));
        Breaker consumerBreaker = breakers.percept(cortex().name("consumer.breaker"));

        List<String> events = new ArrayList<>();
        breakers.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(sign -> {
                    String entity = subject.name().toString();
                    events.add(entity + ":" + sign);
                });
            }
        ));

        // ACT - Producer breaks, consumer remains healthy
        producerBreaker.trip();
        producerBreaker.open();
        consumerBreaker.close();  // Consumer still working

        circuit.await();

        // ASSERT - Independent state tracking
        assertThat(events).containsExactly(
            "producer.breaker:TRIP",
            "producer.breaker:OPEN",
            "consumer.breaker:CLOSE"
        );
    }
}
