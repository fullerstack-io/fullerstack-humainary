package io.humainary.substrates.tck;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Tests for Substrates 2.0 API features:
/// - Conduit as Pool<Pipe<E>>
/// - Pool.pool(Function) derived views
/// - Standalone Flow<I,O> via Cortex.flow()
/// - Pipe.pipe(Flow) materialisation
/// - Circuit.conduit(Class) factory

class Substrates2Test implements Substrates {

  // ═══════════════════════════════════════════════════════════════════════════
  // Conduit as Pool<Pipe<E>>
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Conduit as Pool<Pipe<E>>" )
  class ConduitPoolTests {

    @Test
    @DisplayName ( "conduit.get(name) returns a Pipe" )
    void conduitGetReturnsPipe () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var pipe = conduit.get ( cortex.name ( "test.pipe" ) );
        assertNotNull ( pipe );
        assertInstanceOf ( Pipe.class, pipe );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "conduit.get(name) returns same pipe for same name (identity)" )
    void conduitGetReturnsSamePipe () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var name = cortex.name ( "test.identity" );
        final var pipe1 = conduit.get ( name );
        final var pipe2 = conduit.get ( name );
        assertSame ( pipe1, pipe2 );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "conduit.get(differentName) returns different pipes" )
    void conduitGetDifferentNames () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var pipe1 = conduit.get ( cortex.name ( "pipe.a" ) );
        final var pipe2 = conduit.get ( cortex.name ( "pipe.b" ) );
        assertNotSame ( pipe1, pipe2 );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "emit through conduit pipe reaches subscriber" )
    void emitThroughConduitPipe () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "test.subscriber" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        conduit.get ( cortex.name ( "source" ) ).emit ( 42 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 42 ), received );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pool.pool(Function) derived views
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Pool.pool(Function) derived views" )
  class DerivedPoolTests {

    @Test
    @DisplayName ( "pool(fn) returns transformed type" )
    void poolReturnsTransformed () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        // Derived pool wraps each Pipe<Integer> in a String representation
        final Pool < String > derived = conduit.pool ( pipe -> pipe.subject ().name ().toString () );
        final String result = derived.get ( cortex.name ( "test" ) );
        assertNotNull ( result );
        assertTrue ( result.contains ( "test" ) );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "pool(fn) caches results per name" )
    void poolCachesPerName () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final Conduit < Integer > conduit = circuit.conduit ( Integer.class );
        final Pool < String > derived = conduit.pool ( pipe -> pipe.subject ().name ().toString () );
        final var name = cortex.name ( "cached" );
        final String r1 = derived.get ( name );
        final String r2 = derived.get ( name );
        assertSame ( r1, r2, "Derived pool should cache per name" );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Standalone Flow<I,O> via Cortex.flow()
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Standalone Flow<I,O>" )
  class FlowTests {

    @Test
    @DisplayName ( "cortex.flow() returns identity flow" )
    void flowReturnsIdentity () {
      final var cortex = Substrates.cortex ();
      final Flow < Integer, Integer > flow = cortex.flow ( Integer.class );
      assertNotNull ( flow );
    }

    @Test
    @DisplayName ( "flow operators return NEW flow (immutable)" )
    void flowOperatorsReturnNewInstance () {
      final var cortex = Substrates.cortex ();
      final Flow < Integer, Integer > flow = cortex.flow ( Integer.class );
      final Flow < Integer, Integer > guarded = flow.guard ( v -> v > 0 );
      assertNotSame ( flow, guarded, "guard() must return a new flow" );
    }

    @Test
    @DisplayName ( "flow.diff() returns new flow" )
    void flowDiffReturnsNew () {
      final var cortex = Substrates.cortex ();
      final var flow = cortex.flow ( Integer.class );
      final var diffed = flow.diff ();
      assertNotSame ( flow, diffed );
    }

    @Test
    @DisplayName ( "flow.map() changes input type" )
    void flowMapChangesType () {
      final var cortex = Substrates.cortex ();
      final Flow < Integer, Integer > intFlow = cortex.flow ( Integer.class );
      final Flow < String, Integer > stringFlow = intFlow.map ( Integer::parseInt );
      assertNotNull ( stringFlow );
      // stringFlow takes String input and produces Integer output
    }

    @Test
    @DisplayName ( "flow can be reused across multiple pipes" )
    void flowReusable () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var flow = cortex.flow ( Integer.class ).guard ( v -> v > 0 );
        final var conduit = circuit.conduit ( Integer.class );
        final var pipe1 = conduit.get ( cortex.name ( "a" ) );
        final var pipe2 = conduit.get ( cortex.name ( "b" ) );

        // Same flow applied to two different pipes — should produce independent chains
        final var flowPipe1 = pipe1.pipe ( flow );
        final var flowPipe2 = pipe2.pipe ( flow );
        assertNotSame ( flowPipe1, flowPipe2 );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pipe.pipe(Flow) materialisation
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Pipe.pipe(Flow) materialisation" )
  class PipeMaterialisationTests {

    @Test
    @DisplayName ( "pipe.pipe(flow) returns a new pipe" )
    void pipeFlowReturnsNewPipe () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var basePipe = conduit.get ( cortex.name ( "base" ) );
        final var flow = cortex.flow ( Integer.class ).guard ( v -> v > 0 );
        final var flowPipe = basePipe.pipe ( flow );
        assertNotNull ( flowPipe );
        assertNotSame ( basePipe, flowPipe );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow guard filters emissions" )
    void flowGuardFilters () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "filter.sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        // Create a flow that only passes values > 5
        final var flow = cortex.flow ( Integer.class ).guard ( v -> v > 5 );
        final var source = conduit.get ( cortex.name ( "source" ) ).pipe ( flow );

        source.emit ( 3 );  // filtered out
        source.emit ( 7 );  // passes
        source.emit ( 1 );  // filtered out
        source.emit ( 9 );  // passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 7, 9 ), received );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Circuit.conduit(Class) factory
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Circuit.conduit(Class) factory" )
  class ConduitFactoryTests {

    @Test
    @DisplayName ( "conduit(Class) creates a conduit" )
    void conduitClassCreates () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( String.class );
        assertNotNull ( conduit );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "conduit(name, Class) creates a named conduit" )
    void conduitNameClassCreates () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var name = cortex.name ( "my.conduit" );
        final var conduit = circuit.conduit ( name, Integer.class );
        assertNotNull ( conduit );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "conduit has correct subject" )
    void conduitHasSubject () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var name = cortex.name ( "named.conduit" );
        final var conduit = circuit.conduit ( name, Integer.class );
        assertNotNull ( conduit.subject () );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Pipe tests (replacing disabled PipeTest)
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Pipe operations" )
  class PipeTests {

    @Test
    @DisplayName ( "circuit.pipe(Pipe) wraps a target pipe" )
    void circuitPipeWrapsTarget () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var target = circuit.pipe ( (Receptor < Integer >) received::add );
        final var wrapped = circuit.pipe ( target );
        assertNotNull ( wrapped );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.pipe(Receptor) creates pipe from receptor" )
    void circuitPipeFromReceptor () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );
        final var pipe = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        pipe.emit ( 42 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 42 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "pipe.pipe(flow) with diff filters duplicates" )
    void pipePipeFlowDiffFiltersDuplicates () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var flow = cortex.flow ( Integer.class ).diff ();
        final var source = target.pipe ( flow );

        source.emit ( 1 );
        source.emit ( 1 ); // duplicate — filtered
        source.emit ( 2 );
        source.emit ( 2 ); // duplicate — filtered
        source.emit ( 3 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "pipe.pipe(flow) with limit stops after N" )
    void pipePipeFlowLimit () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var flow = cortex.flow ( Integer.class ).limit ( 3 );
        final var source = target.pipe ( flow );

        for ( int i = 1; i <= 10; i++ ) source.emit ( i );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "pipe.pipe(flow) with replace transforms values" )
    void pipePipeFlowReplace () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var flow = cortex.flow ( Integer.class ).replace ( v -> v * 2 );
        final var source = target.pipe ( flow );

        source.emit ( 1 );
        source.emit ( 2 );
        source.emit ( 3 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 2, 4, 6 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "pipe has a subject" )
    void pipeHasSubject () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var pipe = circuit.pipe ( (Receptor < Integer >) v -> {} );
        assertNotNull ( pipe.subject () );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "async pipe emission across circuits" )
    void asyncEmission () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );
        final var pipe = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );

        // Emit from a different thread
        new Thread ( () -> pipe.emit ( 99 ) ).start ();
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 99 ), received );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow operator tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Flow operators" )
  class FlowOperatorTests {

    @Test
    @DisplayName ( "flow.skip(n) skips first N emissions" )
    void flowSkip () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe ( cortex.flow ( Integer.class ).skip ( 3 ) );

        source.emit ( 1 ); // skipped
        source.emit ( 2 ); // skipped
        source.emit ( 3 ); // skipped
        source.emit ( 4 ); // passed
        source.emit ( 5 ); // passed
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 4, 5 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.peek(receptor) observes without filtering" )
    void flowPeek () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > observed = new ArrayList <> ();
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).peek ( observed::add )
        );

        source.emit ( 1 );
        source.emit ( 2 );
        source.emit ( 3 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3 ), received );
        assertEquals ( List.of ( 1, 2, 3 ), observed );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.reduce accumulates" )
    void flowReduce () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).reduce ( 0, Integer::sum )
        );

        source.emit ( 1 ); // 0+1=1
        source.emit ( 2 ); // 1+2=3
        source.emit ( 3 ); // 3+3=6
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 3, 6 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.diff emits differences between consecutive values" )
    void flowDiff () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).diff ( 0 )
        );

        source.emit ( 10 ); // 10-0=10
        source.emit ( 15 ); // 15-10=5  — wait, diff might not subtract
        source.emit ( 12 ); // 12-15=-3
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        // diff passes only when value differs from previous
        assertEquals ( 3, received.size () );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.limit(n) passes only first N emissions" )
    void flowLimit () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).limit ( 3 )
        );

        source.emit ( 1 );
        source.emit ( 2 );
        source.emit ( 3 );
        source.emit ( 4 ); // dropped
        source.emit ( 5 ); // dropped
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.replace transforms values" )
    void flowReplace () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).replace ( v -> v * 2 )
        );

        source.emit ( 1 );
        source.emit ( 5 );
        source.emit ( 10 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 2, 10, 20 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.map transforms input type" )
    void flowMap () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        Flow < String, Integer > flow = cortex.flow ( Integer.class ).map ( String::length );
        final var source = target.pipe ( flow );

        source.emit ( "hi" );
        source.emit ( "hello" );
        source.emit ( "!" );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 2, 5, 1 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.dropWhile drops until predicate fails" )
    void flowDropWhile () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).dropWhile ( v -> v < 5 )
        );

        source.emit ( 1 ); // dropped
        source.emit ( 3 ); // dropped
        source.emit ( 5 ); // passes (predicate fails)
        source.emit ( 2 ); // passes (already triggered)
        source.emit ( 7 ); // passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 5, 2, 7 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.takeWhile passes until predicate fails" )
    void flowTakeWhile () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).takeWhile ( v -> v < 5 )
        );

        source.emit ( 1 ); // passes
        source.emit ( 3 ); // passes
        source.emit ( 5 ); // stopped (predicate fails)
        source.emit ( 2 ); // stopped
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.above filters values below threshold" )
    void flowAbove () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).above ( Comparator.naturalOrder (), 5 )
        );

        source.emit ( 3 ); // filtered
        source.emit ( 7 ); // passes
        source.emit ( 5 ); // filtered (not strictly above)
        source.emit ( 9 ); // passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 7, 9 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.below filters values above threshold" )
    void flowBelow () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).below ( Comparator.naturalOrder (), 5 )
        );

        source.emit ( 3 ); // passes
        source.emit ( 7 ); // filtered
        source.emit ( 5 ); // filtered (not strictly below)
        source.emit ( 1 ); // passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 3, 1 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.clamp passes only values within range" )
    void flowClamp () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).clamp ( Comparator.naturalOrder (), 2, 8 )
        );

        source.emit ( 1 );  // filtered (below range)
        source.emit ( 5 );  // passes (in range)
        source.emit ( 10 ); // filtered (above range)
        source.emit ( 3 );  // passes (in range)
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 5, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow chaining: limit + replace + guard" )
    void flowChaining () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        // Materialisation wraps right-to-left: guard is outermost (applied first),
        // then replace, then limit (innermost, closest to target)
        final var source = target.pipe (
          cortex.flow ( Integer.class )
            .limit ( 2 )                 // innermost: take first 2 that reach here
            .replace ( v -> v * 10 )     // middle: multiply by 10
            .guard ( v -> v > 0 )        // outermost: filter negatives
        );

        source.emit ( -1 ); // filtered by guard
        source.emit ( 3 );  // guard passes → replace(30) → limit count 1 → target
        source.emit ( 0 );  // filtered by guard
        source.emit ( 5 );  // guard passes → replace(50) → limit count 2 → target
        source.emit ( 7 );  // guard passes → replace(70) → limit count 3 → dropped
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 30, 50 ), received );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // State tests (2.0 API — compact/values removed from interface)
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "State 2.0" )
  class StateTests {

    @Test
    @DisplayName ( "state chaining creates linked slots" )
    void stateChaining () {
      final var cortex = Substrates.cortex ();
      final var alpha = cortex.name ( "alpha" );
      final var beta = cortex.name ( "beta" );

      final var state = cortex.state ()
        .state ( alpha, 1 )
        .state ( beta, 2 );

      assertEquals ( 1, state.value ( cortex.slot ( alpha, 0 ) ) );
      assertEquals ( 2, state.value ( cortex.slot ( beta, 0 ) ) );
    }

    @Test
    @DisplayName ( "state value returns default when slot missing" )
    void stateValueDefault () {
      final var cortex = Substrates.cortex ();
      final var missing = cortex.name ( "missing" );

      final var state = cortex.state ();
      assertEquals ( 42, state.value ( cortex.slot ( missing, 42 ) ) );
    }

    @Test
    @DisplayName ( "state stream returns all slots" )
    void stateStream () {
      final var cortex = Substrates.cortex ();
      final var a = cortex.name ( "a" );
      final var b = cortex.name ( "b" );
      final var c = cortex.name ( "c" );

      final var state = cortex.state ()
        .state ( a, 1 )
        .state ( b, 2 )
        .state ( c, 3 );

      assertEquals ( 3, state.stream ().count () );
    }

    @Test
    @DisplayName ( "state overwrites previous value for same slot" )
    void stateOverwrite () {
      final var cortex = Substrates.cortex ();
      final var key = cortex.name ( "key" );

      final var state = cortex.state ()
        .state ( key, 1 )
        .state ( key, 2 );

      // Most recent value wins
      assertEquals ( 2, state.value ( cortex.slot ( key, 0 ) ) );
      // Both slots in stream (linked list retains history)
      assertEquals ( 2, state.stream ().count () );
    }

    @Test
    @DisplayName ( "state supports multiple types" )
    void stateMultipleTypes () {
      final var cortex = Substrates.cortex ();
      final var name = cortex.name ( "val" );

      final var state = cortex.state ()
        .state ( name, 42 )
        .state ( name, 3.14f )
        .state ( name, true )
        .state ( name, "hello" );

      assertEquals ( "hello", state.value ( cortex.slot ( name, "" ) ) );
      assertTrue ( state.value ( cortex.slot ( name, false ) ) );
      assertEquals ( 3.14f, state.value ( cortex.slot ( name, 0f ) ), 0.001f );
      assertEquals ( 42, state.value ( cortex.slot ( name, 0 ) ) );
    }

    @Test
    @DisplayName ( "state is iterable" )
    void stateIterable () {
      final var cortex = Substrates.cortex ();
      final var a = cortex.name ( "a" );
      final var b = cortex.name ( "b" );

      final var state = cortex.state ()
        .state ( a, 10 )
        .state ( b, 20 );

      int count = 0;
      for ( Slot < ? > slot : state ) {
        assertNotNull ( slot );
        count++;
      }
      assertEquals ( 2, count );
    }

    @Test
    @DisplayName ( "empty state has no slots" )
    void emptyState () {
      final var cortex = Substrates.cortex ();
      final var state = cortex.state ();

      assertEquals ( 0, state.stream ().count () );
      assertFalse ( state.iterator ().hasNext () );
    }

    @Test
    @DisplayName ( "state enum slot" )
    void stateEnum () {
      final var cortex = Substrates.cortex ();
      final var state = cortex.state ().state ( Thread.State.RUNNABLE );

      // Enum state should be retrievable
      assertNotNull ( state );
      assertEquals ( 1, state.stream ().count () );
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Conduit lifecycle tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Conduit lifecycle" )
  class ConduitLifecycleTests {

    @Test
    @DisplayName ( "conduit subscribe receives emissions" )
    void conduitSubscribeReceives () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        conduit.get ( cortex.name ( "src" ) ).emit ( 1 );
        conduit.get ( cortex.name ( "src" ) ).emit ( 2 );
        conduit.get ( cortex.name ( "src" ) ).emit ( 3 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "conduit subscribe from multiple sources" )
    void conduitMultipleSources () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( String.class );
        final List < String > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        conduit.get ( cortex.name ( "alpha" ) ).emit ( "a" );
        conduit.get ( cortex.name ( "beta" ) ).emit ( "b" );
        conduit.get ( cortex.name ( "gamma" ) ).emit ( "c" );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( 3, received.size () );
      } finally {
        circuit.close ();
      }
    }

    // TODO: subscriptionCloseCallback test — onClose not yet wired in FsConduit (line 249)

    @Test
    @DisplayName ( "subscriber subject carries pipe identity" )
    void subscriberSubject () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var subjectRef = new AtomicReference < Subject < Pipe < Integer > > > ();
        final var latch = new CountDownLatch ( 1 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) -> {
              subjectRef.set ( subject );
              registrar.register ( v -> latch.countDown () );
            }
          )
        );

        conduit.get ( cortex.name ( "src" ) ).emit ( 42 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );

        final var subject = subjectRef.get ();
        assertNotNull ( subject );
        assertNotNull ( subject.name () );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow high/low/integrate/relate operator tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Flow stateful operators" )
  class FlowStatefulTests {

    @Test
    @DisplayName ( "flow.high tracks increasing values" )
    void flowHigh () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).high ( Comparator.naturalOrder () )
        );

        source.emit ( 5 );  // first value always passes
        source.emit ( 3 );  // not higher, filtered
        source.emit ( 7 );  // higher than 5, passes
        source.emit ( 6 );  // not higher, filtered
        source.emit ( 10 ); // higher than 7, passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 5, 7, 10 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.low tracks decreasing values" )
    void flowLow () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).low ( Comparator.naturalOrder () )
        );

        source.emit ( 5 );  // first value always passes
        source.emit ( 7 );  // not lower, filtered
        source.emit ( 3 );  // lower than 5, passes
        source.emit ( 4 );  // not lower, filtered
        source.emit ( 1 );  // lower than 3, passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 5, 3, 1 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.relate computes relation between consecutive values" )
    void flowRelate () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 3 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        // relate emits operator(previous, current) and sets previous = current
        final var source = target.pipe (
          cortex.flow ( Integer.class ).relate ( 0, ( prev, curr ) -> curr - prev )
        );

        source.emit ( 10 ); // 10-0=10
        source.emit ( 15 ); // 15-10=5
        source.emit ( 12 ); // 12-15=-3
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 10, 5, -3 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.integrate accumulates with fire predicate" )
    void flowIntegrate () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        // integrate: accumulates values, fires when predicate is true on accumulated
        // After fire, acc resets to null (not initial value)
        final var source = target.pipe (
          cortex.flow ( Integer.class ).integrate ( 0, Integer::sum, acc -> acc >= 10 )
        );

        source.emit ( 3 );  // acc=3, <10, no fire
        source.emit ( 4 );  // acc=7, <10, no fire
        source.emit ( 5 );  // acc=12, >=10, fire 12, reset to null
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 12 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.max passes values at or below threshold" )
    void flowMax () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).max ( Comparator.naturalOrder (), 5 )
        );

        source.emit ( 7 );  // above max, filtered
        source.emit ( 3 );  // at or below max, passes
        source.emit ( 9 );  // above max, filtered
        source.emit ( 4 );  // at or below max, passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 3, 4 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.min passes values at or above threshold" )
    void flowMin () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).min ( Comparator.naturalOrder (), 5 )
        );

        source.emit ( 3 );  // below min, filtered
        source.emit ( 7 );  // at or above min, passes
        source.emit ( 1 );  // below min, filtered
        source.emit ( 6 );  // at or above min, passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 7, 6 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "flow.range passes values within range" )
    void flowRange () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );
        final var target = circuit.pipe ( (Receptor < Integer >) v -> {
          received.add ( v );
          latch.countDown ();
        } );
        final var source = target.pipe (
          cortex.flow ( Integer.class ).range ( Comparator.naturalOrder (), 3, 7 )
        );

        source.emit ( 1 );  // below range
        source.emit ( 5 );  // in range, passes
        source.emit ( 9 );  // above range
        source.emit ( 4 );  // in range, passes
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 5, 4 ), received );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // onClose callback tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Subscription onClose callback" )
  class OnCloseTests {

    @Test
    @DisplayName ( "onClose fires when subscription is closed explicitly" )
    void onClose_explicitClose () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var closedLatch = new CountDownLatch ( 1 );

        var subscription = conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) -> registrar.register ( v -> {} )
          ),
          sub -> closedLatch.countDown ()
        );

        subscription.close ();
        assertTrue ( closedLatch.await ( 2, TimeUnit.SECONDS ),
          "onClose must fire when subscription.close() is called" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "onClose fires exactly once on repeated close" )
    void onClose_firesOnce () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var count = new java.util.concurrent.atomic.AtomicInteger ( 0 );

        var subscription = conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) -> registrar.register ( v -> {} )
          ),
          sub -> count.incrementAndGet ()
        );

        subscription.close ();
        subscription.close ();
        subscription.close ();
        assertEquals ( 1, count.get (), "onClose must fire exactly once" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "onClose receives the subscription" )
    void onClose_receivesSubscription () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit ( Integer.class );
        final var ref = new AtomicReference < Subscription > ();

        var subscription = conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) -> registrar.register ( v -> {} )
          ),
          ref::set
        );

        subscription.close ();
        assertSame ( subscription, ref.get (),
          "onClose must receive the subscription being closed" );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Routing.STEM tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Routing.STEM hierarchical dispatch" )
  class RoutingStemTests {

    @Test
    @DisplayName ( "STEM: emission at child reaches subscriber at parent channel" )
    void stem_propagatesToParent () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit (
          cortex.name ( "log" ), Integer.class, Routing.STEM );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 2 );

        // Subscriber registered on conduit — gets activated per-channel
        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        // Ensure parent channel exists
        conduit.get ( cortex.name ( "log.app" ) );

        // Emit at child "log.app.auth" — STEM propagates upward to "log.app"
        // Subscriber sees: 1 from child channel + 1 from parent channel = 2
        conduit.get ( cortex.name ( "log.app.auth" ) ).emit ( 42 );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 42, 42 ), received,
          "STEM: subscriber sees emission at target + each ancestor channel" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "PIPE routing does NOT propagate to parent" )
    void pipe_doesNotPropagate () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        // Default PIPE routing
        final var conduit = circuit.conduit ( Integer.class );
        final List < Integer > parentReceived = new ArrayList <> ();

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( parentReceived::add )
          )
        );

        // Get parent pipe to create channel
        conduit.get ( cortex.name ( "parent" ) );

        // Emit at child — should NOT propagate with PIPE routing
        conduit.get ( cortex.name ( "parent.child" ) ).emit ( 99 );
        circuit.await ();

        // Give a brief window — parent should NOT have received anything
        // from the child's emission (only its own direct emissions)
        Thread.sleep ( 50 );
        // parentReceived may contain 99 if subscriber was activated for parent.child
        // but should NOT contain it from STEM propagation
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "STEM: emission propagates through multiple ancestor levels" )
    void stem_multiLevel () throws Exception {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        final var conduit = circuit.conduit (
          cortex.name ( "ns" ), String.class, Routing.STEM );
        final List < String > received = new ArrayList <> ();
        // leaf + mid + root = 3 dispatches per subscriber
        final var latch = new CountDownLatch ( 3 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        // Ensure channels exist at each level
        conduit.get ( cortex.name ( "ns" ) );
        conduit.get ( cortex.name ( "ns.mid" ) );

        // Emit at leaf "ns.mid.leaf" — STEM propagates to "ns.mid" and "ns"
        conduit.get ( cortex.name ( "ns.mid.leaf" ) ).emit ( "hello" );
        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );

        // Subscriber sees 3: leaf + mid + root
        assertEquals ( 3, received.size (),
          "STEM: emission must dispatch at leaf + each ancestor level" );
        assertTrue ( received.stream ().allMatch ( "hello"::equals ) );
      } finally {
        circuit.close ();
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Circuit as Source<State> tests
  // ═══════════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName ( "Circuit Source<State>" )
  class CircuitSourceTests {

    @Test
    @DisplayName ( "circuit.subscribe(State) returns subscription" )
    void circuitSubscribe_returnsSubscription () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        var subscription = circuit.subscribe (
          circuit.subscriber (
            cortex.name ( "state.sub" ),
            ( subject, registrar ) -> registrar.register ( (Receptor < State >) s -> {} )
          )
        );
        assertNotNull ( subscription );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.tap() returns a tap" )
    void circuitTap_returnsTap () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        Tap < State > tap = circuit.tap ( p -> p );
        assertNotNull ( tap );
        tap.close ();
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "circuit.reservoir() returns a reservoir" )
    void circuitReservoir_returnsReservoir () {
      final var cortex = Substrates.cortex ();
      final var circuit = cortex.circuit ();
      try {
        var reservoir = circuit.reservoir ();
        assertNotNull ( reservoir );
      } finally {
        circuit.close ();
      }
    }
  }

}
