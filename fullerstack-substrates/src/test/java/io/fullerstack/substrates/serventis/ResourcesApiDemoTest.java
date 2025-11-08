package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Resources.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Resources;

/**
 * Demonstration of the Resources API (RC6) - Resources.Resource acquisition/release (ORIENT phase).
 * <p>
 * Resources API tracks resource lifecycle and capacity management.
 * <p>
 * Resources.Resource Signs (6):
 * - ATTEMPT: Resources.Resource acquisition attempted
 * - ACQUIRE: Acquisition initiated
 * - GRANT: Resources.Resource granted
 * - DENY: Resources.Resource denied (unavailable)
 * - TIMEOUT: Acquisition timed out
 * - RELEASE: Resources.Resource released
 * <p>
 * Kafka Use Cases:
 * - Connection pool management
 * - Thread pool tracking
 * - Memory allocation
 * - Partition assignment
 */
@DisplayName("Resources API (RC6) - Resources.Resource Acquisition/Release (ORIENT)")
class ResourcesApiDemoTest {

    private Circuit circuit;
    private Conduit<Resources.Resource, Sign> resources;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("resources-demo"));
        resources = circuit.conduit(
            cortex().name("resources"),
            Resources::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Successful acquisition: ATTEMPT → ACQUIRE → GRANT → RELEASE")
    void successfulAcquisition() {
        Resources.Resource connectionPool = resources.get(cortex().name("connection-pool"));

        List<Sign> lifecycle = new ArrayList<>();
        resources.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(lifecycle::add);
            }
        ));

        // ACT
        connectionPool.attempt();   // Try to get connection
        connectionPool.acquire();   // Acquiring
        connectionPool.grant();     // Connection granted
        connectionPool.release();   // Return to pool

        circuit.await();

        // ASSERT
        assertThat(lifecycle).containsExactly(
            Sign.ATTEMPT,
            Sign.ACQUIRE,
            Sign.GRANT,
            Sign.RELEASE
        );
    }

    @Test
    @DisplayName("Resources.Resource denial: ATTEMPT → ACQUIRE → DENY")
    void resourceDenial() {
        Resources.Resource threadPool = resources.get(cortex().name("worker-threads"));

        List<Sign> events = new ArrayList<>();
        resources.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        threadPool.attempt();  // Try to get thread
        threadPool.acquire();  // Acquiring
        threadPool.deny();     // Pool exhausted

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.ATTEMPT,
            Sign.ACQUIRE,
            Sign.DENY
        );
    }

    @Test
    @DisplayName("Resources.Resource timeout: ATTEMPT → ACQUIRE → TIMEOUT")
    void resourceTimeout() {
        Resources.Resource resource = resources.get(cortex().name("lock"));

        List<Sign> events = new ArrayList<>();
        resources.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        resource.attempt();   // Try to acquire
        resource.acquire();   // Acquiring
        resource.timeout();   // Timed out waiting

        circuit.await();

        // ASSERT
        assertThat(events).contains(Sign.TIMEOUT);
    }

    @Test
    @DisplayName("Multiple resource tracking")
    void multipleResourceTracking() {
        Resources.Resource conn1 = resources.get(cortex().name("connection-1"));
        Resources.Resource conn2 = resources.get(cortex().name("connection-2"));

        List<String> events = new ArrayList<>();
        resources.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Sign>> subject, Registrar<Sign> registrar) -> {
                registrar.register(sign -> {
                    events.add(subject.name() + ":" + sign);
                });
            }
        ));

        // ACT
        conn1.attempt();
        conn1.grant();
        conn2.attempt();
        conn2.deny();   // No capacity

        circuit.await();

        // ASSERT
        assertThat(events).contains(
            "connection-1:GRANT",
            "connection-2:DENY"
        );
    }

    @Test
    @DisplayName("All 6 signs available")
    void allSignsAvailable() {
        Resources.Resource resource = resources.get(cortex().name("test-resource"));

        // ACT
        resource.attempt();
        resource.acquire();
        resource.grant();
        resource.deny();
        resource.timeout();
        resource.release();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(6);
        assertThat(allSigns).contains(
            Sign.ATTEMPT,
            Sign.ACQUIRE,
            Sign.GRANT,
            Sign.DENY,
            Sign.TIMEOUT,
            Sign.RELEASE
        );
    }
}
