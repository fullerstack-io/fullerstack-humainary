package io.fullerstack.substrates.id;

import io.humainary.substrates.api.Substrates.Id;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidIdentifierTest {

  @Test
  void shouldGenerateUniqueIds () {
    Set < Id > ids = new HashSet <> ();
    for ( int i = 0; i < 1000; i++ ) {
      ids.add ( UuidIdentifier.generate () );
    }

    assertThat ( ids ).hasSize ( 1000 );
  }

  @Test
  void shouldCreateIdFromUUID () {
    UUID uuid = UUID.randomUUID ();
    UuidIdentifier id = (UuidIdentifier) UuidIdentifier.of ( uuid );

    assertThat ( id.uuid () ).isEqualTo ( uuid );
  }

  @Test
  void shouldSupportEquality () {
    UUID uuid = UUID.randomUUID ();
    Id id1 = UuidIdentifier.of ( uuid );
    Id id2 = UuidIdentifier.of ( uuid );

    assertThat ( id1 ).isEqualTo ( id2 );
    assertThat ( id1.hashCode () ).isEqualTo ( id2.hashCode () );
  }

  @Test
  void shouldReturnUUIDAsString () {
    UuidIdentifier id = (UuidIdentifier) UuidIdentifier.generate ();

    assertThat ( id.toString () ).isNotNull ();
    assertThat ( id.toString () ).matches ( "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" );
  }
}
