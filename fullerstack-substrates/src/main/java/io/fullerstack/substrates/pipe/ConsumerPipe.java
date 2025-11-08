package io.fullerstack.substrates.pipe;

import io.humainary.substrates.api.Substrates.*;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Consumer-side pipe that receives emissions from a Conduit.
 * <p>
 * < p >< b >Role in Architecture:</b >
 * ConsumerPipe represents the consumer endpoint in the Producer-Consumer pattern.
 * While ProducerPipe emits values INTO the conduit system, ConsumerPipe receives
 * values FROM the conduit system via subscriber notifications.
 * <p>
 * < p >< b >Creation:</b >
 * Created by subscribers and registered via {@link Registrar#register(Pipe)} to receive
 * emissions from specific subjects. Typically created using the static factory methods:
 * < pre >
 * // Simple consumer
 * ConsumerPipe.of(value -> System.out.println(value))
 * <p>
 * // Named consumer (for debugging/metrics)
 * ConsumerPipe.of(cortex.name("logger"), value -> logValue(value))
 * </pre >
 * <p>
 * < p >< b >Execution Context:</b >
 * Consumer pipes execute on the Circuit's single-threaded processor (Virtual CPU Core pattern).
 * This guarantees ordered execution and eliminates the need for synchronization when accessing
 * circuit-local state.
 * <p>
 * < p >< b >Future Enhancements:</b >
 * The explicit class (vs bare lambda) allows for:
 * < ul >
 * < li >Metrics tracking (emission counts, processing time)</li >
 * < li >Error handling and retry logic</li >
 * < li >Lifecycle hooks (onStart, onComplete)</li >
 * < li >Backpressure signals</li >
 * < li >Better debugging (named instances in stack traces)</li >
 * </ul >
 *
 * @param < E > the emission type (e.g., MonitorSignal, ServiceSignal)
 */
public class ConsumerPipe < E > implements Pipe < E > {

  private final Consumer < E > consumer;
  private final Name           name; // Optional: for debugging/metrics

  /**
   * Private constructor - use static factory methods.
   *
   * @param consumer the consumer function to invoke on emissions
   * @param name     optional name for debugging (can be null)
   */
  private ConsumerPipe ( Consumer < E > consumer, Name name ) {
    this.consumer = Objects.requireNonNull ( consumer, "Consumer cannot be null" );
    this.name = name;
  }

  /**
   * Creates an anonymous consumer pipe from a lambda.
   *
   * @param consumer the function to invoke when emissions arrive
   * @param <        E > the emission type
   * @return a new ConsumerPipe wrapping the consumer
   * @throws NullPointerException if consumer is null
   */
  public static < E > ConsumerPipe < E > of ( Consumer < E > consumer ) {
    return new ConsumerPipe <> ( consumer, null );
  }

  /**
   * Creates a named consumer pipe for debugging and metrics.
   * <p>
   * < p >Named pipes are easier to identify in logs, stack traces, and metrics dashboards.
   *
   * @param name     the name to identify this consumer (e.g., "sensor-aggregator")
   * @param consumer the function to invoke when emissions arrive
   * @param <        E > the emission type
   * @return a new named ConsumerPipe
   * @throws NullPointerException if name or consumer is null
   */
  public static < E > ConsumerPipe < E > of ( Name name, Consumer < E > consumer ) {
    Objects.requireNonNull ( name, "Name cannot be null" );
    return new ConsumerPipe <> ( consumer, name );
  }

  /**
   * Receives an emission from the conduit system.
   * <p>
   * < p >Called by the Circuit's single-threaded processor when a ProducerPipe emits
   * a value that routes to this consumer via subscriber notification.
   *
   * @param emission the value emitted by a ProducerPipe
   */
  @Override
  public void emit ( E emission ) {
    consumer.accept ( emission );
  }

  /**
   * Flushes any buffered emissions.
   * <p>
   * < p >ConsumerPipe has no buffering - emissions are processed immediately.
   * This is a no-op implementation as required by Pipe interface.
   */
  @Override
  public void flush () {
    // No-op: ConsumerPipe has no buffering
  }

  /**
   * Returns the name of this consumer pipe, if named.
   *
   * @return the name, or null if this is an anonymous consumer
   */
  public Name getName () {
    return name;
  }

  @Override
  public String toString () {
    return name != null
           ? "ConsumerPipe[" + name.path () + "]"
           : "ConsumerPipe[anonymous]";
  }
}
