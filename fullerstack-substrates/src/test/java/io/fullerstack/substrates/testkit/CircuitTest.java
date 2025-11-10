// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.humainary.substrates.api.Substrates.Composer.pipe;
import static org.junit.jupiter.api.Assertions.*;

/// Comprehensive tests for the Circuit interface.
///
/// This test class covers:
/// - Circuit creation and lifecycle
/// - Conduit creation with various overloads
/// - Cell creation (experimental API)
/// - Threading model and await semantics
/// - Resource management and cleanup
/// - Integration with other components
///
/// @author wlouth
/// @since 1.0

final class CircuitTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  // ===========================
  // Circuit Creation Tests
  // ===========================

  @Test
  void testAwaitAfterClosureUsesFastPath () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );

      final Pipe < String > pipe =
        conduit.percept ( cortex.name ( "valve.fastpath.channel" ) );

      pipe.emit ( "first" );
      pipe.emit ( "second" );

      circuit.await ();

      circuit.close ();
      circuit.await ();

      final Duration fastPathBudget = Duration.ofMillis ( 200L );

      assertTimeoutPreemptively (
        fastPathBudget,
        circuit::await,
        "await() should short-circuit once the circuit is closed"
      );

      // Subsequent invocations should remain fast even after the first post-close await.
      assertTimeoutPreemptively (
        fastPathBudget,
        circuit::await,
        "await() should consistently use the closed fast-path"
      );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testAwaitCompletesWhenQueueEmpty () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      conduit.percept ( cortex.name ( "test.channel" ) )
        .emit ( 42 );

      // Should complete when queue is drained
      circuit.await ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testAwaitFromExternalThread () throws InterruptedException {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      final var latch = new CountDownLatch ( 1 );

      final var thread = new Thread ( () -> {
        try {

          final Pipe < Integer > pipe =
            conduit.percept ( cortex.name ( "async.channel" ) );

          pipe.emit ( 100 );

          circuit.await ();

          latch.countDown ();

        } catch ( final java.lang.Exception ignored ) {
        }
      } );

      thread.start ();

      assertTrue (
        latch.await ( 5, TimeUnit.SECONDS ),
        "await should complete"
      );

      thread.join ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testAwaitOnCircuitThreadThrowsIllegalStateException () {

    final var circuit = cortex.circuit ();

    try {

      final AtomicReference < Throwable > captured = new AtomicReference <> ();
      final AtomicReference < Thread > workerThread = new AtomicReference <> ();

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "valve.await.conduit" ),
          pipe ()
        );

      final Subscriber < Integer > subscriber =
        cortex.subscriber (
          cortex.name ( "valve.await.subscriber" ),
          ( _, registrar ) ->
            registrar.register (
              _ -> {
                workerThread.set ( Thread.currentThread () );
                try {
                  circuit.await ();
                } catch ( final IllegalStateException ex ) {
                  captured.set ( ex );
                }
              }
            )
        );

      final var subscription =
        conduit.subscribe ( subscriber );

      final Pipe < Integer > pipe =
        conduit.percept ( cortex.name ( "valve.await.channel" ) );

      pipe.emit ( 1 );

      circuit.await ();

      subscription.close ();

      final var thrown = captured.get ();

      assertNotNull ( thrown, "await() on the circuit thread should throw" );
      assertEquals ( IllegalStateException.class, thrown.getClass () );
      assertEquals (
        "Cannot call Circuit::await from within a circuit's thread",
        thrown.getMessage ()
      );
      assertNotNull ( workerThread.percept (), "Subscriber should execute on circuit worker thread" );

    } finally {

      circuit.close ();

    }

  }

  // ===========================
  // Conduit Creation Tests
  // ===========================

  @Test
  void testAwaitWithMultipleEmissions () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );

      final Pipe < String > pipe =
        conduit.percept ( cortex.name ( "multi.emit.channel" ) );

      pipe.emit ( "first" );
      pipe.emit ( "second" );
      pipe.emit ( "third" );

      circuit.await ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitCloseIsIdempotent () {

    final var circuit = cortex.circuit ();

    circuit.close ();
    circuit.close (); // Should not throw
    circuit.close (); // Should not throw

  }

  @Test
  void testCircuitCloseReleasesResources () {

    final var circuit = cortex.circuit ();

    final Conduit < Pipe < Integer >, Integer > conduit =
      circuit.conduit ( pipe () );

    assertNotNull ( conduit );

    circuit.close ();

    // Circuit is closed, but we can't really verify internal state
    // This test mainly ensures close doesn't throw

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testCircuitCreationNullNameGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.circuit ( null )
    );

  }

  @Test
  void testCircuitCreationWithName () {

    final var circuitName = cortex.name ( "circuit.test.named" );
    final var circuit = cortex.circuit ( circuitName );

    assertNotNull ( circuit );
    assertEquals ( circuitName, circuit.subject ().name () );
    assertEquals ( Circuit.class, circuit.subject ().type () );

    circuit.close ();

  }

  @Test
  void testCircuitCreationWithoutName () {

    final var circuit = cortex.circuit ();

    assertNotNull ( circuit );
    assertNotNull ( circuit.subject () );
    assertNotNull ( circuit.subject ().name () );
    assertNotNull ( circuit.subject ().id () );
    assertEquals ( Circuit.class, circuit.subject ().type () );

    circuit.close ();

  }

  // ===========================
  // Cell Creation Tests (Experimental)
  // ===========================
  // TODO: Cell API tests removed - need RC1 BiFunction signature updates


  @Test
  void testCircuitEventOrdering () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      final List < Integer > emissions = new ArrayList <> ();

      final Subscriber < Integer > subscriber =
        cortex.subscriber (
          cortex.name ( "ordering.subscriber" ),
          ( _, registrar ) ->
            registrar.register ( emissions::add )
        );

      conduit.subscribe ( subscriber );

      final Pipe < Integer > pipe =
        conduit.percept ( cortex.name ( "ordering.channel" ) );

      pipe.emit ( 1 );
      pipe.emit ( 2 );
      pipe.emit ( 3 );
      pipe.emit ( 4 );
      pipe.emit ( 5 );

      circuit.await ();

      assertEquals ( List.of ( 1, 2, 3, 4, 5 ), emissions );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitProvidesSource () {

    final var circuit = cortex.circuit ();

    try {

      // Circuit implements Source directly through Context

      assertNotNull ( circuit );
      assertNotNull ( circuit.subject () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitSourceSubscription () {

    final var circuit = cortex.circuit ();

    try {

      final var subjects = new ArrayList < Subject < ? > > ();

      final Subscriber < State > subscriber =
        cortex.subscriber (
          cortex.name ( "circuit.source.subscriber" ),
          ( subject, registrar ) -> {
            subjects.add ( subject );
            registrar.register ( _ -> {
            } );
          }
        );

      final var subscription = circuit.subscribe ( subscriber );

      assertNotNull ( subscription );

      subscription.close ();

    } finally {

      circuit.close ();

    }

  }

  // ===========================
  // Circuit.await() Tests
  // ===========================

  @Test
  void testCircuitSubjectState () {

    final var circuit = cortex.circuit (
      cortex.name ( "circuit.state.test" )
    );

    try {

      final Subject < ? > subject = circuit.subject ();
      final var state = subject.state ();

      assertNotNull ( state );
      // State should be empty for a newly created circuit
      assertEquals ( 0, state.stream ().count () );

    } finally {

      circuit.close ();

    }

  }

  // RC1: tap() API removed

  // ===========================
  // Circuit.close() Tests
  // ===========================

  // RC1: Clock API removed

  @Test
  void testCircuitWithConduitAndSink () {

    final var circuit = cortex.circuit (
      cortex.name ( "integration.circuit" )
    );

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "integration.conduit" ),
          pipe ()
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.percept ( cortex.name ( "integration.channel" ) );

      pipe.emit ( 10 );
      pipe.emit ( 20 );
      pipe.emit ( 30 );

      circuit.await ();

      final var captures =
        sink.drain ().toList ();

      assertEquals ( 3, captures.size () );
      assertEquals ( 10, captures.percept ( 0 ).emission () );
      assertEquals ( 20, captures.percept ( 1 ).emission () );
      assertEquals ( 30, captures.percept ( 2 ).emission () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  // ===========================
  // Source Access Tests
  // ===========================

  @Test
  void testCircuitWithFlowConfiguration () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "flow.conduit" ),
          pipe (),
          flow -> flow.limit ( 2 )
        );

      final Sink < Integer > sink = cortex.sink ( conduit );

      final Pipe < Integer > pipe =
        conduit.percept ( cortex.name ( "flow.channel" ) );

      pipe.emit ( 1 );
      pipe.emit ( 2 );
      pipe.emit ( 3 ); // Should be limited

      circuit.await ();

      final var captures =
        sink.drain ().toList ();

      // Limit should restrict to first 2 emissions
      assertEquals ( 2, captures.size () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitWithMultipleConduitsAndChannels () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit1 =
        circuit.conduit ( cortex.name ( "conduit.one" ), pipe () );

      final Conduit < Pipe < Integer >, Integer > conduit2 =
        circuit.conduit ( cortex.name ( "conduit.two" ), pipe () );

      final Sink < String > sink1 = cortex.sink ( conduit1 );
      final Sink < Integer > sink2 = cortex.sink ( conduit2 );

      final Pipe < String > pipe1 =
        conduit1.percept ( cortex.name ( "channel.alpha" ) );

      final Pipe < Integer > pipe2 =
        conduit2.percept ( cortex.name ( "channel.beta" ) );

      pipe1.emit ( "hello" );
      pipe2.emit ( 42 );

      circuit.await ();

      assertEquals ( 1, sink1.drain ().count () );
      assertEquals ( 1, sink2.drain ().count () );

      sink1.close ();
      sink2.close ();

    } finally {

      circuit.close ();

    }

  }

  // ===========================
  // Integration Tests
  // ===========================

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testConduitCreationNullGuards () {

    final var circuit = cortex.circuit ();

    try {

      final var name = cortex.name ( "test" );

      assertThrows (
        NullPointerException.class,
        () -> circuit.conduit ( (Composer < ?, ? >) null )
      );

      assertThrows (
        NullPointerException.class,
        () -> circuit.conduit ( null, pipe () )
      );

      assertThrows (
        NullPointerException.class,
        () -> circuit.conduit ( name, null )
      );

      assertThrows (
        NullPointerException.class,
        () -> circuit.conduit ( name, pipe (), null )
      );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testConduitCreationWithComposer () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      assertNotNull ( conduit );
      assertNotNull ( conduit.subject () );
      assertEquals ( Conduit.class, conduit.subject ().type () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testConduitCreationWithNameAndComposer () {

    final var circuit = cortex.circuit ();

    try {

      final var conduitName = cortex.name ( "circuit.test.conduit" );
      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( conduitName, pipe () );

      assertNotNull ( conduit );
      assertEquals ( conduitName, conduit.subject ().name () );
      assertEquals ( Conduit.class, conduit.subject ().type () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testConduitCreationWithNameComposerAndConfigurer () {

    final var circuit = cortex.circuit ();

    try {

      final var conduitName = cortex.name ( "circuit.test.conduit.configured" );
      final Conduit < Pipe < Double >, Double > conduit =
        circuit.conduit (
          conduitName,
          pipe (),
          flow -> flow.limit ( 10 )
        );

      assertNotNull ( conduit );
      assertEquals ( conduitName, conduit.subject ().name () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testMultipleCircuitsHaveUniqueSubjects () {

    final var circuit1 = cortex.circuit ();
    final var circuit2 = cortex.circuit ();

    try {

      assertNotSame ( circuit1.subject (), circuit2.subject () );
      assertNotEquals ( circuit1.subject ().id (), circuit2.subject ().id () );

    } finally {

      circuit1.close ();
      circuit2.close ();

    }

  }

  @Test
  void testMultipleConduitsFromSameCircuit () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit1 =
        circuit.conduit ( pipe () );

      final Conduit < Pipe < Integer >, Integer > conduit2 =
        circuit.conduit ( pipe () );

      assertNotSame ( conduit1, conduit2 );
      assertNotSame ( conduit1.subject (), conduit2.subject () );

    } finally {

      circuit.close ();

    }

  }

  // ===========================
  // Cell Creation Tests (RC1 API)
  // ===========================

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testCellCreationNullGuards () {

    final var circuit = cortex.circuit ();

    try {

      // RC3: Cell takes Composer< E, Pipe< I >>, Composer< E, Pipe< E >>, Pipe<? super E >
      final Pipe < Integer > emptyPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) { }

        @Override
        public void flush () { }
      };

      assertThrows (
        NullPointerException.class,
        () -> circuit.cell ( null, Composer.pipe (), emptyPipe )
      );

      assertThrows (
        NullPointerException.class,
        () -> circuit.cell ( Composer.pipe (), null, emptyPipe )
      );

      assertThrows (
        NullPointerException.class,
        () -> circuit.cell ( Composer.pipe (), Composer.pipe (), null )
      );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCellCreationWithTransformerAndAggregator () {

    final var circuit = cortex.circuit ();

    try {

      // RC3: Cell with identity transformer and aggregator (using Composer.pipe())
      final Pipe < Integer > emptyPipe = new Pipe <> () {
        @Override
        public void emit ( Integer value ) { }

        @Override
        public void flush () { }
      };

      final Cell < Integer, Integer > cell =
        circuit.cell (
          Composer.pipe (),  // transformer: identity (returns channel's pipe)
          Composer.pipe (),  // aggregator: identity (returns channel's pipe)
          emptyPipe         // empty aggregation pipe
        );

      assertNotNull ( cell );
      assertNotNull ( cell.subject () );
      assertEquals ( Cell.class, cell.subject ().type () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCellWithNamedCreation () {

    final var circuit = cortex.circuit ();

    try {

      final var cellName = cortex.name ( "circuit.test.cell" );

      final Pipe < String > emptyPipe = new Pipe <> () {
        @Override
        public void emit ( String value ) { }

        @Override
        public void flush () { }
      };

      final Cell < String, String > cell =
        circuit.cell (
          cellName,
          Composer.pipe (),  // transformer
          Composer.pipe (),  // aggregator
          emptyPipe         // empty aggregation pipe
        );

      assertNotNull ( cell );
      assertNotNull ( cell.subject () );
      assertEquals ( cellName, cell.subject ().name () );
      assertEquals ( Cell.class, cell.subject ().type () );

    } finally {

      circuit.close ();

    }

  }


  @Test
  void testCircuitWithCell () {

    final var circuit = cortex.circuit ();

    try {

      // RC3: Cell with identity transformer and aggregator (using Composer.pipe())
      // Simplified test - just verify Cell creation and get() work
      final Pipe < String > emptyPipe = new Pipe <> () {
        @Override
        public void emit ( String value ) { }

        @Override
        public void flush () { }
      };

      final Cell < String, String > cell =
        circuit.cell (
          Composer.pipe (),  // transformer: identity
          Composer.pipe (),  // aggregator: identity
          emptyPipe         // empty aggregation pipe
        );

      // In PREVIEW, Cell extends Pipe directly
      // cell.percept() returns a Cell which IS a Pipe
      final Pipe < String > pipe = cell.percept (
        cortex.name ( "cell.channel" )
      );

      assertNotNull ( pipe );

      // Verify basic emit works (detailed Cell behavior tested separately)
      pipe.emit ( "cell-test" );
      circuit.await ();

    } finally {

      circuit.close ();

    }

  }

}
