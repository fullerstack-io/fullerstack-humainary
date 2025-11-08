package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Reporters.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Reporters;

/**
 * Demonstration of the Reporters API (RC6) - Situation urgency assessment (DECIDE phase).
 * <p>
 * Reporters assess urgency/severity of situations to drive action decisions.
 * <p>
 * Reporter Signs (3 urgency levels):
 * - NORMAL: Routine, no action required
 * - WARNING: Attention needed, prepare for action
 * - CRITICAL: Immediate action required
 * <p>
 * Kafka Use Cases:
 * - Alerting system (escalate from NORMAL → WARNING → CRITICAL)
 * - SLA violation detection
 * - Incident management
 * - Auto-scaling triggers
 */
@DisplayName("Reporters API (RC6) - Situation Urgency Assessment (DECIDE)")
class ReportersApiDemoTest {

    private Circuit circuit;
    private Conduit<Reporter, Sign> reporters;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("reporters-demo"));
        reporters = circuit.conduit(
            cortex().name("reporters"),
            Reporters::composer
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
        Reporter systemStatus = reporters.get(cortex().name("cluster.status"));

        List<Sign> situations = new ArrayList<>();
        reporters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(situations::add);
            }
        ));

        // ACT
        systemStatus.normal();  // All systems operational

        circuit.await();

        // ASSERT
        assertThat(situations).containsExactly(Sign.NORMAL);
    }

    @Test
    @DisplayName("Escalation path: NORMAL → WARNING → CRITICAL")
    void escalationPath() {
        Reporter consumerLagStatus = reporters.get(cortex().name("consumer.lag.status"));

        List<Sign> escalation = new ArrayList<>();
        reporters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(escalation::add);
            }
        ));

        // ACT
        consumerLagStatus.normal();     // Lag within limits
        consumerLagStatus.warning();    // Lag increasing
        consumerLagStatus.critical();   // Lag critical, action needed

        circuit.await();

        // ASSERT
        assertThat(escalation).containsExactly(
            Sign.NORMAL,
            Sign.WARNING,
            Sign.CRITICAL
        );
    }

    @Test
    @DisplayName("De-escalation: CRITICAL → WARNING → NORMAL")
    void deEscalation() {
        Reporter incidentStatus = reporters.get(cortex().name("incident.status"));

        List<Sign> recovery = new ArrayList<>();
        reporters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(recovery::add);
            }
        ));

        // ACT
        incidentStatus.critical();  // Incident active
        incidentStatus.warning();   // Mitigation in progress
        incidentStatus.normal();    // Resolved

        circuit.await();

        // ASSERT
        assertThat(recovery).containsExactly(
            Sign.CRITICAL,
            Sign.WARNING,
            Sign.NORMAL
        );
    }

    @Test
    @DisplayName("Direct critical alert")
    void directCriticalAlert() {
        Reporter brokerStatus = reporters.get(cortex().name("broker.down"));

        List<Sign> alert = new ArrayList<>();
        reporters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(alert::add);
            }
        ));

        // ACT
        brokerStatus.critical();  // Broker down, immediate action

        circuit.await();

        // ASSERT
        assertThat(alert).containsExactly(Sign.CRITICAL);
    }

    @Test
    @DisplayName("Multiple situation tracking")
    void multipleSituationTracking() {
        Reporter lagStatus = reporters.get(cortex().name("lag.status"));
        Reporter errorStatus = reporters.get(cortex().name("error.status"));

        List<String> situations = new ArrayList<>();
        reporters.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    situations.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT
        lagStatus.warning();        // Lag trending up
        errorStatus.critical();     // Errors spiking

        circuit.await();

        // ASSERT
        assertThat(situations).contains(
            "lag.status:WARNING",
            "error.status:CRITICAL"
        );
    }

    @Test
    @DisplayName("All 3 urgency signs available")
    void allUrgencySignsAvailable() {
        Reporter reporter = reporters.get(cortex().name("test-reporter"));

        // ACT
        reporter.normal();
        reporter.warning();
        reporter.critical();

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
