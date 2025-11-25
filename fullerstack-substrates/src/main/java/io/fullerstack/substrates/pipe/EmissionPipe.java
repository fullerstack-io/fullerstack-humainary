package io.fullerstack.substrates.pipe;

import java.util.Objects;

import io.fullerstack.substrates.capture.SubjectCapture;
import io.fullerstack.substrates.flow.FlowRegulator;
import io.humainary.substrates.api.Substrates.Capture;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;

/**
 * Emission pipe in the pipe chain: EmissionPipe → asyncPipe → routingPipe
 * <p>
 * Like Unix pipes, each pipe emits to the next pipe in the chain:
 * <pre>
 *   EmissionPipe.emit(value)
 *       ↓ wraps with Subject context
 *   asyncPipe.emit(capture)     // Circuit.pipe() - queues to circuit thread
 *       ↓ async dispatch
 *   routingPipe.emit(capture)   // Routes to subscriber pipes
 *       ↓
 *   subscriberPipe.emit(value)  // Final destination
 * </pre>
 *
 * @param <E> the emission type
 */
public class EmissionPipe<E> implements Pipe<E> {

  private final Subject<Channel<E>> subject;    // WHO is emitting
  private final Pipe<Capture<E>> nextPipe;      // Next pipe in the chain (async dispatch)
  private final FlowRegulator<E> flow;          // Optional transformations

  /**
   * Creates an EmissionPipe that chains to routingPipe via async dispatch.
   *
   * @param circuit     provides async dispatch via circuit.pipe()
   * @param subject     the Subject context (WHO is emitting)
   * @param routingPipe the pipe that handles routing to subscribers
   */
  public EmissionPipe(Circuit circuit, Subject<Channel<E>> subject, Pipe<Capture<E>> routingPipe) {
    this(circuit, subject, routingPipe, null);
  }

  /**
   * Creates an EmissionPipe with Flow transformations.
   *
   * @param circuit     provides async dispatch via circuit.pipe()
   * @param subject     the Subject context (WHO is emitting)
   * @param routingPipe the pipe that handles routing to subscribers
   * @param flow        optional transformations (null for pass-through)
   */
  public EmissionPipe(Circuit circuit, Subject<Channel<E>> subject, Pipe<Capture<E>> routingPipe, FlowRegulator<E> flow) {
    Objects.requireNonNull(circuit, "Circuit cannot be null");
    this.subject = Objects.requireNonNull(subject, "Subject cannot be null");
    Objects.requireNonNull(routingPipe, "Routing pipe cannot be null");
    this.flow = flow;

    // Chain: this → asyncPipe → routingPipe
    // Circuit.pipe() wraps routingPipe with async dispatch
    this.nextPipe = circuit.pipe(routingPipe);
  }

  @Override
  public void emit(E value) {
    // Apply Flow transformations if configured
    E emission = (flow != null) ? flow.apply(value) : value;

    // Filtered out by Flow (e.g., guard, diff, limit)
    if (emission == null) return;

    // Wrap with Subject context and emit to next pipe
    nextPipe.emit(new SubjectCapture<>(subject, emission));
  }

  @Override
  public void flush() {
    // No buffering - emissions pass through immediately
  }
}
