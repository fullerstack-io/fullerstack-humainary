package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Sift;

/**
 * Tests to verify the @Temporal annotation contract from Substrates API.
 *
 * <p>
 * The @Temporal annotation indicates a type that is transient and whose
 * reference should NOT be retained. This means:
 *
 * <ul>
 * <li>The object is only valid during the callback scope in which it's provided
 * <li>Storing references to @Temporal objects for later use is undefined
 * behavior
 * <li>The implementation may reuse or invalidate the object after the callback
 * returns
 * </ul>
 *
 * <p>
 *
 * @Temporal types in Substrates API include:
 *
 *           <ul>
 *           <li>Flow - valid only during Configurer callback
 *           <li>Sift - valid only during sift configuration callback
 *           <li>Channel - valid only during callback scope
 *           <li>Closure - valid only during callback scope
 *           <li>Current - thread-local context (scoped to current thread)
 *           </ul>
 *
 *           <p>
 *           These tests verify that @Temporal objects are properly usable
 *           within their callback scope. Note: We cannot directly test that
 *           stored references become invalid (that would be testing
 *           implementation details), but we can verify proper usage patterns.
 */
@DisplayName ( "@Temporal Annotation Contract Tests" )
class TemporalAnnotationContractTest {

  private Circuit circuit;

  @BeforeEach
  void setUp () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );
  }

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  // ============================================================
  // Flow @Temporal contract
  // ============================================================

  @Nested
  @DisplayName ( "Flow @Temporal contract" )
  class FlowTemporal {

    @Test
    @DisplayName ( "Flow is usable within configurer callback on pipe creation" )
    void flow_usableWithinPipeCallback () {
      AtomicBoolean flowUsed = new AtomicBoolean ( false );

      // Use circuit.pipe() with configurer - this invokes callback eagerly
      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        // Flow should be fully functional within callback
        assertNotNull ( flow, "Flow must be provided in callback" );
        Flow < Integer > result = flow.diff ().limit ( 100 ).sample ( 10 );
        assertNotNull ( result, "Flow operations must work within callback" );
        flowUsed.set ( true );
      } );

      assertTrue ( flowUsed.get (), "Configurer callback must be invoked" );
    }

    @Test
    @DisplayName ( "Flow operations chain correctly within callback scope" )
    void flow_operationsChainingWorks () {
      AtomicReference < Flow < Integer > > capturedFlow = new AtomicReference <> ();

      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        // Multiple chained operations should work
        Flow < Integer > chained = flow.diff ().limit ( 100 ).sample ( 10 ).skip ( 5 ).peek ( v -> {
        } ).replace ( v -> v * 2 ).guard ( v -> v > 0 ).reduce ( 0, Integer::sum );

        assertNotNull ( chained, "Chained flow operations must work" );
        // Capture for verification (though not recommended in practice)
        capturedFlow.set ( chained );
      } );

      // The captured flow reference may or may not be valid after callback
      // This test just verifies it was usable during the callback
      assertNotNull ( capturedFlow.get () );
    }

    @Test
    @DisplayName ( "Flow with sift works within callback scope" )
    void flow_siftWorksWithinCallback () {
      AtomicBoolean siftUsed = new AtomicBoolean ( false );

      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        flow.sift ( Comparator.naturalOrder (), sift -> {
          assertNotNull ( sift, "Sift must be provided in callback" );
          Sift < Integer > result = sift.min ( 0 ).max ( 100 ).high ().low ();
          assertNotNull ( result, "Sift operations must work within callback" );
          siftUsed.set ( true );
        } );
      } );

      assertTrue ( siftUsed.get (), "Sift configurer callback must be invoked" );
    }
  }

  // ============================================================
  // Sift @Temporal contract
  // ============================================================

  @Nested
  @DisplayName ( "Sift @Temporal contract" )
  class SiftTemporal {

    @Test
    @DisplayName ( "Sift is usable within sift callback" )
    void sift_usableWithinCallback () {
      AtomicBoolean siftUsed = new AtomicBoolean ( false );

      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        flow.sift ( Comparator.naturalOrder (), sift -> {
          assertNotNull ( sift, "Sift must be provided in callback" );
          siftUsed.set ( true );
        } );
      } );

      assertTrue ( siftUsed.get (), "Sift callback must be invoked" );
    }

    @Test
    @DisplayName ( "Sift operations chain correctly within callback scope" )
    void sift_operationsChainingWorks () {
      AtomicReference < Sift < Integer > > capturedSift = new AtomicReference <> ();

      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        flow.sift ( Comparator.naturalOrder (), sift -> {
          Sift < Integer > chained = sift.min ( 0 ).max ( 100 ).above ( 10 ).below ( 90 ).high ().low ().range ( 20, 80 );

          assertNotNull ( chained, "Chained sift operations must work" );
          capturedSift.set ( chained );
        } );
      } );

      // The captured sift reference may or may not be valid after callback
      assertNotNull ( capturedSift.get () );
    }
  }

  // ============================================================
  // Channel @Temporal contract
  // Note: Channel callback is invoked lazily on first emission via percept(),
  // so we verify the semantics rather than immediate invocation.
  // ============================================================

  @Nested
  @DisplayName ( "Channel @Temporal contract" )
  class ChannelTemporal {

    @Test
    @DisplayName ( "Subscriber callback receives channel subject when pipe emits" )
    void channel_subjectAvailableDuringCallback () {
      AtomicBoolean channelUsed = new AtomicBoolean ( false );

      Conduit < Pipe < String >, String > conduit = circuit.conduit ( cortex ().name ( "conduit" ), Composer.pipe () );

      var subscriber = circuit. < String > subscriber ( cortex ().name ( "sub" ), ( channelSubject, registrar ) -> {
        assertNotNull ( channelSubject, "Channel subject must be provided in callback" );
        assertNotNull ( channelSubject.name (), "Channel subject name must be accessible" );
        channelUsed.set ( true );
        registrar.register ( v -> {
        } );
      } );

      conduit.subscribe ( subscriber );

      // Get a pipe and emit to trigger the callback
      Pipe < String > pipe = conduit.percept ( cortex ().name ( "channel" ) );
      pipe.emit ( "test" );

      // Wait for async processing (await flushes batch and waits for processing)
      circuit.await ();

      assertTrue ( channelUsed.get (), "Subscriber callback must be invoked when pipe emits" );
    }
  }

  // ============================================================
  // Current @Temporal contract
  // ============================================================

  @Nested
  @DisplayName ( "Current @Temporal contract" )
  class CurrentTemporal {

    @Test
    @DisplayName ( "Current is thread-local scoped" )
    void current_isThreadLocalScoped () {
      var current = cortex ().current ();
      assertNotNull ( current, "Current must be provided" );
      assertNotNull ( current.subject (), "Current subject must be accessible" );
    }

    @Test
    @DisplayName ( "Current returns same instance on same thread" )
    void current_sameInstanceOnSameThread () {
      var current1 = cortex ().current ();
      var current2 = cortex ().current ();
      assertSame ( current1, current2, "Current should be same instance on same thread" );
    }

    @Test
    @DisplayName ( "Current may differ across threads" )
    void current_mayDifferAcrossThreads () throws InterruptedException {
      var mainCurrent = cortex ().current ();
      AtomicReference < Object > otherCurrent = new AtomicReference <> ();

      Thread other = new Thread ( () -> {
        otherCurrent.set ( cortex ().current () );
      } );
      other.start ();
      other.join ();

      // Current may be same or different across threads (implementation-dependent)
      // The key point is that each thread can access its own current
      assertNotNull ( mainCurrent );
      assertNotNull ( otherCurrent.get () );
    }
  }

  // ============================================================
  // Configurer @Temporal parameter contract
  // ============================================================

  @Nested
  @DisplayName ( "Configurer @Temporal parameter contract" )
  class ConfigurerTemporalParameter {

    @Test
    @DisplayName ( "Configurer receives @Temporal target on pipe creation" )
    void configurer_receivesTemporalTarget () {
      AtomicBoolean configureInvoked = new AtomicBoolean ( false );

      // Use circuit.pipe() which invokes configurer eagerly
      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, target -> {
        // The target parameter is @Temporal
        assertNotNull ( target, "@Temporal target must be provided" );
        configureInvoked.set ( true );
      } );

      assertTrue ( configureInvoked.get (), "Configurer must be invoked with target" );
    }

    @Test
    @DisplayName ( "Nested configurers each receive their @Temporal targets" )
    void nestedConfigurers_eachReceiveTemporalTargets () {
      AtomicBoolean flowConfigured = new AtomicBoolean ( false );
      AtomicBoolean siftConfigured = new AtomicBoolean ( false );

      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        assertNotNull ( flow, "Flow @Temporal target must be provided" );
        flowConfigured.set ( true );

        flow.sift ( Comparator.naturalOrder (), sift -> {
          assertNotNull ( sift, "Sift @Temporal target must be provided" );
          siftConfigured.set ( true );
        } );
      } );

      assertTrue ( flowConfigured.get (), "Flow configurer must be invoked" );
      assertTrue ( siftConfigured.get (), "Sift configurer must be invoked" );
    }
  }

  // ============================================================
  // Best practice verification
  // ============================================================

  @Nested
  @DisplayName ( "Best practice verification" )
  class BestPracticeVerification {

    @Test
    @DisplayName ( "@Temporal objects should not be stored (demonstration)" )
    void temporal_shouldNotBeStored () {
      // This test demonstrates the correct pattern (using within callback)
      // vs incorrect pattern (storing for later use)

      AtomicReference < Flow < Integer > > storedFlow = new AtomicReference <> ();

      // Use circuit.pipe() which invokes configurer eagerly
      Pipe < Integer > pipe = circuit.pipe ( v -> {
      }, flow -> {
        // CORRECT: Use the flow immediately within callback
        flow.diff ().limit ( 100 );

        // INCORRECT (but not prevented): Storing reference
        // This is what @Temporal warns against
        storedFlow.set ( flow );
      } );

      // We can't test that the stored reference becomes invalid,
      // but we document that storing @Temporal objects is undefined behavior
      assertNotNull ( storedFlow.get (), "Reference was stored (but this is not recommended)" );
    }
  }
}
