package io.fullerstack.substrates.pipe;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.fullerstack.substrates.capture.SubjectCapture;
import io.fullerstack.substrates.flow.FlowRegulator;
import io.humainary.substrates.api.Substrates.Capture;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;

/**
 * Producer-side pipe that emits values INTO the conduit system.
 * <p>
 * < p >< b >Role in Architecture:</b >
 * ProducerPipe represents the producer endpoint in the Producer-Consumer pattern.
 * It emits values INTO the conduit system, which then routes them to registered
 * subscriber pipes (lambdas/Observers) via subscriber notifications.
 * <p>
 * < p >< b >Creation:</b >
 * Created by Channels when application code calls {@code conduit.get(name)} or
 * {@code channel.pipe()}. Each ProducerPipe is bound to a specific Channel's Subject,
 * preserving WHO emitted for subscriber routing.
 * <p>
 * < p >< b >Circuit Queue Architecture:</b >
 * Instead of putting Captures directly on a BlockingQueue, ProducerPipes post Scripts to the
 * Circuit's Queue. Each Script creates a Capture and invokes the subscriber notification callback.
 * This ensures all Conduits share the Circuit's single-threaded execution model
 * ("Virtual CPU Core" design principle).
 * <p>
 * < p >< b >Transformation Support:</b >
 * When created without a Flow, emissions pass through directly.
 * When created with a Flow, emissions are transformed (filtered, mapped, reduced, etc.)
 * before being posted as Scripts. Transformations execute on the emitting thread,
 * minimizing work in the Circuit's single-threaded queue processor.
 * <p>
 * < p >< b >Subject Propagation:</b > Each ProducerPipe knows its Channel's Subject, which is paired
 * with the emission value in a Capture within the Script. This preserves the context
 * of WHO emitted for delivery to subscriber pipes via Subscribers.
 * <p>
 * < p >< b >Subscriber Notification:</b > Holds a subscriber notification callback provided by the Conduit
 * (via Channel at construction time). This callback routes emissions to the Conduit's registered
 * subscribers, who then dispatch to their registered pipes (lambdas/Observers).
 *
 * @param < E > the emission type (e.g., MonitorSignal, ServiceSignal)
 * @see Pipe
 */
public class ProducerPipe < E > implements Pipe < E > {

  private final Pipe < Capture < E > >     dispatchPipe; // Async pipe created by Circuit.pipe() for dispatch
  private final Subject < Channel < E > >  channelSubject; // WHO this pipe belongs to
  private final BooleanSupplier            hasSubscribers; // Check for early subscriber optimization
  private final FlowRegulator < E >        flow; // FlowRegulator for apply() and transformation

  /**
   * Creates a ProducerPipe without transformations.
   *
   * @param circuit            the Circuit for async dispatch (uses Circuit.pipe())
   * @param channelSubject     the Subject of the Channel this ProducerPipe belongs to
   * @param subscriberNotifier callback to notify subscribers of emissions
   * @param hasSubscribers     subscriber check for early-exit optimization
   */
  public ProducerPipe ( Circuit circuit, Subject < Channel < E > > channelSubject, Consumer < Capture < E > > subscriberNotifier, BooleanSupplier hasSubscribers ) {
    this ( circuit, channelSubject, subscriberNotifier, hasSubscribers, null );
  }

  /**
   * Creates a ProducerPipe with transformations defined by a Flow.
   *
   * @param circuit            the Circuit for async dispatch (uses Circuit.pipe())
   * @param channelSubject     the Subject of the Channel this ProducerPipe belongs to
   * @param subscriberNotifier callback to notify subscribers of emissions
   * @param hasSubscribers     subscriber check for early-exit optimization
   * @param flow               the flow regulator (null for no transformations)
   */
  public ProducerPipe ( Circuit circuit, Subject < Channel < E > > channelSubject, Consumer < Capture < E > > subscriberNotifier, BooleanSupplier hasSubscribers, FlowRegulator < E > flow ) {
    this.channelSubject = Objects.requireNonNull ( channelSubject, "Channel subject cannot be null" );
    Objects.requireNonNull ( subscriberNotifier, "Subscriber notifier cannot be null" );
    this.hasSubscribers = Objects.requireNonNull ( hasSubscribers, "Subscriber check cannot be null" );
    this.flow = flow;

    // Create async dispatch pipe using Circuit.pipe() - official API method
    // This breaks synchronous call chains and ensures circuit-thread execution
    Objects.requireNonNull ( circuit, "Circuit cannot be null" );

    // Create a Pipe wrapper for the subscriber notifier, then wrap with Circuit.pipe()
    Pipe < Capture < E > > receptorPipe = new Pipe < Capture < E > > () {
      @Override
      public void emit ( Capture < E > emission ) {
        subscriberNotifier.accept ( emission );
      }

      @Override
      public void flush () {
        // No-op: no buffering
      }
    };
    this.dispatchPipe = circuit.pipe ( receptorPipe );
  }

  @Override
  public void emit ( E value ) {
    if ( flow == null ) {
      // No transformations - post Script directly
      postScript ( value );
    } else {
      // Apply transformations before posting
      E transformed = flow.apply ( value );
      if ( transformed != null ) {
        // Transformation passed - post Script with result
        postScript ( transformed );
      }
      // If null, emission was filtered out (by guard, diff, limit, etc.)
    }
  }

  /**
   * Flushes any buffered emissions.
   * <p>
   * < p >ProducerPipe has no buffering - emissions are posted immediately to the Circuit's queue.
   * This is a no-op implementation as required by Pipe interface.
   */
  @Override
  public void flush () {
    // No-op: ProducerPipe has no buffering - emissions posted immediately
  }

  /**
   * Posts emission to Circuit's queue for ordered processing.
   * <p>
   * < p >< b >Architecture:</b > Uses Circuit.pipe() to dispatch captures to the Circuit's queue.
   * The async pipe ensures subscriber callbacks execute on the Circuit's single-threaded
   * execution context, maintaining ordering guarantees as specified by the Substrates API.
   * <p>
   * < p >< b >OPTIMIZATION:</b > Early exit if no subscribers - avoids allocating Capture
   * and posting to queue when no subscribers are registered.
   *
   * @param value the emission value (after transformations, if any)
   */
  private void postScript ( E value ) {
    // OPTIMIZATION: Early exit if no subscribers
    // Avoids: Capture allocation + queue posting when nothing is listening
    if ( !hasSubscribers.getAsBoolean () ) {
      return;
    }

    // Dispatch to Circuit via async pipe (official API) - ensures ordering guarantees
    Capture < E > capture = new SubjectCapture <> ( channelSubject, value );
    dispatchPipe.emit ( capture );
  }

}
