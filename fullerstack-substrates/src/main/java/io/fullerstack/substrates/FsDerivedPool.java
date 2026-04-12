package io.fullerstack.substrates;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pool;
import io.humainary.substrates.api.Substrates.Provided;

/// A derived pool that applies a transformation function to each result from
/// an underlying pool. Results are cached per name — the function is applied
/// exactly once per name. Supports further composition via pool(Function).
@Provided
final class FsDerivedPool < T, S > implements Pool < T > {

  private final Pool < S >                               source;
  private final Function < ? super S, ? extends T >      fn;
  private volatile Map < Name, T > cache;

  FsDerivedPool ( Pool < S > source, Function < ? super S, ? extends T > fn ) {
    this.source = source;
    this.fn     = fn;
  }

  @NotNull
  @Override
  public T get ( @NotNull Name name ) {
    Map < Name, T > map = cache;
    if ( map != null ) {
      T cached = map.get ( name );
      if ( cached != null )
        return cached;
    }
    return computeAndCache ( name );
  }

  private T computeAndCache ( Name name ) {
    synchronized ( this ) {
      Map < Name, T > map = cache;
      if ( map == null ) {
        map = cache = new IdentityHashMap <> ();
      }
      T cached = map.get ( name );
      if ( cached != null )
        return cached;

      T result = fn.apply ( source.get ( name ) );

      Map < Name, T > newMap = new IdentityHashMap <> ( cache );
      newMap.put ( name, result );
      cache = newMap;

      return result;
    }
  }

  @NotNull
  @Override
  public < U > Pool < U > pool ( @NotNull Function < ? super T, ? extends U > nextFn ) {
    return new FsDerivedPool <> ( this, nextFn );
  }

}
