package io.fullerstack.substrates.compliance;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.pool.ConcurrentPool;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compliance tests for Pool interface verifying documented behaviors from Substrates.java.
 * <p>
 * **Documented Behaviors Being Verified:**
 * <ul>
 *   <li>**Caching**: Same name returns same instance (object identity)</li>
 *   <li>**Thread-safety**: Concurrent calls create exactly one instance</li>
 *   <li>**Factory invocation**: Factory called only on first access</li>
 *   <li>**Multiple access methods**: get(Name), get(Subject), get(Substrate) all work</li>
 *   <li>**Name equivalence**: All access methods with same name return same instance</li>
 * </ul>
 */
class PoolBehaviorComplianceTest {

  /**
   * Helper to create a mock Pipe for testing Pool behavior.
   * Uses a String identifier for easy testing.
   */
  private static class MockPipe implements Pipe<String> {
    private final String id;

    MockPipe(String id) {
      this.id = id;
    }

    @Override
    public void emit(String s) {
      // No-op for testing
    }

    @Override
    public void flush() {
      // No-op for testing
    }

    @Override
    public String toString() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MockPipe)) return false;
      MockPipe mockPipe = (MockPipe) o;
      return id.equals(mockPipe.id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }

    public String getId() {
      return id;
    }
  }

  /**
   * **Documented Behavior**: Instance caching - same name returns same instance.
   * <p>
   * From Pool documentation:
   * <pre>
   *   On subsequent calls with the same name:
   *   - Returns the cached instance (same object reference)
   *   - No new instance is created
   * </pre>
   */
  @Test
  void testCaching_SameNameReturnsSameInstance() {
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> new MockPipe("instance-" + name.part()));

    Name name = cortex().name("entity");
    Pipe<String> first = pool.percept(name);
    Pipe<String> second = pool.percept(name);
    Pipe<String> third = pool.percept(name);

    // Verify: Same object reference (identity, not just equality)
    assertThat(first).isSameAs(second);
    assertThat(second).isSameAs(third);
  }

  /**
   * **Documented Behavior**: Factory invocation - called only once per name.
   * <p>
   * From Pool documentation:
   * <pre>
   *   On first call with a particular name:
   *   - Creates a new instance using the implementation's factory
   *   - Caches the instance associated with the name
   *   - Returns the new instance
   * </pre>
   */
  @Test
  void testFactoryInvocation_CalledOnlyOncePerName() {
    AtomicInteger factoryCalls = new AtomicInteger(0);
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> {
      factoryCalls.incrementAndGet();
      return new MockPipe("instance-" + name.part());
    });

    Name name = cortex().name("entity");

    // Access same name multiple times
    pool.percept(name);
    pool.percept(name);
    pool.percept(name);

    // Verify: Factory called exactly once
    assertThat(factoryCalls.get()).isEqualTo(1);
  }

  /**
   * **Documented Behavior**: Thread-safety - concurrent access creates exactly one instance.
   * <p>
   * From Pool documentation:
   * <pre>
   *   Thread-safe: Multiple concurrent calls with the same name will result in exactly
   *   one instance being created and shared.
   * </pre>
   */
  @Test
  void testThreadSafety_ConcurrentAccessCreatesOneInstance() throws InterruptedException {
    AtomicInteger factoryCalls = new AtomicInteger(0);
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> {
      factoryCalls.incrementAndGet();
      // Simulate some work to increase contention
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return new MockPipe("instance-" + name.part());
    });

    Name name = cortex().name("concurrent");
    int threadCount = 20;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    Pipe<String>[] results = new Pipe[threadCount];

    // Create threads that all try to get the same instance simultaneously
    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      new Thread(() -> {
        try {
          startLatch.await(); // Wait for all threads to be ready
          results[index] = pool.percept(name);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      }).start();
    }

    // Start all threads simultaneously
    startLatch.countDown();
    doneLatch.await();

    // Verify: Factory called exactly once despite concurrent access
    assertThat(factoryCalls.get()).isEqualTo(1);

    // Verify: All threads got the same instance
    for (int i = 1; i < threadCount; i++) {
      assertThat(results[i]).isSameAs(results[0]);
    }
  }

  /**
   * **Documented Behavior**: Multiple access methods - get(Subject) delegates to get(Name).
   * <p>
   * From Pool documentation:
   * <pre>
   *   Convenience method that extracts the Name from the subject and delegates
   *   to get(Name). Equivalent to: get(subject.name())
   * </pre>
   */
  @Test
  void testAccessMethods_SubjectDelegatesToName() {
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> new MockPipe("instance-" + name.part()));

    Circuit circuit = cortex().circuit(cortex().name("test-circuit"));
    try {
      // Create a conduit to get a Subject
      Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
        cortex().name("entity"),
        Composer.pipe()
      );

      // Access via Subject
      Pipe<String> viaSubject = pool.percept(conduit.subject());

      // Access via Name directly
      Pipe<String> viaName = pool.percept(cortex().name("entity"));

      // Verify: Both return the same instance
      assertThat(viaSubject).isSameAs(viaName);
    } finally {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: Multiple access methods - get(Substrate) delegates to get(Name).
   * <p>
   * From Pool documentation:
   * <pre>
   *   Convenience method that extracts the Name from the substrate's subject
   *   and delegates to get(Name). Equivalent to: get(substrate.subject().name())
   * </pre>
   */
  @Test
  void testAccessMethods_SubstrateDelegatesToName() {
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> new MockPipe("instance-" + name.part()));

    Circuit circuit = cortex().circuit(cortex().name("test-circuit"));
    try {
      // Create a conduit (which is a Substrate)
      Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
        cortex().name("entity"),
        Composer.pipe()
      );

      // Access via Substrate
      Pipe<String> viaSubstrate = pool.percept((Substrate<?>) conduit);

      // Access via Name directly
      Pipe<String> viaName = pool.percept(cortex().name("entity"));

      // Verify: Both return the same instance
      assertThat(viaSubstrate).isSameAs(viaName);
    } finally {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: Name equivalence across all access methods.
   * <p>
   * All three access methods (Name, Subject, Substrate) should return the same
   * instance when they refer to the same name.
   */
  @Test
  void testNameEquivalence_AllAccessMethodsReturnSameInstance() {
    AtomicInteger factoryCalls = new AtomicInteger(0);
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> {
      factoryCalls.incrementAndGet();
      return new MockPipe("instance-" + name.part());
    });

    Circuit circuit = cortex().circuit(cortex().name("test-circuit"));
    try {
      Name entityName = cortex().name("entity");
      Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(entityName, Composer.pipe());

      // Access via all three methods
      Pipe<String> viaName = pool.percept(entityName);
      Pipe<String> viaSubject = pool.percept(conduit.subject());
      Pipe<String> viaSubstrate = pool.percept((Substrate<?>) conduit);

      // Verify: All return the same instance
      assertThat(viaName).isSameAs(viaSubject);
      assertThat(viaSubject).isSameAs(viaSubstrate);

      // Verify: Factory called exactly once for all three accesses
      assertThat(factoryCalls.get()).isEqualTo(1);
    } finally {
      circuit.close();
    }
  }

  /**
   * **Documented Behavior**: Different names return different instances.
   * <p>
   * Verifies that the pool correctly distinguishes between different names.
   */
  @Test
  void testCaching_DifferentNamesReturnDifferentInstances() {
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> new MockPipe("instance-" + name.part()));

    Pipe<String> entity1 = pool.percept(cortex().name("entity1"));
    Pipe<String> entity2 = pool.percept(cortex().name("entity2"));
    Pipe<String> entity3 = pool.percept(cortex().name("entity3"));

    // Verify: Different instances for different names
    assertThat(entity1).isNotSameAs(entity2);
    assertThat(entity2).isNotSameAs(entity3);
    assertThat(entity1).isNotSameAs(entity3);

    // Verify: Factory used name correctly
    assertThat(entity1.toString()).isEqualTo("instance-entity1");
    assertThat(entity2.toString()).isEqualTo("instance-entity2");
    assertThat(entity3.toString()).isEqualTo("instance-entity3");
  }

  /**
   * **Documented Behavior**: Return value is never null.
   * <p>
   * From Pool documentation:
   * <pre>
   *   @return The cached instance for this name (never null)
   *   @NotNull
   * </pre>
   * <p>
   * Note: The API contract requires factories to return non-null values.
   * Pool implementations may assume the factory never returns null.
   */
  @Test
  void testReturnValue_NeverNull() {
    Pool<Pipe<String>> pool = new ConcurrentPool<>(name -> new MockPipe("non-null-" + name.part()));

    Pipe<String> result = pool.percept(cortex().name("entity"));

    // Verify: Result is not null (API contract)
    assertThat(result).isNotNull();
  }
}
