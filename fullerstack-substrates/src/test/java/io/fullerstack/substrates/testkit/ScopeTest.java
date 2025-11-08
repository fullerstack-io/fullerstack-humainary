// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class ScopeTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  @Test
  void testClosureReuseAndConsumption () {

    final var scope = cortex.scope ();
    final var circuit = cortex.circuit ();

    final var registered = cortex.circuit ();

    try {

      assertSame ( registered, scope.register ( registered ) );

      final var first =
        scope.closure ( circuit );

      assertSame ( first, scope.closure ( circuit ) );

      final var invoked = new AtomicBoolean ( false );

      first.consume ( resource -> {
        invoked.set ( true );
        assertSame ( circuit, resource );
      } );

      assertTrue ( invoked.get (), "closure should invoke consumer while scope open" );

      final var second =
        scope.closure ( circuit );

      assertNotSame ( first, second );

      second.consume ( _ -> {
      } );

    } finally {

      scope.close ();
      circuit.close ();
      registered.close ();

    }

  }

  @Test
  void testScopeClosePreventsFurtherOperations () {

    final var scope = cortex.scope ();
    final var circuit = cortex.circuit ();

    try {

      final var closure =
        scope.closure ( circuit );

      scope.close ();

      final var invoked = new AtomicBoolean ( false );

      closure.consume ( _ -> invoked.set ( true ) );

      assertFalse ( invoked.get (), "closure should not run after scope is closed" );

      final var extra = cortex.circuit ();

      try {
        assertThrows (
          IllegalStateException.class,
          () -> scope.register ( extra )
        );
      } finally {
        extra.close ();
      }

      assertThrows (
        IllegalStateException.class,
        scope::scope
      );

    } finally {

      scope.close ();
      circuit.close ();

    }

  }

  @Test
  void testScopeHierarchyAndEnclosure () {

    try ( final var root = cortex.scope () ) {

      final var named =
        root.scope ( cortex.name ( "scope.test.named" ) );

      final var anonymous =
        root.scope ();

      assertSame ( root, named.enclosure ().orElseThrow () );

      final var captured = new AtomicReference < Scope > ();
      named.enclosure ( captured::set );

      assertSame ( root, captured.get () );

      assertEquals ( named.path ().toString (), named.toString () );
      assertNotNull ( anonymous.subject () );

      root.close ();

      assertThrows ( IllegalStateException.class, named::scope );
      assertThrows ( IllegalStateException.class, anonymous::scope );

    }

  }

}
