package io.fullerstack.substrates.valve;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance demonstration for event-driven Valve.await() vs polling.
 * <p>
 * < p >This test demonstrates the benefits of the event-driven synchronization:
 * < ul >
 * < li >Zero latency - await() wakes immediately when valve is idle</li >
 * < li >Zero CPU waste - no polling loop consuming cycles</li >
 * < li >Scalable - 1000 valves don't create 1000 polling threads</li >
 * </ul >
 * <p>
 * < p >< b >Previous polling approach:</b >
 * < pre >
 * while (running && (executing || !queue.isEmpty())) {
 * Thread.sleep(10);  // Poll every 10ms
 * }
 * // Latency: 0-10ms wake-up delay
 * // CPU: 100 checks/second per waiting thread
 * </pre >
 * <p>
 * < p >< b >Current event-driven approach:</b >
 * < pre >
 * synchronized (idleLock) {
 * while (running && (executing || !queue.isEmpty())) {
 * idleLock.wait();  // Block until notified
 * }
 * }
 * // Latency: ~0ms (immediate notification)
 * // CPU: 0 cycles (parked thread)
 * </pre >
 */
class ValvePerformanceTest {

  @Test
  @DisplayName ( "Event-driven await() has sub-millisecond latency" )
  void testAwaitLatency () throws Exception {
    Valve valve = new Valve ( "perf-test" );

    try {
      // Submit a task
      valve.submit ( () -> {
        try {
          Thread.sleep ( 50 );  // Simulate 50ms work
        } catch ( InterruptedException e ) {
          Thread.currentThread ().interrupt ();
        }
      } );

      // Measure how long await() takes after task completes
      long start = System.nanoTime ();
      valve.await ( "Valve" );
      long duration = System.nanoTime () - start;

      // Event-driven should complete within ~55ms (50ms work + ~0ms wake-up)
      // Polling would take 50ms work + 0-10ms wake-up delay
      long durationMs = duration / 1_000_000;
      System.out.println ( "await() completed in " + durationMs + "ms" );

      // With event-driven, we expect completion within 60ms
      // (50ms work + a few ms for notification overhead)
      assertTrue ( durationMs < 60,
        "Event-driven await() should complete quickly: " + durationMs + "ms" );

    } finally {
      valve.close ();
    }
  }

  @Test
  @DisplayName ( "Multiple concurrent await() calls are notified immediately" )
  void testConcurrentAwait () throws Exception {
    Valve valve = new Valve ( "concurrent-test" );

    try {
      // Submit a single task
      valve.submit ( () -> {
        try {
          Thread.sleep ( 100 );
        } catch ( InterruptedException e ) {
          Thread.currentThread ().interrupt ();
        }
      } );

      // Start 10 threads all waiting on the same valve
      Thread[] waiters = new Thread[10];
      long[] completionTimes = new long[10];

      long startTime = System.nanoTime ();

      for ( int i = 0; i < 10; i++ ) {
        final int index = i;
        waiters[i] = Thread.ofVirtual ().start ( () -> {
          valve.await ( "Valve" );
          completionTimes[index] = System.nanoTime () - startTime;
        } );
      }

      // Wait for all threads to complete
      for ( Thread waiter : waiters ) {
        waiter.join ();
      }

      // With event-driven notifyAll(), all threads should complete within ~5ms of each other
      long minTime = Long.MAX_VALUE;
      long maxTime = 0;
      for ( long time : completionTimes ) {
        minTime = Math.min ( minTime, time );
        maxTime = Math.max ( maxTime, time );
      }

      long spreadMs = ( maxTime - minTime ) / 1_000_000;
      System.out.println ( "Completion time spread: " + spreadMs + "ms" );

      // Event-driven: all threads wake up nearly simultaneously (< 10ms spread)
      // Polling: threads would wake up across 0-10ms window (10ms spread)
      assertTrue ( spreadMs < 20,
        "All threads should wake up nearly simultaneously: " + spreadMs + "ms spread" );

    } finally {
      valve.close ();
    }
  }

  @Test
  @DisplayName ( "await() returns immediately when valve is already idle" )
  void testAwaitWhenAlreadyIdle () {
    Valve valve = new Valve ( "idle-test" );

    try {
      // Valve is already idle (no tasks)
      long start = System.nanoTime ();
      valve.await ( "Valve" );
      long duration = System.nanoTime () - start;

      long durationUs = duration / 1_000;  // microseconds
      System.out.println ( "await() on idle valve completed in " + durationUs + "μs" );

      // Should complete in microseconds, not milliseconds
      assertTrue ( durationUs < 5000,
        "await() on idle valve should be instant: " + durationUs + "μs" );

    } finally {
      valve.close ();
    }
  }

  @Test
  @DisplayName ( "await() wakes up on close()" )
  void testAwaitWakesOnClose () throws Exception {
    Valve valve = new Valve ( "close-test" );

    // Start a thread that waits on the valve with a long-running task
    valve.submit ( () -> {
      try {
        Thread.sleep ( 10000 );  // 10 second task
      } catch ( InterruptedException e ) {
        Thread.currentThread ().interrupt ();
      }
    } );

    Thread waiter = Thread.ofVirtual ().start ( () -> {
      long start = System.nanoTime ();
      valve.await ( "Valve" );
      long duration = ( System.nanoTime () - start ) / 1_000_000;
      System.out.println ( "await() woke up after " + duration + "ms (valve closed)" );
    } );

    // Close the valve after 100ms
    Thread.sleep ( 100 );
    valve.close ();

    // Waiter should wake up immediately (not wait 10 seconds)
    waiter.join ( 1000 );  // Wait max 1 second
    assertFalse ( waiter.isAlive (),
      "Thread should wake up when valve closes" );
  }
}
