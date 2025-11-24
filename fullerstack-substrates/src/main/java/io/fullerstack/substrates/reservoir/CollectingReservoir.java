package io.fullerstack.substrates.reservoir;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.capture.SubjectCapture;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.name.InternedName;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Implementation of Substrates.Reservoir for buffering and draining emissions.
 * <p>
 * < p >Reservoir accumulates Capture events from a Source and provides them via drain().
 * Each call to drain() returns accumulated events since the last drain (or creation)
 * and clears the buffer.
 * <p>
 * < p >Thread-safe implementation using CopyOnWriteArrayList for concurrent access.
 *
 * @param < E > the emission type
 * @see Reservoir
 * @see Source
 * @see Capture
 */
public class CollectingReservoir < E > implements Reservoir < E > {

  private final    Subject < Reservoir < E > > reservoirSubject;
  private final    Source < E, ? >        source;
  private final    List < Capture < E > > buffer = new CopyOnWriteArrayList <> ();
  private final    Subscription           subscription;
  private volatile boolean                closed = false;

  // Cache the internal subscriber's Subject - represents persistent identity
  private final Subject < Subscriber < E > > internalSubscriberSubject;

  /**
   * Creates a Reservoir that subscribes to the given Source.
   *
   * @param source the source to subscribe to
   * @throws NullPointerException if source is null
   */
  @SuppressWarnings ( "unchecked" )
  public CollectingReservoir ( Source < E, ? > source ) {
    Objects.requireNonNull ( source, "Source cannot be null" );

    // Using InternedName.of() static factory
    this.source = source;
    this.reservoirSubject = new ContextualSubject <> (
      InternedName.of ( "reservoir" ),
      (Class < Reservoir < E > >) (Class < ? >) Reservoir.class
    );

    // Create internal subscriber's Subject once
    this.internalSubscriberSubject = new ContextualSubject <> (
      InternedName.of ( "sink-subscriber" ),
      (Class < Subscriber < E > >) (Class < ? >) Subscriber.class
    );

    // Subscribe to source and buffer all emissions
    //  Use ContextSubscriber with callback
    this.subscription = source.subscribe (
      new io.fullerstack.substrates.subscriber.ContextSubscriber < E > (
        InternedName.of ( "sink-subscriber" ),
        ( subject, registrar ) -> {
          // Register a pipe that captures emissions into the buffer
          registrar.register ( emission -> {
            if ( !closed ) {
              buffer.add ( new SubjectCapture <> ( subject, emission ) );
            }
          } );
        }
      )
    );
  }

  @Override
  public Subject subject () {
    return reservoirSubject;
  }

  @Override
  public Stream < Capture < E > > drain () {
    // Get all accumulated captures and clear the buffer
    List < Capture < E > > captured = List.copyOf ( buffer );
    buffer.clear ();
    return captured.stream ();
  }

  @Override
  public void close () {
    if ( !closed ) {
      closed = true;
      subscription.close ();
      buffer.clear ();
    }
  }
}
