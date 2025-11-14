package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Probes.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Probes;

/**
 * Demonstration of the Probes API (RC6) - Communication operation observability.
 * <p>
 * The Probes API enables observation of communication operations and outcomes from
 * dual perspectives: RELEASE (self) and RECEIPT (observed).
 * <p>
 * Key Concepts:
 * - Signs represent operation types (CONNECT, TRANSMIT, RECEIVE, etc.)
 * - Polaritys represent perspective (RELEASE = self, RECEIPT = observed)
 * - Signals combine Sign + Polarity for complete observability
 * <p>
 * Communication Signs (7):
 * - CONNECT/CONNECTED: Connection establishment
 * - DISCONNECT/DISCONNECTED: Connection closure
 * - TRANSMIT/TRANSMITTED: Data transmission
 * - RECEIVE/RECEIVED: Data reception
 * - PROCESS/PROCESSED: Data processing
 * - SUCCEED/SUCCEEDED: Successful completion
 * - FAIL/FAILED: Failed completion
 * <p>
 * Kafka Use Cases:
 * - Producer send operations (TRANSMIT/TRANSMITTED)
 * - Consumer fetch operations (RECEIVE/RECEIVED)
 * - Broker connection management (CONNECT/DISCONNECT)
 * - Request/response success/failure tracking
 */
@DisplayName("Probes API (RC6) - Communication Operation Observability")
class ProbesApiDemoTest {

    private Circuit circuit;
    private Conduit<Probe, Signal> probes;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("probes-demo"));
        probes = circuit.conduit(
            cortex().name("probes"),
            Probes::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic RPC flow: CONNECT → TRANSMIT → RECEIVE → SUCCEED → DISCONNECT")
    void basicRPCFlow() {
        // Scenario: Successful RPC call from client perspective (RELEASE)

        Probe rpcClient = probes.percept(cortex().name("rpc.client"));

        List<Signal> clientOps = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(clientOps::add);
            }
        ));

        // ACT: Client performs RPC

        rpcClient.connect();      // I am connecting
        rpcClient.transmit();     // I am transmitting request
        rpcClient.receive();      // I am receiving response
        rpcClient.succeed();      // I succeeded
        rpcClient.disconnect();   // I am disconnecting

        circuit.await();

        // ASSERT: Operation sequence captured
        assertThat(clientOps).containsExactly(
            Signal.CONNECT,
            Signal.TRANSMIT,
            Signal.RECEIVE,
            Signal.SUCCEED,
            Signal.DISCONNECT
        );
    }

    @Test
    @DisplayName("Dual perspective: RELEASE (self) vs RECEIPT (observed)")
    void dualPerspective() {
        // RELEASE = self-perspective (present tense: "I am doing")
        // RECEIPT = observed-perspective (past tense: "It did")

        Probe clientProbe = probes.percept(cortex().name("client"));
        Probe serverProbe = probes.percept(cortex().name("server"));

        List<String> timeline = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    String perspective = signal.dimension() == Dimension.RELEASE ? "SELF" : "OBSERVED";
                    timeline.add(subject.name() + ":" + signal.sign() + ":" + perspective);
                });
            }
        ));

        // ACT: Client-server interaction

        // Client connects (self-perspective)
        clientProbe.connect();      // RELEASE: "I am connecting"

        // Server observes connection (observed-perspective)
        serverProbe.connected();    // RECEIPT: "It connected"

        // Client transmits (self-perspective)
        clientProbe.transmit();     // RELEASE: "I am transmitting"

        // Server observes transmission (observed-perspective)
        serverProbe.transmitted();  // RECEIPT: "It transmitted"

        circuit.await();

        // ASSERT: Both perspectives captured
        assertThat(timeline).contains(
            "client:CONNECT:SELF",
            "server:CONNECT:OBSERVED",
            "client:TRANSMIT:SELF",
            "server:TRANSMIT:OBSERVED"
        );
    }

    @Test
    @DisplayName("Failure tracking: FAIL vs FAILED")
    void failureTracking() {
        // Scenario: Client detects failure vs observing server failure

        Probe client = probes.percept(cortex().name("client"));
        Probe server = probes.percept(cortex().name("server"));

        List<Signal> failures = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> {
                    if (signal.sign() == Sign.FAIL) {
                        failures.add(signal);
                    }
                });
            }
        ));

        // ACT: Different failure perspectives

        client.fail();      // RELEASE: "I failed" (client-side error)
        server.failed();    // RECEIPT: "It failed" (observed client failure)

        circuit.await();

        // ASSERT: Both failure signals captured
        assertThat(failures).hasSize(2);
        assertThat(failures.get(0).dimension()).isEqualTo(Dimension.RELEASE);
        assertThat(failures.get(1).dimension()).isEqualTo(Dimension.RECEIPT);
    }

    @Test
    @DisplayName("Kafka producer send pattern")
    void kafkaProducerSend() {
        // Scenario: Producer sends message to broker

        Probe producer = probes.percept(cortex().name("producer-1"));
        Probe broker = probes.percept(cortex().name("broker-1"));

        List<String> sendFlow = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    sendFlow.add(subject.name() + ":" + signal);
                });
            }
        ));

        // ACT: Producer send operation

        // Producer perspective (RELEASE)
        producer.connect();      // Connect to broker
        producer.transmit();     // Send message
        producer.receive();      // Receive ack
        producer.succeed();      // Send succeeded

        // Broker perspective (RECEIPT)
        broker.connected();      // Producer connected
        broker.transmitted();    // Message received from producer
        broker.processed();      // Message processed

        circuit.await();

        // ASSERT: Complete send flow tracked
        assertThat(sendFlow).contains(
            "producer-1:CONNECT",
            "producer-1:TRANSMIT",
            "broker-1:CONNECTED",
            "broker-1:TRANSMITTED",
            "broker-1:PROCESSED"
        );
    }

    @Test
    @DisplayName("Kafka consumer fetch pattern")
    void kafkaConsumerFetch() {
        // Scenario: Consumer fetches messages from broker

        Probe consumer = probes.percept(cortex().name("consumer-1"));
        Probe broker = probes.percept(cortex().name("broker-1"));

        List<Sign> consumerOps = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    if (subject.name().toString().contains("consumer")) {
                        consumerOps.add(signal.sign());
                    }
                });
            }
        ));

        // ACT: Consumer fetch operation

        consumer.connect();      // Connect to broker
        consumer.transmit();     // Send fetch request
        consumer.receive();      // Receive message batch
        consumer.process();      // Process messages
        consumer.succeed();      // Fetch succeeded

        circuit.await();

        // ASSERT: Fetch cycle captured
        assertThat(consumerOps).containsExactly(
            Sign.CONNECT,
            Sign.TRANSMIT,    // Fetch request
            Sign.RECEIVE,     // Message batch
            Sign.PROCESS,     // Message processing
            Sign.SUCCEED
        );
    }

    @Test
    @DisplayName("Connection failure pattern")
    void connectionFailure() {
        // Scenario: Client fails to connect to server

        Probe client = probes.percept(cortex().name("client"));

        AtomicReference<Signal> lastSignal = new AtomicReference<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(lastSignal::set);
            }
        ));

        // ACT: Connection failure

        client.connect();        // Attempting to connect
        client.fail();           // Connection failed
        client.disconnect();     // Clean up connection

        circuit.await();

        // ASSERT: Failure signal emitted
        assertThat(lastSignal.get()).isEqualTo(Signal.DISCONNECT);
    }

    @Test
    @DisplayName("Processing pipeline: RECEIVE → PROCESS → SUCCEED")
    void processingPipeline() {
        // Scenario: Message processing pipeline

        Probe processor = probes.percept(cortex().name("message.processor"));

        List<Signal> pipeline = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(pipeline::add);
            }
        ));

        // ACT: Process messages

        processor.receive();     // Received message
        processor.process();     // Processing message
        processor.succeed();     // Processing succeeded

        circuit.await();

        // ASSERT: Processing flow tracked
        assertThat(pipeline).containsExactly(
            Signal.RECEIVE,
            Signal.PROCESS,
            Signal.SUCCEED
        );
    }

    @Test
    @DisplayName("Observed server-side operations")
    void observedServerOperations() {
        // Scenario: Monitoring server from external receptor

        Probe serverMonitor = probes.percept(cortex().name("server.monitor"));

        List<Signal> observations = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(observations::add);
            }
        ));

        // ACT: Observe server operations (all RECEIPT)

        serverMonitor.connected();      // Server accepted connection
        serverMonitor.transmitted();    // Server sent response
        serverMonitor.succeeded();      // Server completed successfully
        serverMonitor.disconnected();   // Server closed connection

        circuit.await();

        // ASSERT: All observations are RECEIPT (observed)
        assertThat(observations).allMatch(
            signal -> signal.dimension() == Dimension.RECEIPT
        );
    }

    @Test
    @DisplayName("Bidirectional communication pattern")
    void bidirectionalCommunication() {
        // Scenario: Request-response with both sides transmitting

        Probe client = probes.percept(cortex().name("client"));
        Probe server = probes.percept(cortex().name("server"));

        List<String> communication = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    String actor = subject.name().toString().contains("client") ? "CLIENT" : "SERVER";
                    communication.add(actor + ":" + signal.sign() + ":" + signal.dimension());
                });
            }
        ));

        // ACT: Bidirectional flow

        // Client sends request
        client.transmit();          // RELEASE: Client transmits
        server.transmitted();       // RECEIPT: Server observes transmission

        // Server sends response
        server.transmit();          // RELEASE: Server transmits
        client.transmitted();       // RECEIPT: Client observes transmission

        circuit.await();

        // ASSERT: Bidirectional pattern captured
        assertThat(communication).containsExactly(
            "CLIENT:TRANSMIT:RELEASE",
            "SERVER:TRANSMIT:RECEIPT",
            "SERVER:TRANSMIT:RELEASE",
            "CLIENT:TRANSMIT:RECEIPT"
        );
    }

    @Test
    @DisplayName("All 7 signs and 2 orientations available")
    void allSignsAndPolaritysAvailable() {
        // Verify complete API surface

        Probe probe = probes.percept(cortex().name("test-probe"));

        // ACT: Emit all signs in both orientations

        // RELEASE orientation (self-perspective)
        probe.connect();
        probe.disconnect();
        probe.transmit();
        probe.receive();
        probe.process();
        probe.succeed();
        probe.fail();

        // RECEIPT orientation (observed-perspective)
        probe.connected();
        probe.disconnected();
        probe.transmitted();
        probe.received();
        probe.processed();
        probe.succeeded();
        probe.failed();

        circuit.await();

        // ASSERT: All sign types exist
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(7);
        assertThat(allSigns).contains(
            Sign.CONNECT,
            Sign.DISCONNECT,
            Sign.TRANSMIT,
            Sign.RECEIVE,
            Sign.PROCESS,
            Sign.SUCCEED,
            Sign.FAIL
        );

        // ASSERT: Both orientations exist
        Dimension[] allDimensions = Dimension.values();
        assertThat(allDimensions).hasSize(2);
        assertThat(allDimensions).contains(
            Dimension.RELEASE,
            Dimension.RECEIPT
        );

        // ASSERT: All 14 signals exist (7 signs × 2 orientations)
        Signal[] allSignals = Signal.values();
        assertThat(allSignals).hasSize(14);
    }

    @Test
    @DisplayName("Signal properties: sign() and orientation()")
    void signalProperties() {
        // Verify Signal enum provides access to constituent parts

        Probe probe = probes.percept(cortex().name("test-probe"));

        AtomicReference<Signal> capturedSignal = new AtomicReference<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(capturedSignal::set);
            }
        ));

        // ACT: Emit a signal
        probe.transmit();  // TRANSMIT (Sign.TRANSMIT, Dimension.RELEASE)

        circuit.await();

        // ASSERT: Signal properties accessible
        Signal signal = capturedSignal.get();
        assertThat(signal.sign()).isEqualTo(Sign.TRANSMIT);
        assertThat(signal.dimension()).isEqualTo(Dimension.RELEASE);
    }
}
