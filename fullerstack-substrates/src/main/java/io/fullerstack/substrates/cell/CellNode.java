package io.fullerstack.substrates.cell;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.fullerstack.substrates.channel.EmissionChannel;
import io.fullerstack.substrates.conduit.RoutingConduit;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Channel;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Subscriber;
import io.humainary.substrates.api.Substrates.Subscription;

/**
 * Hierarchical Cell node implementation for Circuit.cell() support (PREVIEW API).
 * <p>
 * < p >Cell< I, E > provides bidirectional signal transformation:
 * < ul >
 * < li >Accepts input type I via emit() - delegates to input Pipe< I ></li >
 * < li >Emits type E to output Pipe< E > (PREVIEW API)</li >
 * < li >Supports hierarchical children via get(Name)</li >
 * </ul >
 * <p>
 * < p >< b >PREVIEW API Design:</b >
 * - Input: Pipe< I > created by Composer (receives I values)
 * - Output: Pipe< E > provided by caller (emits E values)
 * - The Conduit provides the Source< E > subscription infrastructure
 * - Implements Pipe< I > by delegating to input pipe
 * - Implements Source< E > by delegating to conduit
 * - Implements Extent for hierarchy (get, enclosure)
 *
 * @param < I > input type (what Cell receives)
 * @param < E > emission type (what Cell emits to output pipe)
 */
public class CellNode < I, E > implements Cell < I, E > {

  private final CellNode < I, E >           parent;
  private final String                      segment;
  private final Pipe < I >                  inputPipe;                 // Input: created by Composer
  private final Pipe < E >                  outputPipe;                // Output: provided by M18 API
  private final Conduit < ?, E >            conduit;                   // Provides subscription infrastructure (type E emissions)
  private final Composer < E, Pipe < I > >  ingressComposer;           // Transforms Channel<E> -> Pipe<I>
  private final Composer < E, Pipe < E > >  egressComposer;            // Transforms Channel<E> -> Pipe<E>
  private final Subject                     subject;
  private final Map < Name, Cell < I, E > > children = new HashMap <> ();

  /**
   * Creates a new Cell with input and output pipes and composers (API).
   *
   * @param parent          parent Cell (null for root)
   * @param name            Cell name
   * @param inputPipe       Pipe created by applying ingress composer (receives I values)
   * @param outputPipe      Pipe for emitting E values
   * @param conduit         Conduit providing subscription infrastructure (emits type E)
   * @param ingressComposer Composer to transform Channel<E> -> Pipe<I> for children
   * @param egressComposer  Composer to transform Channel<E> -> Pipe<E> for children
   * @param parentSubject   parent Subject for hierarchy (null for root)
   */
  public CellNode (
    CellNode < I, E > parent,
    Name name,
    Pipe < I > inputPipe,
    Pipe < E > outputPipe,
    Conduit < ?, E > conduit,
    Composer < E, Pipe < I > > ingressComposer,
    Composer < E, Pipe < E > > egressComposer,
    Subject < ? > parentSubject
  ) {
    this.parent = parent;
    this.segment = name.part ().toString ();
    this.inputPipe = Objects.requireNonNull ( inputPipe, "inputPipe cannot be null" );
    this.outputPipe = Objects.requireNonNull ( outputPipe, "outputPipe cannot be null" );
    this.conduit = Objects.requireNonNull ( conduit, "conduit cannot be null" );
    this.ingressComposer = Objects.requireNonNull ( ingressComposer, "ingressComposer cannot be null" );
    this.egressComposer = Objects.requireNonNull ( egressComposer, "egressComposer cannot be null" );
    this.subject = new ContextualSubject <> (
      name,
      Cell.class,
      parentSubject
    );
  }

  // ========== Pipe< I > implementation (PREVIEW API: Cell extends Pipe<I>) ==========

  @Override
  public void emit ( I emission ) {
    // Delegate to the input pipe
    inputPipe.emit ( emission );
  }

  @Override
  public void flush () {
    // Delegate to the input pipe
    inputPipe.flush ();
  }

  // ========== Source< E > implementation (output) ==========

  @Override
  public Subscription subscribe ( Subscriber < E > subscriber ) {
    // Delegate to the conduit's subscription mechanism
    return conduit.subscribe ( subscriber );
  }

  // ========== Extent implementation (hierarchy) ==========

  @Override
  public CharSequence part () {
    return segment;
  }

  @Override
  public Optional < Cell < I, E > > enclosure () {
    return Optional.ofNullable ( parent );
  }

  @Override
  public Cell < I, E > percept ( Name name ) {
    return children.computeIfAbsent ( name, n -> {
      // RC7 Pattern: Create Channel directly like Circuit.cell() does
      // Conduit.percept() returns Percept (Pipe), not Channel
      // We need to create Channel and pass it to composers
      RoutingConduit < ?, E > transformingConduit =
        (RoutingConduit < ?, E >) conduit;
      Channel < E > childChannel = new EmissionChannel <> ( n, transformingConduit, null );

      // Apply ingress composer: Channel<E> -> Pipe<I>
      Pipe < I > childInputPipe = ingressComposer.compose ( childChannel );

      // Apply egress composer: Channel<E> -> Pipe<E>
      Pipe < E > childOutputPipe = egressComposer.compose ( childChannel );

      // Create child cell with same composers (all children use same transformation logic)
      return new CellNode <> ( this, n, childInputPipe, childOutputPipe, conduit, ingressComposer, egressComposer, this.subject );
    } );
  }

  // ========== Container implementation ==========

  @Override
  public Subject subject () {
    return subject;
  }

  // ========== Object overrides ==========

  @Override
  public String toString () {
    return path ().toString ();
  }

  @Override
  public int hashCode () {
    return path ().hashCode ();
  }

  @Override
  public boolean equals ( Object o ) {
    if ( this == o ) return true;
    if ( !( o instanceof Cell ) ) return false;
    Cell < ?, ? > other = (Cell < ?, ? >) o;
    return path ().equals ( other.path () );
  }
}
