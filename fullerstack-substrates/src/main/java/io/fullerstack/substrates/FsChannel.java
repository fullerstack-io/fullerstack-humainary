package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.function.Consumer;

/// A named port in a conduit that provides a pipe for emission.
///
/// Channels serve as named entry points into a conduit's processing pipeline.
/// Each channel has a unique Subject with an associated Name, and emissions
/// to the channel are routed through the conduit's Flow pipeline to registered
/// subscribers.
///
/// @param <E> the class type of emitted value
/// @see FsConduit
final class FsChannel<E> implements Channel<E> {

  /// The subject identity for this channel.
  private final Subject<Channel<E>> subject;

  /// The circuit that owns this channel.
  private final FsInternalCircuit circuit;

  /// The emission consumer for routing emissions to subscribers.
  private final Consumer<E> router;

  /// Creates a new channel with the given subject and emission router.
  ///
  /// @param subject the subject identity for this channel
  /// @param circuit the circuit that owns this channel
  /// @param router the consumer that routes emissions to subscribers
  FsChannel(Subject<Channel<E>> subject, FsInternalCircuit circuit, Consumer<E> router) {
    this.subject = subject;
    this.circuit = circuit;
    this.router = router;
  }

  /// Returns the subject identity of this channel.
  ///
  /// @return the subject of this channel
  @Override
  public Subject<Channel<E>> subject() {
    return subject;
  }

  /// Returns a new pipe for emitting to this channel.
  /// The pipe has its own subject with the channel as parent.
  ///
  /// @return A new pipe routing to this channel
  @Override
  public Pipe<E> pipe() {
    // Create pipe subject with channel as parent (same name, channel as parent)
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(
        subject.name(), (FsSubject<?>) subject, Pipe.class);
    return new FsPipe<>(pipeSubject, circuit, router);
  }

  /// Returns a new pipe with custom flow configuration.
  /// The pipe has its own subject with the channel as parent.
  ///
  /// @param configurer A configurer responsible for configuring flow
  /// @return A new pipe instance with the configured flow
  @Override
  public Pipe<E> pipe(Configurer<Flow<E>> configurer) {
    // Create pipe subject with channel as parent (same name, channel as parent)
    Subject<Pipe<E>> pipeSubject = new FsSubject<>(
        subject.name(), (FsSubject<?>) subject, Pipe.class);
    Pipe<E> basePipe = new FsPipe<>(pipeSubject, circuit, router);
    FsFlow<E> flow = new FsFlow<>(pipeSubject, circuit, basePipe);
    configurer.configure(flow);
    return flow.pipe();
  }
}
