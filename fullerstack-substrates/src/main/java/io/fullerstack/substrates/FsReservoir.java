package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Capture;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// An in-memory buffer of captures that is also a Substrate.
///
/// A Reservoir is created from a Source and is given its own identity (Subject)
/// that is a child of the Source it was created from. It subscribes to its
/// source to capture all emissions into an internal buffer.
///
/// @param <E> the class type of the emitted value
/// @see Capture
public final class FsReservoir < E >
  implements Reservoir < E > {

  /// A capture of an emitted value from a channel with its associated subject.
  private record Cap < E > (
    E emission,
    Subject < Channel < E > > subject
  ) implements Capture < E > {}

  /// The subject identity for this reservoir.
  private final Subject < Reservoir < E > > subject;

  /// Internal buffer storing captured emissions.
  private final List < Cap < E > > buffer = new ArrayList <> ();

  /// Creates a new reservoir with the given subject identity.
  ///
  /// @param subject the subject identity for this reservoir
  public FsReservoir ( Subject < Reservoir < E > > subject ) {
    this.subject = subject;
  }

  /// Returns the subject identity of this reservoir.
  ///
  /// @return the subject of this reservoir
  @Override
  public Subject < Reservoir < E > > subject () {
    return subject;
  }

  /// Returns a stream representing the events that have accumulated since the
  /// reservoir was created or the last call to this method.
  ///
  /// @return A stream consisting of stored events captured from channels.
  /// @see Capture
  public Stream < Capture < E > > drain () {
    // Optimized: use buffer snapshot instead of toArray allocation
    List < Cap < E > > snapshot = new ArrayList <> ( buffer );
    buffer.clear ();
    return snapshot.stream ().map ( c -> c );
  }

  /// Captures an emission with its channel subject.
  void capture ( E emission, Subject < Channel < E > > channelSubject ) {
    buffer.add ( new Cap <> ( emission, channelSubject ) );
  }

  /// Closes this reservoir, releasing the captured emissions buffer.
  @Override
  public void close () {
    buffer.clear ();
  }

}
