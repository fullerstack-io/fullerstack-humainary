package io.fullerstack.substrates.channel;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.pipe.EmissionPipe;
import io.fullerstack.substrates.flow.FlowRegulator;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.conduit.RoutingConduit;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Channel creates EmissionPipes that chain to the Conduit's routing pipe.
 * <p>
 * Pipe chain: EmissionPipe → asyncPipe → routingPipe → subscriberPipes
 * <p>
 * Channel's job is simple: create EmissionPipes bound to this channel's Subject.
 *
 * @param <E> the emission type
 */
public class EmissionChannel<E> implements Channel<E> {

  private final RoutingConduit<?, E> conduit;
  private final Subject<Channel<E>> subject;
  private final Consumer<Flow<E>> flowConfigurer;

  // Shared FlowRegulator for all pipes from this channel (ensures consistent state)
  private volatile FlowRegulator<E> sharedFlow;

  /**
   * Creates a Channel bound to a Conduit.
   *
   * @param name           channel name
   * @param conduit        parent Conduit (provides Circuit and routing pipe)
   * @param flowConfigurer optional Flow configuration (null for pass-through)
   */
  @SuppressWarnings("unchecked")
  public EmissionChannel(Name name, RoutingConduit<?, E> conduit, Consumer<Flow<E>> flowConfigurer) {
    this.conduit = Objects.requireNonNull(conduit, "Conduit cannot be null");
    this.subject = new ContextualSubject<>(
      name,
      (Class<Channel<E>>) (Class<?>) Channel.class,
      conduit.subject()
    );
    this.flowConfigurer = flowConfigurer;
  }

  @Override
  public Subject<Channel<E>> subject() {
    return subject;
  }

  @Override
  public Pipe<E> pipe() {
    // Create pipe chain: EmissionPipe → asyncPipe → routingPipe
    if (flowConfigurer != null) {
      ensureSharedFlowInitialized();
      return new EmissionPipe<>(
        conduit.getCircuit(),
        subject,
        conduit.routingPipe(),
        sharedFlow
      );
    } else {
      return new EmissionPipe<>(
        conduit.getCircuit(),
        subject,
        conduit.routingPipe()
      );
    }
  }

  @Override
  public Pipe<E> pipe(Consumer<Flow<E>> configurer) {
    Objects.requireNonNull(configurer, "Flow configurer cannot be null");

    // Create a new FlowRegulator for this pipe (independent state)
    FlowRegulator<E> flow = new FlowRegulator<>();
    configurer.accept(flow);

    // Create pipe chain with custom Flow
    return new EmissionPipe<>(
      conduit.getCircuit(),
      subject,
      conduit.routingPipe(),
      flow
    );
  }

  private void ensureSharedFlowInitialized() {
    if (sharedFlow == null) {
      synchronized (this) {
        if (sharedFlow == null) {
          sharedFlow = new FlowRegulator<>();
          flowConfigurer.accept(sharedFlow);
        }
      }
    }
  }
}
