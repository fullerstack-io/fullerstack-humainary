package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Services.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Services;

/**
 * Demonstration of the Services API (RC6) - Service interaction lifecycle.
 * <p>
 * The Services API enables observation of service call patterns, outcomes, and
 * remediation strategies through semantic signal emission.
 * <p>
 * Key lifecycle Signs (16 total):
 * - START/STOP: Work execution boundaries
 * - CALL/SUCCESS/FAIL: Basic service invocation
 * - RETRY/REDIRECT/RECOURSE: Recovery strategies
 * - REJECT/DISCARD: Admission control
 * - SCHEDULE/DELAY/SUSPEND/RESUME: Work management
 * - EXPIRE/DISCONNECT: Failure modes
 * <p>
 * Kafka Use Cases:
 * - Producer send lifecycle (CALL → SUCCESS/FAIL)
 * - Consumer rebalance (START → STOP)
 * - Broker request handling (CALL → SUCCESS/FAIL)
 * - Partition reassignment (SCHEDULE → START → STOP)
 */
@DisplayName("Services API (RC6) - Service Interaction Lifecycle")
class ServicesApiDemoTest {

    private Circuit circuit;
    private Conduit<Service, Signal> services;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("services-demo"));
        services = circuit.conduit(
            cortex().name("services"),
            Services::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Basic service call: CALL → START → SUCCESS → STOP")
    void basicServiceCall() {
        Service service = services.get(cortex().name("user-api"));

        List<Sign> lifecycle = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> lifecycle.add(signal.sign()));
            }
        ));

        // ACT
        service.call();       // Initiate service call
        service.start();      // Begin execution
        service.success();    // Successful result
        service.stop();       // Complete execution

        circuit.await();

        // ASSERT
        assertThat(lifecycle).containsExactly(
            Sign.CALL,
            Sign.START,
            Sign.SUCCESS,
            Sign.STOP
        );
    }

    @Test
    @DisplayName("Service failure with retry: CALL → FAIL → RETRY → SUCCESS")
    void serviceFailureWithRetry() {
        Service service = services.get(cortex().name("payment-api"));

        List<Sign> retryFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> retryFlow.add(signal.sign()));
            }
        ));

        // ACT
        service.call();       // Initial attempt
        service.fail();       // First attempt failed
        service.retry();      // Retry strategy triggered
        service.call();       // Second attempt
        service.success();    // Retry succeeded

        circuit.await();

        // ASSERT
        assertThat(retryFlow).contains(
            Sign.CALL,
            Sign.FAIL,
            Sign.RETRY
        );
    }

    @Test
    @DisplayName("Circuit breaker recourse: FAIL → RECOURSE")
    void circuitBreakerRecourse() {
        Service service = services.get(cortex().name("recommendations"));

        List<Sign> degradedFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> degradedFlow.add(signal.sign()));
            }
        ));

        // ACT
        service.call();       // Attempt call
        service.fail();       // Service unavailable
        service.recourse();   // Fall back to cached results

        circuit.await();

        // ASSERT
        assertThat(degradedFlow).containsExactly(
            Sign.CALL,
            Sign.FAIL,
            Sign.RECOURSE
        );
    }

    @Test
    @DisplayName("Load balancer redirect: CALL → REDIRECT → SUCCESS")
    void loadBalancerRedirect() {
        Service primaryService = services.get(cortex().name("api.primary"));
        Service fallbackService = services.get(cortex().name("api.fallback"));

        List<String> redirectFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    redirectFlow.add(subject.name() + ":" + signal.sign());
                });
            }
        ));

        // ACT
        primaryService.call();       // Try primary
        primaryService.redirect();   // Primary overloaded, redirect
        fallbackService.called();    // Fallback receives redirect
        fallbackService.success();   // Fallback succeeds

        circuit.await();

        // ASSERT
        assertThat(redirectFlow).contains(
            "api.primary:CALL",
            "api.primary:REDIRECT",
            "api.fallback:CALL",
            "api.fallback:SUCCESS"
        );
    }

    @Test
    @DisplayName("Rate limiting: CALL → REJECT")
    void rateLimiting() {
        Service service = services.get(cortex().name("rate-limited-api"));

        List<Sign> rejected = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> rejected.add(signal.sign()));
            }
        ));

        // ACT - Exceed rate limit
        for (int i = 0; i < 5; i++) {
            service.call();
        }
        service.reject();  // Rate limit exceeded

        circuit.await();

        // ASSERT
        assertThat(rejected).contains(Sign.REJECT);
    }

    @Test
    @DisplayName("Work scheduling: SCHEDULE → DELAY → START")
    void workScheduling() {
        Service batchJob = services.get(cortex().name("nightly-batch"));

        List<Sign> scheduled = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> scheduled.add(signal.sign()));
            }
        ));

        // ACT
        batchJob.schedule();   // Queue for execution
        batchJob.delay();      // Backpressure delay
        batchJob.start();      // Begin execution

        circuit.await();

        // ASSERT
        assertThat(scheduled).containsExactly(
            Sign.SCHEDULE,
            Sign.DELAY,
            Sign.START
        );
    }

    @Test
    @DisplayName("Long-running workflow: START → SUSPEND → RESUME → STOP")
    void longRunningWorkflow() {
        Service workflow = services.get(cortex().name("order-saga"));

        List<Sign> sagaFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> sagaFlow.add(signal.sign()));
            }
        ));

        // ACT
        workflow.start();     // Begin saga
        workflow.suspend();   // Wait for payment confirmation
        workflow.resume();    // Payment confirmed, continue
        workflow.stop();      // Saga complete

        circuit.await();

        // ASSERT
        assertThat(sagaFlow).containsExactly(
            Sign.START,
            Sign.SUSPEND,
            Sign.RESUME,
            Sign.STOP
        );
    }

    @Test
    @DisplayName("Service disconnect: CALL → DISCONNECT")
    void serviceDisconnect() {
        Service service = services.get(cortex().name("remote-service"));

        List<Sign> connectionIssues = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (subject, registrar) -> {
                registrar.register(signal -> connectionIssues.add(signal.sign()));
            }
        ));

        // ACT
        service.call();         // Attempt call
        service.disconnect();   // Network failure

        circuit.await();

        // ASSERT
        assertThat(connectionIssues).containsExactly(
            Sign.CALL,
            Sign.DISCONNECT
        );
    }

    @Test
    @DisplayName("Dual orientation: RELEASE vs RECEIPT")
    void dualPolarity() {
        Service client = services.get(cortex().name("client"));
        Service server = services.get(cortex().name("server"));

        List<String> orientations = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("observer"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    String perspective = signal.dimension() == Dimension.RELEASE ? "SELF" : "OBSERVED";
                    orientations.add(subject.name() + ":" + signal.sign() + ":" + perspective);
                });
            }
        ));

        // ACT
        client.call();       // RELEASE: I am calling
        server.called();     // RECEIPT: It was called
        server.success();    // RELEASE: I succeeded
        client.succeeded();  // RECEIPT: It succeeded

        circuit.await();

        // ASSERT
        assertThat(orientations).containsExactly(
            "client:CALL:SELF",
            "server:CALL:OBSERVED",
            "server:SUCCESS:SELF",
            "client:SUCCESS:OBSERVED"
        );
    }

    @Test
    @DisplayName("All 16 signs available")
    void allSignsAvailable() {
        Service service = services.get(cortex().name("test-service"));

        // ACT - Emit all signs
        service.start();
        service.stop();
        service.call();
        service.success();
        service.fail();
        service.recourse();
        service.redirect();
        service.expire();
        service.retry();
        service.reject();
        service.discard();
        service.delay();
        service.schedule();
        service.suspend();
        service.resume();
        service.disconnect();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(16);
        assertThat(allSigns).contains(
            Sign.START, Sign.STOP, Sign.CALL, Sign.SUCCESS, Sign.FAIL,
            Sign.RECOURSE, Sign.REDIRECT, Sign.EXPIRE, Sign.RETRY,
            Sign.REJECT, Sign.DISCARD, Sign.DELAY, Sign.SCHEDULE,
            Sign.SUSPEND, Sign.RESUME, Sign.DISCONNECT
        );

        // 16 signs × 2 orientations = 32 signals
        Signal[] allSignals = Signal.values();
        assertThat(allSignals).hasSize(32);
    }
}
