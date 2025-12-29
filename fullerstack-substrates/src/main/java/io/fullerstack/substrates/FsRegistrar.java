package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Registrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// A registrar that collects pipes during subscriber callback.
///
/// @param <E> the emission type
@Provided
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
  public void register(Pipe<? super E> pipe) {
    pipes.add(pipe::emit);
  }
}
