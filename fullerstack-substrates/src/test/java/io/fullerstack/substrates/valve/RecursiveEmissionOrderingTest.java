package io.fullerstack.substrates.valve;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.Composer;
import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RC3 dual-queue depth-first recursive emission ordering.
 * <p>
 * < p >< b >RC3 Requirement</b >:
 * < pre >
 * External thread emits: [A, B, C] → Ingress queue
 * Circuit processes A, which emits: [A1, A2] → Transit queue
 * <p>
 * Execution order: A, A1, A2, B, C (depth-first)
 * NOT: A, B, C, A1, A2 (breadth-first)
 * </pre >
 * <p>
 * < p >< b >Dual-Queue Architecture</b >:
 * < ul >
 * < li >Ingress Queue - Emissions from external threads</li >
 * < li >Transit Queue - Emissions from within circuit thread (recursive)</li >
 * < li >Transit queue has priority - processed before next ingress item</li >
 * </ul >
 */
class RecursiveEmissionOrderingTest {

  private Circuit circuit;

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  @Test
  void testDepthFirstRecursiveEmissions () {
    circuit = cortex().circuit ( cortex().name ( "test" ) );

    // Create execution log
    List < String > executionOrder = new ArrayList <> ();

    // Create conduit with subscriber that emits recursively
    Conduit < Pipe < String >, String > conduit = circuit.conduit (
      cortex().name ( "events" ),
      Composer.pipe ()
    );

    conduit.subscribe ( cortex().subscriber (
      cortex().name ( "logger" ),
      ( subject, registrar ) -> {
        registrar.register ( value -> {
          executionOrder.add ( value );

          // Recursive emissions when processing "A"
          if ( value.equals ( "A" ) ) {
            Pipe < String > pipe = conduit.percept ( subject.name () );
            pipe.emit ( "A1" );
            pipe.emit ( "A2" );
          }
        } );
      }
    ) );

    // Get pipe for emissions
    Pipe < String > pipe = conduit.percept ( cortex().name ( "events" ) );

    // Emit A, B, C from external thread
    pipe.emit ( "A" );
    pipe.emit ( "B" );
    pipe.emit ( "C" );

    // Wait for all processing to complete
    circuit.await ();

    // RC3 Requirement: Depth-first ordering
    // A triggers A1, A2 which should be processed BEFORE B
    assertThat ( executionOrder )
      .as ( "RC3 dual-queue depth-first execution" )
      .containsExactly ( "A", "A1", "A2", "B", "C" );

    // Current implementation (single queue) gives BREADTH-first:
    // Execution order: A, B, C, A1, A2
    // This test will FAIL with single-queue implementation!
  }

  @Test
  void testNestedRecursiveEmissions () {
    circuit = cortex().circuit ( cortex().name ( "test" ) );

    List < String > executionOrder = new ArrayList <> ();

    Conduit < Pipe < String >, String > conduit = circuit.conduit (
      cortex().name ( "events" ),
      Composer.pipe ()
    );

    conduit.subscribe ( cortex().subscriber (
      cortex().name ( "logger" ),
      ( subject, registrar ) -> {
        registrar.register ( value -> {
          executionOrder.add ( value );

          Pipe < String > pipe = conduit.percept ( subject.name () );

          // A → A1, A2
          if ( value.equals ( "A" ) ) {
            pipe.emit ( "A1" );
            pipe.emit ( "A2" );
          }
          // A1 → A1a, A1b (nested recursion)
          else if ( value.equals ( "A1" ) ) {
            pipe.emit ( "A1a" );
            pipe.emit ( "A1b" );
          }
        } );
      }
    ) );

    Pipe < String > pipe = conduit.percept ( cortex().name ( "events" ) );

    // Emit A, B
    pipe.emit ( "A" );
    pipe.emit ( "B" );

    circuit.await ();

    // RC3 Depth-first: Recursive emissions have priority, but FIFO within Transit
    // A emits [A1, A2], then A1 emits [A1a, A1b]
    // Transit priority ensures A's children processed before B, but siblings are FIFO
    assertThat ( executionOrder )
      .as ( "RC3 nested depth-first execution - Transit priority with FIFO siblings" )
      .containsExactly ( "A", "A1", "A2", "A1a", "A1b", "B" );
  }

  @Test
  void testConcurrentExternalEmissionsWithRecursion () {
    circuit = cortex().circuit ( cortex().name ( "test" ) );

    List < String > executionOrder = new ArrayList <> ();

    Conduit < Pipe < String >, String > conduit = circuit.conduit (
      cortex().name ( "events" ),
      Composer.pipe ()
    );

    conduit.subscribe ( cortex().subscriber (
      cortex().name ( "logger" ),
      ( subject, registrar ) -> {
        registrar.register ( value -> {
          executionOrder.add ( value );

          if ( value.equals ( "A" ) ) {
            Pipe < String > pipe = conduit.percept ( subject.name () );
            pipe.emit ( "A1" );
            pipe.emit ( "A2" );
          }
        } );
      }
    ) );

    Pipe < String > pipe = conduit.percept ( cortex().name ( "events" ) );

    // Emit from multiple threads simultaneously
    Thread t1 = new Thread ( () -> pipe.emit ( "A" ) );
    Thread t2 = new Thread ( () -> pipe.emit ( "B" ) );
    Thread t3 = new Thread ( () -> pipe.emit ( "C" ) );

    t1.start ();
    t2.start ();
    t3.start ();

    try {
      t1.join ();
      t2.join ();
      t3.join ();
    } catch ( InterruptedException e ) {
      Thread.currentThread ().interrupt ();
    }

    circuit.await ();

    // A's recursive emissions (A1, A2) should be processed before moving to next ingress item
    // We can't predict if A, B, or C is processed first (concurrent),
    // but whichever is first, its recursive emissions should be depth-first
    assertThat ( executionOrder )
      .as ( "Contains all emissions" )
      .containsExactlyInAnyOrder ( "A", "A1", "A2", "B", "C" );

    // Verify depth-first property: A1 and A2 are consecutive after A
    int indexA = executionOrder.indexOf ( "A" );
    int indexA1 = executionOrder.indexOf ( "A1" );
    int indexA2 = executionOrder.indexOf ( "A2" );

    assertThat ( indexA1 )
      .as ( "A1 immediately follows A (depth-first)" )
      .isEqualTo ( indexA + 1 );

    assertThat ( indexA2 )
      .as ( "A2 immediately follows A1 (depth-first)" )
      .isEqualTo ( indexA1 + 1 );
  }
}
