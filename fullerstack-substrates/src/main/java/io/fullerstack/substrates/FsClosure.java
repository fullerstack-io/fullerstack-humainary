package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Closure;
import io.humainary.substrates.api.Substrates.Resource;
import java.util.function.Consumer;

/// A single-use block-scoped resource wrapper.
///
/// @param <R> the resource type
final class FsClosure < R extends Resource >
  implements Closure < R > {

  private final R resource;
  private final FsScope scope;
  private boolean consumed;

  FsClosure ( R resource, FsScope scope ) {
    this.resource = resource;
    this.scope = scope;
  }

  boolean isConsumed () {
    return consumed;
  }

  @Override
  public void consume ( Consumer < ? super R > consumer ) {
    // If scope is closed or already consumed, no-op (fail-safe)
    if ( scope.isClosed () || consumed ) {
      return;
    }
    consumed = true;
    // Remove from scope's cache so next closure() call creates new one
    scope.closureConsumed ( resource );
    try {
      consumer.accept ( resource );
    } finally {
      resource.close ();
    }
  }

}
