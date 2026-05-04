package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pool;
import io.humainary.substrates.api.Substrates.Provided;

import java.util.HashMap;
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
/// ## Allocation strategy — three-state lazy storage
///
/// Most derived pools see at most one name (the pool is created at the
/// moment of first lookup and often discarded afterwards). The cache is
/// arranged as a state machine that allocates only when needed:
///
/// - **EMPTY**: pool freshly constructed; only `source`/`fn` set.
/// - **SINGLE**: one entry resolved; held inline as `(firstName, firstValue)`.
///   No map allocated.
/// - **MULTI**: a second distinct name forced promotion to a `HashMap`.
///
/// All transitions and cache reads are serialised on the pool's monitor.
/// Most derived pools never leave SINGLE, so the synchronized cost is
/// uncontended (~3 ns) and the only allocations are the pool object
/// itself and the wrapper produced by the user function.
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

  // Inline single-entry cache (state SINGLE). Both fields plain — accessed
  // only under `synchronized(this)` so plain stores/reads are safe.
  private Name   firstName;
  private Object firstValue;

  // Multi-entry cache (state MULTI), allocated lazily on the second distinct
  // name. Plain HashMap is fine — all access is under `synchronized(this)`.
  private HashMap < Name, Object > overflow;

  FsDerivedPool ( Pool < S > source, Function < ? super S, ? extends T > fn ) {
    this.source = source;
    this.fn     = fn;
  }

  @NotNull
  @Override
  public synchronized T get ( @NotNull Name name ) {
    // SINGLE — fast match on the inline entry. Names are interned so `==` is
    // the canonical comparison.
    if ( name == firstName ) return unwrap ( firstValue, name );

    // MULTI — overflow map exists; look up there.
    HashMap < Name, Object > m = overflow;
    if ( m != null ) {
      Object cached = m.get ( name );
      if ( cached != null ) return unwrap ( cached, name );
      Object computed = computeAndWrap ( name );
      m.put ( name, computed );
      return unwrap ( computed, name );
    }

    // EMPTY → SINGLE: this is the first entry.
    if ( firstName == null ) {
      Object result = computeAndWrap ( name );
      firstValue = result;
      firstName  = name;
      return unwrap ( result, name );
    }

    // SINGLE → MULTI: a second distinct name forces map promotion. Preserve
    // the inline entry inside the new map. We deliberately keep `firstName`
    // populated as well so that the fast path above continues to short-circuit
    // for the dominant first-name lookup pattern.
    m = new HashMap <> ( 4 );
    m.put ( firstName, firstValue );
    Object result = computeAndWrap ( name );
    m.put ( name, result );
    overflow = m;
    return unwrap ( result, name );
  }

  /// Wraps the user function so we can cache sentinels for null results and
  /// thrown exceptions — both of which the spec requires us to memoise so
  /// the function isn't re-invoked.
  private Object computeAndWrap ( Name name ) {
    try {
      T result = fn.apply ( source.get ( name ) );
      return result != null ? result : NULL_RESULT;
    } catch ( RuntimeException ex ) {
      return new CachedFailure ( ex );
    }
  }

  @SuppressWarnings ( "unchecked" )
  private T unwrap ( Object cached, Name name ) {
    if ( cached == NULL_RESULT ) {
      throw new NullPointerException ( "Pool function returned null for name: " + name );
    }
    if ( cached instanceof CachedFailure cf ) {
      throw cf.ex;
    }
    return (T) cached;
  }

  @NotNull
  @Override
  public < U > Pool < U > pool ( @NotNull Function < ? super T, ? extends U > nextFn ) {
    return new FsDerivedPool <> ( this, nextFn );
  }

}
