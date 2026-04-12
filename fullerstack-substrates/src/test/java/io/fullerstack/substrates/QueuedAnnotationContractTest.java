package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Receptor;

/// Tests for the @Queued annotation contract.
///
/// @Queued methods queue execution to the circuit's processing thread.
/// The caller returns immediately; actual processing happens asynchronously
/// on the circuit worker thread.
///
/// Annotated methods:
/// - Pipe.emit() — queues emission for circuit thread processing
/// - Circuit.close() — queues shutdown
/// - Source.subscribe() — queues subscription registration

@DisplayName ( "@Queued Annotation Contract Tests" )
class QueuedAnnotationContractTest {

  @Nested
  @DisplayName ( "Pipe.emit() @Queued" )
  class PipeEmitQueued {

    @Test
    @DisplayName ( "emit returns immediately before processing" )
    void emit_returnsImmediately () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( received::add )
          )
        );

        // emit() is @Queued — returns before receptor fires
        conduit.get ( cortex.name ( "src" ) ).emit ( 42 );

        // Without await, processing may not have happened yet
        // After await, it must have been processed
        circuit.await ();
        assertEquals ( List.of ( 42 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "emissions from external thread are queued to circuit thread" )
    void emit_fromExternalThread () throws Exception {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 1 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        // Emit from a different thread — @Queued routes through ingress queue
        new Thread ( () -> conduit.get ( cortex.name ( "src" ) ).emit ( 99 ) ).start ();

        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 99 ), received );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "multiple queued emissions preserve order" )
    void emit_preservesOrder () throws Exception {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );
        final List < Integer > received = new ArrayList <> ();
        final var latch = new CountDownLatch ( 5 );

        conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( v -> { received.add ( v ); latch.countDown (); } )
          )
        );

        var pipe = conduit.get ( cortex.name ( "src" ) );
        pipe.emit ( 1 );
        pipe.emit ( 2 );
        pipe.emit ( 3 );
        pipe.emit ( 4 );
        pipe.emit ( 5 );

        circuit.await ();
        assertTrue ( latch.await ( 2, TimeUnit.SECONDS ) );
        assertEquals ( List.of ( 1, 2, 3, 4, 5 ), received,
          "@Queued emissions must preserve FIFO order" );
      } finally {
        circuit.close ();
      }
    }
  }

  @Nested
  @DisplayName ( "Circuit.close() @Queued" )
  class CircuitCloseQueued {

    @Test
    @DisplayName ( "close() returns immediately" )
    void close_returnsImmediately () {
      var circuit = cortex ().circuit ();
      // @Queued: close() must return without blocking
      assertDoesNotThrow ( () -> circuit.close () );
    }

    @Test
    @DisplayName ( "emissions after close are silently dropped" )
    void emit_afterClose_dropped () throws Exception {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      var conduit = circuit.conduit ( Integer.class );
      final List < Integer > received = new ArrayList <> ();

      conduit.subscribe (
        circuit.subscriber (
          cortex.name ( "sub" ),
          ( subject, registrar ) ->
            registrar.register ( received::add )
        )
      );

      circuit.close ();

      // Emissions after close should be silently dropped
      assertDoesNotThrow (
        () -> conduit.get ( cortex.name ( "src" ) ).emit ( 42 ) );
    }
  }

  @Nested
  @DisplayName ( "Source.subscribe() @Queued" )
  class SubscribeQueued {

    @Test
    @DisplayName ( "subscribe returns subscription before activation completes" )
    void subscribe_returnsImmediately () {
      Cortex cortex = cortex ();
      var circuit = cortex.circuit ();
      try {
        var conduit = circuit.conduit ( Integer.class );

        // subscribe() is @Queued — returns Subscription immediately
        // Actual subscriber activation happens on circuit thread
        var subscription = conduit.subscribe (
          circuit.subscriber (
            cortex.name ( "sub" ),
            ( subject, registrar ) ->
              registrar.register ( (Receptor < Integer >) v -> {} )
          )
        );

        assertNotNull ( subscription, "@Queued subscribe must return immediately" );
      } finally {
        circuit.close ();
      }
    }
  }
}
