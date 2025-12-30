package io.fullerstack.substrates;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Test if circuit thread spinning causes cache contention with producer.
 */
public class CacheContentionTest {

  private static final int ITERATIONS = 10_000_000;

  public static void main ( String[] args ) throws Exception {
    System.out.println ( "=== Cache Contention Test ===\n" );

    Consumer < Integer > receiver = v -> {
    };

    // Test 1: Submit with NO consumer thread (no contention)
    testNoConsumer ( receiver );

    // Test 2: Submit with consumer thread PARKED (minimal contention)
    testConsumerParked ( receiver );

    // Test 3: Submit with consumer thread SPINNING on same stackHead (max contention)
    testConsumerSpinning ( receiver );
  }

  private static void testNoConsumer ( Consumer < Integer > receiver ) {
    AtomicReference < Job > stackHead = new AtomicReference <> (
      new EmitJob ( receiver, null ) );

    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      Job job = jobs[i];
      Job prev = stackHead.getAndSet ( job );
      job.next = prev;
    }
    long elapsed = System.nanoTime () - start;

    System.out.printf ( "No consumer (no contention):     %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }

  private static void testConsumerParked ( Consumer < Integer > receiver ) throws Exception {
    AtomicReference < Job > stackHead = new AtomicReference <> (
      new EmitJob ( receiver, null ) );

    // Consumer thread that parks immediately
    Thread consumer = Thread.ofVirtual ().start ( () -> {
      try {
        Thread.sleep ( 10000 ); // Just sleep, don't touch stackHead
      } catch ( InterruptedException e ) { }
    } );

    Thread.sleep ( 10 ); // Let consumer start and park

    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      Job job = jobs[i];
      Job prev = stackHead.getAndSet ( job );
      job.next = prev;
    }
    long elapsed = System.nanoTime () - start;

    consumer.interrupt ();

    System.out.printf ( "Consumer parked (minimal):       %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }

  private static volatile boolean running = true;

  private static void testConsumerSpinning ( Consumer < Integer > receiver ) throws Exception {
    AtomicReference < Job > stackHead = new AtomicReference <> (
      new EmitJob ( receiver, null ) );
    Job sentinel = new EmitJob ( receiver, null );

    // Consumer thread that spins on stackHead (like our circuit loop)
    Thread consumer = Thread.ofVirtual ().start ( () -> {
      while ( running ) {
        // Simulate drainIngress - grab batch
        Job batch = stackHead.getAndSet ( sentinel );
        if ( batch != sentinel ) {
          // Process...
        }
        Thread.onSpinWait ();
      }
    } );

    Thread.sleep ( 10 ); // Let consumer start spinning

    Job[] jobs = new Job[ITERATIONS];
    for ( int i = 0; i < ITERATIONS; i++ ) {
      jobs[i] = new EmitJob ( receiver, i );
    }

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      Job job = jobs[i];
      Job prev = stackHead.getAndSet ( job );
      job.next = prev;
    }
    long elapsed = System.nanoTime () - start;

    running = false;
    consumer.join ( 100 );

    System.out.printf ( "Consumer spinning (contention):  %.2f ns/op%n", (double) elapsed / ITERATIONS );
  }
}
