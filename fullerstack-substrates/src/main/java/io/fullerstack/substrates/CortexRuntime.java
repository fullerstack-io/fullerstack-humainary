package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.capture.SubjectCapture;
import io.fullerstack.substrates.circuit.SequentialCircuit;
import io.fullerstack.substrates.current.ThreadCurrent;
import io.fullerstack.substrates.scope.ManagedScope;
import io.fullerstack.substrates.slot.TypedSlot;
import io.fullerstack.substrates.state.LinkedState;
import io.fullerstack.substrates.subject.ContextualSubject;
import io.fullerstack.substrates.subscriber.ContextSubscriber;
import io.fullerstack.substrates.name.InternedName;
import io.fullerstack.substrates.reservoir.CollectingReservoir;

import java.util.concurrent.ConcurrentHashMap;

import java.lang.reflect.Member;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Complete implementation of Substrates.Cortex interface.
 * <p>
 * This class implements ALL methods of the Cortex interface,
 * providing full Humainary Substrates API compliance.
 * <p>
 * Methods include:
 * <ul>
 * <li>Circuit management (2 methods)</li>
 * <li>Name factory (8 methods)</li>
 * <li>Scope management (2 methods)</li>
 * <li>State factory (1 method)</li>
 * <li>Reservoir creation (1 method)</li>
 * <li>Slot management (9 methods)</li>
 * <li>Subscriber management (1 method)</li>
 * <li>Pipe factory (5 methods)</li>
 * <li>Current management (1 method)</li>
 * </ul>
 * <p>
 * This class is instantiated by CortexRuntimeProvider via the SPI mechanism.
 *
 * @see Cortex
 * @see CortexRuntimeProvider
 */
public class CortexRuntime implements Cortex {

  private final Map < Name, Scope > scopes;
  private final Subject < Cortex > subject;

  /**
   * Package-private constructor for SPI instantiation.
   * Only CortexRuntimeProvider should instantiate this class.
   */
  CortexRuntime () {
    this.scopes = new ConcurrentHashMap <> ();
    // Subject generates its own unique Id and State
    this.subject = new ContextualSubject < Cortex > (
      InternedName.of ( "cortex" ),  // Name
      Cortex.class
    );
  }

  @Override
  public Subject < Cortex > subject () {
    return subject;
  }

  // ========== Circuit Management (2 methods) ==========

  @Override
  public Circuit circuit () {
    // Generate unique name for each unnamed circuit (do NOT intern)
    // This ensures each call creates a new circuit with unique subject
    // Use System.nanoTime() for unique names
    return createCircuit ( InternedName.of ( "circuit." + System.nanoTime () ) );
  }

  @Override
  public Circuit circuit ( Name name ) {
    Objects.requireNonNull ( name, "Circuit name cannot be null" );
    // Create a new circuit each time instead of caching
    // This prevents issues where a closed circuit is reused
    return createCircuit ( name );
  }

  private Circuit createCircuit ( Name name ) {
    // Pass Cortex Subject as parent for Circuit hierarchy: Circuit → Cortex
    return new SequentialCircuit ( name, this.subject );
  }

  // ========== Current Management (1 method) ==========

  @Override
  public Current current () {
    // Return Current for the calling thread
    // Analogous to Thread.currentThread() in Java
    return ThreadCurrent.of ();
  }

  // ========== Pipe Factory (5 methods) ==========

  @Override
  public Pipe < Object > pipe () {
    // Factory method for creating a simple no-op pipe (Object type)
    // addition - returns a sink pipe that discards emissions
    return new Pipe < Object > () {
      @Override
      public void emit ( Object value ) {
        // No-op: discard emissions
      }

      @Override
      public void flush () {
        // No-op: no buffering
      }
    };
  }

  @Override
  public < E > Pipe < E > pipe ( Class < E > type ) {
    // Factory method for creating a typed no-op pipe
    return new Pipe < E > () {
      @Override
      public void emit ( E value ) {
        // No-op: discard emissions
      }

      @Override
      public void flush () {
        // No-op: no buffering
      }
    };
  }

  @Override
  public < E > Pipe < E > pipe ( Receptor < ? super E > receptor ) {
    // Factory method for creating a pipe that routes to an receptor
    return new Pipe < E > () {
      @Override
      public void emit ( E value ) {
        receptor.receive( ( value ));
      }

      @Override
      public void flush () {
        // No-op: receptor has no buffering
      }
    };
  }

  @Override
  public < E > Pipe < E > pipe ( Class < E > type, Receptor < ? super E > receptor ) {
    // Factory method for creating a typed pipe that routes to an receptor
    return pipe ( receptor );  // Type is just for compile-time safety
  }

  @Override
  public < I, E > Pipe < I > pipe ( Function < ? super I, ? extends E > transformer, Pipe < ? super E > target ) {
    Objects.requireNonNull ( transformer, "Transformer function cannot be null" );
    Objects.requireNonNull ( target, "Target pipe cannot be null" );
    // Factory method for creating a transforming pipe
    return new Pipe < I > () {
      @Override
      public void emit ( I value ) {
        E transformed = transformer.apply ( value );
        target.emit ( transformed );
      }

      @Override
      public void flush () {
        target.flush ();
      }
    };
  }

  // ========== Name Factory (8 methods) ==========
  // All delegate directly to InternedName.of() overloaded methods

  @Override
  public Name name ( String s ) {
    // Create root name - no path parsing
    return InternedName.of ( s );
  }

  @Override
  public Name name ( Enum < ? > e ) {
    // Use getCanonicalName() which already has dots for inner classes
    // Falls back to getName() for anonymous/local classes
    Class<?> declaringClass = e.getDeclaringClass();
    String className = declaringClass.getCanonicalName();
    if (className == null) {
      // Anonymous or local class - use runtime name
      className = declaringClass.getName();
    }
    return InternedName.of ( className ).name ( e.name () );
  }

  @Override
  public Name name ( Iterable < String > parts ) {
    // Get first element as root, let Name.name() build the rest
    Iterator < String > it = parts.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "parts cannot be empty" );
    }
    return InternedName.of ( it.next () ).name ( it );
  }

  @Override
  public < T > Name name ( Iterable < ? extends T > items, Function < T, String > mapper ) {
    // Get first element as root, let Name.name() build the rest
    Iterator < ? extends T > it = items.iterator ();
    if ( !it.hasNext () ) {
      throw new IllegalArgumentException ( "items cannot be empty" );
    }
    return InternedName.of ( mapper.apply ( it.next () ) ).name ( it, mapper );
  }

  @Override
  public Name name ( Iterator < String > parts ) {
    // Get first element as root, let Name.name() build the rest
    if ( !parts.hasNext () ) {
      throw new IllegalArgumentException ( "parts cannot be empty" );
    }
    return InternedName.of ( parts.next () ).name ( parts );
  }

  @Override
  public < T > Name name ( Iterator < ? extends T > items, Function < T, String > mapper ) {
    // Get first element as root, let Name.name() build the rest
    if ( !items.hasNext () ) {
      throw new IllegalArgumentException ( "items cannot be empty" );
    }
    return InternedName.of ( mapper.apply ( items.next () ) ).name ( items, mapper );
  }

  @Override
  public Name name ( Class < ? > clazz ) {
    // Use getCanonicalName() which already has dots for inner classes
    // Falls back to getName() for anonymous/local classes per API spec
    String className = clazz.getCanonicalName();
    if (className == null) {
      // Anonymous or local class - use runtime name
      className = clazz.getName();
    }
    return InternedName.of ( className );
  }

  @Override
  public Name name ( Member member ) {
    // Create root, let Name.name() handle the hierarchy
    return InternedName.of ( member.getDeclaringClass ().getName () ).name ( member );
  }

  // ========== Scope Management (2 methods) ==========

  @Override
  public Scope scope () {
    // Create a new scope with a unique name each time
    // Each scope is independent and can be closed without affecting others
    // Use System.nanoTime() for unique names
    return new ManagedScope ( InternedName.of ( "scope." + System.nanoTime () ) );
  }

  @Override
  public Scope scope ( Name name ) {
    Objects.requireNonNull ( name, "Scope name cannot be null" );
    return scopes.computeIfAbsent ( name, n -> new ManagedScope ( n ) );
  }

  // ========== State Factory (1 method) ==========

  @Override
  public State state () {
    return LinkedState.empty ();
  }

  // ========== Reservoir Creation (1 method) ==========

  @Override
  public < E, S extends Source < E, S > > Reservoir < E > reservoir ( Source < E, S > source ) {
    Objects.requireNonNull ( source, "Source cannot be null" );
    // Create CollectingReservoir directly
    return new CollectingReservoir <> ( source );
  }

  // ========== Slot Management (8 methods) ==========

  @Override
  public Slot < Boolean > slot ( Name name, boolean value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Integer > slot ( Name name, int value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Long > slot ( Name name, long value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Double > slot ( Name name, double value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Float > slot ( Name name, float value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < String > slot ( Name name, String value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Name > slot ( Name name, Name value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < State > slot ( Name name, State value ) {
    return TypedSlot.of ( name, value );
  }

  @Override
  public Slot < Name > slot ( Enum < ? > value ) {
    Objects.requireNonNull ( value, "Enum value cannot be null" );
    // Slot name = fully qualified enum class name
    // Slot value = fully qualified enum class + class name again + constant
    return TypedSlot.of (
      name ( value.getDeclaringClass () ),
      name ( value ),
      Name.class
    );
  }

  // ========== Subscriber Management (1 method) ==========

  @Override
  public < E > Subscriber < E > subscriber ( Name name, BiConsumer < Subject < Channel < E > >, Registrar < E > > fn ) {
    return new ContextSubscriber <> ( name, fn );
  }
}
