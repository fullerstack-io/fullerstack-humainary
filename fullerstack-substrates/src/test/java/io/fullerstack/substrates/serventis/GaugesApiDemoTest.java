package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Gauges.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Gauges;

/**
 * Demonstration of the Gauges API (RC6) - Value level tracking with bounds.
 * <p>
 * Gauges track values that go up and down (bidirectional), with overflow/underflow bounds.
 * <p>
 * Gauge Signs (5):
 * - INCREMENT: Value increased
 * - DECREMENT: Value decreased
 * - OVERFLOW: Value exceeded upper bound
 * - UNDERFLOW: Value fell below lower bound
 * - RESET: Value reset to baseline
 * <p>
 * Kafka Use Cases:
 * - Consumer lag (INCREMENT when lagging, DECREMENT when catching up)
 * - In-flight requests (INCREMENT on send, DECREMENT on response)
 * - Connection pool size (INCREMENT/DECREMENT)
 * - Partition replica lag
 */
@DisplayName("Gauges API (RC6) - Bidirectional Value Tracking")
class GaugesApiDemoTest {

    private Circuit circuit;
    private Conduit<Gauge, Sign> gauges;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("gauges-demo"));
        gauges = circuit.conduit(
            cortex().name("gauges"),
            Gauges::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic gauge operations: INCREMENT → DECREMENT")
    void basicGaugeOperations() {
        Gauge gauge = gauges.percept(cortex().name("connection-pool"));

        List<Sign> changes = new ArrayList<>();
        gauges.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(changes::add);
            }
        ));

        // ACT
        gauge.increment();  // Connection added
        gauge.increment();  // Another connection
        gauge.decrement();  // Connection released
        gauge.reset();      // Reset to baseline

        circuit.await();

        // ASSERT
        assertThat(changes).containsExactly(
            Sign.INCREMENT,
            Sign.INCREMENT,
            Sign.DECREMENT,
            Sign.RESET
        );
    }

    @Test
    @DisplayName("Consumer lag overflow")
    void consumerLagOverflow() {
        Gauge consumerLag = gauges.percept(cortex().name("consumer-1.lag"));

        List<Sign> lagEvents = new ArrayList<>();
        gauges.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lagEvents::add);
            }
        ));

        // ACT - Lag increasing
        consumerLag.increment();  // Lag growing
        consumerLag.increment();  // Still growing
        consumerLag.overflow();   // Lag threshold exceeded

        circuit.await();

        // ASSERT
        assertThat(lagEvents).contains(Sign.OVERFLOW);
    }

    @Test
    @DisplayName("In-flight requests tracking")
    void inflightRequestsTracking() {
        Gauge inflightRequests = gauges.percept(cortex().name("producer-1.inflight"));

        List<Sign> requestFlow = new ArrayList<>();
        gauges.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(requestFlow::add);
            }
        ));

        // ACT
        inflightRequests.increment();  // Request sent
        inflightRequests.increment();  // Another request
        inflightRequests.decrement();  // Response received
        inflightRequests.decrement();  // Another response

        circuit.await();

        // ASSERT
        assertThat(requestFlow).containsExactly(
            Sign.INCREMENT,
            Sign.INCREMENT,
            Sign.DECREMENT,
            Sign.DECREMENT
        );
    }

    @Test
    @DisplayName("Gauge underflow: value below minimum")
    void gaugeUnderflow() {
        Gauge gauge = gauges.percept(cortex().name("buffer-level"));

        List<Sign> events = new ArrayList<>();
        gauges.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        gauge.decrement();
        gauge.underflow();  // Below minimum threshold

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.DECREMENT,
            Sign.UNDERFLOW
        );
    }

    @Test
    @DisplayName("All 5 signs available")
    void allSignsAvailable() {
        Gauge gauge = gauges.percept(cortex().name("test-gauge"));

        // ACT
        gauge.increment();
        gauge.decrement();
        gauge.overflow();
        gauge.underflow();
        gauge.reset();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(5);
        assertThat(allSigns).contains(
            Sign.INCREMENT,
            Sign.DECREMENT,
            Sign.OVERFLOW,
            Sign.UNDERFLOW,
            Sign.RESET
        );
    }
}
