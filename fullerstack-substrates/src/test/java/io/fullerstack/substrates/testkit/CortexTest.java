// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static io.humainary.substrates.api.Substrates.Composer.pipe;
import static org.junit.jupiter.api.Assertions.*;

/// Comprehensive tests for the Cortex interface focusing on methods
/// not covered by other test classes.
///
/// This test class covers:
/// - Circuit creation and lifecycle
/// - Scope creation and resource management
/// - Reservoir creation and event capture
/// - Subscriber creation with different configurations
///
/// @author wlouth
/// @since 1.0

final class CortexTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  // ===========================
  // Circuit Tests
  // ===========================

  @Test
  void testCircuitAwaitCompletesWhenQueueEmpty () {

    final var circuit = cortex.circuit ();

    try {

      // Create a simple conduit and emit a value
      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      conduit.percept ( cortex.name ( "test.channel" ) )
        .emit ( 42 );

      // Await should complete when queue is drained
      circuit.await ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitClose () {

    final var circuit = cortex.circuit ();

    // Should not throw
    circuit.close ();

    // Multiple closes should be idempotent
    circuit.close ();
    circuit.close ();

  }

  @Test
  void testCircuitConduitSinkIntegration () {

    final var circuit = cortex.circuit (
      cortex.name ( "integration.circuit" )
    );

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit (
          cortex.name ( "integration.conduit" ),
          pipe ()
        );

      final Reservoir < String > sink = cortex.reservoir( conduit );

      final Pipe < String > pipe =
        conduit.percept ( cortex.name ( "integration.channel" ) );

      pipe.emit ( "integration-test" );

      circuit.await ();

      final var captures =
        sink.drain ().toList ();

      assertEquals ( 1, captures.size () );
      assertEquals ( "integration-test", captures.getFirst ().emission () );
      assertEquals (
        Channel.class,
        captures.getFirst ().subject ().type ()
      );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testCircuitCreation () {

    final var circuit = cortex.circuit ();

    assertNotNull ( circuit );
    assertNotNull ( circuit.subject () );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testCircuitCreationNullNameGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.circuit ( null )
    );

  }

  @Test
  void testCircuitCreationWithName () {

    final var circuitName = cortex.name ( "cortex.test.circuit" );
    final var circuit = cortex.circuit ( circuitName );

    assertNotNull ( circuit );
    assertEquals ( circuitName, circuit.subject ().name () );

  }

  @Test
  void testCircuitSubjectType () {

    final var circuit = cortex.circuit ();

    try {

      assertEquals ( Circuit.class, circuit.subject ().type () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testMultipleCircuitsHaveUniqueSubjects () {

    final var circuit1 = cortex.circuit ();
    final var circuit2 = cortex.circuit ();

    try {

      assertNotSame ( circuit1.subject (), circuit2.subject () );
      assertNotEquals ( circuit1.subject ().id (), circuit2.subject ().id () );

    } finally {

      circuit1.close ();
      circuit2.close ();

    }

  }

  @Test
  void testNestedScopesWithResources () {

    final var root = cortex.scope (
      cortex.name ( "nested.root" )
    );

    final var child = root.scope (
      cortex.name ( "nested.child" )
    );

    final var rootCircuit = root.register ( cortex.circuit () );
    final var childCircuit = child.register ( cortex.circuit () );

    assertNotNull ( rootCircuit );
    assertNotNull ( childCircuit );

    child.close ();
    root.close ();

  }


  // ===========================
  // Scope Tests
  // ===========================

  @Test
  void testScopeClosesRegisteredResources () {

    final var scope = cortex.scope ();

    final var circuit = cortex.circuit ();
    scope.register ( circuit );

    // Closing scope should close registered resources
    scope.close ();

    // Multiple closes should be idempotent
    scope.close ();

  }

  @Test
  void testScopeClosureCachesPerResourceAndCleansUp () {

    final var scope = cortex.scope ();

    final var circuit = cortex.circuit ();

    try {

      final Closure < Circuit > first = scope.closure ( circuit );
      final Closure < Circuit > second = scope.closure ( circuit );

      assertSame ( first, second );

      final var consumed = new AtomicBoolean ( false );

      first.consume ( _ ->
        consumed.set ( true )
      );

      assertTrue ( consumed.get () );

      final Closure < Circuit > third = scope.closure ( circuit );

      assertNotSame ( first, third );

      third.consume ( ignored -> {
      } );

    } finally {

      scope.close ();
      circuit.close ();

    }

  }

  @Test
  void testScopeCreateChildScope () {

    final var parent = cortex.scope ();

    final var child = parent.scope ();

    assertNotNull ( child );
    assertNotNull ( child.subject () );

    child.close ();
    parent.close ();

  }

  @Test
  void testScopeCreateNamedChildScope () {

    final var parent = cortex.scope ();

    final var childName = cortex.name ( "cortex.test.child" );
    final var child = parent.scope ( childName );

    assertEquals ( childName, child.subject ().name () );

    child.close ();
    parent.close ();

  }

  @Test
  void testScopeCreation () {

    final var scope = cortex.scope ();

    assertNotNull ( scope );
    assertNotNull ( scope.subject () );

    scope.close ();

  }

  @SuppressWarnings ( {"DataFlowIssue", "resource"} )
  @Test
  void testScopeCreationNullNameGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.scope ( null )
    );

  }

  @Test
  void testScopeCreationWithName () {

    final var scopeName = cortex.name ( "cortex.test.scope" );
    final var scope = cortex.scope ( scopeName );

    assertNotNull ( scope );
    assertEquals ( scopeName, scope.subject ().name () );

    scope.close ();

  }

  // ===========================
  // Reservoir Tests
  // ===========================

  @Test
  void testScopeEnclosureAccessors () {

    final var parent = cortex.scope (
      cortex.name ( "scope.enclosure.parent" )
    );

    final var child = parent.scope ();
    final var grandchild = child.scope ();

    final Scope[] captured = new Scope[1];
    final var rootCalled = new AtomicBoolean ( false );

    assertSame ( parent, child.enclosure ().orElseThrow () );
    assertSame ( child, grandchild.enclosure ().orElseThrow () );

    grandchild.enclosure ( scope ->
      captured[0] = scope
    );

    parent.enclosure ( ignored ->
      rootCalled.set ( true )
    );

    assertSame ( child, captured[0] );
    assertFalse ( parent.enclosure ().isPresent () );
    assertFalse ( rootCalled.get () );

    grandchild.close ();
    child.close ();
    parent.close ();

  }

  @Test
  void testScopeHierarchy () {

    final var root = cortex.scope ();
    final var child = root.scope ();
    final var grandchild = child.scope ();

    assertTrue ( child.within ( root ) );
    assertTrue ( grandchild.within ( child ) );
    assertTrue ( grandchild.within ( root ) );

    grandchild.close ();
    child.close ();
    root.close ();

  }

  @Test
  void testScopeManagesMultipleResources () {

    final var scope = cortex.scope (
      cortex.name ( "multi.resource.scope" )
    );

    final var circuit1 = scope.register ( cortex.circuit () );
    final var circuit2 = scope.register ( cortex.circuit () );
    final var circuit3 = scope.register ( cortex.circuit () );

    assertNotNull ( circuit1 );
    assertNotNull ( circuit2 );
    assertNotNull ( circuit3 );

    // All should be closed when scope closes
    scope.close ();

  }

  @Test
  void testScopeOperationsDisallowedAfterClose () {

    final var scope = cortex.scope ();

    scope.close ();

    assertThrows (
      IllegalStateException.class,
      scope::scope
    );

    final var circuit = cortex.circuit ();

    try {

      assertThrows (
        IllegalStateException.class,
        () -> scope.register ( circuit )
      );

      assertThrows (
        IllegalStateException.class,
        () -> scope.closure ( circuit )
      );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testScopeRegisterResource () {

    final var scope = cortex.scope ();

    final var circuit = cortex.circuit ();
    final var registered = scope.register ( circuit );

    assertSame ( circuit, registered );

    scope.close ();

  }

  @Test
  void testScopeSubjectType () {

    final var scope = cortex.scope ();

    assertEquals ( Scope.class, scope.subject ().type () );

    scope.close ();

  }

  @Test
  void testSinkCapturesEmissions () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      final Reservoir < Integer > sink = cortex.reservoir( conduit );

      final Pipe < Integer > pipe =
        conduit.percept ( cortex.name ( "test.channel" ) );

      pipe.emit ( 10 );
      pipe.emit ( 20 );
      pipe.emit ( 30 );

      circuit.await ();

      final var captures =
        sink.drain ().toList ();

      assertEquals ( 3, captures.size () );
      assertEquals ( 10, captures.get ( 0 ).emission () );
      assertEquals ( 20, captures.get ( 1 ).emission () );
      assertEquals ( 30, captures.get ( 2 ).emission () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSinkCreationFromContext () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( pipe () );

      // Conduit is a Context
      final Reservoir < Integer > sink = cortex.reservoir( conduit );

      assertNotNull ( sink );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSinkCreationFromSource () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );
      final Reservoir < String > sink = cortex.reservoir( conduit );

      assertNotNull ( sink );
      assertNotNull ( sink.subject () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSinkCreationNullContextGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.reservoir( null )
    );

  }

  // ===========================
  // Subscriber Tests
  // ===========================

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSinkCreationNullSourceGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.reservoir( null )
    );

  }

  @Test
  void testSinkDrainCapturesNewEmissions () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );

      final Reservoir < String > sink = cortex.reservoir( conduit );

      final Pipe < String > pipe =
        conduit.percept ( cortex.name ( "test.channel" ) );

      pipe.emit ( "first" );

      circuit.await ();

      final var firstDrain =
        sink.drain ().toList ();

      assertEquals ( 1, firstDrain.size () );
      assertEquals ( "first", firstDrain.getFirst ().emission () );

      // Emit another value after the first drain
      pipe.emit ( "second" );

      circuit.await ();

      // Second drain should have only new emissions since last drain
      final var secondDrain =
        sink.drain ().toList ();

      assertEquals ( 1, secondDrain.size () );
      assertEquals ( "second", secondDrain.getFirst ().emission () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSinkSubjectType () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );

      final var sink = cortex.reservoir( conduit );

      assertEquals ( Reservoir.class, sink.subject ().type () );

      sink.close ();

    } finally {

      circuit.close ();

    }

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSubscriberCreationNullBehaviorGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.subscriber (
        cortex.name ( "test" ),
        (BiConsumer < Subject < Channel < String > >, Registrar < String > >) null
      )
    );

  }

  @SuppressWarnings ( "DataFlowIssue" )
  @Test
  void testSubscriberCreationNullNameGuard () {

    assertThrows (
      NullPointerException.class,
      () -> cortex.subscriber (
        null,
        ( _, _ ) -> {
        }
      )
    );

  }


  @Test
  void testSubscriberCreationWithBehavior () {

    final var subscriberName = cortex.name ( "cortex.test.subscriber" );

    final Subscriber < String > subscriber =
      cortex.subscriber (
        subscriberName,
        ( _, _ ) -> {
          // Subscriber behavior
        }
      );

    assertNotNull ( subscriber );
    assertEquals ( subscriberName, subscriber.subject ().name () );

  }


  // ===========================
  // Integration Tests
  // ===========================

  @Test
  void testSubscriberReceivesChannelSubjects () {

    final var circuit = cortex.circuit ();

    try {

      final Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( pipe () );

      final List < Subject < ? > > receivedSubjects = new ArrayList <> ();

      final Subscriber < String > subscriber =
        cortex.subscriber (
          cortex.name ( "test.subscriber" ),
          ( subject, registrar ) -> {
            receivedSubjects.add ( subject );
            registrar.register ( _ -> {
            } );
          }
        );

      // Subscribe before creating channels
      conduit.subscribe ( subscriber );

      // Create channels which should trigger subscriber
      final Pipe < String > pipe1 = conduit.percept ( cortex.name ( "channel.one" ) );
      final Pipe < String > pipe2 = conduit.percept ( cortex.name ( "channel.two" ) );
      final Pipe < String > pipe3 = conduit.percept ( cortex.name ( "channel.three" ) );

      // Emit values to ensure channels are actually created and subscribed
      pipe1.emit ( "test1" );
      pipe2.emit ( "test2" );
      pipe3.emit ( "test3" );

      circuit.await ();

      assertEquals ( 3, receivedSubjects.size () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSubscriberSubjectType () {

    final Subscriber < String > subscriber =
      cortex.subscriber (
        cortex.name ( "subscriber.type.test" ),
        ( _, _ ) -> {
        }
      );

    assertEquals ( Subscriber.class, subscriber.subject ().type () );

  }


}
