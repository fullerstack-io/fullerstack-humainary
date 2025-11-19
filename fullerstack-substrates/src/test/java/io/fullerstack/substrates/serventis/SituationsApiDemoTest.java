package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Situations.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Situations;

/**
 * Demonstration of the Situations API - Situation urgency assessment (DECIDE phase).
 * <p>
 * Situations assess urgency/severity with variability dimensions to drive action decisions.
 * <p>
 * Situation Signs (3 urgency levels):
 * - NORMAL: Routine, no action required
 * - WARNING: Attention needed, prepare for action
 * - CRITICAL: Immediate action required
 * <p>
 * Variability Dimensions:
 * - CONSTANT: No variation, unchanging
 * - VARIABLE: Moderate variation
 * - VOLATILE: High variation, chaotic
 * <p>
 * Kafka Use Cases:
 * - Alerting system (escalate from NORMAL → WARNING → CRITICAL)
 * - SLA violation detection with variability tracking
 * - Incident management
 * - Auto-scaling triggers
 */
@DisplayName("Situations API - Situation Urgency Assessment (DECIDE)")
class SituationsApiDemoTest {

    private Circuit circuit;
    private Conduit<Situation, Signal> situations;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("situations-demo"));
        situations = circuit.conduit(
            cortex().name("situations"),
            Situations::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Normal operation")
    void normalOperation() {
        Situation systemStatus = situations.percept(cortex().name("cluster.status"));

        List<Signal> signals = new ArrayList<>();
        situations.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signals::add);
            }
        ));

        // ACT
        systemStatus.normal(Dimension.CONSTANT);  // All systems operational, stable

        circuit.await();

        // ASSERT
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).sign()).isEqualTo(Sign.NORMAL);
        assertThat(signals.get(0).dimension()).isEqualTo(Dimension.CONSTANT);
    }

    @Test
    @DisplayName("Escalation path: NORMAL → WARNING → CRITICAL")
    void escalationPath() {
        Situation consumerLagStatus = situations.percept(cortex().name("consumer.lag.status"));

        List<Signal> escalation = new ArrayList<>();
        situations.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(escalation::add);
            }
        ));

        // ACT
        consumerLagStatus.normal(Dimension.CONSTANT);     // Lag within limits, stable
        consumerLagStatus.warning(Dimension.VARIABLE);    // Lag increasing, fluctuating
        consumerLagStatus.critical(Dimension.CONSTANT);   // Lag critical, stuck

        circuit.await();

        // ASSERT
        assertThat(escalation).hasSize(3);
        assertThat(escalation.get(0).sign()).isEqualTo(Sign.NORMAL);
        assertThat(escalation.get(0).dimension()).isEqualTo(Dimension.CONSTANT);
        assertThat(escalation.get(1).sign()).isEqualTo(Sign.WARNING);
        assertThat(escalation.get(1).dimension()).isEqualTo(Dimension.VARIABLE);
        assertThat(escalation.get(2).sign()).isEqualTo(Sign.CRITICAL);
        assertThat(escalation.get(2).dimension()).isEqualTo(Dimension.CONSTANT);
    }

    @Test
    @DisplayName("De-escalation: CRITICAL → WARNING → NORMAL")
    void deEscalation() {
        Situation incidentStatus = situations.percept(cortex().name("incident.status"));

        List<Signal> recovery = new ArrayList<>();
        situations.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(recovery::add);
            }
        ));

        // ACT
        incidentStatus.critical(Dimension.VOLATILE);  // Incident active, chaotic
        incidentStatus.warning(Dimension.VARIABLE);   // Mitigation in progress, improving
        incidentStatus.normal(Dimension.CONSTANT);    // Resolved, stable

        circuit.await();

        // ASSERT
        assertThat(recovery).hasSize(3);
        assertThat(recovery.get(0).sign()).isEqualTo(Sign.CRITICAL);
        assertThat(recovery.get(0).dimension()).isEqualTo(Dimension.VOLATILE);
        assertThat(recovery.get(1).sign()).isEqualTo(Sign.WARNING);
        assertThat(recovery.get(1).dimension()).isEqualTo(Dimension.VARIABLE);
        assertThat(recovery.get(2).sign()).isEqualTo(Sign.NORMAL);
        assertThat(recovery.get(2).dimension()).isEqualTo(Dimension.CONSTANT);
    }

    @Test
    @DisplayName("Direct critical alert")
    void directCriticalAlert() {
        Situation brokerStatus = situations.percept(cortex().name("broker.down"));

        List<Signal> alert = new ArrayList<>();
        situations.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(alert::add);
            }
        ));

        // ACT
        brokerStatus.critical(Dimension.CONSTANT);  // Broker down, immediate action, stuck

        circuit.await();

        // ASSERT
        assertThat(alert).hasSize(1);
        assertThat(alert.get(0).sign()).isEqualTo(Sign.CRITICAL);
        assertThat(alert.get(0).dimension()).isEqualTo(Dimension.CONSTANT);
    }

    @Test
    @DisplayName("Multiple situation tracking")
    void multipleSituationTracking() {
        Situation lagStatus = situations.percept(cortex().name("lag.status"));
        Situation errorStatus = situations.percept(cortex().name("error.status"));

        List<String> assessments = new ArrayList<>();
        situations.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    assessments.add(subject.name() + ":" + signal.sign());
                });
            }
        ));

        // ACT
        lagStatus.warning(Dimension.VARIABLE);        // Lag trending up, fluctuating
        errorStatus.critical(Dimension.VOLATILE);     // Errors spiking, chaotic

        circuit.await();

        // ASSERT
        assertThat(assessments).contains(
            "lag.status:WARNING",
            "error.status:CRITICAL"
        );
    }

    @Test
    @DisplayName("All 3 urgency signs available")
    void allUrgencySignsAvailable() {
        Situation situation = situations.percept(cortex().name("test-situation"));

        // ACT
        situation.normal(Dimension.CONSTANT);
        situation.warning(Dimension.CONSTANT);
        situation.critical(Dimension.CONSTANT);

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(3);
        assertThat(allSigns).contains(
            Sign.NORMAL,
            Sign.WARNING,
            Sign.CRITICAL
        );
    }
}
