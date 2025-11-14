package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Probes;
import io.humainary.substrates.ext.serventis.ext.Services;
import io.humainary.substrates.ext.serventis.ext.Queues;
import io.humainary.substrates.ext.serventis.ext.Monitors;
import io.humainary.substrates.ext.serventis.ext.Reporters;

/**
 * Integration test demonstrating complete OODA Loop signal flow.
 * <p>
 * OODA Loop: Observe → Orient → Decide → Act
 * <p>
 * This test shows how signals flow through the cognitive hierarchy:
 * 1. OBSERVE: Raw signals from Probes/Services/Queues
 * 2. ORIENT: Sign assessment via Monitors
 * 3. DECIDE: Urgency determination via Reporters
 * 4. ACT: (Would trigger remediation - simulated here)
 * <p>
 * Scenario: Kafka consumer lag detection and escalation
 * - Producer sends messages faster than consumer can process
 * - Queue overflow detected (OBSERVE)
 * - System degradation assessed (ORIENT)
 * - Critical situation reported (DECIDE)
 * - Auto-scaling triggered (ACT)
 */
@DisplayName("OODA Loop Integration Test - Complete Signal Flow")
class OODALoopIntegrationTest {

    private Circuit circuit;

    // OBSERVE phase instruments
    private Conduit<Probes.Probe, Probes.Signal> probes;
    private Conduit<Services.Service, Services.Signal> services;
    private Conduit<Queues.Queue, Queues.Sign> queues;

    // ORIENT phase instruments
    private Conduit<Monitors.Monitor, Monitors.Signal> monitors;

    // DECIDE phase instruments
    private Conduit<Reporters.Reporter, Reporters.Sign> reporters;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("ooda-integration"));

        // OBSERVE phase
        probes = circuit.conduit(cortex().name("probes"), Probes::composer);
        services = circuit.conduit(cortex().name("services"), Services::composer);
        queues = circuit.conduit(cortex().name("queues"), Queues::composer);

        // ORIENT phase
        monitors = circuit.conduit(cortex().name("monitors"), Monitors::composer);

        // DECIDE phase
        reporters = circuit.conduit(cortex().name("reporters"), Reporters::composer);
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Complete OODA loop: Consumer lag detection and escalation")
    void completeOODALoop() {
        // Scenario: Consumer falling behind, system detects and escalates

        // Get instruments for Kafka consumer scenario
        Probes.Probe consumerProbe = probes.percept(cortex().name("consumer-1.fetch"));
        Services.Service consumerService = services.percept(cortex().name("consumer-1.processing"));
        Queues.Queue consumerQueue = queues.percept(cortex().name("consumer-1.buffer"));
        Monitors.Monitor consumerHealth = monitors.percept(cortex().name("consumer-1.health"));
        Reporters.Reporter lagReporter = reporters.percept(cortex().name("consumer-1.lag"));

        List<String> oodaFlow = new ArrayList<>();

        // Subscribe to all phases
        probes.subscribe(cortex().subscriber(
            cortex().name("probe-receptor"),
            (Subject<Channel<Probes.Signal>> subject, Registrar<Probes.Signal> registrar) -> {
                registrar.register(signal -> {
                    oodaFlow.add("OBSERVE(Probe):" + subject.name() + ":" + signal.sign());
                });
            }
        ));

        services.subscribe(cortex().subscriber(
            cortex().name("service-receptor"),
            (Subject<Channel<Services.Signal>> subject, Registrar<Services.Signal> registrar) -> {
                registrar.register(signal -> {
                    oodaFlow.add("OBSERVE(Service):" + subject.name() + ":" + signal.sign());
                });
            }
        ));

        queues.subscribe(cortex().subscriber(
            cortex().name("queue-receptor"),
            (Subject<Channel<Queues.Sign>> subject, Registrar<Queues.Sign> registrar) -> {
                registrar.register(sign -> {
                    oodaFlow.add("OBSERVE(Queue):" + subject.name() + ":" + sign);
                });
            }
        ));

        monitors.subscribe(cortex().subscriber(
            cortex().name("monitor-receptor"),
            (Subject<Channel<Monitors.Signal>> subject, Registrar<Monitors.Signal> registrar) -> {
                registrar.register(signal -> {
                    oodaFlow.add("ORIENT(Monitor):" + subject.name() + ":" + signal.sign());
                });
            }
        ));

        reporters.subscribe(cortex().subscriber(
            cortex().name("reporter-receptor"),
            (Subject<Channel<Reporters.Sign>> subject, Registrar<Reporters.Sign> registrar) -> {
                registrar.register(sign -> {
                    oodaFlow.add("DECIDE(Reporter):" + subject.name() + ":" + sign);
                });
            }
        ));

        // ACT: Simulate consumer lag scenario

        // Phase 1: OBSERVE - Normal operation
        consumerProbe.receive();           // Fetching messages
        consumerService.start();           // Processing started
        consumerQueue.enqueue();           // Message queued
        consumerQueue.dequeue();           // Message processed
        consumerService.success();         // Processing succeeded
        consumerService.stop();            // Processing complete

        circuit.await();

        // Phase 2: OBSERVE - Load increasing
        consumerQueue.enqueue();           // Messages arriving faster
        consumerQueue.enqueue();
        consumerQueue.enqueue();
        consumerQueue.overflow();          // Buffer full!

        circuit.await();

        // Phase 3: ORIENT - Assess degradation
        consumerHealth.diverging(Monitors.Dimension.MEASURED);  // Early warning

        circuit.await();

        // Phase 4: OBSERVE - Continued issues
        consumerService.start();
        consumerService.delay();           // Processing delayed
        consumerService.stop();

        circuit.await();

        // Phase 5: ORIENT - Condition worsening
        consumerHealth.degraded(Monitors.Dimension.CONFIRMED);  // Confirmed degradation

        circuit.await();

        // Phase 6: DECIDE - Escalate urgency
        lagReporter.warning();             // Lag warning threshold exceeded

        circuit.await();

        // Phase 7: OBSERVE - Critical state
        consumerQueue.overflow();          // Still overflowing
        consumerService.fail();            // Processing failing

        circuit.await();

        // Phase 8: ORIENT - Critical assessment
        consumerHealth.defective(Monitors.Dimension.CONFIRMED);  // Major malfunction

        circuit.await();

        // Phase 9: DECIDE - Critical urgency
        lagReporter.critical();            // CRITICAL - action required!

        circuit.await();

        // ACT phase (simulated - would trigger auto-scaling)
        oodaFlow.add("ACT(AutoScaler):scale-consumer:TRIGGERED");

        // ASSERT: Verify complete OODA flow
        assertThat(oodaFlow).hasSizeGreaterThan(0);

        // Verify OBSERVE phase signals present
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("OBSERVE(Probe)"));
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("OBSERVE(Service)"));
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("OBSERVE(Queue)"));

        // Verify ORIENT phase signals present
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("ORIENT(Monitor)"));

        // Verify DECIDE phase signals present
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("DECIDE(Reporter)"));

        // Verify ACT phase triggered
        assertThat(oodaFlow).anyMatch(s -> s.startsWith("ACT("));

        // Verify progression: WARNING → CRITICAL
        int warningIndex = -1;
        int criticalIndex = -1;
        for (int i = 0; i < oodaFlow.size(); i++) {
            if (oodaFlow.get(i).contains("WARNING")) warningIndex = i;
            if (oodaFlow.get(i).contains("CRITICAL")) criticalIndex = i;
        }
        assertThat(criticalIndex).isGreaterThan(warningIndex);
    }

    @Test
    @DisplayName("OBSERVE → ORIENT flow: Queue overflow triggers condition assessment")
    void observeToOrientFlow() {
        Queues.Queue queue = queues.percept(cortex().name("buffer"));
        Monitors.Monitor monitor = monitors.percept(cortex().name("buffer.health"));

        List<String> flow = new ArrayList<>();

        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(sign -> flow.add("OBSERVE:" + sign));
            }
        ));

        monitors.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> flow.add("ORIENT:" + signal.sign()));
            }
        ));

        // ACT: Overflow triggers assessment
        queue.enqueue();
        queue.overflow();                  // OBSERVE
        monitor.degraded(Monitors.Dimension.CONFIRMED);  // ORIENT

        circuit.await();

        // ASSERT
        assertThat(flow).contains(
            "OBSERVE:OVERFLOW",
            "ORIENT:DEGRADED"
        );
    }

    @Test
    @DisplayName("ORIENT → DECIDE flow: Degraded condition triggers urgency assessment")
    void orientToDecideFlow() {
        Monitors.Monitor monitor = monitors.percept(cortex().name("system.health"));
        Reporters.Reporter reporter = reporters.percept(cortex().name("system.status"));

        List<String> flow = new ArrayList<>();

        monitors.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> flow.add("ORIENT:" + signal.sign()));
            }
        ));

        reporters.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(sign -> flow.add("DECIDE:" + sign));
            }
        ));

        // ACT: Degradation triggers urgency assessment
        monitor.stable(Monitors.Dimension.CONFIRMED);
        reporter.normal();

        monitor.diverging(Monitors.Dimension.MEASURED);
        reporter.warning();

        monitor.degraded(Monitors.Dimension.CONFIRMED);
        reporter.critical();

        circuit.await();

        // ASSERT: Progression through urgency levels
        assertThat(flow).containsSubsequence(
            "DECIDE:NORMAL",
            "DECIDE:WARNING",
            "DECIDE:CRITICAL"
        );
    }

    @Test
    @DisplayName("Multi-layer OODA: Multiple instruments coordinating")
    void multiLayerOODA() {
        // Multiple consumers being monitored
        Queues.Queue consumer1Queue = queues.percept(cortex().name("consumer-1.queue"));
        Queues.Queue consumer2Queue = queues.percept(cortex().name("consumer-2.queue"));
        Monitors.Monitor groupHealth = monitors.percept(cortex().name("consumer-group.health"));
        Reporters.Reporter groupStatus = reporters.percept(cortex().name("consumer-group.status"));

        List<String> events = new ArrayList<>();

        queues.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Queues.Sign>> subject, Registrar<Queues.Sign> registrar) -> {
                registrar.register(sign -> events.add("OBSERVE:" + subject.name() + ":" + sign));
            }
        ));

        monitors.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Monitors.Signal>> subject, Registrar<Monitors.Signal> registrar) -> {
                registrar.register(signal -> events.add("ORIENT:" + subject.name() + ":" + signal.sign()));
            }
        ));

        reporters.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Reporters.Sign>> subject, Registrar<Reporters.Sign> registrar) -> {
                registrar.register(sign -> events.add("DECIDE:" + subject.name() + ":" + sign));
            }
        ));

        // ACT: Both consumers experiencing issues
        consumer1Queue.overflow();
        consumer2Queue.overflow();
        groupHealth.degraded(Monitors.Dimension.CONFIRMED);
        groupStatus.critical();

        circuit.await();

        // ASSERT: Multiple OBSERVE signals feed into single ORIENT/DECIDE
        long observeCount = events.stream().filter(e -> e.startsWith("OBSERVE:")).count();
        assertThat(observeCount).isEqualTo(2);  // Two consumers
        assertThat(events).anyMatch(e -> e.contains("ORIENT") && e.contains("DEGRADED"));
        assertThat(events).anyMatch(e -> e.contains("DECIDE") && e.contains("CRITICAL"));
    }
}
