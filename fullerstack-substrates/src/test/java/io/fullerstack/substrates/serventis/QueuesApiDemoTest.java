package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Queues.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Queues;

/**
 * Demonstration of the Queues API (RC6) - Queue flow control observability.
 * <p>
 * The Queues API enables observation of queue operations and flow control conditions.
 * <p>
 * Queue Signs (4):
 * - ENQUEUE: Item added to queue
 * - DEQUEUE: Item removed from queue
 * - OVERFLOW: Queue capacity exceeded (backpressure)
 * - UNDERFLOW: Queue empty when dequeue attempted
 * <p>
 * Kafka Use Cases:
 * - Producer buffer management (ENQUEUE/OVERFLOW)
 * - Consumer fetch queue (DEQUEUE/UNDERFLOW)
 * - Broker request queues (ENQUEUE/DEQUEUE)
 * - Partition replication queues
 */
@DisplayName("Queues API (RC6) - Queue Flow Control Observability")
class QueuesApiDemoTest {

    private Circuit circuit;
    private Conduit<Queue, Sign> queues;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("queues-demo"));
        queues = circuit.conduit(
            cortex().name("queues"),
            Queues::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic queue operations: ENQUEUE → DEQUEUE")
    void basicQueueOperations() {
        Queue queue = queues.percept(cortex().name("message-queue"));

        List<Sign> operations = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(operations::add);
            }
        ));

        // ACT
        queue.enqueue();   // Add item
        queue.enqueue();   // Add another
        queue.dequeue();   // Remove item
        queue.dequeue();   // Remove another

        circuit.await();

        // ASSERT
        assertThat(operations).containsExactly(
            Sign.ENQUEUE,
            Sign.ENQUEUE,
            Sign.DEQUEUE,
            Sign.DEQUEUE
        );
    }

    @Test
    @DisplayName("Queue overflow: ENQUEUE → OVERFLOW")
    void queueOverflow() {
        Queue producerBuffer = queues.percept(cortex().name("producer.buffer"));

        List<Sign> events = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT - Produce faster than consume
        for (int i = 0; i < 10; i++) {
            producerBuffer.enqueue();
        }
        producerBuffer.overflow();  // Buffer full, backpressure

        circuit.await();

        // ASSERT
        assertThat(events).contains(Sign.OVERFLOW);
    }

    @Test
    @DisplayName("Queue underflow: DEQUEUE → UNDERFLOW")
    void queueUnderflow() {
        Queue consumerQueue = queues.percept(cortex().name("consumer.queue"));

        List<Sign> events = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT - Try to dequeue from empty queue
        consumerQueue.dequeue();
        consumerQueue.underflow();  // Queue empty

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.DEQUEUE,
            Sign.UNDERFLOW
        );
    }

    @Test
    @DisplayName("Kafka producer buffer pattern")
    void kafkaProducerBuffer() {
        Queue producerBuffer = queues.percept(cortex().name("producer-1.buffer"));

        List<Sign> bufferFlow = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(bufferFlow::add);
            }
        ));

        // ACT - Producer sending messages
        producerBuffer.enqueue();   // Message 1
        producerBuffer.enqueue();   // Message 2
        producerBuffer.enqueue();   // Message 3
        producerBuffer.dequeue();   // Sent to broker
        producerBuffer.enqueue();   // Message 4
        producerBuffer.overflow();  // Buffer full, apply backpressure

        circuit.await();

        // ASSERT
        assertThat(bufferFlow).contains(
            Sign.ENQUEUE,
            Sign.DEQUEUE,
            Sign.OVERFLOW
        );
    }

    @Test
    @DisplayName("Kafka consumer fetch queue pattern")
    void kafkaConsumerFetchQueue() {
        Queue fetchQueue = queues.percept(cortex().name("consumer-1.fetch-queue"));

        List<Sign> fetchFlow = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(fetchFlow::add);
            }
        ));

        // ACT - Consumer fetching from broker
        fetchQueue.enqueue();    // Batch fetched
        fetchQueue.dequeue();    // Message consumed
        fetchQueue.dequeue();    // Message consumed
        fetchQueue.underflow();  // Fetch queue empty, trigger new fetch

        circuit.await();

        // ASSERT
        assertThat(fetchFlow).containsExactly(
            Sign.ENQUEUE,
            Sign.DEQUEUE,
            Sign.DEQUEUE,
            Sign.UNDERFLOW
        );
    }

    @Test
    @DisplayName("Multiple queues tracking")
    void multipleQueuesTracking() {
        Queue queue1 = queues.percept(cortex().name("producer-1.buffer"));
        Queue queue2 = queues.percept(cortex().name("producer-2.buffer"));

        List<String> queueEvents = new ArrayList<>();
        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    queueEvents.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT - Track both queues
        queue1.enqueue();
        queue2.enqueue();
        queue1.overflow();   // Queue 1 full
        queue2.dequeue();    // Queue 2 draining

        circuit.await();

        // ASSERT
        assertThat(queueEvents).contains(
            "producer-1.buffer:ENQUEUE",
            "producer-2.buffer:ENQUEUE",
            "producer-1.buffer:OVERFLOW",
            "producer-2.buffer:DEQUEUE"
        );
    }

    @Test
    @DisplayName("All 4 signs available")
    void allSignsAvailable() {
        Queue queue = queues.percept(cortex().name("test-queue"));

        // ACT
        queue.enqueue();
        queue.dequeue();
        queue.overflow();
        queue.underflow();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(4);
        assertThat(allSigns).contains(
            Sign.ENQUEUE,
            Sign.DEQUEUE,
            Sign.OVERFLOW,
            Sign.UNDERFLOW
        );
    }
}
