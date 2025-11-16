package io.fullerstack.substrates.userguide;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test demonstrating the User Guide "Hello Substrates" example.
 * <p>
 * This verifies that the example from the Substrates User Guide works correctly
 * with our fullerstack-substrates implementation.
 */
@DisplayName("User Guide - Hello Substrates Example")
class HelloSubstratesTest {

    @Test
    @DisplayName("Basic reservoir pattern from user guide")
    void helloSubstratesExample() {
        // 1. Get the Cortex (singleton factory)
        final Cortex cortex = cortex();

        // 2. Create a Circuit (processing engine)
        final Circuit circuit = cortex.circuit();

        try {
            // 3. Create a Conduit (channel pool) with a composer
            final Conduit<Pipe<String>, String> conduit =
                circuit.conduit(Composer.pipe(String.class));

            // 4. Get a named channel (creates or reuses)
            final Name name = cortex.name("greeting");
            final Pipe<String> pipe = conduit.percept(name);

            // 5. Create a reservoir to observe emissions
            final Reservoir<String> reservoir = cortex.reservoir(conduit);

            // 6. Emit values
            pipe.emit("Hello");
            pipe.emit("Substrates");

            // 7. Wait for all emissions to process
            circuit.await();

            // 8. Drain observations
            List<String> results = reservoir.drain()
                .map(capture -> capture.subject().name() + ": " + capture.emission())
                .toList();

            // ASSERT: Verify we got both emissions
            assertThat(results).containsExactly(
                "greeting: Hello",
                "greeting: Substrates"
            );

        } finally {
            // 9. Always close resources
            circuit.close();
        }
    }

    @Test
    @DisplayName("Multiple percepts with reservoir")
    void multiplePerceptsWithReservoir() {
        final Cortex cortex = cortex();
        final Circuit circuit = cortex.circuit();

        try {
            final Conduit<Pipe<String>, String> conduit =
                circuit.conduit(Composer.pipe(String.class));

            // Create multiple named pipes
            final Pipe<String> greeting = conduit.percept(cortex.name("greeting"));
            final Pipe<String> farewell = conduit.percept(cortex.name("farewell"));

            // Single reservoir observes all emissions from the conduit
            final Reservoir<String> reservoir = cortex.reservoir(conduit);

            // Emit from different pipes
            greeting.emit("Hello");
            farewell.emit("Goodbye");
            greeting.emit("World");

            circuit.await();

            // Drain and verify all emissions captured
            List<Capture<String>> captures = reservoir.drain().toList();

            assertThat(captures).hasSize(3);
            assertThat(captures.get(0).subject().name().toString()).isEqualTo("greeting");
            assertThat(captures.get(0).emission()).isEqualTo("Hello");
            assertThat(captures.get(1).subject().name().toString()).isEqualTo("farewell");
            assertThat(captures.get(1).emission()).isEqualTo("Goodbye");
            assertThat(captures.get(2).subject().name().toString()).isEqualTo("greeting");
            assertThat(captures.get(2).emission()).isEqualTo("World");

        } finally {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Reservoir drain clears buffer")
    void reservoirDrainClearsBuffer() {
        final Cortex cortex = cortex();
        final Circuit circuit = cortex.circuit();

        try {
            final Conduit<Pipe<String>, String> conduit =
                circuit.conduit(Composer.pipe(String.class));
            final Pipe<String> pipe = conduit.percept(cortex.name("test"));
            final Reservoir<String> reservoir = cortex.reservoir(conduit);

            // First batch
            pipe.emit("First");
            circuit.await();
            List<String> firstDrain = reservoir.drain()
                .map(Capture::emission)
                .toList();

            // Second batch
            pipe.emit("Second");
            circuit.await();
            List<String> secondDrain = reservoir.drain()
                .map(Capture::emission)
                .toList();

            // ASSERT: Each drain only contains emissions since last drain
            assertThat(firstDrain).containsExactly("First");
            assertThat(secondDrain).containsExactly("Second");

        } finally {
            circuit.close();
        }
    }
}
