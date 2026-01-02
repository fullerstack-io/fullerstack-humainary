package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Configurer;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.Fluent;
import io.humainary.substrates.api.Substrates.Name;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;
import io.humainary.substrates.api.Substrates.Sift;
import io.humainary.substrates.api.Substrates.Subject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A configurable processing pipeline for data transformation.
 *
 * <p>
 * Flow provides operators like diff, guard, limit, sample, etc. Each operator
 * returns a new Flow representing the extended pipeline. Operators are composed
 * in the order they are added (left-to-right).
 *
 * @param <E>
 *            the class type of emitted value
 */
@Provided
public final class FsFlow < E > implements Flow < E > {

  private final Name      name; // null for anonymous
  private final FsCircuit circuit;

  /// The target pipe that receives emissions after all transformations.
  private final Pipe < E > target;

  /// Single operator optimization: avoid ArrayList allocation for common single-operator case.
  /// First operator stored here; ArrayList only created if 2+ operators added.
  private Function < Consumer < E >, Consumer < E > >          firstOp;
  private List < Function < Consumer < E >, Consumer < E > > > moreOps;

  /**
   * Creates a new flow targeting the given pipe (lazy subject).
   *
   * @param name
   *            the pipe name (null for anonymous)
   * @param circuit
   *            the circuit that owns this flow
   * @param target
   *            the pipe that receives emissions
   */
  public FsFlow ( Name name, FsCircuit circuit, Pipe < E > target ) {
    this.name = name;
    this.circuit = circuit;
    this.target = target;
  }

  /**
   * Creates a new flow targeting the given pipe (eager subject).
   *
   * @param subject
   *            the pre-computed subject
   * @param circuit
   *            the circuit that owns this flow
   * @param target
   *            the pipe that receives emissions
   */
  FsFlow ( Subject < Pipe < E > > subject, FsCircuit circuit, Pipe < E > target ) {
    this.name = subject.name ();
    this.circuit = circuit;
    this.target = target;
  }

  /// Adds an operator to this flow. Optimized for single-operator case.
  private void addOp ( Function < Consumer < E >, Consumer < E > > op ) {
    if ( firstOp == null ) {
      firstOp = op;
    } else if ( moreOps == null ) {
      moreOps = new ArrayList <> ( 4 );
      moreOps.add ( op );
    } else {
      moreOps.add ( op );
    }
  }

  /// Returns the pipe that applies this flow's transformations.
  /// Operators are composed in order: first operator wraps second, etc.
  public Pipe < E > pipe () {
    // Start with the target's receiver to avoid double-enqueue.
    Consumer < E > consumer;
    if ( target instanceof FsPipe < E > fsPipe ) {
      consumer = fsPipe.receiver ();
    } else {
      consumer = target::emit;
    }
    // Apply operators in reverse order so the first-added operator executes first
    if ( moreOps != null ) {
      for ( int i = moreOps.size () - 1; i >= 0; i-- ) {
        consumer = moreOps.get ( i ).apply ( consumer );
      }
    }
    if ( firstOp != null ) {
      consumer = firstOp.apply ( consumer );
    }
    return new FsPipe <> ( name, circuit, consumer );
  }

  @Fluent
  @Override
  public Flow < E > diff () {
    addOp ( downstream -> {
      Object[] prev = {null};
      return v -> {
        if ( !Objects.equals ( v, prev[0] ) ) {
          prev[0] = v;
          downstream.accept ( v );
        }
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > diff ( E initial ) {
    addOp ( downstream -> {
      Object[] prev = {initial};
      return v -> {
        if ( !Objects.equals ( v, prev[0] ) ) {
          prev[0] = v;
          downstream.accept ( v );
        }
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > guard ( Predicate < ? super E > predicate ) {
    addOp ( downstream -> v -> {
      if ( predicate.test ( v ) ) {
        downstream.accept ( v );
      }
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > guard ( E initial, BiPredicate < ? super E, ? super E > predicate ) {
    addOp ( downstream -> {
      Object[] prev = {initial};
      return v -> {
        @SuppressWarnings ( "unchecked" )
        E p = (E) prev[0];
        if ( predicate.test ( p, v ) ) {
          prev[0] = v;
          downstream.accept ( v );
        }
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > limit ( int limit ) {
    return limit ( (long) limit );
  }

  @Fluent
  @Override
  public Flow < E > limit ( long limit ) {
    addOp ( downstream -> {
      long[] count = {0};
      return v -> {
        if ( count[0] < limit ) {
          count[0]++;
          downstream.accept ( v );
        }
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > peek ( Receptor < ? super E > receptor ) {
    addOp ( downstream -> v -> {
      receptor.receive ( v );
      downstream.accept ( v );
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > reduce ( E initial, BinaryOperator < E > operator ) {
    addOp ( downstream -> {
      Object[] acc = {initial};
      return v -> {
        @SuppressWarnings ( "unchecked" )
        E a = (E) acc[0];
        E result = operator.apply ( a, v );
        acc[0] = result;
        downstream.accept ( result );
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > replace ( UnaryOperator < E > transformer ) {
    addOp ( downstream -> v -> downstream.accept ( transformer.apply ( v ) ) );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sample ( int sample ) {
    addOp ( downstream -> {
      int[] count = {0};
      return v -> {
        // Emit on every Nth element (N-1, 2N-1, 3N-1, ...)
        // e.g., sample(3) emits at indices 2, 5, 8 (0-based)
        if ( ++count[0] % sample == 0 ) {
          downstream.accept ( v );
        }
      };
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sample ( double probability ) {
    addOp ( downstream -> v -> {
      if ( Math.random () < probability ) {
        downstream.accept ( v );
      }
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > sift ( Comparator < ? super E > comparator, Configurer < Sift < E > > configurer ) {
    FsSift < E > sift = new FsSift <> ( comparator );
    configurer.configure ( sift );
    Predicate < E > filter = sift.predicate ();
    addOp ( downstream -> v -> {
      if ( filter.test ( v ) ) {
        downstream.accept ( v );
      }
    } );
    return this;
  }

  @Fluent
  @Override
  public Flow < E > skip ( long n ) {
    addOp ( downstream -> {
      long[] count = {0};
      return v -> {
        if ( count[0] >= n ) {
          downstream.accept ( v );
        } else {
          count[0]++;
        }
      };
    } );
    return this;
  }
}
