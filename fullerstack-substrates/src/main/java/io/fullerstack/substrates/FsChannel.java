package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
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
@Provided
final class FsChannel < E > implements Channel < E > {

  /// The subject identity for this channel.
  private final Subject < Channel < E > > subject;

  /// The circuit that owns this channel.
  private final FsCircuit circuit;

  /// The emission consumer for routing emissions to subscribers.
  private final Consumer < E > router;

  /// Cached pipe subject - all pipes from this channel share the same identity.
  /// Lazy initialized on first pipe() call.
  private volatile Subject < Pipe < E > > cachedPipeSubject;

  /// Creates a new channel with the given subject and emission router.
  ///
  /// @param subject the subject identity for this channel
  /// @param circuit the circuit that owns this channel
  /// @param router the consumer that routes emissions to subscribers
  FsChannel ( Subject < Channel < E > > subject, FsCircuit circuit, Consumer < E > router ) {
    this.subject = subject;
    this.circuit = circuit;
    this.router = router;
  }

  /// Returns the subject identity of this channel.
  ///
  /// @return the subject of this channel
  @Override
  public Subject < Channel < E > > subject () {
    return subject;
  }

  /// Returns the cached pipe subject, creating it lazily if needed.
  /// All pipes from this channel share the same subject identity.
  private Subject < Pipe < E > > pipeSubject () {
    Subject < Pipe < E > > cached = cachedPipeSubject;
    if ( cached == null ) {
      cached = new FsSubject <> ( subject.name (), (FsSubject < ? >) subject, Pipe.class );
      cachedPipeSubject = cached;
    }
    return cached;
  }

  /// Returns a new pipe for emitting to this channel.
  /// The pipe shares the channel's pipe subject identity (all pipes from same
  /// channel have same subject - they represent the same emission endpoint).
  ///
  /// The returned pipe enqueues emissions to the circuit. On the circuit thread,
  /// emissions pass through the conduit's flow pipeline before reaching this
  /// channel,
  /// which then dispatches to all registered subscribers.
  ///
  /// @return A new pipe routing to this channel
  @New
  @NotNull
  @Override
  public Pipe < E > pipe () {
    return new FsPipe <> ( pipeSubject (), circuit, router );
  }

  /// Returns a new pipe with custom flow configuration.
  /// The pipe shares the channel's pipe subject identity.
  ///
  /// @param configurer A configurer responsible for configuring flow
  /// @return A new pipe instance with the configured flow
  @New
  @NotNull
  @Override
  public Pipe < E > pipe ( @NotNull Configurer < Flow < E > > configurer ) {
    Subject < Pipe < E > > ps = pipeSubject ();
    Pipe < E > basePipe = new FsPipe <> ( ps, circuit, router );
    FsFlow < E > flow = new FsFlow <> ( ps, circuit, basePipe );
    configurer.configure ( flow );
    return flow.pipe ();
  }
}
