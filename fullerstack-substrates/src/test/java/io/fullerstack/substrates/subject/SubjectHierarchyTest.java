package io.fullerstack.substrates.subject;

import io.humainary.substrates.api.Substrates.*;
import static io.humainary.substrates.api.Substrates.cortex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test Subject hierarchy via enclosure() to match Humainary blog examples.
 * <p>
 * Expected output format from https://humainary.io/blog/observability-x-subjects/:
 * <p>
 * Subject[name=Redis,type=CIRCUIT,id=...]
 * Subject[name=Counters,type=CONDUIT,id=...]
 * Subject[name=cache.hit,type=CHANNEL,id=...]
 */
class SubjectHierarchyTest {

  @Test
  void shouldBuildSubjectHierarchyViaEnclosure () {
    // Recreate the Humainary blog example
    Cortex cortex = cortex();

    // Create Circuit
    Circuit circuit = cortex.circuit ( cortex.name ( "Redis" ) );

    // Create Conduit
    Conduit < Pipe < Integer >, Integer > counters = circuit.conduit (
      cortex.name ( "Counters" ),
      Composer.pipe ()
    );

    // Capture Channel Subject via subscriber
    final Subject < ? >[] channelSubjectHolder = new Subject < ? >[1];

    Subscriber < Integer > subscriber = cortex.subscriber (
      cortex.name ( "test-subscriber" ),
      ( subject, registrar ) -> {
        // Store the Channel Subject
        channelSubjectHolder[0] = subject;
      }
    );

    counters.subscribe ( subscriber );

    // Create Channel (triggers subscriber)
    Pipe < Integer > cacheHit = counters.get ( cortex.name ( "cache.hit" ) );

    Subject < ? > channelSubject = channelSubjectHolder[0];
    assertThat ( (Object) channelSubject ).isNotNull ();

    // Debug: print the hierarchy
    System.out.println ( "Channel Subject: " + channelSubject.part () );
    System.out.println ( "Channel hierarchy:\n" + channelSubject.path ( "\n  " ) );

    // Verify hierarchy via enclosure()
    // Channel's parent should be Conduit
    assertThat ( channelSubject.enclosure () ).isPresent ();
    Subject < ? > conduitSubject = channelSubject.enclosure ().get ();
    // Conduit's name is hierarchical: "Redis.Counters"
    assertThat ( conduitSubject.name ().path ().toString () ).contains ( "Counters" );
    assertThat ( conduitSubject.type () ).isEqualTo ( Conduit.class );

    // Conduit's parent should be Circuit
    assertThat ( conduitSubject.enclosure () ).isPresent ();
    Subject < ? > circuitSubject = conduitSubject.enclosure ().get ();
    assertThat ( circuitSubject.name ().path () ).hasToString ( "Redis" );
    assertThat ( circuitSubject.type () ).isEqualTo ( Circuit.class );

    // Circuit has no parent
    assertThat ( circuitSubject.enclosure () ).isEmpty ();
  }

  @Test
  void shouldFormatSubjectPathWithHierarchy () {
    // Recreate the Humainary blog example
    Cortex cortex = cortex();

    // Create Circuit → Conduit → Channel hierarchy
    Circuit circuit = cortex.circuit ( cortex.name ( "Redis" ) );
    Conduit < Pipe < Integer >, Integer > counters = circuit.conduit (
      cortex.name ( "Counters" ),
      Composer.pipe ()
    );

    // Capture Channel Subject via subscriber
    final Subject < ? >[] channelSubjectHolder = new Subject < ? >[1];

    Subscriber < Integer > subscriber = cortex.subscriber (
      cortex.name ( "path-test-subscriber" ),
      ( subject, registrar ) -> {
        channelSubjectHolder[0] = subject;
      }
    );

    counters.subscribe ( subscriber );

    counters.get ( cortex.name ( "cache.hit" ) );

    Subject < ? > channelSubject = channelSubjectHolder[0];

    // Call path() with newline separator to match blog output
    String hierarchicalPath = channelSubject.path ( "\n\t" ).toString ();

    // Should show Circuit → Conduit → Channel hierarchy
    assertThat ( hierarchicalPath ).contains ( "Subject[name=Redis,type=Circuit" );
    assertThat ( hierarchicalPath ).contains ( "Subject[name=Counters,type=Conduit" );
    assertThat ( hierarchicalPath ).contains ( "Subject[name=cache.hit,type=Channel" );

    // Verify hierarchy ordering (Circuit first, then Conduit, then Channel)
    int circuitIndex = hierarchicalPath.indexOf ( "Circuit" );
    int conduitIndex = hierarchicalPath.indexOf ( "Conduit" );
    int channelIndex = hierarchicalPath.indexOf ( "Channel" );

    assertThat ( circuitIndex ).isLessThan ( conduitIndex );
    assertThat ( conduitIndex ).isLessThan ( channelIndex );
  }

  @Test
  void shouldFormatSubjectPartWithMetadata () {
    // Subject.part() should return formatted string with name, type, and id
    Cortex cortex = cortex();
    Circuit circuit = cortex.circuit ( cortex.name ( "Redis" ) );

    Subject < ? > circuitSubject = circuit.subject ();
    String part = circuitSubject.part ().toString ();

    // Should match format: Subject[name=Redis,type=Circuit,id=...]
    assertThat ( part ).startsWith ( "Subject[name=Redis,type=Circuit,id=" );
    assertThat ( part ).endsWith ( "]" );
    assertThat ( part ).contains ( circuitSubject.id ().toString () );
  }
}
