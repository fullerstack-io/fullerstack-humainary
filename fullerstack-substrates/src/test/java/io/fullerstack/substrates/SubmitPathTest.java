package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Circuit;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Isolate submit path to find the overhead.
 */
public class SubmitPathTest {

  private static final int ITERATIONS = 10_000_000;

  // Simulate FsCircuit fields
  private static volatile boolean                 running   = true;
  private static final    AtomicReference < Job > stackHead =
    new AtomicReference <> ( new EmitJob ( v -> {
    }, null ) );
  private static          Thread                  circuitThread;

  public static void main ( String[] args ) throws Exception {
    // Create a dummy thread for comparison
    circuitThread = Thread.ofVirtual ().unstarted ( () -> {
    } );

    Consumer < Integer > receiver = v -> {
    };

    System.out.println ( "=== Submit Path Isolation ===\n" );

    // Warmup
    for ( int i = 0; i < 100_000; i++ ) {
      inlineSubmit ( new EmitJob ( receiver, i ) );
    }
    Thread.sleep ( 100 );

    // Test 1: Inline submit logic (no method call)
    testInlineSubmit ( receiver );

    // Test 2: Actual circuit.submit()
    testActualSubmit ( receiver );

    // Test 3: Just the external path inline
    testExternalPathOnly ( receiver );
  }

  private static void testInlineSubmit ( Consumer < Integer > receiver ) {
    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      inlineSubmit ( jobs[i] );
    }
    long elapsed = System.nanoTime () - start;

    System.out.printf ( "Inline submit logic:        %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }

  private static void inlineSubmit ( Job job ) {
    if ( Thread.currentThread () == circuitThread ) {
      // cascade path - won't hit in this test
    } else {
      if ( !running ) { return; }
      Job prev = stackHead.getAndSet ( job );
      job.next = prev;
    }
  }

  private static void testActualSubmit ( Consumer < Integer > receiver ) {
    FsSubject < Circuit > subject = new FsSubject <> ( cortex ().name ( "test" ), null, Circuit.class );
    FsCircuit circuit = new FsCircuit ( subject );

    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      circuit.submit ( jobs[i] );
    }
    long elapsed = System.nanoTime () - start;

    circuit.close ();

    System.out.printf ( "Actual circuit.submit():    %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }

  private static void testExternalPathOnly ( Consumer < Integer > receiver ) {
    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      // Just the external path operations
      Job job = jobs[i];
      if ( !running ) { continue; }
      Job prev = stackHead.getAndSet ( job );
      job.next = prev;
    }
    long elapsed = System.nanoTime () - start;

    System.out.printf ( "External path only:         %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }
}
