package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Monitors.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Monitors;
import static io.humainary.substrates.ext.serventis.ext.Monitors.Dimension.*;

/**
 * Demonstration of the Monitors API (RC6) - Condition assessment (ORIENT phase).
 */
@DisplayName("Monitors API (RC6) - Condition Assessment (ORIENT)")
class MonitorsApiDemoTest {

    private Circuit circuit;
    private Conduit<Monitor, Signal> monitors;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("monitors-demo"));
        monitors = circuit.conduit(
            cortex().name("monitors"),
            Monitors::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic condition assessment")
    void basicConditionAssessment() {
        Monitor monitor = monitors.get(cortex().name("broker-health"));

        List<Signal> signals = new ArrayList<>();
        monitors.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signals::add);
            }
        ));

        // ACT
        monitor.stable(CONFIRMED);

        circuit.await();

        // ASSERT
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).sign()).isEqualTo(Sign.STABLE);
        assertThat(signals.get(0).dimension()).isEqualTo(CONFIRMED);
    }

    @Test
    @DisplayName("All 7 condition signs available")
    void allConditionsAvailable() {
        Monitor monitor = monitors.get(cortex().name("test-monitor"));

        // ACT
        monitor.converging(CONFIRMED);
        monitor.stable(CONFIRMED);
        monitor.diverging(CONFIRMED);
        monitor.erratic(CONFIRMED);
        monitor.degraded(CONFIRMED);
        monitor.defective(CONFIRMED);
        monitor.down(CONFIRMED);

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(7);
        assertThat(allSigns).contains(
            Sign.CONVERGING,
            Sign.STABLE,
            Sign.DIVERGING,
            Sign.ERRATIC,
            Sign.DEGRADED,
            Sign.DEFECTIVE,
            Sign.DOWN
        );
    }
}
