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
 * Demonstration of the Probes API (1.0.0-PREVIEW) - Communication operation observability.
 * <p>
 * The Probes API enables observation of communication operations and outcomes from
 * dual perspectives: OUTBOUND (self) and INBOUND (observed).
 * <p>
 * Key Concepts:
 * - Signs represent operation types (CONNECT, TRANSFER, PROCESS, etc.)
 * - Dimensions represent direction (OUTBOUND = outgoing, INBOUND = incoming)
 * - Signals combine Sign + Dimension for complete observability
 * <p>
 * Communication Signs (6): TRANSMIT + RECEIVE merged to TRANSFER
 * - CONNECT: Connection establishment
 * - DISCONNECT: Connection closure
 * - TRANSFER: Data transmission/reception (unified from TRANSMIT/RECEIVE)
 * - PROCESS: Data processing
 * - SUCCEED: Successful completion
 * - FAIL: Failed completion
 * <p>
 * Dimensions (OUTBOUND/INBOUND): Replaced RELEASE/RECEIPT
 * - OUTBOUND: Outgoing operations ("outbound connection", "outbound transfer")
 * - INBOUND: Incoming operations ("inbound connection", "inbound transfer")
 * <p>
 * Kafka Use Cases:
 * - Producer send operations (TRANSFER with OUTBOUND)
 * - Consumer fetch operations (TRANSFER with INBOUND)
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
    @DisplayName("Basic RPC flow: CONNECT → TRANSFER (out) → TRANSFER (in) → SUCCEED → DISCONNECT")
    void basicRPCFlow() {
        // Scenario: Successful RPC call from client perspective (OUTBOUND)

        Probe rpcClient = probes.percept(cortex().name("rpc.client"));

        List<Signal> clientOps = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(clientOps::add);
            }
        ));

        // ACT: Client performs RPC

        rpcClient.connect(Dimension.OUTBOUND);      // Outbound connection
        rpcClient.transfer(Dimension.OUTBOUND);     // Outbound data transfer (request)
        rpcClient.transfer(Dimension.INBOUND);      // Inbound data transfer (response)
        rpcClient.succeed(Dimension.OUTBOUND);      // Outbound success
        rpcClient.disconnect(Dimension.OUTBOUND);   // Outbound disconnection

        circuit.await();

        // ASSERT: Operation sequence captured
        assertThat(clientOps).hasSize(5);
        assertThat(clientOps.get(0).sign()).isEqualTo(Sign.CONNECT);
        assertThat(clientOps.get(1).sign()).isEqualTo(Sign.TRANSFER);
        assertThat(clientOps.get(2).sign()).isEqualTo(Sign.TRANSFER);
        assertThat(clientOps.get(3).sign()).isEqualTo(Sign.SUCCEED);
        assertThat(clientOps.get(4).sign()).isEqualTo(Sign.DISCONNECT);
    }

    @Test
    @DisplayName("Dual direction: OUTBOUND vs INBOUND")
    void dualPerspective() {
        // OUTBOUND = outgoing direction
        // INBOUND = incoming direction

        Probe clientProbe = probes.percept(cortex().name("client"));
        Probe serverProbe = probes.percept(cortex().name("server"));

        List<String> timeline = new ArrayList<>();
        probes.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    String direction = signal.dimension() == Dimension.OUTBOUND ? "OUTBOUND" : "INBOUND";
                    timeline.add(subject.name() + ":" + signal.sign() + ":" + direction);
                });
            }
        ));

        // ACT: Client-server interaction

        // Client connects outbound
        clientProbe.connect(Dimension.OUTBOUND);      // OUTBOUND: outgoing connection

        // Server receives inbound connection
        serverProbe.connect(Dimension.INBOUND);    // INBOUND: incoming connection

        // Client transfers data outbound
        clientProbe.transfer(Dimension.OUTBOUND);     // OUTBOUND: outgoing data

        // Server receives data inbound
        serverProbe.transfer(Dimension.INBOUND);  // INBOUND: incoming data

        circuit.await();

        // ASSERT: Both directions captured
        assertThat(timeline).contains(
            "client:CONNECT:OUTBOUND",
            "server:CONNECT:INBOUND",
            "client:TRANSFER:OUTBOUND",
            "server:TRANSFER:INBOUND"
        );
    }

    @Test
    @DisplayName("Failure tracking: OUTBOUND vs INBOUND failures")
    void failureTracking() {
        // Scenario: Outbound failure vs inbound failure

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

        // ACT: Different failure directions

        client.fail(Dimension.OUTBOUND);      // OUTBOUND: outgoing operation failed
        server.fail(Dimension.INBOUND);       // INBOUND: incoming operation failed

        circuit.await();

        // ASSERT: Both failure signals captured
        assertThat(failures).hasSize(2);
        assertThat(failures.get(0).dimension()).isEqualTo(Dimension.OUTBOUND);
        assertThat(failures.get(1).dimension()).isEqualTo(Dimension.INBOUND);
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
                    sendFlow.add(subject.name() + ":" + signal.sign());
                });
            }
        ));

        // ACT: Producer send operation

        // Producer perspective (OUTBOUND)
        producer.connect(Dimension.OUTBOUND);      // Outbound connection to broker
        producer.transfer(Dimension.OUTBOUND);     // Outbound send message
        producer.transfer(Dimension.INBOUND);      // Inbound receive ack
        producer.succeed(Dimension.OUTBOUND);      // Outbound send succeeded

        // Broker perspective (INBOUND)
        broker.connect(Dimension.INBOUND);      // Inbound producer connected
        broker.transfer(Dimension.INBOUND);     // Inbound message received from producer
        broker.process(Dimension.INBOUND);      // Inbound message processed

        circuit.await();

        // ASSERT: Complete send flow tracked
        assertThat(sendFlow).contains(
            "producer-1:CONNECT",
            "producer-1:TRANSFER",
            "broker-1:CONNECT",
            "broker-1:TRANSFER",
            "broker-1:PROCESS"
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

        consumer.connect(Dimension.OUTBOUND);      // Outbound connect to broker
        consumer.transfer(Dimension.OUTBOUND);     // Outbound send fetch request
        consumer.transfer(Dimension.INBOUND);      // Inbound receive message batch
        consumer.process(Dimension.INBOUND);       // Inbound process messages
        consumer.succeed(Dimension.OUTBOUND);      // Outbound fetch succeeded

        circuit.await();

        // ASSERT: Fetch cycle captured
        assertThat(consumerOps).containsExactly(
            Sign.CONNECT,
            Sign.TRANSFER,    // Fetch request (outbound)
            Sign.TRANSFER,    // Message batch (inbound)
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

        client.connect(Dimension.OUTBOUND);        // Attempting outbound connection
        client.fail(Dimension.OUTBOUND);           // Outbound connection failed
        client.disconnect(Dimension.OUTBOUND);     // Clean up outbound connection

        circuit.await();

        // ASSERT: Disconnect signal emitted
        assertThat(lastSignal.get().sign()).isEqualTo(Sign.DISCONNECT);
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

        processor.transfer(Dimension.INBOUND);     // Inbound received message
        processor.process(Dimension.INBOUND);      // Inbound processing message
        processor.succeed(Dimension.INBOUND);      // Inbound processing succeeded

        circuit.await();

        // ASSERT: Processing flow tracked
        assertThat(pipeline).hasSize(3);
        assertThat(pipeline.get(0).sign()).isEqualTo(Sign.TRANSFER);
        assertThat(pipeline.get(1).sign()).isEqualTo(Sign.PROCESS);
        assertThat(pipeline.get(2).sign()).isEqualTo(Sign.SUCCEED);
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

        // ACT: Observe server operations (all INBOUND)

        serverMonitor.connect(Dimension.INBOUND);      // Server accepted inbound connection
        serverMonitor.transfer(Dimension.OUTBOUND);    // Server sent outbound response
        serverMonitor.succeed(Dimension.OUTBOUND);     // Server completed outbound successfully
        serverMonitor.disconnect(Dimension.INBOUND);   // Server closed inbound connection

        circuit.await();

        // ASSERT: Mixed directions observed
        assertThat(observations).hasSize(4);
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
        client.transfer(Dimension.OUTBOUND);          // OUTBOUND: Client transmits
        server.transfer(Dimension.INBOUND);           // INBOUND: Server receives

        // Server sends response
        server.transfer(Dimension.OUTBOUND);          // OUTBOUND: Server transmits
        client.transfer(Dimension.INBOUND);           // INBOUND: Client receives

        circuit.await();

        // ASSERT: Bidirectional pattern captured
        assertThat(communication).containsExactly(
            "CLIENT:TRANSFER:OUTBOUND",
            "SERVER:TRANSFER:INBOUND",
            "SERVER:TRANSFER:OUTBOUND",
            "CLIENT:TRANSFER:INBOUND"
        );
    }

    @Test
    @DisplayName("All 6 signs and 2 dimensions available")
    void allSignsAndPolaritysAvailable() {
        // Verify complete API surface

        Probe probe = probes.percept(cortex().name("test-probe"));

        // ACT: Emit all signs in both dimensions

        // OUTBOUND dimension
        probe.connect(Dimension.OUTBOUND);
        probe.disconnect(Dimension.OUTBOUND);
        probe.transfer(Dimension.OUTBOUND);
        probe.process(Dimension.OUTBOUND);
        probe.succeed(Dimension.OUTBOUND);
        probe.fail(Dimension.OUTBOUND);

        // INBOUND dimension
        probe.connect(Dimension.INBOUND);
        probe.disconnect(Dimension.INBOUND);
        probe.transfer(Dimension.INBOUND);
        probe.process(Dimension.INBOUND);
        probe.succeed(Dimension.INBOUND);
        probe.fail(Dimension.INBOUND);

        circuit.await();

        // ASSERT: All sign types exist (TRANSMIT + RECEIVE merged to TRANSFER)
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(6);
        assertThat(allSigns).contains(
            Sign.CONNECT,
            Sign.DISCONNECT,
            Sign.TRANSFER,
            Sign.PROCESS,
            Sign.SUCCEED,
            Sign.FAIL
        );

        // ASSERT: Both dimensions exist
        Dimension[] allDimensions = Dimension.values();
        assertThat(allDimensions).hasSize(2);
        assertThat(allDimensions).contains(
            Dimension.OUTBOUND,
            Dimension.INBOUND
        );

        // ASSERT: 6 signs × 2 dimensions = 12 possible signal combinations
        // Note: Signal is a record combining Sign + Dimension, not an enum with values()
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
        probe.transfer(Dimension.OUTBOUND);  // TRANSFER (Sign.TRANSFER, Dimension.OUTBOUND)

        circuit.await();

        // ASSERT: Signal properties accessible
        Signal signal = capturedSignal.get();
        assertThat(signal.sign()).isEqualTo(Sign.TRANSFER);
        assertThat(signal.dimension()).isEqualTo(Dimension.OUTBOUND);
    }
}
