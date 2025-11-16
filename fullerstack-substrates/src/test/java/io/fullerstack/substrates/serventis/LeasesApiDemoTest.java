package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Leases.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Leases;

/**
 * Demonstration of the Leases API (PREVIEW) - Time-Bounded Ownership Observability.
 * <p>
 * Leases track time-bounded resource ownership for distributed coordination observability.
 * <p>
 * Lease Signs (9):
 * - ACQUIRE: Client requesting new lease
 * - GRANT: Lease successfully granted (positive ACQUIRE outcome)
 * - DENY: Lease request denied (negative ACQUIRE outcome)
 * - RENEW: Holder requesting to extend lease duration
 * - EXTEND: Lease duration successfully extended (positive RENEW outcome)
 * - RELEASE: Holder voluntarily terminated lease before expiration
 * - EXPIRE: Lease automatically terminated (TTL exhausted)
 * - REVOKE: Lease forcefully revoked by authority
 * - PROBE: Status check on lease validity or holder identity
 * <p>
 * Lease Dimensions (2):
 * - LESSOR: Authority perspective (coordination service, lock manager)
 * - LESSEE: Client perspective (application instance, service node)
 * <p>
 * Total: 9 signs × 2 dimensions = 18 possible signals
 * <p>
 * Lease Lifecycle:
 * <pre>
 * Request:  ACQUIRE (lessee) → GRANT (lessor) | DENY (lessor)
 *              ↓
 * Holding:  [lease active with TTL]
 *              ↓
 * Extension: RENEW (lessee) → EXTEND (lessor)
 *              ↓
 * Termination: RELEASE (lessee)     [voluntary]
 *          OR: EXPIRE (lessor)       [TTL exhausted]
 *          OR: REVOKE (lessor)       [forced revocation]
 *              ↓
 * Monitoring: PROBE (lessor/lessee) [status check]
 * </pre>
 * <p>
 * Kafka Use Cases:
 * - Consumer group coordinator leadership (leadership lease)
 * - Partition assignment ownership (partition lease per consumer)
 * - Producer transaction coordinator (transaction ID lease)
 * - Cluster controller leadership (broker lease)
 */
@DisplayName("Leases API (PREVIEW) - Time-Bounded Ownership Observability")
class LeasesApiDemoTest {

    private Circuit circuit;
    private Conduit<Lease, Signal> leases;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("leases-demo"));
        leases = circuit.conduit(
            cortex().name("leases"),
            Leases::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Full lease lifecycle: ACQUIRE → GRANT → RENEW → EXTEND → RELEASE")
    void fullLeaseLifecycle() {
        Lease leadership = leases.percept(cortex().name("group.leadership"));

        List<Signal> timeline = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Normal lease lifecycle
        leadership.acquire(Dimension.LESSEE);   // Client requests lease
        leadership.grant(Dimension.LESSOR);      // Authority grants lease
        leadership.renew(Dimension.LESSEE);      // Client renews lease (heartbeat)
        leadership.extend(Dimension.LESSOR);     // Authority extends TTL
        leadership.release(Dimension.LESSEE);    // Client voluntarily releases

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.RENEW, Dimension.LESSEE),
            new Signal(Sign.EXTEND, Dimension.LESSOR),
            new Signal(Sign.RELEASE, Dimension.LESSEE)
        );
    }

    @Test
    @DisplayName("Lease denial: ACQUIRE → DENY (resource already locked)")
    void leaseDenial() {
        Lease lock = leases.percept(cortex().name("critical.section.lock"));

        List<Signal> timeline = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Resource already locked by another
        lock.acquire(Dimension.LESSEE);   // Client 1 requests
        lock.grant(Dimension.LESSOR);      // Granted to client 1
        lock.acquire(Dimension.LESSEE);   // Client 2 requests
        lock.deny(Dimension.LESSOR);       // Denied - already locked

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.DENY, Dimension.LESSOR)
        );
    }

    @Test
    @DisplayName("Lease expiration: ACQUIRE → GRANT → [no renewal] → EXPIRE (TTL exhausted)")
    void leaseExpiration() {
        Lease session = leases.percept(cortex().name("consumer.session"));

        List<Signal> timeline = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Lease expires due to missing heartbeat
        session.acquire(Dimension.LESSEE);   // Consumer joins
        session.grant(Dimension.LESSOR);      // Session granted
        // ... consumer crashes, no RENEW heartbeat ...
        session.expire(Dimension.LESSOR);     // Session timeout

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.EXPIRE, Dimension.LESSOR)
        );
    }

    @Test
    @DisplayName("Lease revocation: GRANT → REVOKE (split-brain detection)")
    void leaseRevocation() {
        Lease leadership = leases.percept(cortex().name("cluster.controller"));

        List<Signal> timeline = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Split-brain: old leader still claims leadership after expiration
        leadership.grant(Dimension.LESSOR);      // Broker 1 becomes controller
        // ... network partition ...
        leadership.expire(Dimension.LESSOR);     // Broker 1 lease expires (no heartbeat)
        leadership.grant(Dimension.LESSOR);      // Broker 2 becomes new controller
        leadership.revoke(Dimension.LESSOR);     // System detects Broker 1 still claims leadership

        circuit.await();

        // ASSERT
        assertThat(timeline).containsExactly(
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.EXPIRE, Dimension.LESSOR),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.REVOKE, Dimension.LESSOR)
        );
    }

    @Test
    @DisplayName("Leader election pattern: Multiple candidates, one GRANT, others DENY")
    void leaderElectionPattern() {
        Lease groupLeadership = leases.percept(cortex().name("consumer-group-1.leadership"));

        List<String> events = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> {
                    String entity = subject.name().toString();
                    events.add(entity + ":" + signal.sign() + ":" + signal.dimension());
                });
            }
        ));

        // ACT - Leader election during rebalance
        groupLeadership.acquire(Dimension.LESSEE);   // Consumer A requests
        groupLeadership.acquire(Dimension.LESSEE);   // Consumer B requests
        groupLeadership.acquire(Dimension.LESSEE);   // Consumer C requests
        groupLeadership.grant(Dimension.LESSOR);      // Coordinator grants to A
        groupLeadership.deny(Dimension.LESSOR);       // B denied
        groupLeadership.deny(Dimension.LESSOR);       // C denied

        circuit.await();

        // ASSERT - One grant, two denies
        assertThat(events).containsExactly(
            "consumer-group-1.leadership:ACQUIRE:LESSEE",
            "consumer-group-1.leadership:ACQUIRE:LESSEE",
            "consumer-group-1.leadership:ACQUIRE:LESSEE",
            "consumer-group-1.leadership:GRANT:LESSOR",
            "consumer-group-1.leadership:DENY:LESSOR",
            "consumer-group-1.leadership:DENY:LESSOR"
        );
    }

    @Test
    @DisplayName("Dual-dimension signals: LESSOR (authority) vs LESSEE (client)")
    void dualDimensionSignals() {
        Lease lease = leases.percept(cortex().name("dual.test"));

        List<Signal> timeline = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Emit same signs from different dimensions
        lease.acquire(Dimension.LESSEE);   // Client perspective: I'm requesting
        lease.grant(Dimension.LESSOR);      // Authority perspective: I'm granting
        lease.renew(Dimension.LESSEE);      // Client perspective: I'm renewing
        lease.extend(Dimension.LESSOR);     // Authority perspective: I'm extending

        circuit.await();

        // ASSERT - Dimensions captured
        assertThat(timeline).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.RENEW, Dimension.LESSEE),
            new Signal(Sign.EXTEND, Dimension.LESSOR)
        );

        // Verify dimensions are distinct
        assertThat(timeline.get(0).dimension()).isEqualTo(Dimension.LESSEE);
        assertThat(timeline.get(1).dimension()).isEqualTo(Dimension.LESSOR);
    }

    @Test
    @DisplayName("All 9 signs × 2 dimensions = 18 signals available")
    void allSignalsAvailable() {
        Lease lease = leases.percept(cortex().name("comprehensive.test"));

        List<Signal> observed = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(observed::add);
            }
        ));

        // ACT - Emit all 9 signs from both dimensions
        lease.acquire(Dimension.LESSEE);
        lease.acquire(Dimension.LESSOR);
        lease.deny(Dimension.LESSEE);
        lease.deny(Dimension.LESSOR);
        lease.extend(Dimension.LESSEE);
        lease.extend(Dimension.LESSOR);
        lease.expire(Dimension.LESSEE);
        lease.expire(Dimension.LESSOR);
        lease.grant(Dimension.LESSEE);
        lease.grant(Dimension.LESSOR);
        lease.probe(Dimension.LESSEE);
        lease.probe(Dimension.LESSOR);
        lease.release(Dimension.LESSEE);
        lease.release(Dimension.LESSOR);
        lease.renew(Dimension.LESSEE);
        lease.renew(Dimension.LESSOR);
        lease.revoke(Dimension.LESSEE);
        lease.revoke(Dimension.LESSOR);

        circuit.await();

        // ASSERT - All 18 signals emitted
        assertThat(observed).hasSize(18);

        // ASSERT - All 9 signs present
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(9);
        assertThat(allSigns).contains(
            Sign.ACQUIRE,
            Sign.DENY,
            Sign.EXTEND,
            Sign.EXPIRE,
            Sign.GRANT,
            Sign.PROBE,
            Sign.RELEASE,
            Sign.RENEW,
            Sign.REVOKE
        );

        // ASSERT - Both dimensions present
        Dimension[] allDimensions = Dimension.values();
        assertThat(allDimensions).hasSize(2);
        assertThat(allDimensions).contains(
            Dimension.LESSOR,
            Dimension.LESSEE
        );
    }

    @Test
    @DisplayName("Kafka consumer group coordinator leadership lease")
    void kafkaConsumerGroupLeadershipLease() {
        Lease groupLeadership = leases.percept(cortex().name("consumer-group-1.leadership"));

        List<Signal> leadershipEvents = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(leadershipEvents::add);
            }
        ));

        // ACT - Simulate consumer group coordinator leadership lifecycle
        groupLeadership.acquire(Dimension.LESSEE);   // Consumer requests leadership during rebalance
        groupLeadership.grant(Dimension.LESSOR);      // Coordinator grants leadership lease
        groupLeadership.renew(Dimension.LESSEE);      // Leader sends heartbeat
        groupLeadership.extend(Dimension.LESSOR);     // Coordinator extends lease TTL
        groupLeadership.renew(Dimension.LESSEE);      // Another heartbeat
        groupLeadership.extend(Dimension.LESSOR);     // Lease extended again
        groupLeadership.release(Dimension.LESSEE);    // Leader voluntarily steps down

        circuit.await();

        // ASSERT - Leadership lease lifecycle captured
        assertThat(leadershipEvents).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.RENEW, Dimension.LESSEE),
            new Signal(Sign.EXTEND, Dimension.LESSOR),
            new Signal(Sign.RENEW, Dimension.LESSEE),
            new Signal(Sign.EXTEND, Dimension.LESSOR),
            new Signal(Sign.RELEASE, Dimension.LESSEE)
        );
    }

    @Test
    @DisplayName("Kafka partition assignment ownership lease")
    void kafkaPartitionAssignmentLease() {
        Lease partitionLease = leases.percept(cortex().name("consumer-1.partition-0.assignment"));

        List<Signal> assignmentEvents = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(assignmentEvents::add);
            }
        ));

        // ACT - Simulate partition assignment lease
        partitionLease.acquire(Dimension.LESSEE);   // Consumer requests partition ownership
        partitionLease.grant(Dimension.LESSOR);      // Coordinator grants partition assignment
        partitionLease.renew(Dimension.LESSEE);      // Consumer heartbeat maintains ownership
        partitionLease.extend(Dimension.LESSOR);     // Coordinator extends assignment
        // ... rebalance triggered ...
        partitionLease.revoke(Dimension.LESSOR);     // Coordinator revokes partition (reassignment)

        circuit.await();

        // ASSERT - Partition ownership lease with revocation
        assertThat(assignmentEvents).containsExactly(
            new Signal(Sign.ACQUIRE, Dimension.LESSEE),
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.RENEW, Dimension.LESSEE),
            new Signal(Sign.EXTEND, Dimension.LESSOR),
            new Signal(Sign.REVOKE, Dimension.LESSOR)
        );
    }

    @Test
    @DisplayName("Lease probe: Checking lease validity and holder identity")
    void leaseProbe() {
        Lease lease = leases.percept(cortex().name("session.lease"));

        List<Signal> probeEvents = new ArrayList<>();
        leases.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(probeEvents::add);
            }
        ));

        // ACT - Lease granted, then probed from both perspectives
        lease.grant(Dimension.LESSOR);      // Lease active
        lease.probe(Dimension.LESSOR);       // Authority checks: "Is lease still valid?"
        lease.probe(Dimension.LESSEE);       // Client checks: "Do I still hold this lease?"

        circuit.await();

        // ASSERT - PROBE from both dimensions
        assertThat(probeEvents).containsExactly(
            new Signal(Sign.GRANT, Dimension.LESSOR),
            new Signal(Sign.PROBE, Dimension.LESSOR),
            new Signal(Sign.PROBE, Dimension.LESSEE)
        );
    }
}
