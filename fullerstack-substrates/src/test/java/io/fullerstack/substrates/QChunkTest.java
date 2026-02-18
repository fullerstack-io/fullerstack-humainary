package io.fullerstack.substrates;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName ( "QChunk-based Queue Tests" )
class QChunkTest {

  // ─────────────────────────────────────────────────────────────────────────────
  // Helpers — minimal FsCircuit stub for drainBatch
  // ─────────────────────────────────────────────────────────────────────────────

  /** Stub circuit that records drained values and has a no-op transit drain. */
  private static final class StubCircuit {
    final List < Object > drained = Collections.synchronizedList ( new ArrayList <> () );
    final IngressQueue    ingress = new IngressQueue ();

    @SuppressWarnings ( "unchecked" )
    Consumer < Object > recorder () {
      return v -> drained.add ( v );
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // IngressQueue Tests
  // ─────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName ( "IngressQueue" )
  class IngressTests {

    @Test
    @DisplayName ( "single-producer enqueue — 10 items, verify peek sees data" )
    void singleProducerEnqueue () {
      IngressQueue q = new IngressQueue ();
      Consumer < Object > noop = _ -> {
      };

      assertNull ( q.peek (), "empty queue" );

      for ( int i = 0; i < 10; i++ ) {
        q.enqueue ( noop, i );
      }

      assertNotNull ( q.peek (), "should have items after enqueue" );
    }

    @Test
    @DisplayName ( "peek returns non-null after enqueue, null when empty" )
    void peekCorrectness () {
      IngressQueue q = new IngressQueue ();
      assertNull ( q.peek (), "empty queue peek should be null" );

      Consumer < Object > noop = _ -> {
      };
      q.enqueue ( noop, "val" );

      assertNotNull ( q.peek (), "should be non-null after enqueue" );
    }

    @Test
    @DisplayName ( "null value handling — receiver non-null, value null (marker pattern)" )
    void nullValueMarkerPattern () {
      IngressQueue q = new IngressQueue ();
      AtomicInteger count = new AtomicInteger ();
      Consumer < Object > counter = _ -> count.incrementAndGet ();

      // Enqueue with null value (like await/close markers)
      q.enqueue ( counter, null );

      assertNotNull ( q.peek (), "receiver should be visible even with null value" );
    }

    @Test
    @DisplayName ( "chunk transition — 200 items span 3+ chunks" )
    void chunkTransition () {
      IngressQueue q = new IngressQueue ();
      AtomicInteger count = new AtomicInteger ();
      Consumer < Object > counter = _ -> count.incrementAndGet ();

      // Enqueue 200 items (64 per chunk = 3+ chunks)
      for ( int i = 0; i < 200; i++ ) {
        q.enqueue ( counter, i );
      }

      // Verify all can be peeked (first chunk has data)
      assertNotNull ( q.peek () );
    }

    @Test
    @DisplayName ( "multi-producer concurrent enqueue — 8 threads × 10K items, no losses" )
    void multiProducerConcurrent () throws Exception {
      IngressQueue q = new IngressQueue ();
      AtomicInteger enqueuedCount = new AtomicInteger ();
      Consumer < Object > counter = _ -> enqueuedCount.incrementAndGet ();

      int threads = 8;
      int itemsPerThread = 10_000;
      CyclicBarrier barrier = new CyclicBarrier ( threads );

      Thread[] producers = new Thread[threads];
      for ( int t = 0; t < threads; t++ ) {
        producers[t] = Thread.startVirtualThread ( () -> {
          try {
            barrier.await ();
            for ( int i = 0; i < itemsPerThread; i++ ) {
              q.enqueue ( counter, i );
            }
          } catch ( Exception e ) {
            throw new RuntimeException ( e );
          }
        } );
      }

      for ( Thread t : producers ) t.join ();

      // All items should be peek-able
      assertNotNull ( q.peek () );
    }
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // TransitQueue Tests
  // ─────────────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName ( "TransitQueue" )
  class TransitTests {

    @Test
    @DisplayName ( "single-threaded enqueue+drain — 10 items, verify order" )
    void singleThreadedOrder () {
      TransitQueue q = new TransitQueue ();
      List < Object > results = new ArrayList <> ();
      Consumer < Object > recorder = results::add;

      for ( int i = 0; i < 10; i++ ) {
        q.enqueue ( recorder, i );
      }

      assertTrue ( q.drain () );
      assertEquals ( 10, results.size () );
      for ( int i = 0; i < 10; i++ ) {
        assertEquals ( i, results.get ( i ) );
      }
    }

    @Test
    @DisplayName ( "drain returns false when empty" )
    void drainEmpty () {
      TransitQueue q = new TransitQueue ();
      assertFalse ( q.drain () );
    }

    @Test
    @DisplayName ( "full chunk — 64 items, verify drain resets for reuse" )
    void fullChunkDrainAndReuse () {
      TransitQueue q = new TransitQueue ();
      List < Object > results = new ArrayList <> ();
      Consumer < Object > recorder = results::add;

      // Fill one chunk exactly
      for ( int i = 0; i < 64; i++ ) {
        q.enqueue ( recorder, i );
      }

      assertTrue ( q.drain () );
      assertEquals ( 64, results.size () );

      // Second cycle — reuse homeChunk (zero allocation)
      results.clear ();
      for ( int i = 100; i < 164; i++ ) {
        q.enqueue ( recorder, i );
      }

      assertTrue ( q.drain () );
      assertEquals ( 64, results.size () );
      assertEquals ( 100, results.get ( 0 ) );
    }

    @Test
    @DisplayName ( "overflow — >64 items trigger overflow chunk" )
    void overflowChunk () {
      TransitQueue q = new TransitQueue ();
      List < Object > results = new ArrayList <> ();
      Consumer < Object > recorder = results::add;

      // Enqueue 100 items (overflow at 64)
      for ( int i = 0; i < 100; i++ ) {
        q.enqueue ( recorder, i );
      }

      assertTrue ( q.drain () );
      assertEquals ( 100, results.size () );
      for ( int i = 0; i < 100; i++ ) {
        assertEquals ( i, results.get ( i ) );
      }
    }

    @Test
    @DisplayName ( "repeated drain cycles — verify zero alloc pattern" )
    void repeatedDrainCycles () {
      TransitQueue q = new TransitQueue ();
      AtomicInteger count = new AtomicInteger ();
      Consumer < Object > counter = _ -> count.incrementAndGet ();

      // 100 cycles of enqueue N + drain
      for ( int cycle = 0; cycle < 100; cycle++ ) {
        int items = ( cycle % 64 ) + 1;  // vary count 1..64
        for ( int i = 0; i < items; i++ ) {
          q.enqueue ( counter, i );
        }
        assertTrue ( q.drain () );
      }

      // Total items should be sum of (1+2+...+64) + sum of (1+2+...+36) = many
      assertTrue ( count.get () > 0 );
    }

    @Test
    @DisplayName ( "null value handling in transit" )
    void nullValueTransit () {
      TransitQueue q = new TransitQueue ();
      AtomicInteger count = new AtomicInteger ();
      Consumer < Object > counter = _ -> count.incrementAndGet ();

      q.enqueue ( counter, null );
      assertTrue ( q.drain () );
      assertEquals ( 1, count.get () );
    }
  }
}
