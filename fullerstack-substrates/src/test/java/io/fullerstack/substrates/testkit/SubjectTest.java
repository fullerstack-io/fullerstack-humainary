// Copyright (c) 2025 William David Louth

package io.fullerstack.substrates.testkit;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.humainary.substrates.api.Substrates.Composer.pipe;
import static org.junit.jupiter.api.Assertions.*;

final class SubjectTest
  extends TestSupport {

  private Cortex cortex;

  @BeforeEach
  void setup () {

    cortex = cortex ();

  }

  @Test
  void testNestedSubjectPathAndEnclosure () {

    final var circuit = cortex.circuit (
      cortex.name ( "subject.test.nested.circuit" )
    );

    try {

      final var conduitName = cortex.name ( "subject.test.nested.conduit" );
      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit ( conduitName, pipe () );

      final var circuitSubject = circuit.subject ();
      final var conduitSubject = conduit.subject ();

      final var path = conduitSubject.path ().toString ();

      assertTrue ( path.startsWith ( circuitSubject.part ().toString () ) );
      assertTrue ( path.endsWith ( conduitSubject.part ().toString () ) );
      assertEquals ( path, conduitSubject.toString () );

      assertEquals ( 2, conduitSubject.depth () );

      assertTrue ( conduitSubject.enclosure ().isPresent () );
      assertSame ( circuitSubject, conduitSubject.enclosure ().orElseThrow () );

      final AtomicReference < Subject < ? > > captured = new AtomicReference <> ();
      conduitSubject.enclosure ( captured::set );

      assertSame ( circuitSubject, captured.get () );
      assertSame ( circuitSubject, conduitSubject.extremity () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testRootSubjectProperties () {

    final var circuitName = cortex.name ( "subject.test.circuit" );
    final var circuit = cortex.circuit ( circuitName );

    try {

      final var subject = circuit.subject ();

      assertEquals ( Circuit.class, subject.type () );
      assertEquals ( circuitName, subject.name () );
      assertNotNull ( subject.id () );

      final var part = subject.part ().toString ();

      assertTrue ( part.startsWith ( "Subject[name=" ) );
      assertTrue ( part.contains ( circuitName.toString () ) );
      assertTrue ( part.contains ( "type=Circuit" ) );

      assertEquals ( part, subject.path ().toString () );
      assertEquals ( part, subject.toString () );

      assertEquals ( 1, subject.depth () );
      assertTrue ( subject.enclosure ().isEmpty () );
      assertSame ( subject, subject.extremity () );

      assertEquals ( 0L, subject.state ().stream ().count () );

    } finally {

      circuit.close ();

    }

  }

  @Test
  void testSubjectHierarchyIterationAndWithin () {

    final var circuit = cortex.circuit (
      cortex.name ( "subject.test.hierarchy.circuit" )
    );

    try {

      final Conduit < Pipe < Integer >, Integer > conduit =
        circuit.conduit (
          cortex.name ( "subject.test.hierarchy.conduit" ),
          pipe ()
        );

      final Subscriber < Integer > subscriber =
        cortex.subscriber (
          cortex.name ( "subject.test.hierarchy.subscriber" ),
          ( _, registrar ) -> registrar.register ( _ -> {
          } )
        );

      final var subscription = conduit.subscribe ( subscriber );

      try {

        assertEquals ( Subscription.class, subscription.subject ().type () );
        assertSame ( conduit.subject (), subscription.subject ().enclosure ().orElseThrow () );
        assertSame ( circuit.subject (), conduit.subject ().enclosure ().orElseThrow () );

        final List < Subject < ? > > expectedStream = List.of (
          subscription.subject (),
          conduit.subject (),
          circuit.subject ()
        );

        assertEquals (
          expectedStream,
          subscription.subject ().stream ().toList ()
        );

        assertTrue ( subscription.subject ().within ( conduit.subject () ) );
        assertTrue ( subscription.subject ().within ( circuit.subject () ) );
        assertFalse ( conduit.subject ().within ( subscription.subject () ) );

        assertEquals ( circuit.subject (), subscription.subject ().extremity () );
        final var comparison = conduit.subject ().compareTo ( subscription.subject () );

        assertTrue ( comparison < 0 );
        assertEquals ( 3, subscription.subject ().depth () );

      } finally {

        subscription.close ();
        circuit.await ();

      }

    } finally {

      circuit.close ();

    }

  }

}
