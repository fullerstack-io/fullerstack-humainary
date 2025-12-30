package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Scope;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

/**
 * Tests to verify the @Idempotent annotation contract from Substrates API.
 *
 * <p>The @Idempotent annotation indicates that calling a method multiple times has the same effect
 * as calling it once. This is a critical contract that ensures:
 *
 * <ul>
 *   <li>First call performs the operation (e.g., cleanup, release resources)
 *   <li>Subsequent calls have no effect (safe no-op)
 *   <li>No exceptions thrown on repeated calls
 * </ul>
 *
 * <p>@Idempotent annotated methods in Substrates API:
 *
 * <ul>
 *   <li>{@link Circuit#close()} - Close circuit (line 738)
 *   <li>{@link Scope#close()} - Close scope (line 3833)
 *   <li>{@link Subscription#close()} - Close subscription (line 3964)
 * </ul>
 */
@DisplayName ( "@Idempotent Annotation Contract Tests" )
class IdempotentAnnotationContractTest {

  // ============================================================
  // Circuit.close() @Idempotent
  // ============================================================

  @Nested
  @DisplayName ( "Circuit.close() @Idempotent" )
  class CircuitCloseIdempotent {

    @Test
    @DisplayName ( "Circuit.close() can be called multiple times without exception" )
    void circuitClose_canBeCalledMultipleTimes () {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      // First close should work
      assertDoesNotThrow ( () -> circuit.close (), "First close() should not throw" );

      // Subsequent closes should be no-ops (no exception)
      assertDoesNotThrow ( () -> circuit.close (), "Second close() should not throw" );
      assertDoesNotThrow ( () -> circuit.close (), "Third close() should not throw" );
      assertDoesNotThrow ( () -> circuit.close (), "Fourth close() should not throw" );
    }

    @Test
    @DisplayName ( "Circuit.close() multiple times is safe with concurrent calls" )
    void circuitClose_safeWithConcurrentCalls () throws InterruptedException {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      // Simulate multiple threads closing concurrently
      Thread[] threads = new Thread[10];
      boolean[] exceptions = new boolean[10];

      for ( int i = 0; i < 10; i++ ) {
        final int idx = i;
        threads[i] =
          new Thread (
            () -> {
              try {
                circuit.close ();
              } catch ( RuntimeException e ) {
                exceptions[idx] = true;
              }
            } );
      }

      for ( Thread t : threads ) t.start ();
      for ( Thread t : threads ) t.join ();

      // No exceptions should have occurred
      for ( int i = 0; i < 10; i++ ) {
        assertFalse ( exceptions[i], "Thread " + i + " threw exception on close()" );
      }
    }

    @Test
    @DisplayName ( "Circuit remains closed after multiple close calls" )
    void circuitRemainsClosed_afterMultipleCloses () {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      circuit.close ();
      circuit.close ();
      circuit.close ();

      // Circuit should still be closed (not reopened)
      // We can verify by checking that await() doesn't hang
      // (a closed circuit should return immediately from await)
      assertDoesNotThrow ( () -> circuit.await (), "await() should work on closed circuit" );
    }
  }

  // ============================================================
  // Scope.close() @Idempotent
  // ============================================================

  @Nested
  @DisplayName ( "Scope.close() @Idempotent" )
  class ScopeCloseIdempotent {

    @Test
    @DisplayName ( "Scope.close() can be called multiple times without exception" )
    void scopeClose_canBeCalledMultipleTimes () {
      Scope scope = cortex ().scope ( cortex ().name ( "test" ) );

      // First close should work
      assertDoesNotThrow ( () -> scope.close (), "First close() should not throw" );

      // Subsequent closes should be no-ops (no exception)
      assertDoesNotThrow ( () -> scope.close (), "Second close() should not throw" );
      assertDoesNotThrow ( () -> scope.close (), "Third close() should not throw" );
      assertDoesNotThrow ( () -> scope.close (), "Fourth close() should not throw" );
    }

    @Test
    @DisplayName ( "Scope.close() is idempotent with registered resources" )
    void scopeClose_idempotentWithRegisteredResources () {
      Scope scope = cortex ().scope ( cortex ().name ( "test" ) );
      Circuit circuit = scope.register ( cortex ().circuit ( cortex ().name ( "nested" ) ) );

      // First close cleans up registered resources
      assertDoesNotThrow ( () -> scope.close (), "First close() should not throw" );

      // Subsequent closes should be no-ops
      assertDoesNotThrow ( () -> scope.close (), "Second close() should not throw" );
      assertDoesNotThrow ( () -> scope.close (), "Third close() should not throw" );
    }

    @Test
    @DisplayName ( "Scope.close() multiple times is safe with concurrent calls" )
    void scopeClose_safeWithConcurrentCalls () throws InterruptedException {
      Scope scope = cortex ().scope ( cortex ().name ( "test" ) );

      // Simulate multiple threads closing concurrently
      Thread[] threads = new Thread[10];
      boolean[] exceptions = new boolean[10];

      for ( int i = 0; i < 10; i++ ) {
        final int idx = i;
        threads[i] =
          new Thread (
            () -> {
              try {
                scope.close ();
              } catch ( RuntimeException e ) {
                exceptions[idx] = true;
              }
            } );
      }

      for ( Thread t : threads ) t.start ();
      for ( Thread t : threads ) t.join ();

      // No exceptions should have occurred
      for ( int i = 0; i < 10; i++ ) {
        assertFalse ( exceptions[i], "Thread " + i + " threw exception on close()" );
      }
    }
  }

  // ============================================================
  // Subscription.close() @Idempotent
  // ============================================================

  @Nested
  @DisplayName ( "Subscription.close() @Idempotent" )
  class SubscriptionCloseIdempotent {

    @Test
    @DisplayName ( "Subscription.close() can be called multiple times without exception" )
    void subscriptionClose_canBeCalledMultipleTimes () {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      try {
        Subscriber < State > subscriber =
          circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
          } );
        Subscription subscription = circuit.subscribe ( subscriber );

        // First close should work
        assertDoesNotThrow ( () -> subscription.close (), "First close() should not throw" );

        // Subsequent closes should be no-ops (no exception)
        assertDoesNotThrow ( () -> subscription.close (), "Second close() should not throw" );
        assertDoesNotThrow ( () -> subscription.close (), "Third close() should not throw" );
        assertDoesNotThrow ( () -> subscription.close (), "Fourth close() should not throw" );
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "Subscription.close() multiple times is safe with concurrent calls" )
    void subscriptionClose_safeWithConcurrentCalls () throws InterruptedException {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      try {
        Subscriber < State > subscriber =
          circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
          } );
        Subscription subscription = circuit.subscribe ( subscriber );

        // Simulate multiple threads closing concurrently
        Thread[] threads = new Thread[10];
        boolean[] exceptions = new boolean[10];

        for ( int i = 0; i < 10; i++ ) {
          final int idx = i;
          threads[i] =
            new Thread (
              () -> {
                try {
                  subscription.close ();
                } catch ( RuntimeException e ) {
                  exceptions[idx] = true;
                }
              } );
        }

        for ( Thread t : threads ) t.start ();
        for ( Thread t : threads ) t.join ();

        // No exceptions should have occurred
        for ( int i = 0; i < 10; i++ ) {
          assertFalse ( exceptions[i], "Thread " + i + " threw exception on close()" );
        }
      } finally {
        circuit.close ();
      }
    }

    @Test
    @DisplayName ( "Multiple subscriptions can be closed independently" )
    void multipleSubscriptions_canBeClosedIndependently () {
      Circuit circuit = cortex ().circuit ( cortex ().name ( "test" ) );

      try {
        Subscriber < State > subscriber1 =
          circuit.subscriber ( cortex ().name ( "sub1" ), ( subject, registrar ) -> {
          } );
        Subscriber < State > subscriber2 =
          circuit.subscriber ( cortex ().name ( "sub2" ), ( subject, registrar ) -> {
          } );

        Subscription subscription1 = circuit.subscribe ( subscriber1 );
        Subscription subscription2 = circuit.subscribe ( subscriber2 );

        // Close subscription1 multiple times
        assertDoesNotThrow ( () -> subscription1.close () );
        assertDoesNotThrow ( () -> subscription1.close () );

        // subscription2 should still be closeable
        assertDoesNotThrow ( () -> subscription2.close () );
        assertDoesNotThrow ( () -> subscription2.close () );

      } finally {
        circuit.close ();
      }
    }
  }

  // ============================================================
  // Cross-cutting @Idempotent verification
  // ============================================================

  @Nested
  @DisplayName ( "Cross-cutting @Idempotent verification" )
  class CrossCuttingIdempotent {

    @Test
    @DisplayName ( "Closing parent and child resources in any order is idempotent" )
    void closingParentAndChild_anyOrderIdempotent () {
      Scope scope = cortex ().scope ( cortex ().name ( "parent" ) );
      Circuit circuit = scope.register ( cortex ().circuit ( cortex ().name ( "child" ) ) );

      // Close child first, then parent (both idempotent)
      assertDoesNotThrow ( () -> circuit.close () );
      assertDoesNotThrow ( () -> circuit.close () );
      assertDoesNotThrow ( () -> scope.close () );
      assertDoesNotThrow ( () -> scope.close () );

      // Even after both are closed, more closes should be safe
      assertDoesNotThrow ( () -> circuit.close () );
      assertDoesNotThrow ( () -> scope.close () );
    }

    @Test
    @DisplayName ( "Closing parent scope also closes children idempotently" )
    void closingParentScope_closesChildrenIdempotently () {
      Scope scope = cortex ().scope ( cortex ().name ( "parent" ) );
      Circuit circuit1 = scope.register ( cortex ().circuit ( cortex ().name ( "child1" ) ) );
      Circuit circuit2 = scope.register ( cortex ().circuit ( cortex ().name ( "child2" ) ) );

      // Close parent (closes children)
      assertDoesNotThrow ( () -> scope.close () );

      // Children should be closeable again (idempotent)
      assertDoesNotThrow ( () -> circuit1.close () );
      assertDoesNotThrow ( () -> circuit2.close () );

      // Parent can also be closed again
      assertDoesNotThrow ( () -> scope.close () );
    }

    @Test
    @DisplayName ( "Interleaved close calls across multiple resources are safe" )
    void interleavedClose_acrossMultipleResources () {
      Scope scope = cortex ().scope ( cortex ().name ( "scope" ) );
      Circuit circuit = cortex ().circuit ( cortex ().name ( "circuit" ) );
      Subscriber < State > subscriber =
        circuit.subscriber ( cortex ().name ( "sub" ), ( subject, registrar ) -> {
        } );
      Subscription subscription = circuit.subscribe ( subscriber );

      // Interleaved closes (all idempotent)
      assertDoesNotThrow ( () -> subscription.close () );
      assertDoesNotThrow ( () -> circuit.close () );
      assertDoesNotThrow ( () -> scope.close () );
      assertDoesNotThrow ( () -> subscription.close () ); // again
      assertDoesNotThrow ( () -> circuit.close () ); // again
      assertDoesNotThrow ( () -> scope.close () ); // again
    }
  }
}
