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
import static io.humainary.substrates.ext.serventis.ext.Agents.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Agents;

/**
 * Demonstration of the Agents API (RC6) - Promise Theory-based coordination.
 * <p>
 * The Agents API enables observability of autonomous agent coordination through
 * promises rather than commands. Agents can only promise what they control.
 * <p>
 * Key Concepts:
 * - OUTBOUND signals: Self-reporting (present tense) - "I promise to scale"
 * - INBOUND signals: Observing others (past tense) - "They promised to scale"
 * - Promise lifecycle: INQUIRE → OFFER → PROMISE → ACCEPT → FULFILL/BREACH
 * - Autonomy preserved: Agents can RETRACT promises they cannot keep
 * <p>
 * Use Cases:
 * - Consumer group rebalancing
 * - Auto-scaling coordination
 * - Leader election
 * - Partition reassignment
 * - Resource capacity promises
 */
@DisplayName("Agents API (RC6) - Promise Theory Coordination")
class AgentsApiDemoTest {

    private Circuit circuit;
    private Conduit<Agent, Signal> agents;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("agents-demo"));
        agents = circuit.conduit(
            cortex().name("agents"),
            Agents::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Promise lifecycle: OFFER → PROMISE → ACCEPT → FULFILL")
    void promiseLifecycle() {
        // Scenario: Auto-scaler promises to scale consumer group

        Agent scaler = agents.percept(cortex().name("auto-scaler"));
        Agent monitor = agents.percept(cortex().name("lag-monitor"));

        List<Signal> scalerSignals = new ArrayList<>();
        List<Signal> monitorSignals = new ArrayList<>();

        // Subscribe to observe all agent signals
        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    if (subject.name().toString().contains("scaler")) {
                        scalerSignals.add(signal);
                    } else if (subject.name().toString().contains("monitor")) {
                        monitorSignals.add(signal);
                    }
                });
            }
        ));

        // ACT: Execute promise lifecycle

        // 1. Monitor inquires about scaling capability
        monitor.inquire();  // OUTBOUND: "Can anyone scale?"

        // 2. Scaler offers scaling capability
        scaler.offered();   // INBOUND: "Monitor asked about scaling"
        scaler.offer();     // OUTBOUND: "I can scale consumers"

        // 3. Scaler makes promise
        scaler.promise();   // OUTBOUND: "I promise to scale"

        // 4. Monitor accepts the promise
        monitor.offered();  // INBOUND: "Scaler offered"
        monitor.accept();   // OUTBOUND: "I accept your promise"

        // 5. Scaler acknowledges acceptance and fulfills
        scaler.accepted();  // INBOUND: "Monitor accepted my promise"
        scaler.fulfill();   // OUTBOUND: "I kept my promise (scaled consumers)"

        circuit.await();

        // ASSERT: Verify signal sequence
        assertThat(scalerSignals).containsExactly(
            Signal.OFFERED,   // Inbound: observed inquiry
            Signal.OFFER,     // Outbound: advertise capability
            Signal.PROMISE,   // Outbound: commit
            Signal.ACCEPTED,  // Inbound: observed acceptance
            Signal.FULFILL    // Outbound: kept promise
        );

        assertThat(monitorSignals).containsExactly(
            Signal.INQUIRE,   // Outbound: ask about capabilities
            Signal.OFFERED,   // Inbound: observed offer
            Signal.ACCEPT     // Outbound: accept promise
        );
    }

    @Test
    @DisplayName("Promise breach: Agent fails to fulfill commitment")
    void promiseBreach() {
        // Scenario: Consumer promises to join rebalance but times out

        Agent consumer = agents.percept(cortex().name("consumer-1"));
        Agent coordinator = agents.percept(cortex().name("group-coordinator"));

        AtomicReference<Signal> lastSignal = new AtomicReference<>();
        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lastSignal::set);
            }
        ));

        // ACT: Consumer promises but breaches
        consumer.promise();  // OUTBOUND: "I promise to join rebalance"
        coordinator.promised();  // INBOUND: "Consumer promised"

        // Consumer times out or crashes
        consumer.breach();   // OUTBOUND: "I failed to keep my promise"

        circuit.await();

        // ASSERT: Breach signal emitted
        assertThat(lastSignal.get()).isEqualTo(Signal.BREACH);
    }

    @Test
    @DisplayName("Promise retraction: Agent withdraws commitment")
    void promiseRetraction() {
        // Scenario: Broker promises capacity but must retract

        Agent broker = agents.percept(cortex().name("broker-1"));

        AtomicReference<Signal> lastSignal = new AtomicReference<>();
        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lastSignal::set);
            }
        ));

        // ACT: Broker retracts promise
        broker.promise();   // OUTBOUND: "I promise capacity"
        broker.retract();   // OUTBOUND: "I withdraw my promise"

        circuit.await();

        // ASSERT: Retraction signal emitted
        assertThat(lastSignal.get()).isEqualTo(Signal.RETRACT);
    }

    @Test
    @DisplayName("Dependency management: DEPEND → VALIDATE → FULFILL")
    void dependencyManagement() {
        // Scenario: Leader depends on followers for replication

        Agent leader = agents.percept(cortex().name("partition-leader"));
        Agent follower1 = agents.percept(cortex().name("follower-1"));
        Agent follower2 = agents.percept(cortex().name("follower-2"));

        List<Signal> leaderSignals = new ArrayList<>();

        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    if (subject.name().toString().contains("leader")) {
                        leaderSignals.add(signal);
                    }
                });
            }
        ));

        // ACT: Leader declares dependencies
        leader.depend();      // OUTBOUND: "I depend on followers"

        follower1.depended(); // INBOUND: "Leader depends on me"
        follower1.promise();  // OUTBOUND: "I promise to replicate"

        follower2.depended(); // INBOUND: "Leader depends on me"
        follower2.promise();  // OUTBOUND: "I promise to replicate"

        leader.validate();    // OUTBOUND: "I validate dependencies are met"

        follower1.fulfill();  // OUTBOUND: "I fulfilled replication"
        follower2.fulfill();  // OUTBOUND: "I fulfilled replication"

        circuit.await();

        // ASSERT: Dependency lifecycle tracked
        assertThat(leaderSignals).contains(
            Signal.DEPEND,
            Signal.VALIDATE
        );
    }

    @Test
    @DisplayName("Multi-agent coordination: Complete promise network")
    void multiAgentCoordination() {
        // Scenario: Multiple agents coordinate via promises
        // Controller coordinates partition reassignment across brokers

        Agent controller = agents.percept(cortex().name("controller"));
        Agent broker1 = agents.percept(cortex().name("broker-1"));
        Agent broker2 = agents.percept(cortex().name("broker-2"));

        List<String> timeline = new ArrayList<>();

        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    timeline.add(subject.name() + ":" + signal);
                });
            }
        ));

        // ACT: Complete coordination cycle

        // Phase 1: Discovery
        controller.inquire();    // Controller asks about capacity
        broker1.offered();       // Broker1 observes inquiry
        broker1.offer();         // Broker1 offers capacity
        broker2.offered();       // Broker2 observes inquiry
        broker2.offer();         // Broker2 offers capacity

        // Phase 2: Commitment
        controller.offered();    // Controller observes offers
        broker1.promise();       // Broker1 commits to taking partition
        broker2.promise();       // Broker2 commits to taking partition

        // Phase 3: Acceptance
        controller.promised();   // Controller observes promises
        controller.accept();     // Controller accepts promises

        // Phase 4: Execution
        broker1.accepted();      // Broker1 observes acceptance
        broker1.fulfill();       // Broker1 completes partition transfer
        broker2.accepted();      // Broker2 observes acceptance
        broker2.fulfill();       // Broker2 completes partition transfer

        circuit.await();

        // ASSERT: Complete coordination captured
        assertThat(timeline).hasSize(14);  // All signals tracked
        assertThat(timeline).contains(
            "controller:INQUIRE",
            "broker-1:OFFER",
            "broker-2:OFFER",
            "broker-1:PROMISE",
            "broker-2:PROMISE",
            "controller:ACCEPT",
            "broker-1:FULFILL",
            "broker-2:FULFILL"
        );
    }

    @Test
    @DisplayName("Outbound vs Inbound perspective distinction")
    void perspectiveDistinction() {
        // Demonstrates the dual-direction signal model

        Agent agent = agents.percept(cortex().name("test-agent"));

        List<Signal> signals = new ArrayList<>();
        agents.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signals::add);
            }
        ));

        // ACT: Self-reporting (OUTBOUND) vs observing others (INBOUND)

        // OUTBOUND: "I promise" (present tense, self)
        agent.promise();

        // INBOUND: "They promised" (past tense, other)
        agent.promised();

        // OUTBOUND: "I fulfill"
        agent.fulfill();

        // INBOUND: "They fulfilled"
        agent.fulfilled();

        circuit.await();

        // ASSERT: Both perspectives captured
        assertThat(signals).containsExactly(
            Signal.PROMISE,    // Outbound
            Signal.PROMISED,   // Inbound
            Signal.FULFILL,    // Outbound
            Signal.FULFILLED   // Inbound
        );

        // Verify signal properties
        assertThat(Signal.PROMISE.dimension())
            .isEqualTo(Dimension.PROMISER);
        assertThat(Signal.PROMISED.dimension())
            .isEqualTo(Dimension.PROMISEE);
    }

    @Test
    @DisplayName("All 20 signals available")
    void allSignalsAvailable() {
        // Verify complete API surface

        Agent agent = agents.percept(cortex().name("test-agent"));

        // ACT: Emit all 20 signals

        // Discovery (4 signals)
        agent.inquire();
        agent.inquired();
        agent.offer();
        agent.offered();

        // Commitment (4 signals)
        agent.promise();
        agent.promised();
        agent.accept();
        agent.accepted();

        // Dependency (4 signals)
        agent.depend();
        agent.depended();
        agent.observe();
        agent.observed();

        // Validation (2 signals)
        agent.validate();
        agent.validated();

        // Resolution (6 signals)
        agent.fulfill();
        agent.fulfilled();
        agent.breach();
        agent.breached();
        agent.retract();
        agent.retracted();

        circuit.await();

        // ASSERT: All signal types exist
        Signal[] allSignals = Signal.values();
        assertThat(allSignals).hasSize(20);

        // Verify 10 sign types exist
        Agents.Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(10);
        assertThat(allSigns).contains(
            Sign.INQUIRE,
            Sign.OFFER,
            Sign.PROMISE,
            Sign.ACCEPT,
            Sign.DEPEND,
            Sign.OBSERVE,
            Sign.VALIDATE,
            Sign.FULFILL,
            Sign.BREACH,
            Sign.RETRACT
        );
    }
}
