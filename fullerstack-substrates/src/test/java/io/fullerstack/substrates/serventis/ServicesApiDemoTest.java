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
 * Demonstration of the Services API (1.0.0-PREVIEW) - Service interaction lifecycle.
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
 * Dimensions (CALLER/CALLEE): Replaced RELEASE/RECEIPT for clarity
 * - CALLER: Client perspective ("I am calling", "my call succeeded")
 * - CALLEE: Server perspective ("serving request", "request failed")
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
        Service service = services.percept(cortex().name("user-api"));

        List<Sign> lifecycle = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> lifecycle.add(signal.sign()));
            }
        ));

        // ACT
        service.call(Dimension.CALLER);       // Initiate service call (caller perspective)
        service.start(Dimension.CALLEE);      // Begin execution (callee serving)
        service.success(Dimension.CALLEE);    // Successful result
        service.stop(Dimension.CALLEE);       // Complete execution

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
        Service service = services.percept(cortex().name("payment-api"));

        List<Sign> retryFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> retryFlow.add(signal.sign()));
            }
        ));

        // ACT
        service.call(Dimension.CALLER);       // Initial attempt
        service.fail(Dimension.CALLER);       // First attempt failed
        service.retry(Dimension.CALLER);      // Retry strategy triggered
        service.call(Dimension.CALLER);       // Second attempt
        service.success(Dimension.CALLER);    // Retry succeeded

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
        Service service = services.percept(cortex().name("recommendations"));

        List<Sign> degradedFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> degradedFlow.add(signal.sign()));
            }
        ));

        // ACT
        service.call(Dimension.CALLER);       // Attempt call
        service.fail(Dimension.CALLER);       // Service unavailable
        service.recourse(Dimension.CALLER);   // Fall back to cached results

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
        Service primaryService = services.percept(cortex().name("api.primary"));
        Service fallbackService = services.percept(cortex().name("api.fallback"));

        List<String> redirectFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    redirectFlow.add(subject.name() + ":" + signal.sign());
                });
            }
        ));

        // ACT
        primaryService.call(Dimension.CALLER);       // Try primary
        primaryService.redirect(Dimension.CALLER);   // Primary overloaded, redirect
        fallbackService.call(Dimension.CALLEE);    // Fallback receives redirect
        fallbackService.success(Dimension.CALLEE);   // Fallback succeeds

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
        Service service = services.percept(cortex().name("rate-limited-api"));

        List<Sign> rejected = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> rejected.add(signal.sign()));
            }
        ));

        // ACT - Exceed rate limit
        for (int i = 0; i < 5; i++) {
            service.call(Dimension.CALLER);
        }
        service.reject(Dimension.CALLEE);  // Rate limit exceeded

        circuit.await();

        // ASSERT
        assertThat(rejected).contains(Sign.REJECT);
    }

    @Test
    @DisplayName("Work scheduling: SCHEDULE → DELAY → START")
    void workScheduling() {
        Service batchJob = services.percept(cortex().name("nightly-batch"));

        List<Sign> scheduled = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> scheduled.add(signal.sign()));
            }
        ));

        // ACT
        batchJob.schedule(Dimension.CALLER);   // Queue for execution
        batchJob.delay(Dimension.CALLEE);      // Backpressure delay
        batchJob.start(Dimension.CALLEE);      // Begin execution

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
        Service workflow = services.percept(cortex().name("order-saga"));

        List<Sign> sagaFlow = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> sagaFlow.add(signal.sign()));
            }
        ));

        // ACT
        workflow.start(Dimension.CALLEE);     // Begin saga
        workflow.suspend(Dimension.CALLEE);   // Wait for payment confirmation
        workflow.resume(Dimension.CALLEE);    // Payment confirmed, continue
        workflow.stop(Dimension.CALLEE);      // Saga complete

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
        Service service = services.percept(cortex().name("remote-service"));

        List<Sign> connectionIssues = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> connectionIssues.add(signal.sign()));
            }
        ));

        // ACT
        service.call(Dimension.CALLER);         // Attempt call
        service.disconnect(Dimension.CALLER);   // Network failure

        circuit.await();

        // ASSERT
        assertThat(connectionIssues).containsExactly(
            Sign.CALL,
            Sign.DISCONNECT
        );
    }

    @Test
    @DisplayName("Dual perspective: CALLER vs CALLEE")
    void dualPolarity() {
        Service client = services.percept(cortex().name("client"));
        Service server = services.percept(cortex().name("server"));

        List<String> orientations = new ArrayList<>();
        services.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (Subject<Channel<Signal>> subject, Registrar<Signal> registrar) -> {
                registrar.register(signal -> {
                    String perspective = signal.dimension() == Dimension.CALLER ? "CALLER" : "CALLEE";
                    orientations.add(subject.name() + ":" + signal.sign() + ":" + perspective);
                });
            }
        ));

        // ACT
        client.call(Dimension.CALLER);       // CALLER: I am calling
        server.call(Dimension.CALLEE);       // CALLEE: Server was called
        server.success(Dimension.CALLEE);    // CALLEE: Server succeeded
        client.success(Dimension.CALLER);    // CALLER: My call succeeded

        circuit.await();

        // ASSERT
        assertThat(orientations).containsExactly(
            "client:CALL:CALLER",
            "server:CALL:CALLEE",
            "server:SUCCESS:CALLEE",
            "client:SUCCESS:CALLER"
        );
    }

    @Test
    @DisplayName("All 16 signs available")
    void allSignsAvailable() {
        Service service = services.percept(cortex().name("test-service"));

        // ACT - Emit all signs (with CALLER dimension for demonstration)
        service.start(Dimension.CALLEE);
        service.stop(Dimension.CALLEE);
        service.call(Dimension.CALLER);
        service.success(Dimension.CALLEE);
        service.fail(Dimension.CALLEE);
        service.recourse(Dimension.CALLER);
        service.redirect(Dimension.CALLER);
        service.expire(Dimension.CALLEE);
        service.retry(Dimension.CALLER);
        service.reject(Dimension.CALLEE);
        service.discard(Dimension.CALLEE);
        service.delay(Dimension.CALLEE);
        service.schedule(Dimension.CALLER);
        service.suspend(Dimension.CALLEE);
        service.resume(Dimension.CALLEE);
        service.disconnect(Dimension.CALLER);

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

        // 16 signs × 2 dimensions = 32 signals
        // Note: Signal is a record, not an enum, so no values() method
    }
}
