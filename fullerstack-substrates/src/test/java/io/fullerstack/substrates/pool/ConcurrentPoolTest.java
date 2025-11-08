package io.fullerstack.substrates.pool;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Pool;
import io.fullerstack.substrates.name.InternedName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentPoolTest {

  /**
   * Helper to create a simple mock Pipe for testing.
   */
  private static class MockPipe<T> implements Pipe<T> {
    private final String value;

    MockPipe(String value) {
      this.value = value;
    }

    @Override
    public void emit(T t) {
      // No-op for testing
    }

    @Override
    public void flush() {
      // No-op for testing
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  @Test
  void shouldReturnSameInstanceForSameName () {
    Pool<Pipe<String>> pool = new ConcurrentPool<>( name -> new MockPipe<>("value-" + name.value()) );

    Name name = InternedName.of ( "test" );
    Pipe<String> value1 = pool.get ( name );
    Pipe<String> value2 = pool.get ( name );

    assertThat ( value1 ).isSameAs ( value2 );
  }

  @Test
  void shouldReturnDifferentInstancesForDifferentNames () {
    Pool<Pipe<String>> pool = new ConcurrentPool<>( name -> new MockPipe<>("value-" + name.value()) );

    Pipe<String> value1 = pool.get ( InternedName.of ( "test1" ) );
    Pipe<String> value2 = pool.get ( InternedName.of ( "test2" ) );

    assertThat ( value1 ).isNotEqualTo ( value2 );
    assertThat ( value1.toString() ).isEqualTo ( "value-test1" );
    assertThat ( value2.toString() ).isEqualTo ( "value-test2" );
  }

  @Test
  void shouldCallFactoryOnlyOnce () {
    AtomicInteger factoryCalls = new AtomicInteger ( 0 );
    Pool<Pipe<String>> pool = new ConcurrentPool<>( name -> {
      factoryCalls.incrementAndGet ();
      return new MockPipe<>("value");
    } );

    Name name = InternedName.of ( "test" );
    pool.get ( name );
    pool.get ( name );
    pool.get ( name );

    assertThat ( factoryCalls.get () ).isEqualTo ( 1 );
  }

  @Test
  void shouldSupportComplexObjects () {
    // Use path() to get full hierarchical name, not just value() which returns the last segment
    Pool<ComplexPipe> pool = new ConcurrentPool<>( name -> new ComplexPipe( name.path ().toString () ) );

    Name name = InternedName.of ( "kafka.broker.1" );
    ComplexPipe obj = pool.get ( name );

    assertThat ( obj.value ).isEqualTo ( "kafka.broker.1" );
  }

  @Test
  void shouldHandleNullFactory () {
    Pool<Pipe<String>> pool = new ConcurrentPool<>( name -> null );

    Pipe<String> value = pool.get ( InternedName.of ( "test" ) );

    assertThat ( value ).isNull ();
  }

  @Test
  void shouldSupportConcurrentAccess () throws Exception {
    Pool<Pipe<String>> pool = new ConcurrentPool<>( name -> new MockPipe<>("value-" + name.value()) );
    Name name = InternedName.of ( "concurrent" );

    Thread[] threads = new Thread[10];
    Pipe<String>[] results = new Pipe[10];

    for ( int i = 0; i < threads.length; i++ ) {
      final int index = i;
      threads[i] = new Thread ( () -> {
        results[index] = pool.get ( name );
      } );
      threads[i].start ();
    }

    for ( Thread thread : threads ) {
      thread.join ();
    }

    // All threads should get the same instance
    for ( int i = 1; i < results.length; i++ ) {
      assertThat ( results[i] ).isSameAs ( results[0] );
    }
  }

  /**
   * Complex Pipe implementation for testing
   */
  private static class ComplexPipe implements Pipe<Object> {
    final String value;

    ComplexPipe ( String value ) {
      this.value = value;
    }

    @Override
    public void emit(Object o) {
      // No-op for testing
    }

    @Override
    public void flush() {
      // No-op for testing
    }
  }
}
