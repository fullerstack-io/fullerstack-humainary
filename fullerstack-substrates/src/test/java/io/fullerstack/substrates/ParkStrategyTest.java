package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Pipe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostic test to measure latency vs CPU trade-offs at different park durations.
 * Run with: mvn test -Dtest=ParkStrategyTest
 */
public class ParkStrategyTest {

  private static final int WARMUP     = 50_000;
  private static final int ITERATIONS = 100_000;

  public static void main ( String[] args ) throws Exception {
    System.out.println ( "=== Park Strategy Latency Test ===\n" );
    System.out.println ( "Current PARK_NANOS = 1000 (1 microsecond)\n" );

    // Warmup JIT
    warmup ();
    Thread.sleep ( 100 );

    // Test 1: Burst throughput (producer is faster than consumer)
    testBurstThroughput ();

    // Test 2: Round-trip latency (producer waits for each item)
    testRoundTripLatency ();

    // Test 3: Sparse emissions (one per 10ms - simulates real metrics workload)
    testSparseEmissions ();

    // Test 4: Cascade depth (how fast we drain transit queue)
    testCascadeLatency ();
  }

  private static void warmup () {
    Circuit circuit = cortex ().circuit ( cortex ().name ( "warmup" ) );
    Pipe < Integer > pipe = circuit.pipe ( v -> {
    } );
    for ( int i = 0; i < WARMUP; i++ ) {
      pipe.emit ( i );
    }
    circuit.await ();
    circuit.close ();
  }

  private static void testBurstThroughput () {
    System.out.println ( "Test 1: Burst Throughput (producer >> consumer)" );
    System.out.println ( "  Measures: producer emit speed when consumer can't keep up" );

    Circuit circuit = cortex ().circuit ( cortex ().name ( "burst" ) );
    AtomicLong counter = new AtomicLong ();
    Pipe < Integer > pipe = circuit.pipe ( v -> counter.incrementAndGet () );

    long start = System.nanoTime ();
    for ( int i = 0; i < ITERATIONS; i++ ) {
      pipe.emit ( i );
    }
    long enqueueTime = System.nanoTime () - start;

    circuit.await ();
    long totalTime = System.nanoTime () - start;
    circuit.close ();

    System.out.printf ( "  Enqueue: %.2f ns/op (%.1fM ops/sec)%n",
      (double) enqueueTime / ITERATIONS,
      ITERATIONS / ( enqueueTime / 1_000_000_000.0 ) / 1_000_000 );
    System.out.printf ( "  Total:   %.2f ns/op (including processing)%n",
      (double) totalTime / ITERATIONS );
    System.out.println ();
  }

  private static void testRoundTripLatency () throws Exception {
    System.out.println ( "Test 2: Round-Trip Latency" );
    System.out.println ( "  Measures: time from emit to callback execution" );

    Circuit circuit = cortex ().circuit ( cortex ().name ( "roundtrip" ) );
    AtomicLong totalLatency = new AtomicLong ();
    AtomicLong count = new AtomicLong ();

    Pipe < Long > pipe = circuit.pipe ( emitTime -> {
      long latency = System.nanoTime () - emitTime;
      totalLatency.addAndGet ( latency );
      count.incrementAndGet ();
    } );

    // Let circuit thread start and park
    Thread.sleep ( 10 );

    // Emit one at a time, measuring latency
    int samples = 10_000;
    for ( int i = 0; i < samples; i++ ) {
      pipe.emit ( System.nanoTime () );
      // Small delay to let circuit process before next emit
      Thread.onSpinWait ();
    }

    circuit.await ();
    circuit.close ();

    double avgLatency = (double) totalLatency.get () / count.get ();
    System.out.printf ( "  Average round-trip: %.2f ns (%.2f µs)%n", avgLatency, avgLatency / 1000 );
    System.out.println ( "  (includes: enqueue + park wake + dequeue + callback)" );
    System.out.println ();
  }

  private static void testSparseEmissions () throws Exception {
    System.out.println ( "Test 3: Sparse Emissions (10ms apart)" );
    System.out.println ( "  Measures: latency when circuit thread is fully parked" );

    Circuit circuit = cortex ().circuit ( cortex ().name ( "sparse" ) );
    AtomicLong totalLatency = new AtomicLong ();
    AtomicLong count = new AtomicLong ();

    Pipe < Long > pipe = circuit.pipe ( emitTime -> {
      long latency = System.nanoTime () - emitTime;
      totalLatency.addAndGet ( latency );
      count.incrementAndGet ();
    } );

    // Let circuit thread park deeply
    Thread.sleep ( 50 );

    // Emit once every 10ms (circuit will be parked each time)
    int samples = 20;
    for ( int i = 0; i < samples; i++ ) {
      pipe.emit ( System.nanoTime () );
      Thread.sleep ( 10 );
    }

    circuit.await ();
    circuit.close ();

    double avgLatency = (double) totalLatency.get () / count.get ();
    System.out.printf ( "  Average wake-up latency: %.2f ns (%.2f µs)%n", avgLatency, avgLatency / 1000 );
    System.out.println ( "  (worst-case: circuit was parked, had to wait for parkNanos timeout)" );
    System.out.println ();
  }

  private static void testCascadeLatency () throws Exception {
    System.out.println ( "Test 4: Cascade Depth (transit queue processing)" );
    System.out.println ( "  Measures: time to process cascading chain" );

    int cascadeDepth = 100;

    Circuit circuit = cortex ().circuit ( cortex ().name ( "cascade" ) );
    CountDownLatch done = new CountDownLatch ( 1 );
    AtomicLong cascadeComplete = new AtomicLong ();

    // Create a chain of pipes that cascade into each other
    Pipe < Long >[] pipes = new Pipe[cascadeDepth];

    // Last pipe records completion time
    pipes[cascadeDepth - 1] = circuit.pipe ( startTime -> {
      cascadeComplete.set ( System.nanoTime () - startTime );
      done.countDown ();
    } );

    // Each pipe emits to the next
    for ( int i = cascadeDepth - 2; i >= 0; i-- ) {
      final int nextIdx = i + 1;
      final Pipe < Long > nextPipe = pipes[nextIdx];
      pipes[i] = circuit.pipe ( startTime -> nextPipe.emit ( startTime ) );
    }

    // Let circuit start
    Thread.sleep ( 10 );

    // Trigger cascade
    pipes[0].emit ( System.nanoTime () );
    done.await ();

    circuit.close ();

    System.out.printf ( "  Cascade depth %d: %.2f ns total (%.2f ns per hop)%n",
      cascadeDepth,
      (double) cascadeComplete.get (),
      (double) cascadeComplete.get () / cascadeDepth );
    System.out.println ( "  (all cascade jobs go through transit queue, no stack recursion)" );
    System.out.println ();
  }
}
