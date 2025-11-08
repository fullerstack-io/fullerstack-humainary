package io.fullerstack.substrates.id;

import io.humainary.substrates.api.Substrates.Id;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * UUID-based implementation of Substrates.Id using Lombok for boilerplate reduction.
 * <p>
 * < p >Provides unique identifiers for Substrates subjects.
 * <p>
 * < p >This class uses Lombok annotations to auto-generate:
 * < ul >
 * < li >Public constructor via {@code @AllArgsConstructor}</li >
 * < li >Null checks via {@code @NonNull}</li >
 * < li >Getter method for uuid field via {@code @Getter}</li >
 * < li >equals() and hashCode() based on uuid via {@code @EqualsAndHashCode}</li >
 * < li >toString() returning uuid.toString() via {@code @ToString}</li >
 * </ul >
 * <p>
 * < p >Use static factory methods {@link #generate()} or {@link #of(UUID)} to create instances.
 *
 * @see Id
 */
@Getter
public class UuidIdentifier implements Id {
  /**
   * The underlying UUID providing uniqueness.
   */
  private final UUID uuid;

  /**
   * Creates an ID with the specified UUID.
   *
   * @param uuid the UUID
   * @throws NullPointerException if uuid is null
   */
  public UuidIdentifier ( @NonNull UUID uuid ) {
    this.uuid = uuid;
  }

  /**
   * Returns the underlying UUID.
   *
   * @return the UUID
   */
  public UUID uuid () {
    return uuid;
  }

  /**
   * Generates a new random ID.
   *
   * @return new unique ID with random UUID
   */
  public static Id generate () {
    return new UuidIdentifier ( UUID.randomUUID () );
  }

  /**
   * Creates an ID from existing UUID.
   *
   * @param uuid the UUID
   * @return ID wrapping the UUID
   * @throws NullPointerException if uuid is null
   */
  public static Id of ( UUID uuid ) {
    return new UuidIdentifier ( uuid );
  }

  @Override
  public String toString () {
    return uuid.toString ();
  }

  @Override
  public boolean equals ( Object o ) {
    if ( this == o ) return true;
    if ( !( o instanceof UuidIdentifier ) ) return false;
    UuidIdentifier id = (UuidIdentifier) o;
    return uuid.equals ( id.uuid );
  }

  @Override
  public int hashCode () {
    return uuid.hashCode ();
  }
}
