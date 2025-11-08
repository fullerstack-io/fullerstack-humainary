package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Counters.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Counters;

/**
 * Demonstration of the Counters API (RC6) - Monotonic increment tracking.
 * <p>
 * Counters track values that only go up (monotonic), with overflow detection.
 * <p>
 * Counter Signs (3):
 * - INCREMENT: Counter increased
 * - OVERFLOW: Counter exceeded threshold
 * - RESET: Counter reset to zero
 * <p>
 * Kafka Use Cases:
 * - Message send count
 * - Bytes sent/received
 * - Error count
 * - Request count
 */
@DisplayName("Counters API (RC6) - Monotonic Increment Tracking")
class CountersApiDemoTest {

    private Circuit circuit;
    private Conduit<Counter, Sign> counters;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("counters-demo"));
        counters = circuit.conduit(
            cortex().name("counters"),
            Counters::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic counter: INCREMENT → INCREMENT → RESET")
    void basicCounter() {
        Counter counter = counters.get(cortex().name("messages.sent"));

        List<Sign> events = new ArrayList<>();
        counters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        counter.increment();  // Message 1
        counter.increment();  // Message 2
        counter.increment();  // Message 3
        counter.reset();      // Reset count

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.INCREMENT,
            Sign.INCREMENT,
            Sign.INCREMENT,
            Sign.RESET
        );
    }

    @Test
    @DisplayName("Counter overflow")
    void counterOverflow() {
        Counter errorCount = counters.get(cortex().name("errors"));

        List<Sign> errors = new ArrayList<>();
        counters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(errors::add);
            }
        ));

        // ACT
        for (int i = 0; i < 100; i++) {
            errorCount.increment();
        }
        errorCount.overflow();  // Error threshold exceeded

        circuit.await();

        // ASSERT
        assertThat(errors).contains(Sign.OVERFLOW);
    }

    @Test
    @DisplayName("Kafka message send counter")
    void kafkaMessageSendCounter() {
        Counter messagesSent = counters.get(cortex().name("producer-1.messages-sent"));

        List<Sign> sendEvents = new ArrayList<>();
        counters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(sendEvents::add);
            }
        ));

        // ACT - Track sends
        for (int i = 0; i < 5; i++) {
            messagesSent.increment();
        }

        circuit.await();

        // ASSERT
        assertThat(sendEvents).hasSize(5);
        assertThat(sendEvents).allMatch(sign -> sign == Sign.INCREMENT);
    }

    @Test
    @DisplayName("All 3 signs available")
    void allSignsAvailable() {
        Counter counter = counters.get(cortex().name("test-counter"));

        // ACT
        counter.increment();
        counter.overflow();
        counter.reset();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(3);
        assertThat(allSigns).contains(
            Sign.INCREMENT,
            Sign.OVERFLOW,
            Sign.RESET
        );
    }
}
