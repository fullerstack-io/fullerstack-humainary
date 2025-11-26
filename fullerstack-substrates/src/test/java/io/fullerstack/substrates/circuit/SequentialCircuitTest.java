package io.fullerstack.substrates.circuit;

import static io.humainary.substrates.api.Substrates.Composer.pipe;
import static io.humainary.substrates.api.Substrates.cortex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.fullerstack.substrates.name.InternedName;
import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Pipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SequentialCircuit (RC1 API compliant).
 * <p>
 * Note: Clock and tap() APIs were removed in RC1, so those tests are deleted.
 */
class SequentialCircuitTest {
  private Circuit circuit;

  @AfterEach
  void cleanup () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  @Test
  void shouldCreateCircuitWithName () {
    circuit = cortex ().circuit ( cortex ().name ( "test-circuit" ) );

    assertThat ( (Object) circuit ).isNotNull ();
    assertThat ( (Object) circuit.subject () ).isNotNull ();
    assertThat ( circuit.subject ().type () ).isEqualTo ( Circuit.class );
  }

  // RC1: Circuit no longer extends Source< State > - test removed

  // RC1: Clock API removed - tests deleted

  @Test
  void shouldRequireNonNullName () {
    assertThatThrownBy ( () -> cortex ().circuit ( null ) )
      .isInstanceOf ( NullPointerException.class );
  }

  @Test
  void shouldRequireNonNullConduitName () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );

    assertThatThrownBy ( () -> circuit.conduit ( null, pipe () ) )
      .isInstanceOf ( NullPointerException.class )
      .hasMessageContaining ( "Conduit name cannot be null" );
  }

  @Test
  void shouldRequireNonNullComposer () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );

    assertThatThrownBy ( () -> circuit.conduit ( InternedName.of ( "test" ), null ) )
      .isInstanceOf ( NullPointerException.class )
      .hasMessageContaining ( "Composer cannot be null" );
  }

  // RC1: tap() API removed - tests deleted

  @Test
  void shouldAllowMultipleCloses () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );

    circuit.close ();
    circuit.close (); // Should not throw

    assertThat ( (Object) circuit ).isNotNull ();
  }

  @Test
  void shouldCreateDifferentConduitsForDifferentComposers () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );

    // Same name, different composers should create DIFFERENT Conduits
    Composer < String, Pipe < String > > composer1 = pipe ();
    Composer < String, Pipe < String > > composer2 = channel -> channel.pipe ();

    Conduit < Pipe < String >, String > conduit1 = circuit.conduit ( InternedName.of ( "shared" ), composer1 );
    Conduit < Pipe < String >, String > conduit2 = circuit.conduit ( InternedName.of ( "shared" ), composer2 );

    assertThat ( (Object) conduit1 ).isNotSameAs ( conduit2 );
  }

  // Caching removed - conduits are created fresh every time (matches reference implementation)
  // This aligns with TCK requirement that circuit.conduit(composer) creates new instance each time

  @Test
  void shouldProvideAccessToSubject () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );

    assertThat ( (Object) circuit.subject () ).isNotNull ();
    assertThat ( (Object) circuit ).isNotNull ();
  }
}
