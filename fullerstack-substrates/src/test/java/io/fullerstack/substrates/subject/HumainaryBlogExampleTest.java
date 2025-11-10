package io.fullerstack.substrates.subject;

import io.humainary.substrates.api.Substrates.*;
import static io.humainary.substrates.api.Substrates.cortex;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test implementation against EXACT Humainary blog examples.
 * <p>
 * Blog: https://humainary.io/blog/observability-x-subjects/
 */
class HumainaryBlogExampleTest {

  /**
   * Example from blog showing hierarchical Subject output:
   * <p>
   * Subject[name=Redis,type=CIRCUIT,id=e41bfccf-dc78-4423-ab78-657a321a5e1b]
   * Subject[name=Counters,type=CONDUIT,id=3dbee192-962d-40f9-aa57-404c0e3b6427]
   * Subject[name=cache.hit,type=CHANNEL,id=4fdbf813-2be8-4905-af70-5406d3e35612] -> 1
   */
  @Test
  void shouldMatchBlogExample_SubjectHierarchy () throws java.lang.Exception {
    Cortex cortex = cortex();

    // Exact blog example code
    var resource = cortex.circuit ( cortex.name ( "Redis" ) );

    var counters = resource.conduit (
      cortex.name ( "Counters" ),
      Composer.pipe ()
    );

    // Capture the Subject when Channel is created
    final Subject < ? >[] capturedSubject = new Subject < ? >[1];
    final Integer[] capturedEmission = new Integer[1];

    Subscriber < Integer > subscriber = cortex.subscriber (
      cortex.name ( "test-subscriber" ),
      ( subject, registrar ) -> {
        // Register pipe to capture emissions
        registrar.register ( emission -> {
          capturedSubject[0] = subject;
          capturedEmission[0] = emission;

          // Blog example prints: subject.path("\n\t") -> emission
          System.out.println ( "=== Blog Example Output ===" );
          System.out.printf ( "%s -> %d%n", subject.path ( "\n\t" ), emission );
          System.out.println ( "===========================" );
        } );
      }
    );

    counters.subscribe ( (Subscriber) subscriber );

    var hits = counters.percept ( cortex.name ( "cache.hit" ) );
    hits.emit ( 1 );

    // Wait for emission to be processed
    resource.await ();

    // Verify we captured the emission
    assertThat ( capturedEmission[0] ).isEqualTo ( 1 );
    assertThat ( (Object) capturedSubject[0] ).isNotNull ();

    Subject < ? > channelSubject = capturedSubject[0];

    // Verify the hierarchy
    System.out.println ( "\n=== Hierarchy Verification ===" );
    System.out.println ( "Channel Subject: " + channelSubject.part () );

    // Channel → Conduit → Circuit hierarchy
    assertThat ( channelSubject.enclosure () ).as ( "Channel should have Conduit as parent" ).isPresent ();
    Subject < ? > conduitSubject = channelSubject.enclosure ().get ();
    System.out.println ( "Conduit Subject: " + conduitSubject.part () );
    assertThat ( conduitSubject.type () ).isEqualTo ( Conduit.class );

    assertThat ( conduitSubject.enclosure () ).as ( "Conduit should have Circuit as parent" ).isPresent ();
    Subject < ? > circuitSubject = conduitSubject.enclosure ().get ();
    System.out.println ( "Circuit Subject: " + circuitSubject.part () );
    assertThat ( circuitSubject.type () ).isEqualTo ( Circuit.class );

    assertThat ( circuitSubject.enclosure () ).as ( "Circuit should have no parent" ).isEmpty ();
    System.out.println ( "==============================\n" );
  }

  /**
   * Example from blog showing Name hierarchy:
   * <p>
   * var circuit = cortex.circuit(cortex.name("network.5g"));
   * var conduit = circuit.conduit(cortex.name("region.eu-nl"), Valve::new);
   * var valve = conduit.percept(cortex.name("pop.ams"));
   * <p>
   * Output: network/5g/region/eu-nl/pop/ams
   */
  @Test
  void shouldMatchBlogExample_NameHierarchy () throws java.lang.Exception {
    Cortex cortex = cortex();

    // Blog example: naming percepts with hierarchical names
    var circuit = cortex.circuit ( cortex.name ( "network.5g" ) );

    var conduit = circuit.conduit (
      cortex.name ( "region.eu-nl" ),
      Composer.pipe ()
    );

    // Capture the Channel Subject
    final Subject < ? >[] capturedSubject = new Subject < ? >[1];
    final CopyOnWriteArrayList < Integer > emissions = new CopyOnWriteArrayList <> ();

    Subscriber < Object > subscriber = cortex.subscriber (
      cortex.name ( "valve-subscriber" ),
      ( subject, registrar ) -> {
        capturedSubject[0] = subject;
        registrar.register ( value -> emissions.add ( (Integer) value ) );
      }
    );

    conduit.subscribe ( (Subscriber) subscriber );

    var valve = conduit.percept ( cortex.name ( "pop.ams" ) );
    valve.emit ( 42 );

    // Wait for emission
    circuit.await ();

    assertThat ( (Object) emissions ).asString ().contains ( "42" );
    assertThat ( (Object) capturedSubject[0] ).isNotNull ();

    Subject < ? > valveSubject = capturedSubject[0];

    System.out.println ( "\n=== Name Hierarchy Example ===" );
    System.out.println ( "Valve Name (part): " + valveSubject.name ().part () );
    System.out.println ( "Valve Name (path): " + valveSubject.name ().path () );

    // Subject path with / separator (like blog output)
    System.out.println ( "Subject path(/): " + valveSubject.path ( '/' ) );

    // Subject path with newline separator
    System.out.println ( "Subject path(\\n\\t):" );
    System.out.println ( valveSubject.path ( "\n\t" ) );
    System.out.println ( "==============================\n" );

    // The blog shows output: network/5g/region/eu-nl/pop/ams
    // This is the Subject path, walking the Subject hierarchy
    String subjectPath = valveSubject.path ( '/' ).toString ();

    // Should contain all the name parts from the hierarchy
    assertThat ( subjectPath ).contains ( "network" );
    assertThat ( subjectPath ).contains ( "region" );
    assertThat ( subjectPath ).contains ( "pop" );
  }

  /**
   * Test Name extension per blog example:
   * name = name.name("toString")
   */
  @Test
  void shouldExtendNamesLikeBlogExample () {
    Cortex cortex = cortex();

    // Start with a base name
    Name baseName = cortex.name ( java.lang.String.class );
    System.out.println ( "Base name path: " + baseName.path () );

    // Extend it (like blog example: name.name("toString"))
    Name extendedName = baseName.name ( "toString" );
    System.out.println ( "Extended name path: " + extendedName.path () );

    // Verify hierarchy
    assertThat ( extendedName.enclosure () )
      .isPresent ()
      .get ()
      .isEqualTo ( baseName );

    // Verify part() returns just the leaf
    assertThat ( extendedName.part ().toString () ).isEqualTo ( "toString" );
  }
}
