package io.fullerstack.substrates.flow;

import io.humainary.substrates.api.Substrates.*;
import io.fullerstack.substrates.sift.ComparatorSift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.*;

/**
 * Implementation of Substrates.Flow that regulates emission flow characteristics.
 * <p>
 * < p >< b >Metaphor:</b > Like a flow regulator in hydraulics, this class controls the
 * characteristics of emissions flowing through a Pipe - adjusting rate (limit, skip, sample),
 * filtering content (guard, diff, sift), and transforming values (replace, reduce).
 * <p>
 * < p >< b >Note:</b > Flow is the latest name in the API evolution: Path → Segment → Flow.
 * The API evolved to use "Flow" to better represent the continuous data stream concept
 * with Consumer< Flow > replacing the Sequencer pattern.
 * <p>
 * < p >FlowRegulator provides a fluent API for configuring transformation rules that are applied
 * to emissions flowing through a Pipe. Transformations include filtering, mapping, reducing,
 * sampling, and comparison-based sifting.
 * <p>
 * < p >Key characteristics:
 * < ul >
 * < li >Immutable - each transformation returns a new Flow</li >
 * < li >Lazy - transformations are captured, not executed immediately</li >
 * < li >Composable - transformations chain fluently with @Fluent annotations</li >
 * < li >Executed by Circuit - not by emitting thread (per Humainary design)</li >
 * < li >Optimized - fuses adjacent transformations like JVM hot loop optimization</li >
 * </ul >
 * <p>
 * < p >< b >Regulation Optimizations:</b >
 * < ul >
 * < li >Adjacent skip() calls are fused: skip(3).skip(2) → skip(5)</li >
 * < li >Adjacent limit() calls are minimized: limit(10).limit(5) → limit(5)</li >
 * < li >Reduces transformation overhead in hot paths</li >
 * </ul >
 * <p>
 * < p >Usage (RC1 with Consumer):
 * < pre >
 * channel.pipe(
 * flow -> flow
 * .guard(value -> value > 0)
 * .limit(10)
 * .reduce(0, Integer::sum)
 * )
 * </pre >
 *
 * @param < E > emission type
 * @see < a href="https://humainary.io/blog/observability-x-channels/">Observability X - Channels</a >
 * @see < a href="https://humainary.io/blog/observability-x-staging-state/">Observability X - Staging State</a >
 */
public class FlowRegulator < E > implements Flow < E > {

  /**
   * Transformation operations that can be applied to emissions.
   */
  private final List < Function < E, TransformResult < E > > > transformations = new ArrayList <> ();

  /**
   * Metadata about transformations for optimization.
   */
  private enum TransformType {
    SKIP, LIMIT, GUARD, REPLACE, OTHER
  }

  private static class TransformMetadata {
    final TransformType type;
    final Object        metadata; // Skip count, limit count, etc.

    TransformMetadata ( TransformType type, Object metadata ) {
      this.type = type;
      this.metadata = metadata;
    }
  }

  private final List < TransformMetadata > metadata = new ArrayList <> ();

  public FlowRegulator () {
  }

  @Override
  public Flow < E > diff () {
    E[] lastValue = (E[]) new Object[1];
    return addTransformation ( value -> {
      if ( lastValue[0] == null ) {
        lastValue[0] = value;
        return TransformResult.pass ( value );
      }
      if ( !Objects.equals ( value, lastValue[0] ) ) {
        lastValue[0] = value;
        return TransformResult.pass ( value );
      }
      return TransformResult.filter ();
    } );
  }

  @Override
  public Flow < E > diff ( E initial ) {
    E[] lastValue = (E[]) new Object[]{initial};
    return addTransformation ( value -> {
      if ( !Objects.equals ( value, lastValue[0] ) ) {
        lastValue[0] = value;
        return TransformResult.pass ( value );
      }
      return TransformResult.filter ();
    } );
  }

  @Override
  public Flow < E > forward ( Pipe < ? super E > pipe ) {
    Objects.requireNonNull ( pipe, "Pipe cannot be null" );
    return addTransformation ( value -> {
      pipe.emit ( value );
      return TransformResult.pass ( value );
    } );
  }

  public Flow < E > forward ( Consumer < ? super E > consumer ) {
    Objects.requireNonNull ( consumer, "Consumer cannot be null" );
    return addTransformation ( value -> {
      consumer.accept ( value );
      return TransformResult.pass ( value );
    } );
  }

  @Override
  public Flow < E > guard ( Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate, "Predicate cannot be null" );
    return addTransformation ( value ->
      predicate.test ( value ) ? TransformResult.pass ( value ) : TransformResult.filter ()
    );
  }

  @Override
  public Flow < E > guard ( E reference, BiPredicate < ? super E, ? super E > predicate ) {
    Objects.requireNonNull ( predicate, "BiPredicate cannot be null" );
    E[] previous = (E[]) new Object[]{reference};
    return addTransformation ( value -> {
      boolean passes = predicate.test ( previous[0], value );
      if ( passes ) {
        previous[0] = value; // Update previous for next emission
        return TransformResult.pass ( value );
      }
      return TransformResult.filter ();
    } );
  }

  @Override
  public Flow < E > limit ( int maxEmissions ) {
    return limit ( (long) maxEmissions );
  }

  @Override
  public Flow < E > limit ( long maxEmissions ) {
    if ( maxEmissions < 0 ) {
      throw new IllegalArgumentException ( "Limit must be non-negative" );
    }

    // OPTIMIZATION: Fuse adjacent limit() calls - take minimum
    // limit(10).limit(5) → limit(5)
    if ( !metadata.isEmpty () && metadata.get ( metadata.size () - 1 ).type == TransformType.LIMIT ) {
      TransformMetadata lastMeta = metadata.get ( metadata.size () - 1 );
      long existingLimit = (Long) lastMeta.metadata;
      long fusedLimit = Math.min ( existingLimit, maxEmissions );

      // Remove last transformation and metadata
      transformations.remove ( transformations.size () - 1 );
      metadata.remove ( metadata.size () - 1 );

      // Re-add with fused limit
      return limit ( fusedLimit );
    }

    long[] counter = {0};
    addTransformation ( value -> {
      if ( counter[0] >= maxEmissions ) {
        return TransformResult.filter (); // Limit reached
      }
      counter[0]++;
      return TransformResult.pass ( value );
    } );
    metadata.add ( new TransformMetadata ( TransformType.LIMIT, maxEmissions ) );
    return this;
  }

  @Override
  public Flow < E > skip ( long n ) {
    if ( n < 0 ) {
      throw new IllegalArgumentException ( "Skip count must be non-negative" );
    }

    // OPTIMIZATION: Fuse adjacent skip() calls - sum the counts
    // skip(3).skip(2) → skip(5)
    if ( !metadata.isEmpty () && metadata.get ( metadata.size () - 1 ).type == TransformType.SKIP ) {
      TransformMetadata lastMeta = metadata.get ( metadata.size () - 1 );
      long existingSkip = (Long) lastMeta.metadata;
      long fusedSkip = existingSkip + n;

      // Remove last transformation and metadata
      transformations.remove ( transformations.size () - 1 );
      metadata.remove ( metadata.size () - 1 );

      // Re-add with fused skip
      return skip ( fusedSkip );
    }

    long[] counter = {0};
    addTransformation ( value -> {
      if ( counter[0] < n ) {
        counter[0]++;
        return TransformResult.filter (); // Still skipping
      }
      return TransformResult.pass ( value );
    } );
    metadata.add ( new TransformMetadata ( TransformType.SKIP, n ) );
    return this;
  }

  @Override
  public Flow < E > peek ( Consumer < ? super E > consumer ) {
    Objects.requireNonNull ( consumer, "Consumer cannot be null" );
    return addTransformation ( value -> {
      consumer.accept ( value );
      return TransformResult.pass ( value );
    } );
  }

  @Override
  public Flow < E > reduce ( E identity, BinaryOperator < E > accumulator ) {
    Objects.requireNonNull ( accumulator, "Accumulator cannot be null" );
    E[] accumulated = (E[]) new Object[]{identity};
    return addTransformation ( value -> {
      accumulated[0] = accumulator.apply ( accumulated[0], value );
      return TransformResult.replace ( accumulated[0] );
    } );
  }

  @Override
  public Flow < E > replace ( UnaryOperator < E > mapper ) {
    Objects.requireNonNull ( mapper, "Mapper cannot be null" );
    return addTransformation ( value -> TransformResult.replace ( mapper.apply ( value ) ) );
  }

  @Override
  public Flow < E > sample ( int n ) {
    if ( n <= 0 ) {
      throw new IllegalArgumentException ( "Sample rate must be positive" );
    }
    long[] counter = {0};
    return addTransformation ( value -> {
      counter[0]++;
      return ( counter[0] % n == 0 ) ? TransformResult.pass ( value ) : TransformResult.filter ();
    } );
  }

  @Override
  public Flow < E > sample ( double probability ) {
    if ( probability < 0.0 || probability > 1.0 ) {
      throw new IllegalArgumentException ( "Probability must be between 0.0 and 1.0" );
    }
    return addTransformation ( value ->
      Math.random () < probability ? TransformResult.pass ( value ) : TransformResult.filter ()
    );
  }

  @Override
  public Flow < E > sift ( Comparator < ? super E > comparator, Consumer < Sift < E > > configurer ) {
    Objects.requireNonNull ( comparator, "Comparator cannot be null" );
    Objects.requireNonNull ( configurer, "Configurer cannot be null" );

    ComparatorSift < E > sift = new ComparatorSift <> ( comparator );
    configurer.accept ( sift );

    return addTransformation ( value ->
      sift.test ( value ) ? TransformResult.pass ( value ) : TransformResult.filter ()
    );
  }

  /**
   * Applies all transformations to an emission.
   *
   * @param emission the value to transform
   * @return the transformed value, or null if filtered
   */
  public E apply ( E emission ) {
    E current = emission;
    for ( Function < E, TransformResult < E > > transformation : transformations ) {
      TransformResult < E > result = transformation.apply ( current );
      if ( result.isFiltered () ) {
        return null; // Filtered out
      }
      current = result.getValue ();
    }
    return current;
  }

  private Flow < E > addTransformation ( Function < E, TransformResult < E > > transformation ) {
    this.transformations.add ( transformation );
    this.metadata.add ( new TransformMetadata ( TransformType.OTHER, null ) );
    return this;
  }

  /**
   * Result of applying a transformation.
   */
  private static class TransformResult < E > {
    private final E       value;
    private final boolean filtered;

    private TransformResult ( E value, boolean filtered ) {
      this.value = value;
      this.filtered = filtered;
    }

    static < E > TransformResult < E > pass ( E value ) {
      return new TransformResult <> ( value, false );
    }

    static < E > TransformResult < E > replace ( E value ) {
      return new TransformResult <> ( value, false );
    }

    static < E > TransformResult < E > filter () {
      return new TransformResult <> ( null, true );
    }

    E getValue () {
      return value;
    }

    boolean isFiltered () {
      return filtered;
    }
  }
}
