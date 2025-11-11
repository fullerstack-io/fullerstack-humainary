package io.fullerstack.substrates.id;

import io.humainary.substrates.api.Substrates.Id;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight sequential ID implementation using AtomicLong.
 * <p>
 * Provides unique identifiers with minimal allocation overhead (~24 bytes vs UUID's ~56 bytes).
 * <p>
 * Thread-safe via AtomicLong.incrementAndGet().
 * <p>
 * Use this for performance-critical paths where global uniqueness across JVM restarts
 * is not required (e.g., Subject IDs within a single JVM session).
 *
 * @see Id
 * @see UuidIdentifier
 */
@Getter
@EqualsAndHashCode
public class SequentialIdentifier implements Id {

  private static final AtomicLong SEQUENCE = new AtomicLong(0);

  /**
   * The unique sequential value.
   */
  private final long value;

  /**
   * Creates an ID with the specified value.
   *
   * @param value the sequential value
   */
  public SequentialIdentifier(long value) {
    this.value = value;
  }

  /**
   * Generates a new sequential ID.
   *
   * @return new unique ID with sequential value
   */
  public static Id generate() {
    return new SequentialIdentifier(SEQUENCE.incrementAndGet());
  }

  /**
   * Creates an ID from existing value.
   *
   * @param value the sequential value
   * @return ID with the specified value
   */
  public static Id of(long value) {
    return new SequentialIdentifier(value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
