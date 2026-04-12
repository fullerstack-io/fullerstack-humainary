package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.cortex;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.humainary.substrates.api.Substrates.Cortex;
import io.humainary.substrates.api.Substrates.Name;

/// Tests for the @Identity annotation contract.
///
/// @Identity types use reference equality (==) instead of .equals().
/// Annotated types: Id, Name, Subject.
///
/// For Name (which is also @Tenure(INTERNED)):
/// - Same path must return the same instance (identity)
/// - Different paths must return different instances
/// - Reference equality (==) is the correct comparison

@DisplayName ( "@Identity Annotation Contract Tests" )
class IdentityAnnotationContractTest {

  @Nested
  @DisplayName ( "Name @Identity" )
  class NameIdentity {

    @Test
    @DisplayName ( "same path returns same Name instance (==)" )
    void samePath_sameInstance () {
      Cortex cortex = cortex ();
      Name a = cortex.name ( "alpha.beta" );
      Name b = cortex.name ( "alpha.beta" );
      assertSame ( a, b, "Interned names with same path must be reference-equal" );
    }

    @Test
    @DisplayName ( "different paths return different Name instances" )
    void differentPaths_differentInstances () {
      Cortex cortex = cortex ();
      Name a = cortex.name ( "alpha" );
      Name b = cortex.name ( "beta" );
      assertNotSame ( a, b, "Names with different paths must not be reference-equal" );
    }

    @Test
    @DisplayName ( "name from chaining is same as name from parsing" )
    void chaining_sameAsParsing () {
      Cortex cortex = cortex ();
      Name parsed = cortex.name ( "a.b.c" );
      Name chained = cortex.name ( "a" ).name ( "b" ).name ( "c" );
      assertSame ( parsed, chained, "Chained and parsed names must be reference-equal" );
    }

    @Test
    @DisplayName ( "name equality uses == not .equals()" )
    void referenceEquality () {
      Cortex cortex = cortex ();
      Name a = cortex.name ( "test.identity" );
      Name b = cortex.name ( "test.identity" );
      //noinspection NumberEquality
      assertTrue ( a == b, "@Identity requires reference equality" );
    }

    @Test
    @DisplayName ( "enclosure identity is preserved" )
    void enclosureIdentity () {
      Cortex cortex = cortex ();
      Name parent = cortex.name ( "parent" );
      Name child = cortex.name ( "parent.child" );
      assertSame ( parent, child.enclosure ().orElseThrow (),
        "Enclosure must return the same interned parent instance" );
    }
  }

  @Nested
  @DisplayName ( "Subject @Identity" )
  class SubjectIdentity {

    @Test
    @DisplayName ( "same substrate returns same subject instance" )
    void sameSubstrate_sameSubject () {
      Cortex cortex = cortex ();
      assertSame ( cortex.subject (), cortex.subject (),
        "Subject must be reference-stable for same substrate" );
    }

    @Test
    @DisplayName ( "circuit subject is reference-stable" )
    void circuitSubject_stable () {
      var circuit = cortex ().circuit ();
      try {
        assertSame ( circuit.subject (), circuit.subject () );
      } finally {
        circuit.close ();
      }
    }
  }
}
