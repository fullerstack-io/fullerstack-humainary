package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.*;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify the @Queued annotation contract from Substrates API.
 *
 * <p>
 * The @Queued annotation indicates that a method's execution is queued to the
 * circuit's processing thread. Key semantics:
 *
 * <ul>
 * <li>Method returns immediately (non-blocking to caller)
 * <li>Actual work executes on circuit's processing thread
 * <li>Effects are not visible until circuit processes the job
 * <li>Use {@link Circuit#await()} to synchronize
 * <li>Ordering is deterministic relative to other circuit operations
 * </ul>
 *
 * <p>
 *
 * @Queued methods in Substrates API:
 *
 *         <ul>
 *         <li>{@link Circuit#close()} - queues circuit shutdown
 *         <li>{@link Pipe#emit(Object)} - queues emission to receptor
 *         <li>{@code Resource.close()} - queues resource cleanup (default
 *         no-op)
 *         <li>{@code Source.subscribe(Subscriber)} - queues subscriber
 *         registration
 *         </ul>
 */
@DisplayName ( "@Queued Annotation Contract Tests" )
class QueuedAnnotationContractTest {

  private Circuit circuit;
  private Name    testName;

  @BeforeEach
  void setUp () {
    testName = cortex ().name ( "test" );
    circuit = cortex ().circuit ( testName );
  }

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  // ============================================================
  // Pipe.emit() @Queued contract
  // ============================================================

  @Nested
  @DisplayName ( "Pipe.emit() @Queued contract" )
  class PipeEmitQueued {

    @Test
    @DisplayName ( "emit() effect becomes visible after await()" )
    void emit_effectVisibleAfterAwait () {
      AtomicBoolean received = new AtomicBoolean ( false );

      Pipe < Integer > pipe = circuit.pipe (
        (Receptor < Integer >) value -> received.set ( true )
      );

      pipe.emit ( 42 );
      circuit.await ();

      assertTrue ( received.get (), "Emission effect must be visible after await()" );
    }

    @Test
    @DisplayName ( "emit() multiple values processed in submission order" )
    void emit_multipleValues_processedInOrder () {
      List < Integer > results = Collections.synchronizedList ( new ArrayList <> () );

      Pipe < Integer > pipe = circuit.pipe (
        (Receptor < Integer >) results::add
      );

      pipe.emit ( 1 );
      pipe.emit ( 2 );
      pipe.emit ( 3 );
      circuit.await ();

      assertEquals ( List.of ( 1, 2, 3 ), results,
        "Queued emissions must be processed in submission order" );
    }

    @Test
    @DisplayName ( "emit() processes all values before await() returns" )
    void emit_allProcessedBeforeAwaitReturns () {
      AtomicInteger count = new AtomicInteger ();

      Pipe < Integer > pipe = circuit.pipe (
        (Receptor < Integer >) value -> count.incrementAndGet ()
      );

      for ( int i = 0; i < 100; i++ ) {
        pipe.emit ( i );
      }
      circuit.await ();

      assertEquals ( 100, count.get (),
        "All queued emissions must complete before await() returns" );
    }
  }

  // ============================================================
  // Circuit.close() @Queued contract
  // ============================================================

  @Nested
  @DisplayName ( "Circuit.close() @Queued contract" )
  class CircuitCloseQueued {

    @Test
    @DisplayName ( "close() allows prior queued operations to complete" )
    void close_priorQueuedOperationsComplete () {
      AtomicBoolean received = new AtomicBoolean ( false );

      Circuit localCircuit = cortex ().circuit ( cortex ().name ( "close-test" ) );

      Pipe < Integer > pipe = localCircuit.pipe (
        (Receptor < Integer >) value -> received.set ( true )
      );

      pipe.emit ( 42 );
      localCircuit.close ();
      localCircuit.await ();

      assertTrue ( received.get (),
        "Emissions queued before close() must be processed" );
    }

    @Test
    @DisplayName ( "close() is idempotent - multiple close calls are safe" )
    void close_idempotent () {
      Circuit localCircuit = cortex ().circuit ( cortex ().name ( "idempotent-test" ) );

      // Multiple closes should not throw
      assertDoesNotThrow ( () -> {
        localCircuit.close ();
        localCircuit.close ();
        localCircuit.close ();
        localCircuit.await ();
      }, "Multiple close() calls must be safe (idempotent)" );
    }
  }

  // ============================================================
  // Source.subscribe() @Queued contract
  // ============================================================

  @Nested
  @DisplayName ( "Source.subscribe() @Queued contract" )
  class SourceSubscribeQueued {

    @Test
    @DisplayName ( "subscribe() callback invoked after emission and await()" )
    void subscribe_callbackInvokedAfterEmissionAndAwait () {
      AtomicBoolean callbackFired = new AtomicBoolean ( false );

      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "conduit" ),
        Composer.pipe ()
      );

      conduit.subscribe (
        circuit.subscriber (
          cortex ().name ( "sub" ),
          ( subject, registrar ) -> {
            callbackFired.set ( true );
            registrar.register ( Receptor.of ( Integer.class ) );
          }
        )
      );

      // Emit to trigger subscriber callback
      conduit.percept ( cortex ().name ( "channel" ) ).emit ( 1 );
      circuit.await ();

      assertTrue ( callbackFired.get (),
        "Subscriber callback must be invoked after emission and await()" );
    }

    @Test
    @DisplayName ( "subscribe() multiple subscribers all notified" )
    void subscribe_multipleSubscribers_allNotified () {
      AtomicInteger subscriberCount = new AtomicInteger ();

      Conduit < Pipe < Integer >, Integer > conduit = circuit.conduit (
        cortex ().name ( "multi-sub" ),
        Composer.pipe ()
      );

      for ( int i = 0; i < 3; i++ ) {
        conduit.subscribe (
          circuit.subscriber (
            cortex ().name ( "sub-" + i ),
            ( subject, registrar ) -> {
              subscriberCount.incrementAndGet ();
              registrar.register ( Receptor.of ( Integer.class ) );
            }
          )
        );
      }

      conduit.percept ( cortex ().name ( "channel" ) ).emit ( 1 );
      circuit.await ();

      assertEquals ( 3, subscriberCount.get (),
        "All subscribers must be notified after emission" );
    }
  }

  // ============================================================
  // Queued ordering contract
  // ============================================================

  @Nested
  @DisplayName ( "Queued ordering contract" )
  class QueuedOrdering {

    @Test
    @DisplayName ( "Queued operations execute in deterministic order" )
    void queuedOperations_deterministicOrder () {
      List < String > events = Collections.synchronizedList ( new ArrayList <> () );

      Pipe < Integer > pipe = circuit.pipe (
        (Receptor < Integer >) value -> events.add ( "emit:" + value )
      );

      pipe.emit ( 1 );
      pipe.emit ( 2 );
      pipe.emit ( 3 );
      circuit.await ();

      assertEquals (
        List.of ( "emit:1", "emit:2", "emit:3" ),
        events,
        "Queued operations must execute in deterministic submission order"
      );
    }

    @Test
    @DisplayName ( "Emissions from different pipes on same circuit preserve per-pipe order" )
    void differentPipes_perPipeOrderPreserved () {
      List < String > events = Collections.synchronizedList ( new ArrayList <> () );

      Pipe < Integer > pipeA = circuit.pipe (
        (Receptor < Integer >) value -> events.add ( "A:" + value )
      );

      Pipe < Integer > pipeB = circuit.pipe (
        (Receptor < Integer >) value -> events.add ( "B:" + value )
      );

      pipeA.emit ( 1 );
      pipeB.emit ( 1 );
      pipeA.emit ( 2 );
      pipeB.emit ( 2 );
      circuit.await ();

      // Verify per-pipe ordering is maintained
      List < String > aEvents = events.stream ()
        .filter ( e -> e.startsWith ( "A:" ) )
        .toList ();
      List < String > bEvents = events.stream ()
        .filter ( e -> e.startsWith ( "B:" ) )
        .toList ();

      assertEquals ( List.of ( "A:1", "A:2" ), aEvents,
        "Pipe A emissions must maintain order" );
      assertEquals ( List.of ( "B:1", "B:2" ), bEvents,
        "Pipe B emissions must maintain order" );
    }
  }
}
