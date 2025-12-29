package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Fluent;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Sift;
import io.humainary.substrates.api.Substrates.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/// Configurable filter builder for comparator-based filtering operations.
///
/// FsSift collects filtering conditions and builds a composite predicate
/// that checks all conditions. Supports:
/// - Absolute bounds: min, max, range, above, below
/// - Stateful extrema tracking: high, low
///
/// @param <E> the class type of emitted values
@Provided
@Temporal
final class FsSift < E >
  implements Sift < E > {

  private final Comparator < ? super E > comparator;
  private final List < Predicate < E > > filters = new ArrayList <> ();

  FsSift ( Comparator < ? super E > comparator ) {
    this.comparator = comparator;
  }

  /// Returns a predicate that ANDs all configured filters.
  Predicate < E > predicate () {
    if ( filters.isEmpty () ) {
      return v -> true;
    }
    return v -> {
      for ( Predicate < E > f : filters ) {
        if ( !f.test ( v ) ) return false;
      }
      return true;
    };
  }

  @Fluent
  @Override
  public Sift < E > above ( E lower ) {
    filters.add ( v -> comparator.compare ( v, lower ) > 0 );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > below ( E upper ) {
    filters.add ( v -> comparator.compare ( v, upper ) < 0 );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > high () {
    Object[] currentHigh = { null };
    boolean[] hasValue = { false };
    filters.add ( v -> {
      @SuppressWarnings ( "unchecked" )
      E high = (E) currentHigh[0];
      if ( !hasValue[0] || comparator.compare ( v, high ) > 0 ) {
        currentHigh[0] = v;
        hasValue[0] = true;
        return true;
      }
      return false;
    } );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > low () {
    Object[] currentLow = { null };
    boolean[] hasValue = { false };
    filters.add ( v -> {
      @SuppressWarnings ( "unchecked" )
      E low = (E) currentLow[0];
      if ( !hasValue[0] || comparator.compare ( v, low ) < 0 ) {
        currentLow[0] = v;
        hasValue[0] = true;
        return true;
      }
      return false;
    } );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > max ( E max ) {
    filters.add ( v -> comparator.compare ( v, max ) <= 0 );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > min ( E min ) {
    filters.add ( v -> comparator.compare ( v, min ) >= 0 );
    return this;
  }

  @Fluent
  @Override
  public Sift < E > range ( E lower, E upper ) {
    filters.add ( v -> comparator.compare ( v, lower ) >= 0 && comparator.compare ( v, upper ) <= 0 );
    return this;
  }

}
