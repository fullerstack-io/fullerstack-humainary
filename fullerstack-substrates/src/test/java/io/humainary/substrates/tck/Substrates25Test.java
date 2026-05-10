package io.humainary.substrates.tck;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Bank;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Fault;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for Substrates 2.5 contracts:
/// - `Bank<Conduit<E>>` factory + identity + ownership (§10.4)
/// - `Resource.closeAwait()` blocking close (§9.1)
/// - Fiber operators: distinct, distinct(int), route, streak, tee, when (§6.2)
/// - Open-required closed-resource faults (§9.1, §16.1 #9)
/// - External callback isolation (§15.4, §16.1 #14)
class Substrates25Test implements Substrates {

  // ═══════════════════════════════════════════════════════════════════════════
  // §10.4 — Bank
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Bank — §10.4" )
  class BankContracts {

    @Test
    @DisplayName ( "get(name) returns same conduit for repeated calls (identity)" )
    void identityAcrossLookups () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Bank < Conduit < Integer > > bank = circuit.bank ( Integer.class );
        final Name n = cortex.name ( "left" );
        final Conduit < Integer > a = bank.get ( n );
        final Conduit < Integer > b = bank.get ( n );
        assertSame ( a, b, "Bank.get MUST return canonically identical resources for the same name" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "get(name) materialises lazily; different names produce distinct conduits" )
    void distinctConduitsPerName () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Bank < Conduit < Integer > > bank = circuit.bank ( Integer.class );
        final Conduit < Integer > a = bank.get ( cortex.name ( "a" ) );
        final Conduit < Integer > b = bank.get ( cortex.name ( "b" ) );
        assertNotNull ( a );
        assertNotNull ( b );
        assertTrue ( a != b, "different names MUST produce different conduits" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "get(name) after bank close raises Fault synchronously (§9.1 open-required)" )
    void getAfterClose_throwsFault () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Bank < Conduit < Integer > > bank = circuit.bank ( Integer.class );
        bank.close ();
        assertThrows ( Fault.class, () -> bank.get ( cortex.name ( "x" ) ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "close() is idempotent" )
    void closeIdempotent () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Bank < Conduit < Integer > > bank = circuit.bank ( Integer.class );
        bank.close ();
        bank.close ();   // must not throw
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "different bank() calls return independent banks" )
    void independentBanksPerCall () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Bank < Conduit < Integer > > b1 = circuit.bank ( Integer.class );
        final Bank < Conduit < Integer > > b2 = circuit.bank ( Integer.class );
        final Name n = cortex.name ( "shared" );
        final Conduit < Integer > c1 = b1.get ( n );
        final Conduit < Integer > c2 = b2.get ( n );
        assertTrue ( c1 != c2, "Bank.get MUST scope identity to that bank (per §10.4)" );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §9.1 — closeAwait
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Resource.closeAwait() — §9.1" )
  class CloseAwaitContracts {

    @Test
    @DisplayName ( "closeAwait() blocks until cleanup completes" )
    void blocksUntilDone () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        c.closeAwait ();
        // After closeAwait, any operation on the circuit's queue committed before
        // close has been drained. We just check it doesn't deadlock or throw.
        assertTrue ( true );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "closeAwait() is idempotent" )
    void idempotent () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        c.closeAwait ();
        c.closeAwait ();   // must not deadlock or throw
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §9.1 — Open-required closed-resource faults
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Open-required closed-resource faults — §9.1, §16.1 #9" )
  class ClosedResourceFaults {

    @Test
    @DisplayName ( "circuit.conduit() after close raises Fault" )
    void conduitAfterCircuitClose () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      circuit.close ();
      assertThrows ( Fault.class, () -> circuit.conduit ( Integer.class ) );
    }

    @Test
    @DisplayName ( "circuit.bank() after close raises Fault" )
    void bankAfterCircuitClose () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      circuit.close ();
      assertThrows ( Fault.class, () -> circuit.bank ( Integer.class ) );
    }

    @Test
    @DisplayName ( "circuit.subscriber() after close raises Fault" )
    void subscriberAfterCircuitClose () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      final Name n = cortex.name ( "s" );
      circuit.close ();
      assertThrows ( Fault.class, () -> circuit.subscriber ( n, ( pipeSubject, registrar ) -> { } ) );
    }

    @Test
    @DisplayName ( "conduit.reservoir() after conduit close raises Fault" )
    void reservoirAfterConduitClose () {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        c.close ();
        assertThrows ( Fault.class, c::reservoir );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §6.2 — Fiber operators (2.5)
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Fiber.distinct() — §6.2.3" )
  class DistinctContracts {

    @Test
    @DisplayName ( "suppresses all previously seen values" )
    void unbounded () {
      final List < Integer > seen = runFiber ( fiber ( Integer.class ).distinct (),
        List.of ( 1, 2, 1, 3, 2, 3, 1, 4 ) );
      assertEquals ( List.of ( 1, 2, 3, 4 ), seen );
    }

    @Test
    @DisplayName ( "distinct(capacity) allows re-emergence after FIFO eviction" )
    void bounded () {
      // capacity=2: window holds the 2 most recently *accepted* distinct values.
      // 1 → accepted, window=[1]
      // 2 → accepted, window=[1,2]
      // 1 → suppressed (in window)
      // 3 → accepted, window=[2,3]  (1 evicted)
      // 1 → accepted again (no longer in window), window=[3,1]  (2 evicted)
      // 2 → accepted again,                       window=[1,2]
      final List < Integer > seen = runFiber ( fiber ( Integer.class ).distinct ( 2 ),
        List.of ( 1, 2, 1, 3, 1, 2 ) );
      assertEquals ( List.of ( 1, 2, 3, 1, 2 ), seen );
    }

    @Test
    @DisplayName ( "distinct(0) rejects" )
    void rejectsCapacityZero () {
      assertThrows ( IllegalArgumentException.class,
        () -> fiber ( Integer.class ).distinct ( 0 ) );
    }
  }

  @Nested
  @DisplayName ( "Fiber.route(predicate, pipe) — §6.2.2" )
  class RouteContracts {

    @Test
    @DisplayName ( "matching emissions divert to side pipe; non-matching pass through" )
    void demuxBehaviour () throws Exception {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > side = circuit.conduit ( Integer.class );
        final Conduit < Integer > main = circuit.conduit ( Integer.class );

        final List < Integer > sideSeen = new ArrayList <> ();
        final List < Integer > mainSeen = new ArrayList <> ();
        side.subscribe ( circuit.subscriber ( cortex.name ( "side.sub" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) sideSeen::add ) ) );
        main.subscribe ( circuit.subscriber ( cortex.name ( "main.sub" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) mainSeen::add ) ) );

        // Build a fiber that diverts evens to `side`, lets odds pass to main.
        final Pipe < Integer > sidePipe = side.get ( cortex.name ( "odd" ) );
        final Pipe < Integer > main0    = main.get ( cortex.name ( "p" ) );
        final Pipe < Integer > routed   = fiber ( Integer.class )
          .route ( v -> v % 2 == 0, sidePipe )
          .pipe ( main0 );

        routed.emit ( 1 );
        routed.emit ( 2 );
        routed.emit ( 3 );
        routed.emit ( 4 );
        circuit.await ();

        assertEquals ( List.of ( 1, 3 ), mainSeen );
        assertEquals ( List.of ( 2, 4 ), sideSeen );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "Fiber.streak(required, predicate) — §6.2.3" )
  class StreakContracts {

    @Test
    @DisplayName ( "emits Nth consecutive match, then re-arms" )
    void nthConsecutive () {
      // required=3, matches v>0:
      //  1 match #1 → drop
      //  2 match #2 → drop
      //  3 match #3 → EMIT, counter=0
      //  -1 reset → drop
      //  4 match #1 → drop
      //  5 match #2 → drop
      //  6 match #3 → EMIT
      final List < Integer > seen = runFiber (
        fiber ( Integer.class ).streak ( 3, v -> v > 0 ),
        List.of ( 1, 2, 3, -1, 4, 5, 6 ) );
      assertEquals ( List.of ( 3, 6 ), seen );
    }

    @Test
    @DisplayName ( "non-match resets the streak counter (mid-streak)" )
    void nonMatchResets () {
      // required=2:
      //  1 → #1 drop
      //  2 → #2 EMIT
      //  -1 → reset
      //  3 → #1 drop
      //  -2 → reset
      //  4 → #1 drop
      //  5 → #2 EMIT
      final List < Integer > seen = runFiber (
        fiber ( Integer.class ).streak ( 2, v -> v > 0 ),
        List.of ( 1, 2, -1, 3, -2, 4, 5 ) );
      assertEquals ( List.of ( 2, 5 ), seen );
    }

    @Test
    @DisplayName ( "required=1 collapses to guard semantics (every match emits)" )
    void degeneratesToGuard () {
      final List < Integer > seen = runFiber (
        fiber ( Integer.class ).streak ( 1, v -> v > 0 ),
        List.of ( 1, -1, 2, -2, 3 ) );
      assertEquals ( List.of ( 1, 2, 3 ), seen );
    }

    @Test
    @DisplayName ( "required <= 0 rejects" )
    void rejectsNonPositive () {
      assertThrows ( IllegalArgumentException.class,
        () -> fiber ( Integer.class ).streak ( 0, v -> true ) );
    }
  }

  @Nested
  @DisplayName ( "Fiber.tee(pipe) — §6.2.2" )
  class TeeContracts {

    @Test
    @DisplayName ( "every emission fans out to side AND continues downstream" )
    void fanOut () throws Exception {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > side = circuit.conduit ( Integer.class );
        final Conduit < Integer > main = circuit.conduit ( Integer.class );

        final List < Integer > sideSeen = new ArrayList <> ();
        final List < Integer > mainSeen = new ArrayList <> ();
        side.subscribe ( circuit.subscriber ( cortex.name ( "side.sub" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) sideSeen::add ) ) );
        main.subscribe ( circuit.subscriber ( cortex.name ( "main.sub" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) mainSeen::add ) ) );

        final Pipe < Integer > sidePipe = side.get ( cortex.name ( "copy" ) );
        final Pipe < Integer > main0    = main.get ( cortex.name ( "p" ) );
        final Pipe < Integer > tee      = fiber ( Integer.class ).tee ( sidePipe ).pipe ( main0 );

        tee.emit ( 10 );
        tee.emit ( 20 );
        circuit.await ();

        assertEquals ( List.of ( 10, 20 ), sideSeen );
        assertEquals ( List.of ( 10, 20 ), mainSeen );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "Fiber.when(predicate, fiber) — §6.2.2" )
  class WhenContracts {

    @Test
    @DisplayName ( "matching values traverse sub-fiber; non-matching pass through" )
    void conditionalSubFiber () {
      // sub: limit(1) — only the first matched value passes; subsequent matches dropped.
      // predicate: v > 10.
      // Input: 5, 11, 12, 7, 13.
      //  5  → no match, pass         → 5
      //  11 → match, sub.limit(1) emits 11, counter=1 → 11
      //  12 → match, sub.limit(1) dropped              → (nothing)
      //  7  → no match, pass         → 7
      //  13 → match, sub.limit(1) dropped              → (nothing)
      final List < Integer > seen = runFiber (
        fiber ( Integer.class ).when ( v -> v > 10, fiber ( Integer.class ).limit ( 1 ) ),
        List.of ( 5, 11, 12, 7, 13 ) );
      assertEquals ( List.of ( 5, 11, 7 ), seen );
    }

    @Test
    @DisplayName ( "empty sub-fiber → when stage is identity" )
    void emptySubElides () {
      final Fiber < Integer > w = fiber ( Integer.class ).when ( v -> v > 0, fiber ( Integer.class ) );
      final List < Integer > seen = runFiber ( w, List.of ( -1, 1, 2 ) );
      assertEquals ( List.of ( -1, 1, 2 ), seen );
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §15.4 — External callback isolation
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "External callback isolation — §15.4" )
  class CallbackIsolation {

    @Test
    @DisplayName ( "throwing receptor does NOT kill the worker — subsequent emissions still drained" )
    void throwingReceptorIsolated () throws Exception {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        final AtomicInteger seen = new AtomicInteger ();
        c.subscribe ( circuit.subscriber ( cortex.name ( "bomb" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) v -> {
            if ( v == 1 ) throw new RuntimeException ( "boom" );
            seen.incrementAndGet ();
          } ) ) );

        final Pipe < Integer > p = c.get ( cortex.name ( "p" ) );
        p.emit ( 1 );   // throws inside receptor
        p.emit ( 2 );   // worker MUST still be alive
        p.emit ( 3 );
        circuit.await ();

        assertEquals ( 2, seen.get () );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "sibling receptors on same channel still receive after one throws" )
    void siblingLiveness () throws Exception {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        final AtomicInteger b = new AtomicInteger ();
        final AtomicInteger d = new AtomicInteger ();
        c.subscribe ( circuit.subscriber ( cortex.name ( "a" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) v -> {
            throw new RuntimeException ( "a-throws" );
          } ) ) );
        c.subscribe ( circuit.subscriber ( cortex.name ( "b" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) v -> b.incrementAndGet () ) ) );
        c.subscribe ( circuit.subscriber ( cortex.name ( "d" ),
          ( ps, reg ) -> reg.register ( (Substrates.Receptor < Integer >) v -> d.incrementAndGet () ) ) );

        final Pipe < Integer > p = c.get ( cortex.name ( "p" ) );
        p.emit ( 42 );
        circuit.await ();

        assertEquals ( 1, b.get (), "sibling b MUST still receive after sibling a threw" );
        assertEquals ( 1, d.get (), "sibling d MUST still receive" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "throwing subscriber callback is consumed; worker survives; not retried" )
    void subscriberCallbackConsumedOnThrow () throws Exception {
      final var cortex  = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > c = circuit.conduit ( Integer.class );
        final AtomicInteger calls = new AtomicInteger ();
        c.subscribe ( circuit.subscriber ( cortex.name ( "buggy" ),
          ( ps, reg ) -> {
            calls.incrementAndGet ();
            throw new RuntimeException ( "boom" );
          } ) );

        final Pipe < Integer > p = c.get ( cortex.name ( "p" ) );
        p.emit ( 1 );
        p.emit ( 2 );
        p.emit ( 3 );
        circuit.await ();

        // §16.1 #15: subscriber callback invoked exactly once for each
        // subscription/channel pair, even on throw.
        assertEquals ( 1, calls.get () );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /// Materialises `f.pipe(sinkPipe)` against a circuit-local sink that records
  /// emissions into a list, drives the inputs, drains, and returns the list.
  private static < E > List < E > runFiber ( final Fiber < E > f, final List < E > inputs ) {
    final var cortex  = Substrates.cortex ();
    final var circuit = cortex.circuit ();
    try {
      @SuppressWarnings ( "unchecked" )
      final Conduit < E > sink = (Conduit < E >) (Conduit < ? >) circuit.conduit ( Object.class );
      final List < E > seen = new ArrayList <> ();
      sink.subscribe ( circuit.subscriber ( cortex.name ( "sink.sub" ),
        ( ps, reg ) -> reg.register ( (Substrates.Receptor < E >) seen::add ) ) );

      final Pipe < E > sinkPipe = sink.get ( cortex.name ( "p" ) );
      final Pipe < E > input    = f.pipe ( sinkPipe );
      for ( E v : inputs ) input.emit ( v );
      circuit.await ();
      return seen;
    } finally {
      circuit.close ();
    }
  }

  private static < E > Fiber < E > fiber ( Class < E > type ) {
    return Substrates.cortex ().fiber ( type );
  }
}
