package io.fullerstack.substrates;

import static io.humainary.substrates.api.Substrates.*;
import static org.junit.jupiter.api.Assertions.*;

import io.humainary.substrates.api.Substrates.Circuit;
import io.humainary.substrates.api.Substrates.Composer;
import io.humainary.substrates.api.Substrates.Conduit;
import io.humainary.substrates.api.Substrates.Id;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Subject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify the @Identity annotation contract from Substrates API.
 *
 * <p>The @Identity annotation indicates that a type uses **reference equality**:
 *
 * <ul>
 *   <li>Two instances are equal if and only if they refer to the same object
 *   <li>Use `==` for comparison, not `.equals()`
 *   <li>Hash codes are identity-based (System.identityHashCode)
 *   <li>This provides O(1) comparison performance
 * </ul>
 *
 * <p>@Identity annotated types in Substrates API:
 *
 * <ul>
 *   <li>{@link Id} - unique identifiers use reference equality
 *   <li>{@link Name} - interned names use reference equality
 *   <li>{@link Subject} - subjects use reference equality
 * </ul>
 */
@DisplayName ( "@Identity Annotation Contract Tests" )
class IdentityAnnotationContractTest {

  private Circuit circuit;

  @BeforeEach
  void setUp () {
    circuit = cortex ().circuit ( cortex ().name ( "test" ) );
  }

  @AfterEach
  void tearDown () {
    if ( circuit != null ) {
      circuit.close ();
    }
  }

  // ============================================================
  // Name @Identity contract
  // ============================================================

  @Nested
  @DisplayName ( "Name @Identity contract" )
  class NameIdentityContract {

    @Test
    @DisplayName ( "Same path produces same Name instance (interning)" )
    void samePath_producesSameInstance () {
      Name name1 = cortex ().name ( "foo.bar.baz" );
      Name name2 = cortex ().name ( "foo.bar.baz" );

      // @Identity means == should work for equality
      assertSame ( name1, name2, "Names with same path must be interned (same instance)" );
    }

    @Test
    @DisplayName ( "Different paths produce different Name instances" )
    void differentPaths_produceDifferentInstances () {
      Name name1 = cortex ().name ( "foo.bar" );
      Name name2 = cortex ().name ( "foo.baz" );

      assertNotSame ( name1, name2, "Names with different paths must be different instances" );
    }

    @Test
    @DisplayName ( "Name equality uses reference comparison" )
    void nameEquality_usesReferenceComparison () {
      Name name1 = cortex ().name ( "test.name" );
      Name name2 = cortex ().name ( "test.name" );

      // Both == and equals() should be true for interned names
      assertTrue ( name1 == name2, "Names should use reference equality (==)" );
      assertEquals ( name1, name2, "equals() should also work via reference equality" );
    }

    @Test
    @DisplayName ( "Name hashCode is identity-based" )
    void nameHashCode_isIdentityBased () {
      Name name1 = cortex ().name ( "hash.test" );
      Name name2 = cortex ().name ( "hash.test" );

      // Same instance means same identity hash
      assertEquals (
        System.identityHashCode ( name1 ),
        System.identityHashCode ( name2 ),
        "Same instance should have same identity hash" );
    }

    @Test
    @DisplayName ( "Name name(suffix) preserves interning" )
    void nameExtension_preservesInterning () {
      Name parent = cortex ().name ( "parent" );
      Name child1 = parent.name ( cortex ().name ( "child" ) );
      Name child2 = parent.name ( cortex ().name ( "child" ) );

      assertSame ( child1, child2, "Extended names must be interned (same instance)" );
    }

    @Test
    @DisplayName ( "Name can be used as HashMap key with reference equality" )
    void name_canBeUsedAsHashMapKey () {
      Name name = cortex ().name ( "key.test" );
      java.util.Map < Name, String > map = new java.util.HashMap <> ();

      map.put ( name, "value" );

      // Same interned name should find the value
      Name lookupName = cortex ().name ( "key.test" );
      assertSame ( name, lookupName, "Interned names must be same instance" );
      assertEquals ( "value", map.get ( lookupName ), "HashMap lookup should work with interned names" );
    }
  }

  // ============================================================
  // Id @Identity contract
  // ============================================================

  @Nested
  @DisplayName ( "Id @Identity contract" )
  class IdIdentityContract {

    @Test
    @DisplayName ( "Subject.id() returns same instance on repeated calls" )
    void subjectId_returnsSameInstance () {
      Subject < Circuit > subject = circuit.subject ();
      Id id1 = subject.id ();
      Id id2 = subject.id ();

      assertSame ( id1, id2, "subject.id() must return same Id instance each call" );
    }

    @Test
    @DisplayName ( "Different subjects have different Ids" )
    void differentSubjects_haveDifferentIds () {
      Circuit circuit1 = cortex ().circuit ( cortex ().name ( "c1" ) );
      Circuit circuit2 = cortex ().circuit ( cortex ().name ( "c2" ) );

      try {
        Id id1 = circuit1.subject ().id ();
        Id id2 = circuit2.subject ().id ();

        assertNotSame ( id1, id2, "Different subjects must have different Ids" );
      } finally {
        circuit1.close ();
        circuit2.close ();
      }
    }

    @Test
    @DisplayName ( "Id equality uses reference comparison" )
    void idEquality_usesReferenceComparison () {
      Subject < Circuit > subject = circuit.subject ();
      Id id = subject.id ();

      // Reference equality
      assertTrue ( id == subject.id (), "Id should use reference equality (==)" );
    }

    @Test
    @DisplayName ( "Id can be used in identity-based collections" )
    void id_canBeUsedInIdentityCollections () {
      Subject < Circuit > subject = circuit.subject ();
      Id id = subject.id ();

      java.util.Set < Id > set = new java.util.HashSet <> ();
      set.add ( id );

      // Same instance should be found
      assertTrue ( set.contains ( subject.id () ), "Same Id instance should be found in set" );
    }
  }

  // ============================================================
  // Subject @Identity contract
  // ============================================================

  @Nested
  @DisplayName ( "Subject @Identity contract" )
  class SubjectIdentityContract {

    @Test
    @DisplayName ( "Circuit.subject() returns same instance on repeated calls" )
    void circuitSubject_returnsSameInstance () {
      Subject < Circuit > subject1 = circuit.subject ();
      Subject < Circuit > subject2 = circuit.subject ();

      assertSame ( subject1, subject2, "circuit.subject() must return same instance each call" );
    }

    @Test
    @DisplayName ( "Conduit.subject() returns same instance on repeated calls" )
    void conduitSubject_returnsSameInstance () {
      Conduit < Pipe < String >, String > conduit =
        circuit.conduit ( cortex ().name ( "conduit" ), Composer.pipe () );

      Subject < ? > subject1 = conduit.subject ();
      Subject < ? > subject2 = conduit.subject ();

      assertSame ( subject1, subject2, "conduit.subject() must return same instance each call" );
    }

    @Test
    @DisplayName ( "Different circuits have different subjects" )
    void differentCircuits_haveDifferentSubjects () {
      Circuit circuit1 = cortex ().circuit ( cortex ().name ( "c1" ) );
      Circuit circuit2 = cortex ().circuit ( cortex ().name ( "c2" ) );

      try {
        Subject < Circuit > subject1 = circuit1.subject ();
        Subject < Circuit > subject2 = circuit2.subject ();

        assertNotSame ( subject1, subject2, "Different circuits must have different subjects" );
      } finally {
        circuit1.close ();
        circuit2.close ();
      }
    }

    @Test
    @DisplayName ( "Subject equality uses reference comparison" )
    void subjectEquality_usesReferenceComparison () {
      Subject < Circuit > subject = circuit.subject ();

      // Reference equality
      assertTrue ( subject == circuit.subject (), "Subject should use reference equality (==)" );
    }

    @Test
    @DisplayName ( "Subject can be used as Map key" )
    void subject_canBeUsedAsMapKey () {
      Subject < Circuit > subject = circuit.subject ();
      java.util.Map < Subject < ? >, String > map = new java.util.HashMap <> ();

      map.put ( subject, "value" );

      // Same instance should find the value
      assertEquals ( "value", map.get ( circuit.subject () ), "Same subject should find value in map" );
    }
  }

  // ============================================================
  // Cross-cutting @Identity verification
  // ============================================================

  @Nested
  @DisplayName ( "Cross-cutting @Identity verification" )
  class CrossCuttingIdentity {

    @Test
    @DisplayName ( "Name identity is preserved across parent/child relationships" )
    void nameIdentity_preservedAcrossHierarchy () {
      Name root = cortex ().name ( "root" );
      Name child = root.name ( cortex ().name ( "child" ) );
      Name grandchild = child.name ( cortex ().name ( "grandchild" ) );

      // Re-create the same path
      Name childAgain = root.name ( cortex ().name ( "child" ) );
      Name grandchildAgain = childAgain.name ( cortex ().name ( "grandchild" ) );

      assertSame ( child, childAgain, "Child names must be interned" );
      assertSame ( grandchild, grandchildAgain, "Grandchild names must be interned" );
    }

    @Test
    @DisplayName ( "Subject-Id relationship maintains identity" )
    void subjectIdRelationship_maintainsIdentity () {
      Subject < Circuit > subject = circuit.subject ();
      Id id = subject.id ();

      // For FsSubject, the Subject IS the Id (they're the same object)
      // This is an implementation optimization
      assertTrue (
        id == subject || id == subject.id (),
        "Subject and Id relationship should maintain identity semantics" );
    }

    @Test
    @DisplayName ( "IdentityHashMap works with @Identity types" )
    void identityHashMap_worksWithIdentityTypes () {
      java.util.IdentityHashMap < Name, String > nameMap = new java.util.IdentityHashMap <> ();
      java.util.IdentityHashMap < Subject < ? >, String > subjectMap = new java.util.IdentityHashMap <> ();

      Name name = cortex ().name ( "identity.test" );
      Subject < Circuit > subject = circuit.subject ();

      nameMap.put ( name, "name-value" );
      subjectMap.put ( subject, "subject-value" );

      // Lookup with same reference should work
      assertEquals ( "name-value", nameMap.get ( cortex ().name ( "identity.test" ) ) );
      assertEquals ( "subject-value", subjectMap.get ( circuit.subject () ) );
    }
  }
}
