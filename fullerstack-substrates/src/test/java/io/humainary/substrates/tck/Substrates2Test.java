package io.humainary.substrates.tck;

import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

}
