package io.fullerstack.substrates.closure;

import io.humainary.substrates.api.Substrates.*;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Implementation of Substrates.Closure for automatic resource management (ARM).
 * <p>
 * < p >Closure provides a try-with-resources pattern wrapper that ensures
 * the resource is properly closed after the consumer completes (or throws).
 * <p>
 * < p >Example usage:
 * < pre >
 * Closure&lt;Sink&lt;String&gt;&gt; closure = scope.closure(sink);
 * closure.consume(s -> {
 * // Use sink here
 * s.drain().forEach(System.out::println);
 * });
 * // sink.close() is called automatically
 * </pre >
 *
 * @param < R > the resource type
 * @see Closure
 * @see Resource
 * @see Scope
 */
public class AutoClosingResource < R extends Resource > implements Closure < R > {

  private final    R                                  resource;
  private final    Runnable                           onConsume;
  private final    java.util.function.BooleanSupplier isValid;
  private volatile boolean                            consumed = false;

  /**
   * Creates a Closure that manages the given Resource.
   *
   * @param resource the resource to manage
   * @throws NullPointerException if resource is null
   */
  public AutoClosingResource ( R resource ) {
    this ( resource, null, null );
  }

  /**
   * Creates a Closure that manages the given Resource with cleanup callback.
   *
   * @param resource  the resource to manage
   * @param onConsume callback to run after consume (e.g., to clear cache)
   * @throws NullPointerException if resource is null
   */
  public AutoClosingResource ( R resource, Runnable onConsume ) {
    this ( resource, onConsume, null );
  }

  /**
   * Creates a Closure with validity check.
   *
   * @param resource  the resource to manage
   * @param onConsume callback to run after consume
   * @param isValid   check if closure is still valid (e.g., scope not closed)
   */
  public AutoClosingResource ( R resource, Runnable onConsume, java.util.function.BooleanSupplier isValid ) {
    this.resource = Objects.requireNonNull ( resource, "Resource cannot be null" );
    this.onConsume = onConsume;
    this.isValid = isValid;
  }

  @Override
  public void consume ( Consumer < ? super R > consumer ) {
    Objects.requireNonNull ( consumer, "Consumer cannot be null" );

    // Check if already consumed - idempotent guard
    if ( consumed ) {
      return; // Already consumed, skip execution
    }

    // Check if closure is still valid (e.g., scope not closed)
    if ( isValid != null && !isValid.getAsBoolean () ) {
      return; // Scope closed or resource invalidated, skip execution
    }

    // Mark as consumed
    consumed = true;

    // ARM pattern: use resource and ensure close() is called
    try {
      consumer.accept ( resource );
    } finally {
      try {
        resource.close ();
      } catch ( java.lang.Exception e ) {
        // Ignore close errors - resource may already be closed by scope
      } finally {
        // Run cleanup callback (e.g., clear cache in Scope)
        if ( onConsume != null ) {
          onConsume.run ();
        }
      }
    }
  }
}
