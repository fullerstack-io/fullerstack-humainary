package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Transactions.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Transactions;

/**
 * Demonstration of the Transactions API (PREVIEW) - Distributed Transaction Observability.
 * <p>
 * Transactions track distributed coordination patterns and transactional semantics for
 * distributed systems observability.
 * <p>
 * Transaction Signs (8):
 * - START: Transaction initiation
 * - PREPARE: Voting/prepare phase (2PC: "can you commit?")
 * - COMMIT: Final commitment (all voted yes)
 * - ROLLBACK: Transaction abort (explicit rollback)
 * - ABORT: Forced termination (deadlock, constraint violation)
 * - EXPIRE: Transaction exceeded time budget
 * - CONFLICT: Write conflict or constraint violation
 * - COMPENSATE: Compensating action (saga pattern)
 * <p>
 * Transaction Dimensions (2):
 * - COORDINATOR: Transaction manager perspective (protocol leader)
 * - PARTICIPANT: Client perspective (protocol member)
 * <p>
 * Total: 8 signs × 2 dimensions = 16 possible signals
 * <p>
 * Transaction Lifecycle (Two-Phase Commit):
 * <pre>
 * Initiation:    START (coordinator) → START (participant)
 *      ↓
 * Voting Phase:  PREPARE (coordinator) → PREPARE (participant votes yes/no)
 *      ↓
 * Decision:      COMMIT (coordinator) → COMMIT (participant) [if all yes]
 *            OR: ROLLBACK (coordinator) → ROLLBACK (participant) [if any no]
 *      ↓
 * Resolution:    (Transaction complete)
 * </pre>
 * <p>
 * Kafka Use Cases:
 * - Producer exactly-once semantics (transactional producer)
 * - Kafka Streams exactly-once processing
 * - Consumer group coordinator transactions
 * - Cross-partition atomic writes
 */
@DisplayName("Transactions API (PREVIEW) - Distributed Transaction Observability")
class TransactionsApiDemoTest {

    private Circuit circuit;
    private Conduit<Transaction, Signal> transactions;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("transactions-demo"));
        transactions = circuit.conduit(
            cortex().name("transactions"),
            Transactions::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Two-phase commit success: START → PREPARE → COMMIT (all participants vote yes)")
    void twoPhasesCommitSuccess() {
        Transaction txn = transactions.percept(cortex().name("db.transaction-1"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Successful 2PC flow
        txn.start(Dimension.COORDINATOR);       // Coordinator starts transaction
        txn.start(Dimension.PARTICIPANT);       // Participant observes start
        txn.prepare(Dimension.COORDINATOR);     // Coordinator sends prepare
        txn.prepare(Dimension.PARTICIPANT);     // Participant votes yes (prepared)
        txn.commit(Dimension.COORDINATOR);      // Coordinator commits (all voted yes)
        txn.commit(Dimension.PARTICIPANT);      // Participant applies commit

        circuit.await();

        // ASSERT - Complete 2PC success timeline
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.COORDINATOR),
            new Signal(Sign.PREPARE, Dimension.PARTICIPANT),
            new Signal(Sign.COMMIT, Dimension.COORDINATOR),
            new Signal(Sign.COMMIT, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Two-phase commit rollback: START → PREPARE → ROLLBACK (participant votes no)")
    void twoPhaseCommitRollback() {
        Transaction txn = transactions.percept(cortex().name("db.transaction-2"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - 2PC rollback flow (participant voted no or failed to prepare)
        txn.start(Dimension.COORDINATOR);       // Coordinator starts
        txn.start(Dimension.PARTICIPANT);       // Participant observes start
        txn.prepare(Dimension.COORDINATOR);     // Coordinator sends prepare
        // (Participant failed to prepare or voted no - no PREPARE from participant)
        txn.rollback(Dimension.COORDINATOR);    // Coordinator rolls back
        txn.rollback(Dimension.PARTICIPANT);    // Participant discards changes

        circuit.await();

        // ASSERT - 2PC rollback timeline
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.COORDINATOR),
            new Signal(Sign.ROLLBACK, Dimension.COORDINATOR),
            new Signal(Sign.ROLLBACK, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Transaction expiration: START → PREPARE → EXPIRE → ROLLBACK (timeout)")
    void transactionExpiration() {
        Transaction txn = transactions.percept(cortex().name("db.transaction-3"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Transaction expires (participant took too long to prepare)
        txn.start(Dimension.COORDINATOR);       // Coordinator starts
        txn.start(Dimension.PARTICIPANT);       // Participant observes start
        txn.prepare(Dimension.COORDINATOR);     // Coordinator sends prepare
        // ... participant timeout (no PREPARE response) ...
        txn.expire(Dimension.COORDINATOR);      // Coordinator detects expiration
        txn.rollback(Dimension.COORDINATOR);    // Coordinator rolls back
        txn.rollback(Dimension.PARTICIPANT);    // Participant discards changes

        circuit.await();

        // ASSERT - Expiration timeline
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.COORDINATOR),
            new Signal(Sign.EXPIRE, Dimension.COORDINATOR),
            new Signal(Sign.ROLLBACK, Dimension.COORDINATOR),
            new Signal(Sign.ROLLBACK, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Transaction conflict: START → PREPARE → CONFLICT → ABORT (write-write conflict)")
    void transactionConflict() {
        Transaction txn = transactions.percept(cortex().name("db.transaction-4"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Transaction conflicts (write-write conflict, optimistic locking failure)
        txn.start(Dimension.COORDINATOR);       // Coordinator starts
        txn.start(Dimension.PARTICIPANT);       // Participant observes start
        txn.prepare(Dimension.COORDINATOR);     // Coordinator sends prepare
        txn.conflict(Dimension.PARTICIPANT);    // Participant detects conflict
        txn.abort(Dimension.COORDINATOR);       // Coordinator aborts
        txn.abort(Dimension.PARTICIPANT);       // Participant aborts

        circuit.await();

        // ASSERT - Conflict timeline
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.COORDINATOR),
            new Signal(Sign.CONFLICT, Dimension.PARTICIPANT),
            new Signal(Sign.ABORT, Dimension.COORDINATOR),
            new Signal(Sign.ABORT, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Saga pattern compensation: START → steps → COMPENSATE → ROLLBACK")
    void sagaPatternCompensation() {
        Transaction saga = transactions.percept(cortex().name("saga.order-processing"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Saga pattern with compensation
        saga.start(Dimension.COORDINATOR);         // Saga orchestrator starts
        saga.start(Dimension.PARTICIPANT);         // Saga participant observes start
        // ... saga steps execute successfully ...
        // ... then a later step fails ...
        saga.compensate(Dimension.COORDINATOR);    // Saga orchestrator initiates compensation
        saga.compensate(Dimension.PARTICIPANT);    // Participant executes compensating transaction
        saga.rollback(Dimension.COORDINATOR);      // Saga orchestrator rolls back
        saga.rollback(Dimension.PARTICIPANT);      // Participant completes rollback

        circuit.await();

        // ASSERT - Saga compensation timeline
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.COMPENSATE, Dimension.COORDINATOR),
            new Signal(Sign.COMPENSATE, Dimension.PARTICIPANT),
            new Signal(Sign.ROLLBACK, Dimension.COORDINATOR),
            new Signal(Sign.ROLLBACK, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Multiple participants in 2PC: One participant votes no")
    void multipleParticipantsOneVotesNo() {
        Transaction coordinator = transactions.percept(cortex().name("coordinator.txn-5"));
        Transaction participant1 = transactions.percept(cortex().name("participant-1.txn-5"));
        Transaction participant2 = transactions.percept(cortex().name("participant-2.txn-5"));

        List<String> events = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(signal -> {
                    String entity = subject.name().toString();
                    events.add(entity + ":" + signal.sign() + ":" + signal.dimension());
                });
            }
        ));

        // ACT - 2PC with multiple participants
        coordinator.start(Dimension.COORDINATOR);      // Coordinator starts
        participant1.start(Dimension.PARTICIPANT);     // Participant 1 observes start
        participant2.start(Dimension.PARTICIPANT);     // Participant 2 observes start
        coordinator.prepare(Dimension.COORDINATOR);    // Coordinator sends prepare
        participant1.prepare(Dimension.PARTICIPANT);   // Participant 1 votes yes
        participant2.conflict(Dimension.PARTICIPANT);  // Participant 2 votes no (conflict)
        coordinator.rollback(Dimension.COORDINATOR);   // Coordinator rolls back (not unanimous)
        participant1.rollback(Dimension.PARTICIPANT);  // Participant 1 discards
        participant2.rollback(Dimension.PARTICIPANT);  // Participant 2 discards

        circuit.await();

        // ASSERT - Multiple participants with one voting no
        assertThat(events).containsExactly(
            "coordinator.txn-5:START:COORDINATOR",
            "participant-1.txn-5:START:PARTICIPANT",
            "participant-2.txn-5:START:PARTICIPANT",
            "coordinator.txn-5:PREPARE:COORDINATOR",
            "participant-1.txn-5:PREPARE:PARTICIPANT",
            "participant-2.txn-5:CONFLICT:PARTICIPANT",
            "coordinator.txn-5:ROLLBACK:COORDINATOR",
            "participant-1.txn-5:ROLLBACK:PARTICIPANT",
            "participant-2.txn-5:ROLLBACK:PARTICIPANT"
        );
    }

    @Test
    @DisplayName("Dual-dimension signals: COORDINATOR vs PARTICIPANT perspectives")
    void dualDimensionSignals() {
        Transaction txn = transactions.percept(cortex().name("dual.test"));

        List<Signal> timeline = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(timeline::add);
            }
        ));

        // ACT - Emit same signs from different dimensions
        txn.start(Dimension.COORDINATOR);       // Coordinator perspective: I'm starting
        txn.start(Dimension.PARTICIPANT);       // Participant perspective: Coordinator started
        txn.prepare(Dimension.COORDINATOR);     // Coordinator perspective: I'm preparing
        txn.prepare(Dimension.PARTICIPANT);     // Participant perspective: I prepared (voted yes)
        txn.commit(Dimension.COORDINATOR);      // Coordinator perspective: I'm committing
        txn.commit(Dimension.PARTICIPANT);      // Participant perspective: Coordinator committed

        circuit.await();

        // ASSERT - Dimensions captured
        assertThat(timeline).containsExactly(
            new Signal(Sign.START, Dimension.COORDINATOR),
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.COORDINATOR),
            new Signal(Sign.PREPARE, Dimension.PARTICIPANT),
            new Signal(Sign.COMMIT, Dimension.COORDINATOR),
            new Signal(Sign.COMMIT, Dimension.PARTICIPANT)
        );

        // Verify dimensions are distinct
        assertThat(timeline.get(0).dimension()).isEqualTo(Dimension.COORDINATOR);
        assertThat(timeline.get(1).dimension()).isEqualTo(Dimension.PARTICIPANT);
    }

    @Test
    @DisplayName("All 8 signs × 2 dimensions = 16 signals available")
    void allSignalsAvailable() {
        Transaction txn = transactions.percept(cortex().name("comprehensive.test"));

        List<Signal> observed = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(observed::add);
            }
        ));

        // ACT - Emit all 8 signs from both dimensions
        txn.start(Dimension.COORDINATOR);
        txn.start(Dimension.PARTICIPANT);
        txn.prepare(Dimension.COORDINATOR);
        txn.prepare(Dimension.PARTICIPANT);
        txn.commit(Dimension.COORDINATOR);
        txn.commit(Dimension.PARTICIPANT);
        txn.rollback(Dimension.COORDINATOR);
        txn.rollback(Dimension.PARTICIPANT);
        txn.abort(Dimension.COORDINATOR);
        txn.abort(Dimension.PARTICIPANT);
        txn.expire(Dimension.COORDINATOR);
        txn.expire(Dimension.PARTICIPANT);
        txn.conflict(Dimension.COORDINATOR);
        txn.conflict(Dimension.PARTICIPANT);
        txn.compensate(Dimension.COORDINATOR);
        txn.compensate(Dimension.PARTICIPANT);

        circuit.await();

        // ASSERT - All 16 signals emitted
        assertThat(observed).hasSize(16);

        // ASSERT - All 8 signs present
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(8);
        assertThat(allSigns).contains(
            Sign.START,
            Sign.PREPARE,
            Sign.COMMIT,
            Sign.ROLLBACK,
            Sign.ABORT,
            Sign.EXPIRE,
            Sign.CONFLICT,
            Sign.COMPENSATE
        );

        // ASSERT - Both dimensions present
        Dimension[] allDimensions = Dimension.values();
        assertThat(allDimensions).hasSize(2);
        assertThat(allDimensions).contains(
            Dimension.COORDINATOR,
            Dimension.PARTICIPANT
        );
    }

    @Test
    @DisplayName("Kafka producer exactly-once transaction lifecycle")
    void kafkaProducerExactlyOnceTransaction() {
        Transaction producerTxn = transactions.percept(cortex().name("producer-1.transaction"));

        List<Signal> txnEvents = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(txnEvents::add);
            }
        ));

        // ACT - Kafka producer exactly-once semantics (simplified)
        // Producer is PARTICIPANT (Kafka broker is COORDINATOR)
        producerTxn.start(Dimension.PARTICIPANT);      // Producer begins transaction
        // ... producer sends records ...
        producerTxn.prepare(Dimension.PARTICIPANT);    // Producer prepares to commit (flush)
        producerTxn.commit(Dimension.PARTICIPANT);     // Producer commits transaction

        circuit.await();

        // ASSERT - Producer transaction lifecycle
        assertThat(txnEvents).containsExactly(
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.PREPARE, Dimension.PARTICIPANT),
            new Signal(Sign.COMMIT, Dimension.PARTICIPANT)
        );
    }

    @Test
    @DisplayName("Kafka producer transaction abort due to timeout")
    void kafkaProducerTransactionAbortTimeout() {
        Transaction producerTxn = transactions.percept(cortex().name("producer-2.transaction"));

        List<Signal> txnEvents = new ArrayList<>();
        transactions.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(txnEvents::add);
            }
        ));

        // ACT - Kafka producer transaction times out
        producerTxn.start(Dimension.PARTICIPANT);      // Producer begins transaction
        // ... producer sends records ...
        // ... transaction.timeout.ms exceeded ...
        producerTxn.expire(Dimension.PARTICIPANT);     // Producer detects expiration
        producerTxn.abort(Dimension.PARTICIPANT);      // Producer aborts transaction

        circuit.await();

        // ASSERT - Producer transaction timeout
        assertThat(txnEvents).containsExactly(
            new Signal(Sign.START, Dimension.PARTICIPANT),
            new Signal(Sign.EXPIRE, Dimension.PARTICIPANT),
            new Signal(Sign.ABORT, Dimension.PARTICIPANT)
        );
    }
}
