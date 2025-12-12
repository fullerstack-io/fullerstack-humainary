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

  /// The emission consumer for routing emissions to subscribers.
  private final Consumer<E> router;

  /// Creates a new channel with the given subject and emission router.
  ///
  /// @param subject the subject identity for this channel
  /// @param router the consumer that routes emissions to subscribers
  FsChannel(Subject<Channel<E>> subject, Consumer<E> router) {
    this.subject = subject;
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
  /// Each call creates a new Pipe instance with a new Subject/Id (@New contract).
  ///
  /// @return A new pipe routing to this channel
  @Override
  public Pipe<E> pipe() {
    // Create a new Subject for this pipe (child of channel subject)
    FsSubject<Pipe<E>> pipeSubject = new FsSubject<>(subject.name(), (FsSubject<?>) subject, Pipe.class);
    return new FsConsumerPipe<>(pipeSubject, router);
  }

  /// Returns a new pipe with custom flow configuration.
  ///
  /// @param configurer A configurer responsible for configuring flow
  /// @return A new pipe instance with the configured flow
  @Override
  public Pipe<E> pipe(Configurer<Flow<E>> configurer) {
    // Create a new Subject for this pipe (child of channel subject)
    FsSubject<Pipe<E>> pipeSubject = new FsSubject<>(subject.name(), (FsSubject<?>) subject, Pipe.class);
    // Create base pipe that routes to this channel
    Pipe<E> basePipe = new FsConsumerPipe<>(pipeSubject, router);
    // Apply flow configuration
    FsFlow<E> flow = new FsFlow<>(pipeSubject, basePipe);
    configurer.configure(flow);
    return flow.pipe();
  }
}
