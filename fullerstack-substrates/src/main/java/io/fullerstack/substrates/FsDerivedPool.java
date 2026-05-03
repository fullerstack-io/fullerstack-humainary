package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pool;
import io.humainary.substrates.api.Substrates.Provided;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/// A derived pool that applies a transformation function to each result from
/// an underlying pool, per Substrates 2.4 §10.1:
///
/// - The function is invoked **at most once per name**; the cached outcome
///   (success, null-rejection, or failure) is replayed for subsequent lookups.
/// - If the function returns null, `get(name)` raises [NullPointerException]
///   for that name; the rejection is cached.
/// - If the function throws, the **same exception** is re-thrown on every
///   subsequent `get(name)` for that name without re-invoking the function.
///
/// Backed by [ConcurrentHashMap] using `computeIfAbsent` for atomic
/// single-shot resolution. Failures and null results are stored as sentinels
/// because `computeIfAbsent` cannot hold null values.
@Provided
final class FsDerivedPool < T, S > implements Pool < T > {

  /// Sentinel cached for entries whose function returned null. Each `get(name)`
  /// for that name then raises NPE without re-invoking the function.
  private static final Object NULL_RESULT = new Object ();

  /// Wrapper for a cached function failure. Holds the exact exception that
  /// the function threw; `get(name)` rethrows the same instance per spec.
  private record CachedFailure( RuntimeException ex ) { }

  private final Pool < S >                          source;
  private final Function < ? super S, ? extends T > fn;
  private final ConcurrentHashMap < Name, Object >  cache = new ConcurrentHashMap <> ();

  FsDerivedPool ( Pool < S > source, Function < ? super S, ? extends T > fn ) {
    this.source = source;
    this.fn     = fn;
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public T get ( @NotNull Name name ) {
    Object cached = cache.computeIfAbsent ( name, this::compute );
    if ( cached == NULL_RESULT ) {
      throw new NullPointerException ( "Pool function returned null for name: " + name );
    }
    if ( cached instanceof CachedFailure cf ) {
      throw cf.ex;
    }
    return (T) cached;
  }

  /// Mapping function for `computeIfAbsent`. Wraps the user function so we
  /// can cache sentinels for null results and thrown exceptions — both of
  /// which the spec requires us to memoise so the function isn't re-invoked.
  private Object compute ( Name name ) {
    try {
      T result = fn.apply ( source.get ( name ) );
      return result != null ? result : NULL_RESULT;
    } catch ( RuntimeException ex ) {
      return new CachedFailure ( ex );
    }
  }

  @NotNull
  @Override
  public < U > Pool < U > pool ( @NotNull Function < ? super T, ? extends U > nextFn ) {
    return new FsDerivedPool <> ( this, nextFn );
  }

}
