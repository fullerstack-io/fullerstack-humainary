package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A registrar that collects pipes during subscriber callback.
///
/// @param <E> the emission type
public final class FsRegistrar<E> implements Registrar<E> {

  /// Pipes registered by the subscriber.
  private final List<Consumer<E>> pipes = new ArrayList<>();

  @Override
  public void register(Receptor<? super E> receptor) {
    // Store receptor directly as Consumer to minimize indirection
    pipes.add(receptor::receive);
  }

  /// Returns the registered pipes.
  public List<Consumer<E>> pipes() {
    return pipes;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void register(Pipe<? super E> pipe) {
    // If pipe is FsPipe, use terminal receiver to avoid double-enqueue
    if (pipe instanceof FsPipe<?> fsPipe) {
      pipes.add((Consumer<E>) fsPipe.terminalReceiver());
    } else {
      pipes.add(pipe::emit);
    }
  }
}
