package io.fullerstack.substrates.channel;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.pipe.ProducerPipe;
import io.fullerstack.substrates.flow.FlowRegulator;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.conduit.RoutingConduit;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generic implementation of Substrates.Channel interface.
 * <p>
 * < p >Provides a subject-based emission port that posts Scripts to Circuit's shared queue.
 * <p>
 * < p >< b >Parent Reference Pattern:</b >
 * Channel has a parent Conduit reference. When creating Pipes, Channel accesses:
 * < ul >
 * < li >Circuit via {@code conduit.getCircuit()}</li >
 * < li >Emission handler via {@code conduit.emissionHandler()}</li >
 * < li >Subscriber check via {@code conduit.hasSubscribers()}</li >
 * </ul >
 * <p>
 * < p >< b >Circuit Queue Architecture:</b >
 * Uses Circuit.pipe() to dispatch emissions to the Circuit's queue. This breaks
 * synchronous call chains and ensures all Conduits share the Circuit's single-threaded
 * execution model as specified in the Substrates API.
 * <p>
 * < p >< b >Subject Hierarchy:</b >
 * Channel's Subject has Conduit's Subject as parent, enabling full path navigation:
 * Circuit → Conduit → Channel via {@code subject.enclosure()}.
 * <p>
 * < p >< b >Flow Consumer Support:</b >
 * If a Flow Consumer is configured, this Channel creates Pipes with transformation pipelines
 * (Flows) applied. All Channels from the same Conduit share the same transformation
 * pipeline, as configured at the Conduit level.
 * <p>
 * < p >< b >@New Annotation Compliance:</b >
 * Per the Substrates API {@code @New} annotation, {@code pipe()} returns a NEW Pipe instance
 * on each call (not cached). However, when a Flow Consumer is configured at the Conduit level,
 * all Pipe instances share the SAME FlowRegulator to ensure Flow state (emission counters,
 * limit tracking, reduce accumulators, diff last values) is consistent across all emissions.
 * <p>
 * < p >Note: {@code pipe(Consumer< Flow >)} creates a new Pipe with a NEW FlowRegulator,
 * allowing different custom pipelines per call with independent state.
 *
 * @param < E > the emission type (e.g., MonitorSignal, ServiceSignal)
 */
public class EmissionChannel < E > implements Channel < E > {

  private final RoutingConduit < ?, E > conduit; // Parent Conduit in hierarchy (provides Circuit + Subject)
  private final Subject < Channel < E > >    channelSubject;
  private final Consumer < Flow < E > >      flowConfigurer; // Optional transformation pipeline (nullable)

  // Shared FlowRegulator instance - ensures Flow state (limits, accumulators, etc.) is shared
  // across multiple Pipe instances created by pipe()
  private volatile FlowRegulator < E > sharedFlow;

  /**
   * Creates a Channel with parent Conduit reference.
   *
   * @param channelName    simple channel name (e.g., "sensor1") - hierarchy implicit through container
   * @param conduit        parent Conduit (provides Circuit, scheduling, subscribers, and Subject hierarchy)
   * @param flowConfigurer optional transformation pipeline (null if no transformations)
   */
  @SuppressWarnings ( "unchecked" )
  public EmissionChannel ( Name channelName, RoutingConduit < ?, E > conduit, Consumer < Flow < E > > flowConfigurer ) {
    this.conduit = Objects.requireNonNull ( conduit, "Conduit cannot be null" );
    this.channelSubject = new ContextualSubject <> (
      channelName,  // Simple name - hierarchy implicit through Extent.enclosure()
      (Class < Channel < E > >) (Class < ? >) Channel.class,
      conduit.subject ()  // Parent Subject from parent Conduit
    );
    this.flowConfigurer = flowConfigurer; // Can be null
  }

  @Override
  public Subject < Channel < E > > subject () {
    return channelSubject;
  }

  @Override
  public Pipe < E > pipe () {
    // Per API @New annotation: return a new Pipe instance on each call
    // If Conduit has a Flow Consumer configured, use shared FlowRegulator for state
    if ( flowConfigurer != null ) {
      // Lazy-initialize shared FlowRegulator on first call
      if ( sharedFlow == null ) {
        synchronized ( this ) {
          if ( sharedFlow == null ) {
            sharedFlow = new FlowRegulator <> ();
            flowConfigurer.accept ( sharedFlow );
          }
        }
      }
      // Return new ProducerPipe instance with shared Flow state
      return new ProducerPipe < E > (
        conduit.getCircuit (),
        channelSubject,
        conduit.emissionHandler (),
        conduit::hasSubscribers,
        sharedFlow  // Shared Flow ensures state (limits, accumulators) is consistent
      );
    } else {
      // No Flow transformations - create plain ProducerPipe
      return new ProducerPipe < E > (
        conduit.getCircuit (),
        channelSubject,
        conduit.emissionHandler (),
        conduit::hasSubscribers
      );
    }
  }

  @Override
  public Pipe < E > pipe ( Consumer < Flow < E > > configurer ) {
    Objects.requireNonNull ( configurer, "Flow configurer cannot be null" );

    // Create a FlowRegulator and apply the Consumer transformations
    FlowRegulator < E > flow = new FlowRegulator <> ();
    configurer.accept ( flow );

    // Return a ProducerPipe with parent Conduit's capabilities and Flow transformations
    // Uses Circuit.pipe() for async dispatch (official Substrates API)
    return new ProducerPipe < E > (
      conduit.getCircuit (),           // Circuit directly (no cast needed)
      channelSubject,
      conduit.emissionHandler (),      // Emission handler from parent Conduit
      conduit::hasSubscribers,        // Subscriber check from parent Conduit
      flow
    );
  }
}
