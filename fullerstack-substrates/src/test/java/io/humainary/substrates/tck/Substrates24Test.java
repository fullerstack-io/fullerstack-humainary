package io.humainary.substrates.tck;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Current;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Pool;
import io.humainary.substrates.api.Substrates.Pulse;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Subject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for Substrates 2.4-specific contracts:
/// - `Pool.pool(Function)` failure caching, null rejection, single-invocation guarantees (§10.1)
/// - `Circuit.pulse()` timing snapshot semantics (§5.7)
/// - `Circuit.current()` execution-context identity (§11.3)
/// - `Flow.fiber(Function)` per-attachment factory (§6.2)
/// - `Circuit.subscriber(Name, Pool)` overload (§7.2 / 2.4 default)
class Substrates24Test implements Substrates {

  // ═══════════════════════════════════════════════════════════════════════════
  // §10.1 — Derived Pool contracts
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Pool.pool(Function) — 2.4 contracts" )
  class DerivedPoolContracts {

    @Test
    @DisplayName ( "function returning null causes NPE on get(name)" )
    void nullResult_throwsNPE () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final Pool < String > derived = conduit.pool ( pipe -> null );
        assertThrows ( NullPointerException.class,
          () -> derived.get ( cortex.name ( "n" ) ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "null rejection is cached — function not re-invoked on subsequent get" )
    void nullRejection_cached () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final var calls = new AtomicInteger ( 0 );
        final Pool < String > derived = conduit.pool ( pipe -> {
          calls.incrementAndGet ();
          return null;
        } );
        final var name = cortex.name ( "n" );
        assertThrows ( NullPointerException.class, () -> derived.get ( name ) );
        assertThrows ( NullPointerException.class, () -> derived.get ( name ) );
        assertThrows ( NullPointerException.class, () -> derived.get ( name ) );
        assertEquals ( 1, calls.get (), "function must be invoked exactly once per name" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "thrown exception is cached — same instance re-thrown" )
    void failure_cachedReplaysSameException () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final var calls = new AtomicInteger ( 0 );
        final var holder = new AtomicReference < RuntimeException > ();
        final Pool < String > derived = conduit.pool ( pipe -> {
          calls.incrementAndGet ();
          var ex = new IllegalStateException ( "boom" );
          holder.set ( ex );
          throw ex;
        } );
        final var name = cortex.name ( "n" );
        var first = assertThrows ( IllegalStateException.class, () -> derived.get ( name ) );
        var second = assertThrows ( IllegalStateException.class, () -> derived.get ( name ) );
        assertSame ( first, second, "spec: the same exception is re-thrown" );
        assertSame ( holder.get (), first, "exception must be the original instance" );
        assertEquals ( 1, calls.get (), "function must not be re-invoked after failure" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "failure for one name does not affect other names" )
    void failure_perName () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final var bad = cortex.name ( "fails" );
        final Pool < String > derived = conduit.pool ( pipe -> {
          if ( pipe.subject ().name () == bad ) {
            throw new IllegalStateException ( "boom" );
          }
          return pipe.subject ().name ().toString ();
        } );
        assertThrows ( IllegalStateException.class, () -> derived.get ( bad ) );
        // Other names succeed independently.
        assertNotNull ( derived.get ( cortex.name ( "good.one" ) ) );
        assertNotNull ( derived.get ( cortex.name ( "good.two" ) ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "function invoked exactly once per distinct name" )
    void function_invokedOncePerName () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final var calls = new AtomicInteger ( 0 );
        final Pool < String > derived = conduit.pool ( pipe -> {
          calls.incrementAndGet ();
          return pipe.subject ().name ().toString ();
        } );
        final var a = cortex.name ( "a" );
        final var b = cortex.name ( "b" );
        for ( int i = 0; i < 100; i++ ) {
          derived.get ( a );
          derived.get ( b );
        }
        assertEquals ( 2, calls.get (), "exactly one invocation per distinct name" );
      } finally {
        circuit.close ();
      }
    }

  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §5.7 — Circuit.pulse()
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Circuit.pulse() — 2.4 timing probe" )
  class PulseContracts {

    @Test
    @DisplayName ( "returns four ordered timestamps" )
    void timestamps_ordered () {
      final var circuit = Substrates.cortex ().circuit ();
      try {
        Optional < Pulse > maybe = circuit.pulse ();
        assertTrue ( maybe.isPresent (), "pulse() on a live circuit must produce a Pulse" );
        Pulse p = maybe.get ();
        assertTrue ( p.start () <= p.enqueued (), "start <= enqueued" );
        assertTrue ( p.dequeued () >= p.enqueued (),
          "dequeued >= enqueued (best-effort across threads, but should hold on this host)" );
        assertTrue ( p.stop () >= p.dequeued (), "stop >= dequeued" );
        assertTrue ( p.stop () >= p.start (), "stop >= start" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "returns Optional.empty() once circuit has fully closed" )
    void empty_afterClose () throws InterruptedException {
      final var circuit = Substrates.cortex ().circuit ();
      circuit.close ();
      circuit.await ();          // ensure the worker has terminated
      // small sleep to defeat any in-flight close completion race on slow hosts
      Thread.sleep ( 50 );
      Optional < Pulse > maybe = circuit.pulse ();
      assertTrue ( maybe.isEmpty (), "pulse() after close must return empty" );
    }

    @Test
    @DisplayName ( "throws IllegalStateException when called from circuit thread" )
    void throwsFromCircuitThread () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var caught = new AtomicReference < Throwable > ();
        final var latch = new CountDownLatch ( 1 );
        final var pipe = circuit.pipe ( v -> {
          try {
            circuit.pulse ();    // illegal — called from circuit thread
          } catch ( Throwable t ) {
            caught.set ( t );
          } finally {
            latch.countDown ();
          }
        } );
        pipe.emit ( 1 );
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertNotNull ( caught.get () );
        assertInstanceOf ( IllegalStateException.class, caught.get () );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "concurrent pulses from multiple threads each produce a valid Pulse" )
    void concurrent_independent () throws Exception {
      final var circuit = Substrates.cortex ().circuit ();
      try {
        final int N = 8;
        final var start = new CountDownLatch ( 1 );
        final var done = new CountDownLatch ( N );
        final List < Optional < Pulse > > results = new ArrayList <> ();
        for ( int i = 0; i < N; i++ ) results.add ( null );
        for ( int i = 0; i < N; i++ ) {
          final int idx = i;
          Thread.ofVirtual ().start ( () -> {
            try {
              start.await ();
              Optional < Pulse > p = circuit.pulse ();
              synchronized ( results ) { results.set ( idx, p ); }
            } catch ( InterruptedException ignored ) {
              Thread.currentThread ().interrupt ();
            } finally {
              done.countDown ();
            }
          } );
        }
        start.countDown ();
        assertTrue ( done.await ( 5, TimeUnit.SECONDS ) );
        for ( var r : results ) {
          assertNotNull ( r );
          assertTrue ( r.isPresent (), "concurrent pulse must produce a valid result" );
        }
      } finally {
        circuit.close ();
      }
    }

  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §11.3 — Circuit.current()
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Circuit.current() — 2.4 execution context identity" )
  class CurrentContracts {

    @Test
    @DisplayName ( "returns same instance across calls (stable for circuit lifetime)" )
    void stable () {
      final var circuit = Substrates.cortex ().circuit ();
      try {
        Current c1 = circuit.current ();
        Current c2 = circuit.current ();
        assertSame ( c1, c2, "circuit.current() must be stable for the circuit's lifetime" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "different circuits have distinct currents" )
    void distinct_perCircuit () {
      final var cortex = Substrates.cortex ();
      final var c1 = cortex.circuit ();
      final var c2 = cortex.circuit ();
      try {
        assertNotSame ( c1.current (), c2.current () );
      } finally {
        c1.close ();
        c2.close ();
      }
    }

    @Test
    @DisplayName ( "cortex.current() != circuit.current() from outside the circuit thread" )
    void distinguishes_externalContext () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        // Caller thread is not the circuit thread; their currents must differ.
        assertNotSame ( cortex.current (), circuit.current () );
      } finally {
        circuit.close ();
      }
    }

  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §6.2 — Flow.fiber(Function) per-attachment factory
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Flow.fiber(Function) — 2.4 per-attachment factory" )
  class FlowFiberFactoryContracts {

    @Test
    @DisplayName ( "factory invoked exactly once per pipe(target) call" )
    void factory_invokedOncePerPipeCall () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var calls = new AtomicInteger ( 0 );
        final Flow < Integer, Integer > flow = cortex
          .< Integer >flow ( Integer.class )
          .fiber ( subject -> {
            calls.incrementAndGet ();
            return cortex.fiber ( Integer.class );
          } );
        final var conduit = circuit.conduit ( Integer.class );
        flow.pipe ( conduit.get ( cortex.name ( "a" ) ) );
        assertEquals ( 1, calls.get () );
        flow.pipe ( conduit.get ( cortex.name ( "b" ) ) );
        assertEquals ( 2, calls.get (), "factory invoked once per pipe(target) call" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "factory receives the target's subject" )
    void factory_receivesTargetSubject () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var seen = new AtomicReference < Subject < ? > > ();
        final Flow < Integer, Integer > flow = cortex
          .< Integer >flow ( Integer.class )
          .fiber ( subject -> {
            seen.set ( subject );
            return cortex.fiber ( Integer.class );
          } );
        final var conduit = circuit.conduit ( Integer.class );
        final var target = conduit.get ( cortex.name ( "x" ) );
        flow.pipe ( target );
        assertSame ( target.subject (), seen.get () );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "factory throw propagates from pipe(target)" )
    void factory_throwsPropagates () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Flow < Integer, Integer > flow = cortex
          .< Integer >flow ( Integer.class )
          .fiber ( subject -> {
            throw new IllegalStateException ( "factory failed" );
          } );
        final var conduit = circuit.conduit ( Integer.class );
        final var target = conduit.get ( cortex.name ( "x" ) );
        assertThrows ( IllegalStateException.class, () -> flow.pipe ( target ) );
      } finally {
        circuit.close ();
      }
    }

  }

  // ═══════════════════════════════════════════════════════════════════════════
  // §7.2 / 2.4 — Circuit.subscriber(Name, Pool) default overload
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Circuit.subscriber(Name, Pool) — 2.4 convenience overload" )
  class SubscriberPoolContracts {

    @Test
    @DisplayName ( "registers the pool's pipe per channel" )
    void registersFromPool () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var sourceConduit = circuit.conduit ( Integer.class );
        final var sinkConduit = circuit.conduit ( Integer.class );

        // Subscribe with the sink conduit *as a Pool of pipes*. For each
        // channel that gets emitted, the channel's pipe in the sink conduit
        // is registered to receive the upstream emissions.
        sourceConduit.subscribe (
          circuit.subscriber ( cortex.name ( "fanout" ), sinkConduit )
        );

        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );
        sinkConduit.subscribe (
          circuit.subscriber (
            cortex.name ( "collector" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        sourceConduit.get ( cortex.name ( "src" ) ).emit ( 42 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 42 ), received );
      } finally {
        circuit.close ();
      }
    }

  }

}
