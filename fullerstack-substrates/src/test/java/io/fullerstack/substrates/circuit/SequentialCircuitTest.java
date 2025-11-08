package io.fullerstack.substrates.circuit;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.name.InternedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.humainary.substrates.api.Substrates.Composer.pipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SequentialCircuit (RC1 API compliant).
 * <p>
 * Note: Clock and tap() APIs were removed in RC1, so those tests are deleted.
 */
class SequentialCircuitTest {
  private SequentialCircuit circuit;

  @AfterEach
  void cleanup () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  @Test
  void shouldCreateCircuitWithName () {
    circuit = new SequentialCircuit ( InternedName.of ( "test-circuit" ) );

    assertThat ( (Object) circuit ).isNotNull ();
    assertThat ( (Object) circuit.subject () ).isNotNull ();
    assertThat ( circuit.subject ().type () ).isEqualTo ( Circuit.class );
  }

  // RC1: Circuit no longer extends Source< State > - test removed

  // RC1: Clock API removed - tests deleted

  @Test
  void shouldRequireNonNullName () {
    assertThatThrownBy ( () -> new SequentialCircuit ( null ) )
      .isInstanceOf ( NullPointerException.class )
      .hasMessageContaining ( "Circuit name cannot be null" );
  }

  @Test
  void shouldRequireNonNullConduitName () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    assertThatThrownBy ( () -> circuit.conduit ( null, pipe () ) )
      .isInstanceOf ( NullPointerException.class )
      .hasMessageContaining ( "Conduit name cannot be null" );
  }

  @Test
  void shouldRequireNonNullComposer () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    assertThatThrownBy ( () -> circuit.conduit ( InternedName.of ( "test" ), null ) )
      .isInstanceOf ( NullPointerException.class )
      .hasMessageContaining ( "Composer cannot be null" );
  }

  // RC1: tap() API removed - tests deleted

  @Test
  void shouldAllowMultipleCloses () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    circuit.close ();
    circuit.close (); // Should not throw

    assertThat ( (Object) circuit ).isNotNull ();
  }

  @Test
  void shouldCreateDifferentConduitsForDifferentComposers () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    // Same name, different composers should create DIFFERENT Conduits
    Composer < String, Pipe < String > > composer1 = pipe ();
    Composer < String, Pipe < String > > composer2 = channel -> channel.pipe ();

    Conduit < Pipe < String >, String > conduit1 = circuit.conduit ( InternedName.of ( "shared" ), composer1 );
    Conduit < Pipe < String >, String > conduit2 = circuit.conduit ( InternedName.of ( "shared" ), composer2 );

    assertThat ( (Object) conduit1 ).isNotSameAs ( conduit2 );
  }

  @Test
  void shouldCacheConduitsWithSameNameAndComposer () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    Composer < String, Pipe < String > > composer = pipe ();

    Conduit < Pipe < String >, String > conduit1 = circuit.conduit ( InternedName.of ( "cached" ), composer );
    Conduit < Pipe < String >, String > conduit2 = circuit.conduit ( InternedName.of ( "cached" ), composer );

    assertThat ( (Object) conduit1 ).isSameAs ( conduit2 );
  }

  @Test
  void shouldProvideAccessToSubject () {
    circuit = new SequentialCircuit ( InternedName.of ( "test" ) );

    assertThat ( (Object) circuit.subject () ).isNotNull ();
    assertThat ( (Object) circuit ).isNotNull ();
  }
}
