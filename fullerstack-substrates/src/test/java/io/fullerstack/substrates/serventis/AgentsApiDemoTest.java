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
            cortex().name("receptor"),
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
        monitor.inquire(Dimension.PROMISER);  // PROMISER: "I inquire about scaling"

        // 2. Scaler observes inquiry and offers scaling capability
        scaler.inquire(Dimension.PROMISEE);   // PROMISEE: "Monitor inquired"
        scaler.offer(Dimension.PROMISER);     // PROMISER: "I offer to scale consumers"

        // 3. Scaler makes promise
        scaler.promise(Dimension.PROMISER);   // PROMISER: "I promise to scale"

        // 4. Monitor observes offer and accepts the promise
        monitor.offer(Dimension.PROMISEE);  // PROMISEE: "Scaler offered"
        monitor.accept(Dimension.PROMISER);   // PROMISER: "I accept your promise"

        // 5. Scaler acknowledges acceptance and fulfills
        scaler.accept(Dimension.PROMISEE);  // PROMISEE: "Monitor accepted"
        scaler.fulfill(Dimension.PROMISER);   // PROMISER: "I kept my promise (scaled consumers)"

        circuit.await();

        // ASSERT: Verify signal sequence
        assertThat(scalerSignals).hasSize(5);
        assertThat(scalerSignals.get(0).sign()).isEqualTo(Sign.INQUIRE);
        assertThat(scalerSignals.get(0).dimension()).isEqualTo(Dimension.PROMISEE);
        assertThat(scalerSignals.get(1).sign()).isEqualTo(Sign.OFFER);
        assertThat(scalerSignals.get(2).sign()).isEqualTo(Sign.PROMISE);
        assertThat(scalerSignals.get(3).sign()).isEqualTo(Sign.ACCEPT);
        assertThat(scalerSignals.get(3).dimension()).isEqualTo(Dimension.PROMISEE);
        assertThat(scalerSignals.get(4).sign()).isEqualTo(Sign.FULFILL);

        assertThat(monitorSignals).hasSize(3);
        assertThat(monitorSignals.get(0).sign()).isEqualTo(Sign.INQUIRE);
        assertThat(monitorSignals.get(1).sign()).isEqualTo(Sign.OFFER);
        assertThat(monitorSignals.get(1).dimension()).isEqualTo(Dimension.PROMISEE);
        assertThat(monitorSignals.get(2).sign()).isEqualTo(Sign.ACCEPT);
    }

    @Test
    @DisplayName("Promise breach: Agent fails to fulfill commitment")
    void promiseBreach() {
        // Scenario: Consumer promises to join rebalance but times out

        Agent consumer = agents.percept(cortex().name("consumer-1"));
        Agent coordinator = agents.percept(cortex().name("group-coordinator"));

        AtomicReference<Signal> lastSignal = new AtomicReference<>();
        agents.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(lastSignal::set);
            }
        ));

        // ACT: Consumer promises but breaches
        consumer.promise(Dimension.PROMISER);  // PROMISER: "I promise to join rebalance"
        coordinator.promise(Dimension.PROMISEE);  // PROMISEE: "Consumer promised"

        // Consumer times out or crashes
        consumer.breach(Dimension.PROMISER);   // PROMISER: "I failed to keep my promise"

        circuit.await();

        // ASSERT: Breach signal emitted
        assertThat(lastSignal.get().sign()).isEqualTo(Sign.BREACH);
    }

    @Test
    @DisplayName("Promise retraction: Agent withdraws commitment")
    void promiseRetraction() {
        // Scenario: Broker promises capacity but must retract

        Agent broker = agents.percept(cortex().name("broker-1"));

        AtomicReference<Signal> lastSignal = new AtomicReference<>();
        agents.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(lastSignal::set);
            }
        ));

        // ACT: Broker retracts promise
        broker.promise(Dimension.PROMISER);   // PROMISER: "I promise capacity"
        broker.retract(Dimension.PROMISER);   // PROMISER: "I withdraw my promise"

        circuit.await();

        // ASSERT: Retraction signal emitted
        assertThat(lastSignal.get().sign()).isEqualTo(Sign.RETRACT);
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
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    if (subject.name().toString().contains("leader")) {
                        leaderSignals.add(signal);
                    }
                });
            }
        ));

        // ACT: Leader declares dependencies
        leader.depend(Dimension.PROMISER);      // PROMISER: "I depend on followers"

        follower1.depend(Dimension.PROMISEE); // PROMISEE: "Leader depends on me"
        follower1.promise(Dimension.PROMISER);  // PROMISER: "I promise to replicate"

        follower2.depend(Dimension.PROMISEE); // PROMISEE: "Leader depends on me"
        follower2.promise(Dimension.PROMISER);  // PROMISER: "I promise to replicate"

        leader.validate(Dimension.PROMISER);    // PROMISER: "I validate dependencies are met"

        follower1.fulfill(Dimension.PROMISER);  // PROMISER: "I fulfilled replication"
        follower2.fulfill(Dimension.PROMISER);  // PROMISER: "I fulfilled replication"

        circuit.await();

        // ASSERT: Dependency lifecycle tracked
        assertThat(leaderSignals.stream().map(s -> s.sign()).toList()).contains(
            Sign.DEPEND,
            Sign.VALIDATE
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
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    timeline.add(subject.name() + ":" + signal.sign());
                });
            }
        ));

        // ACT: Complete coordination cycle

        // Phase 1: Discovery
        controller.inquire(Dimension.PROMISER);    // Controller asks about capacity
        broker1.inquire(Dimension.PROMISEE);       // Broker1 observes inquiry
        broker1.offer(Dimension.PROMISER);         // Broker1 offers capacity
        broker2.inquire(Dimension.PROMISEE);       // Broker2 observes inquiry
        broker2.offer(Dimension.PROMISER);         // Broker2 offers capacity

        // Phase 2: Commitment
        controller.offer(Dimension.PROMISEE);    // Controller observes offers
        broker1.promise(Dimension.PROMISER);       // Broker1 commits to taking partition
        broker2.promise(Dimension.PROMISER);       // Broker2 commits to taking partition

        // Phase 3: Acceptance
        controller.promise(Dimension.PROMISEE);   // Controller observes promises
        controller.accept(Dimension.PROMISER);     // Controller accepts promises

        // Phase 4: Execution
        broker1.accept(Dimension.PROMISEE);      // Broker1 observes acceptance
        broker1.fulfill(Dimension.PROMISER);       // Broker1 completes partition transfer
        broker2.accept(Dimension.PROMISEE);      // Broker2 observes acceptance
        broker2.fulfill(Dimension.PROMISER);       // Broker2 completes partition transfer

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
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signals::add);
            }
        ));

        // ACT: PROMISER vs PROMISEE perspective

        // PROMISER: "I promise" (I am making the promise)
        agent.promise(Dimension.PROMISER);

        // PROMISEE: "They promised" (I observed their promise)
        agent.promise(Dimension.PROMISEE);

        // PROMISER: "I fulfill" (I am fulfilling my promise)
        agent.fulfill(Dimension.PROMISER);

        // PROMISEE: "They fulfilled" (I observed their fulfillment)
        agent.fulfill(Dimension.PROMISEE);

        circuit.await();

        // ASSERT: Both perspectives captured
        assertThat(signals).hasSize(4);
        assertThat(signals.get(0).sign()).isEqualTo(Sign.PROMISE);
        assertThat(signals.get(0).dimension()).isEqualTo(Dimension.PROMISER);
        assertThat(signals.get(1).sign()).isEqualTo(Sign.PROMISE);
        assertThat(signals.get(1).dimension()).isEqualTo(Dimension.PROMISEE);
        assertThat(signals.get(2).sign()).isEqualTo(Sign.FULFILL);
        assertThat(signals.get(2).dimension()).isEqualTo(Dimension.PROMISER);
        assertThat(signals.get(3).sign()).isEqualTo(Sign.FULFILL);
        assertThat(signals.get(3).dimension()).isEqualTo(Dimension.PROMISEE);
    }

    @Test
    @DisplayName("All 20 signals available")
    void allSignalsAvailable() {
        // Verify complete API surface

        Agent agent = agents.percept(cortex().name("test-agent"));

        // ACT: Emit all signals (10 signs × 2 dimensions = 20 signals)

        // Discovery (4 signals)
        agent.inquire(Dimension.PROMISER);
        agent.inquire(Dimension.PROMISEE);
        agent.offer(Dimension.PROMISER);
        agent.offer(Dimension.PROMISEE);

        // Commitment (4 signals)
        agent.promise(Dimension.PROMISER);
        agent.promise(Dimension.PROMISEE);
        agent.accept(Dimension.PROMISER);
        agent.accept(Dimension.PROMISEE);

        // Dependency (4 signals)
        agent.depend(Dimension.PROMISER);
        agent.depend(Dimension.PROMISEE);
        agent.observe(Dimension.PROMISER);
        agent.observe(Dimension.PROMISEE);

        // Validation (2 signals)
        agent.validate(Dimension.PROMISER);
        agent.validate(Dimension.PROMISEE);

        // Resolution (6 signals)
        agent.fulfill(Dimension.PROMISER);
        agent.fulfill(Dimension.PROMISEE);
        agent.breach(Dimension.PROMISER);
        agent.breach(Dimension.PROMISEE);
        agent.retract(Dimension.PROMISER);
        agent.retract(Dimension.PROMISEE);

        circuit.await();

        // ASSERT: All sign types exist (10 signs × 2 dimensions = 20 signals)
        // Note: Signal is a record combining Sign + Dimension, not an enum with values()

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
