package io.fullerstack.substrates.subject;

import io.humainary.substrates.api.Substrates.Id;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.State;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Substrate;
import io.fullerstack.substrates.id.SequentialIdentifier;
import io.fullerstack.substrates.state.LinkedState;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * Contextual Subject implementation with parent, state, and type information.
 * <p>
 * < p >< b >Design Principles:</b >
 * < ul >
 * < li >Subjects form hierarchical trees via parent references</li >
 * < li >Circuit → Conduit → Channel hierarchy mirrors container relationships</li >
 * < li >Each Subject has: Id (unique), Name (label), State (data), Type (class), Parent (optional)</li >
 * < li >Subject.enclosure() returns parent Subject in hierarchy</li >
 * < li >Subject.path() walks hierarchy via enclosure(), showing all ancestors</li >
 * </ul >
 * <p>
 * < p >< b >Name vs Subject (William's Architecture):</b >
 * < ul >
 * < li >< b >Name</b > = Linguistic referent (like "Miles" the identifier)</li >
 * < li >< b >Subject</b > = Temporal/contextual instantiation (Miles-at-time-T-in-context-C)</li >
 * < li >Same Name can have multiple Subjects across different Circuits or contexts</li >
 * < li >Each Subject has a unique Id but shares the same Name reference</li >
 * </ul >
 * <p>
 * < p >< b >Example - Multiple Temporal Subjects:</b >
 * < pre >
 * Cortex cortex = Cortex.of();
 * Name milesName = cortex.name("Miles");  // Referent
 * <p>
 * // Circuit A creates a Subject for "Miles" (context A)
 * Subject&lt;?&gt; milesInCircuitA = ContextualSubject.builder()
 * .id(SequentialIdentifier.generate())
 * .name(milesName)                    // Same name reference
 * .state(cortex.state()
 * .set(cortex.slot("status", "online"))
 * .set(cortex.slot("circuit", "A")))
 * .type(Person.class)
 * .build();
 * <p>
 * // Circuit B creates a different Subject for "Miles" (context B)
 * Subject&lt;?&gt; milesInCircuitB = ContextualSubject.builder()
 * .id(SequentialIdentifier.generate())
 * .name(milesName)                    // Same name reference
 * .state(cortex.state()
 * .set(cortex.slot("status", "idle"))
 * .set(cortex.slot("circuit", "B")))
 * .type(Person.class)
 * .build();
 * <p>
 * // Same Name referent, different temporal/contextual instances:
 * // milesInCircuitA.id() != milesInCircuitB.id()  // Different IDs
 * // milesInCircuitA.name() == milesInCircuitB.name()  // Same Name
 * // milesInCircuitA.state() != milesInCircuitB.state()  // Different states
 * </pre >
 * <p>
 * < p >< b >Comparison with InternedName:</b >
 * < ul >
 * < li >InternedName: Hierarchical identifiers (strings)</li >
 * < li >ContextualSubject: Hierarchical runtime entities (identity + state)</li >
 * < li >Both use parent-child links for hierarchy</li >
 * < li >Both implement Extent interface with enclosure()</li >
 * </ul >
 *
 * @param < S > The substrate type this subject represents
 * @see Subject
 * @see InternedName
 * @see CellNode
 */
@EqualsAndHashCode
public class ContextualSubject < S extends Substrate < S > > implements Subject < S >, Comparable < Subject < ? > > {
  /**
   * Unique identifier for this subject.
   */
  private final Id id;

  /**
   * Hierarchical name (e.g., "circuit.conduit.channel").
   */
  private final Name name;

  /**
   * Associated state (may be null).
   */
  private final State state;

  /**
   * Subject type class (e.g., Channel.class, Circuit.class).
   */
  private final Class < S > type;

  /**
   * Parent subject in the hierarchy (nullable - root subjects have no parent).
   */
  private final Subject < ? > parent;

  /**
   * Creates a Subject node with auto-generated Id and State (no parent - root node).
   * <p>
   * Subject generates its own unique Id and empty State internally.
   */
  public ContextualSubject ( @NonNull Name name, @NonNull Class < S > type ) {
    this ( name, type, null );
  }

  /**
   * Creates a Subject node with auto-generated Id and State, with parent reference for hierarchy.
   * <p>
   * Subject generates its own unique Id and empty State internally.
   * <p>
   * This is the canonical constructor - all Subject creation flows through here.
   */
  public ContextualSubject ( @NonNull Name name, @NonNull Class < S > type, Subject < ? > parent ) {
    this.id = SequentialIdentifier.generate ();  // Subject creates its own Id
    this.name = name;
    this.state = LinkedState.empty ();  // Subject creates its own State
    this.type = type;
    this.parent = parent;
  }

  // Override Subject interface methods
  @Override
  public Id id () {
    return id;
  }

  @Override
  public Name name () {
    return name;
  }

  @Override
  public State state () {
    return state;
  }

  @Override
  public Class < S > type () {
    return type;
  }

  // Override Extent.enclosure() to return parent Subject
  @Override
  public java.util.Optional < Subject < ? > > enclosure () {
    return java.util.Optional.ofNullable ( parent );
  }

  // NOTE: Do NOT override part() - use Subject's default implementation
  // which formats as "Subject[name=...,type=...,id=...]"

  // Subject.toString() is abstract - must implement
  @Override
  public String toString () {
    // Return hierarchical path using "/" separator (Extent default)
    return path ().toString ();
  }

  // Implement Comparable for Subject ordering
  @Override
  public int compareTo ( Subject < ? > other ) {
    if ( other == null ) {
      return 1;
    }
    // Compare by name first
    int nameCompare = name ().toString ().compareTo ( other.name ().toString () );
    if ( nameCompare != 0 ) {
      return nameCompare;
    }
    // Then by ID
    return id ().toString ().compareTo ( other.id ().toString () );
  }
}
