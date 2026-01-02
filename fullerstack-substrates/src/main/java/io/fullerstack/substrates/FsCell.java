package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;

import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Reservoir;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/// A hierarchical computational cell that receives input and emits output.
///
/// Cells form tree structures where emissions flow upward from children to parents.
/// Each cell can have child cells (via percept()), receive input (via receive()),
/// and emit output that flows to parent cells or subscribers.
///
/// Data flow for root cell:
/// 1. cell.receive(I) → ingressPipe.emit(I) → transforms to E
/// 2. E flows to channel → egressPipe processes E
/// 3. Final E reaches receptor
///
/// Data flow for child cells:
/// 1. child.receive(E) → ingress (identity) → E to channel
/// 2. channel → egress → parent's internal channel
/// 3. Parent's subscribers see the emission
///
/// @param <I> input type received by the cell
/// @param <E> emission type output by the cell
@Provided
public final class FsCell < I, E > implements Cell < I, E > {

  /// The subject identity for this cell.
  private final Subject < Cell < I, E > > subject;

  /// The circuit that owns this cell.
  private final FsCircuit circuit;

  /// Parent cell (null for root cells).
  private final FsCell < ?, E > parentCell;

  /// Child cells by name.
  private final Map < Name, FsCell < E, E > > children = new HashMap <> ();

  /// Subscribers to this cell's emissions.
  private final List < Subscriber < E > > subscribers = new ArrayList <> ();

  /// Subscriber-channel activation tracking.
  private final Set < String > subscriberChannelPairs = new HashSet <> ();

  /// Pipes registered by subscribers for each child channel.
  private final Map < Subscriber < E >, Map < Name, List < Consumer < E > > > > subscriberPipes = new IdentityHashMap <> ();

  /// The pipe that processes input (receives I, transforms to E).
  private final Pipe < I > ingressPipe;

  /// Async pipe for receive() - single cascade/enqueue, avoids submit() overhead.
  private final FsPipe < I > receivePipe;

  /// The receptor for final output.
  private final Receptor < ? super E > receptor;

  /// Internal channel for routing emissions to subscribers/parent.
  private final FsChannel < E > internalChannel;

  /// Creates a root cell with proper data flow pipeline.
  ///
  /// @param subject the cell's subject identity
  /// @param circuit the owning circuit
  /// @param ingress composer that creates Pipe<I> from Channel<E>
  /// @param egress composer that creates Pipe<E> from Channel<E>
  /// @param receptor final destination for E values
  public FsCell ( Subject < Cell < I, E > > subject, FsCircuit circuit, Composer < E, Pipe < I > > ingress,
                  Composer < E, Pipe < E > > egress, Receptor < ? super E > receptor ) {
    this.subject = subject;
    this.circuit = circuit;
    this.parentCell = null;
    this.receptor = receptor;

    // Create internal channel that routes to receptor and subscribers
    @SuppressWarnings ( "unchecked" )
    Subject < Channel < E > > channelSubject = (Subject < Channel < E > >) (Subject < ? >) subject;

    // The router consumer: delivers to receptor and notifies subscribers
    this.internalChannel = new FsChannel <> ( channelSubject, circuit, this::routeEmission );

    // Create egress pipe (processes E before final delivery)
    Pipe < E > egressPipe = egress.compose ( internalChannel );

    // Create a channel that delivers to egress (using egressPipe::emit as the
    // router)
    FsChannel < E > egressChannel = new FsChannel <> ( channelSubject, circuit, egressPipe::emit );

    // Create ingress pipe (receives I, transforms to E, sends to egress)
    this.ingressPipe = ingress.compose ( egressChannel );

    // Create receive pipe - single cascade/enqueue, no double cascade via submit()
    // Cast cell subject to pipe subject (same identity, different type param)
    @SuppressWarnings ( "unchecked" )
    Subject < Pipe < I > > pipeSubject = (Subject < Pipe < I > >) (Subject < ? >) subject;
    this.receivePipe = new FsPipe <> ( pipeSubject, circuit, ingressPipe::emit );
  }

  /// Creates a child cell that routes emissions to parent.
  ///
  /// Child cells have type Cell<E, E> - they receive the parent's output type
  /// and emit the same type upward.
  @SuppressWarnings ( "unchecked" )
  FsCell ( Subject < Cell < I, E > > subject, FsCircuit circuit, FsCell < ?, E > parentCell, Name childName ) {
    this.subject = subject;
    this.circuit = circuit;
    this.parentCell = parentCell;
    this.receptor = Receptor.of (); // Children route to parent, not receptor

    // Create internal channel that routes to parent
    Subject < Channel < E > > channelSubject = (Subject < Channel < E > >) (Subject < ? >) subject;

    // Child's router consumer sends to parent's internal handling
    Consumer < E > childRouter = e -> parentCell.handleChildEmission ( childName, e );

    this.internalChannel = new FsChannel <> ( channelSubject, circuit, childRouter );

    // For children, ingress is a pipe that routes to the channel
    // Child cells have I=E, so cast subject to Pipe<E> and then to Pipe<I>
    Subject < Pipe < E > > childPipeSubjectE = (Subject < Pipe < E > >) (Subject < ? >) subject;
    this.ingressPipe = (Pipe < I >) (Pipe < ? >) new FsPipe <> ( childPipeSubjectE, circuit, childRouter );

    // Create receive pipe - single cascade/enqueue, no double cascade via submit()
    Subject < Pipe < I > > childPipeSubjectI = (Subject < Pipe < I > >) (Subject < ? >) subject;
    this.receivePipe = new FsPipe <> ( childPipeSubjectI, circuit, ingressPipe::emit );
  }

  /// Routes an emission to receptor and subscribers.
  private void routeEmission ( E emission ) {
    // Deliver to receptor
    receptor.receive ( emission );
  }

  /// Handles emission from a child cell (used for parent subscribers).
  void handleChildEmission ( Name childName, E emission ) {
    // Activate subscribers for this child if first time
    Subject < Channel < E > > channelSubject = internalChannel.subject ();

    for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
      String key = System.identityHashCode ( subscriber ) + ":" + childName;
      if ( subscriberChannelPairs.add ( key ) ) {
        // First time this subscriber sees this child - activate
        FsRegistrar < E > registrar = new FsRegistrar <> ();
        ( (FsSubscriber < E >) subscriber ).activate ( channelSubject, registrar );
        subscriberPipes.computeIfAbsent ( subscriber, k -> new HashMap <> () )
          .computeIfAbsent ( childName, k -> new ArrayList <> () ).addAll ( registrar.pipes () );
      }
    }

    // Deliver to all subscriber pipes for this child
    for ( Subscriber < E > subscriber : new ArrayList <> ( subscribers ) ) {
      Map < Name, List < Consumer < E > > > pipes = subscriberPipes.get ( subscriber );
      if ( pipes != null ) {
        List < Consumer < E > > childPipes = pipes.get ( childName );
        if ( childPipes != null ) {
          for ( Consumer < E > pipe : childPipes ) {
            pipe.accept ( emission );
          }
        }
      }
    }

    // Also route upward to parent if this cell has a parent
    if ( parentCell != null ) {
      parentCell.handleChildEmission ( subject.name (), emission );
    }

    // Also deliver to our own receptor
    receptor.receive ( emission );
  }

  @Override
  public Subject < Cell < I, E > > subject () {
    return subject;
  }

  @Override
  public String part () {
    return subject.name ().toString ();
  }

  @Override
  public Optional < Cell < I, E > > enclosure () {
    // Cast the parent to the expected type
    @SuppressWarnings ( "unchecked" )
    Cell < I, E > p = (Cell < I, E >) (Cell < ?, ? >) parentCell;
    return Optional.ofNullable ( p );
  }

  @Override
  public void receive ( I emission ) {
    // Route through async pipe - single cascade/enqueue, no submit() overhead
    receivePipe.emit ( emission );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Cell < I, E > percept ( @NotNull Name name ) {
    // Child cells have type Cell<E, E> - parent's output becomes child's
    // input/output
    return (Cell < I, E >) (Cell < ?, ? >) children.computeIfAbsent ( name, n -> {
      FsSubject < Cell < E, E > > childSubject = new FsSubject <> ( n, (FsSubject < ? >) subject, Cell.class );
      return new FsCell < E, E > ( childSubject, circuit, this, n );
    } );
  }

  @New
  @NotNull
  @Override
  public Subscription subscribe ( @NotNull Subscriber < E > subscriber ) {
    subscribers.add ( subscriber );
    FsSubject < Subscription > subSubject = new FsSubject <> ( subscriber.subject ().name (), (FsSubject < ? >) subject,
      Subscription.class );
    return new FsSubscription ( subSubject, () -> {
      subscribers.remove ( subscriber );
      subscriberPipes.remove ( subscriber );
      subscriberChannelPairs.removeIf ( key -> key.startsWith ( System.identityHashCode ( subscriber ) + ":" ) );
    } );
  }

  @New
  @NotNull
  @Override
  public Reservoir < E > reservoir () {
    FsSubject < Reservoir < E > > resSubject = new FsSubject <> ( cortex ().name ( "reservoir" ), (FsSubject < ? >) subject,
      Reservoir.class );
    FsReservoir < E > reservoir = new FsReservoir <> ( resSubject );
    // Subscribe reservoir to capture emissions
    Subscriber < E > sub = new FsSubscriber <> (
      new FsSubject <> ( cortex ().name ( "reservoir.subscriber" ), resSubject, Subscriber.class ), ( channelSubject,
                                                                                                      registrar ) -> registrar.register (
      emission -> reservoir.capture ( emission, channelSubject ) ) );
    subscribe ( sub );
    return reservoir;
  }
}
