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
import static io.humainary.substrates.ext.serventis.ext.Routers.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Routers;

/**
 * Demonstration of the Routers API (RC6) - Network packet routing observability.
 * <p>
 * The Routers API enables observation of packet routing operations through semantic
 * sign emission. Reports routing semantics, not implementing routers.
 * <p>
 * Key Concepts:
 * - Signs represent discrete events about packet handling
 * - High-frequency operation support (sampling recommended for >1M pps)
 * - Asynchronous sign flow to avoid impacting packet processing latency
 * <p>
 * Routing Signs (9):
 * - SEND: Packet originated by this router (source node)
 * - RECEIVE: Packet arrived from network (entry point)
 * - FORWARD: Packet routed to next hop (intermediary function)
 * - ROUTE: Routing decision/table lookup performed
 * - DROP: Packet discarded (congestion, policy, TTL, etc.)
 * - FRAGMENT: Packet split due to MTU constraints
 * - REASSEMBLE: Fragments recombined into original packet
 * - CORRUPT: Corruption detected (checksum/malformed header)
 * - REORDER: Out-of-order arrival detected
 * <p>
 * Kafka Use Cases:
 * - ISR (In-Sync Replicas) management - partition routing
 * - Partition reassignment tracking
 * - Leader-follower topology
 * - Replica placement decisions
 */
@DisplayName("Routers API (RC6) - Packet Routing Observability")
class RoutersApiDemoTest {

    private Circuit circuit;
    private Conduit<Router, Sign> routers;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("routers-demo"));
        routers = circuit.conduit(
            cortex().name("routers"),
            Routers::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic packet flow: RECEIVE → ROUTE → FORWARD")
    void basicPacketFlow() {
        // Scenario: Router receives packet, makes routing decision, forwards

        Router router = routers.get(cortex().name("router-1"));

        List<Sign> packetFlow = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(packetFlow::add);
            }
        ));

        // ACT: Typical packet routing

        router.receive();  // Packet arrived from network
        router.route();    // Routing table lookup performed
        router.forward();  // Packet sent to next hop

        circuit.await();

        // ASSERT: Routing sequence captured
        assertThat(packetFlow).containsExactly(
            Sign.RECEIVE,
            Sign.ROUTE,
            Sign.FORWARD
        );
    }

    @Test
    @DisplayName("Packet origination: SEND vs FORWARD")
    void sendVsForward() {
        // SEND = packet originated here (source node)
        // FORWARD = packet passing through (intermediary)

        Router sourceRouter = routers.get(cortex().name("source-router"));
        Router intermediateRouter = routers.get(cortex().name("intermediate-router"));

        List<String> timeline = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    timeline.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Different packet origins

        // Source router sends
        sourceRouter.send();  // Packet originated here

        // Intermediate router forwards
        intermediateRouter.receive();
        intermediateRouter.route();
        intermediateRouter.forward();  // Packet just passing through

        circuit.await();

        // ASSERT: Origin vs forwarding distinguished
        assertThat(timeline).containsExactly(
            "source-router:SEND",
            "intermediate-router:RECEIVE",
            "intermediate-router:ROUTE",
            "intermediate-router:FORWARD"
        );
    }

    @Test
    @DisplayName("Packet drop due to congestion/policy")
    void packetDrop() {
        // Scenario: Router drops packet (TTL expired, policy, congestion)

        Router router = routers.get(cortex().name("congested-router"));

        AtomicReference<Sign> lastSign = new AtomicReference<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lastSign::set);
            }
        ));

        // ACT: Packet dropped

        router.receive();
        router.route();
        router.drop();  // Congestion, TTL expired, or policy

        circuit.await();

        // ASSERT: Drop signal emitted
        assertThat(lastSign.get()).isEqualTo(Sign.DROP);
    }

    @Test
    @DisplayName("Fragmentation and reassembly")
    void fragmentationReassembly() {
        // Scenario: Large packet fragmented due to MTU, then reassembled

        Router sendingRouter = routers.get(cortex().name("sender"));
        Router receivingRouter = routers.get(cortex().name("receiver"));

        List<String> packetLifecycle = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    String router = subject.name().toString().contains("sender") ? "SENDER" : "RECEIVER";
                    packetLifecycle.add(router + ":" + sign);
                });
            }
        ));

        // ACT: Fragment and reassemble

        // Sending router fragments large packet
        sendingRouter.fragment();  // Packet too large for MTU
        sendingRouter.send();      // Send fragment 1
        sendingRouter.send();      // Send fragment 2

        // Receiving router reassembles
        receivingRouter.receive(); // Receive fragment 1
        receivingRouter.receive(); // Receive fragment 2
        receivingRouter.reassemble();  // Recombine fragments

        circuit.await();

        // ASSERT: Fragment/reassemble lifecycle tracked
        assertThat(packetLifecycle).contains(
            "SENDER:FRAGMENT",
            "RECEIVER:REASSEMBLE"
        );
    }

    @Test
    @DisplayName("Packet corruption detection")
    void corruptionDetection() {
        // Scenario: Router detects corrupted packet (checksum failure)

        Router router = routers.get(cortex().name("router-1"));

        AtomicReference<Sign> lastSign = new AtomicReference<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lastSign::set);
            }
        ));

        // ACT: Detect corruption

        router.receive();
        router.corrupt();  // Checksum failed or malformed header
        router.drop();     // Discard corrupted packet

        circuit.await();

        // ASSERT: Corruption detected and dropped
        assertThat(lastSign.get()).isEqualTo(Sign.DROP);
    }

    @Test
    @DisplayName("Out-of-order packet detection")
    void outOfOrderDetection() {
        // Scenario: Packets arrive out of sequence

        Router router = routers.get(cortex().name("router-1"));

        List<Sign> events = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT: Detect reordering

        router.receive();   // Packet 3 arrives
        router.reorder();   // Out of order detected
        router.receive();   // Packet 1 arrives late
        router.reorder();   // Still out of order

        circuit.await();

        // ASSERT: Reordering events tracked
        assertThat(events).contains(Sign.REORDER);
        assertThat(events.stream().filter(s -> s == Sign.REORDER).count())
            .isEqualTo(2);
    }

    @Test
    @DisplayName("Kafka ISR management pattern")
    void kafkaISRPattern() {
        // Kafka ISR = In-Sync Replicas routing pattern
        // Leader routes writes to followers

        Router partitionLeader = routers.get(cortex().name("partition-0.leader"));
        Router follower1 = routers.get(cortex().name("partition-0.follower-1"));
        Router follower2 = routers.get(cortex().name("partition-0.follower-2"));

        List<String> replicationFlow = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    replicationFlow.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Leader replicates to followers

        // Leader receives write
        partitionLeader.receive();
        partitionLeader.route();

        // Leader forwards to ISR followers
        partitionLeader.forward();  // → follower-1
        partitionLeader.forward();  // → follower-2

        // Followers receive replication
        follower1.receive();
        follower2.receive();

        circuit.await();

        // ASSERT: ISR replication pattern captured
        assertThat(replicationFlow).contains(
            "partition-0.leader:RECEIVE",
            "partition-0.leader:ROUTE",
            "partition-0.leader:FORWARD",
            "partition-0.follower-1:RECEIVE",
            "partition-0.follower-2:RECEIVE"
        );
    }

    @Test
    @DisplayName("Kafka partition reassignment pattern")
    void kafkaPartitionReassignment() {
        // Scenario: Partition moves from broker-1 to broker-2

        Router oldBroker = routers.get(cortex().name("broker-1.partition-5"));
        Router newBroker = routers.get(cortex().name("broker-2.partition-5"));

        List<String> reassignment = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    reassignment.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Partition reassignment

        // Old broker forwards partition data
        oldBroker.send();     // Send partition data
        oldBroker.forward();  // Forward to new broker

        // New broker receives partition
        newBroker.receive();  // Receive partition data

        circuit.await();

        // ASSERT: Reassignment tracked
        assertThat(reassignment).contains(
            "broker-1.partition-5:SEND",
            "broker-1.partition-5:FORWARD",
            "broker-2.partition-5:RECEIVE"
        );
    }

    @Test
    @DisplayName("Kafka follower dropped from ISR")
    void kafkaISRShrink() {
        // Scenario: Slow follower dropped from ISR

        Router leader = routers.get(cortex().name("partition-leader"));
        Router slowFollower = routers.get(cortex().name("slow-follower"));

        List<String> events = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    events.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Leader drops slow follower from ISR

        leader.drop();  // Drop follower from routing (ISR shrink)

        circuit.await();

        // ASSERT: ISR removal tracked
        assertThat(events).contains("partition-leader:DROP");
    }

    @Test
    @DisplayName("All 9 routing signs available")
    void allRoutingSignsAvailable() {
        // Verify complete API surface

        Router router = routers.get(cortex().name("test-router"));

        // ACT: Emit all 9 signs

        router.send();
        router.receive();
        router.forward();
        router.route();
        router.drop();
        router.fragment();
        router.reassemble();
        router.corrupt();
        router.reorder();

        circuit.await();

        // ASSERT: All sign types exist
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(9);
        assertThat(allSigns).contains(
            Sign.SEND,
            Sign.RECEIVE,
            Sign.FORWARD,
            Sign.ROUTE,
            Sign.DROP,
            Sign.FRAGMENT,
            Sign.REASSEMBLE,
            Sign.CORRUPT,
            Sign.REORDER
        );
    }

    @Test
    @DisplayName("High-frequency operation pattern")
    void highFrequencyPattern() {
        // For high packet rates (>1M pps), use sampling

        Router router = routers.get(cortex().name("high-traffic-router"));

        List<Sign> sampledEvents = new ArrayList<>();
        routers.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(sampledEvents::add);
            }
        ));

        // ACT: Simulate high-frequency routing
        // In production, would sample (e.g., 1 in 1000 packets)

        for (int i = 0; i < 100; i++) {  // Simulate 100 packets
            if (i % 10 == 0) {  // Sample 10%
                router.receive();
                router.route();
                router.forward();
            }
        }

        circuit.await();

        // ASSERT: Sampled events captured
        assertThat(sampledEvents).hasSizeGreaterThan(0);
        assertThat(sampledEvents).hasSizeLessThan(300);  // Not all packets tracked
    }
}
