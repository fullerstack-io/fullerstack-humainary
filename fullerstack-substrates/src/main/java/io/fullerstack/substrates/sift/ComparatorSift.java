package io.fullerstack.substrates.sift;

import io.humainary.substrates.api.Substrates.Sift;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Comparator-based implementation of Substrates.Sift interface.
 * <p>
 * < p >Provides comparison-based filtering operations (above, below, range, min, max, high, low)
 * using a Comparator for ordering-based sifting. Builds up predicate chains for sophisticated
 * filtering logic based on element ordering.
 * <p>
 * < p >Usage (from blog example):
 * < pre >
 * path.sift(
 * Integer::compareTo,
 * sift -> sift.above(0)
 * )
 * </pre >
 *
 * @param < E > emission type
 */
public class ComparatorSift < E > implements Sift < E > {

  private final Comparator < ? super E > comparator;
  private       Predicate < E >          predicate = value -> true; // Default: pass all

  public ComparatorSift ( Comparator < ? super E > comparator ) {
    this.comparator = Objects.requireNonNull ( comparator, "Comparator cannot be null" );
  }

  @Override
  public Sift < E > above ( E threshold ) {
    Objects.requireNonNull ( threshold, "Threshold cannot be null" );
    predicate = predicate.and ( value -> comparator.compare ( value, threshold ) > 0 );
    return this;
  }

  @Override
  public Sift < E > below ( E threshold ) {
    Objects.requireNonNull ( threshold, "Threshold cannot be null" );
    predicate = predicate.and ( value -> comparator.compare ( value, threshold ) < 0 );
    return this;
  }

  @Override
  public Sift < E > high () {
    // Track highest value seen
    E[] highest = (E[]) new Object[1];
    predicate = predicate.and ( value -> {
      if ( highest[0] == null || comparator.compare ( value, highest[0] ) > 0 ) {
        highest[0] = value;
        return true;
      }
      return false;
    } );
    return this;
  }

  @Override
  public Sift < E > low () {
    // Track lowest value seen
    E[] lowest = (E[]) new Object[1];
    predicate = predicate.and ( value -> {
      if ( lowest[0] == null || comparator.compare ( value, lowest[0] ) < 0 ) {
        lowest[0] = value;
        return true;
      }
      return false;
    } );
    return this;
  }

  @Override
  public Sift < E > max ( E maximum ) {
    Objects.requireNonNull ( maximum, "Maximum cannot be null" );
    predicate = predicate.and ( value -> comparator.compare ( value, maximum ) <= 0 );
    return this;
  }

  @Override
  public Sift < E > min ( E minimum ) {
    Objects.requireNonNull ( minimum, "Minimum cannot be null" );
    predicate = predicate.and ( value -> comparator.compare ( value, minimum ) >= 0 );
    return this;
  }

  @Override
  public Sift < E > range ( E lower, E upper ) {
    Objects.requireNonNull ( lower, "Lower bound cannot be null" );
    Objects.requireNonNull ( upper, "Upper bound cannot be null" );
    predicate = predicate.and ( value ->
      comparator.compare ( value, lower ) >= 0 && comparator.compare ( value, upper ) <= 0
    );
    return this;
  }

  /**
   * Tests whether a value passes the sift criteria.
   *
   * @param value value to test
   * @return true if value passes, false otherwise
   */
  public boolean test ( E value ) {
    return predicate.test ( value );
  }
}
