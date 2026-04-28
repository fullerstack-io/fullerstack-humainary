package io.fullerstack.substrates;

import io.fullerstack.substrates.FsOperators.Wrap;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// Fiber<E> — same-type per-emission processing recipe (Substrates 2.3).
///
/// A fiber is an immutable composition of stateless and stateful operators that
/// process emissions of type E without changing the type. Each operator method
/// returns a new FsFiber with the operator appended; the fiber value is reusable
/// and may be materialised against multiple pipes, each materialisation producing
/// independent state.
///
/// Operators are stored in an immutable `Wrap[]` (factory functions). Materialise
/// (via `pipe(Pipe<E>)`) walks the array front-to-back, wrapping the target pipe's
/// receiver with each operator. Operator implementations live in {@link FsOperators}.
@Provided
public final class FsFiber < E > implements Fiber < E > {

  // ─────────────────────────────────────────────────────────────────────────────
  // Immutable state
  // ─────────────────────────────────────────────────────────────────────────────

  private static final Wrap < ? >[] EMPTY = new Wrap < ? >[0];

  private final Wrap < ? >[] operators;
  private final int          count;

  /// Identity fiber — no operators.
  public FsFiber () {
    this.operators = EMPTY;
    this.count     = 0;
  }

  private FsFiber ( Wrap < ? >[] operators, int count ) {
    this.operators = operators;
    this.count     = count;
  }

  /// Returns a new fiber with the given operator appended.
  private FsFiber < E > append ( Wrap < E > op ) {
    Wrap < ? >[] newOps = new Wrap < ? >[count + 1];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[count] = op;
    return new FsFiber <> ( newOps, count + 1 );
  }

  /// Materialise this fiber into a concrete consumer chain that delivers to `target`.
  /// Each call produces independent state for stateful operators.
  ///
  /// operators[0] = first-added = innermost (closest to target)
  /// operators[n-1] = last-added = outermost (closest to input)
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  Consumer < E > materialise ( Consumer < E > target ) {
    Consumer c = target;
    for ( int i = 0; i < count; i++ ) c = ( (Wrap) operators[i] ).wrap ( c );
    return c;
  }

  /// Internal accessors used by FsFlow when inlining a fiber as a stage.
  Wrap < ? >[] operators () { return operators; }
  int operatorCount () { return count; }

  // ─────────────────────────────────────────────────────────────────────────────
  // Composition with a pipe
  // ─────────────────────────────────────────────────────────────────────────────

  /// Returns a pipe that processes emissions through this fiber before reaching `target`.
  ///
  /// Each call materialises a fresh consumer chain with independent stateful state.
  /// The fiber chain runs on the circuit thread (after dequeue). When target is
  /// a same-circuit FsPipe wrapping an FsChannel, the terminal submits
  /// {@code channel.cascadeDispatch} (the pre-built dispatch consumer) to transit —
  /// bypassing the channel's version check on the cascade hot path. Per spec
  /// §5.4.1 + §7.6.2, subscriber changes can't interleave with cascade drains,
  /// so the dispatch is guaranteed stable mid-cascade. STEM propagation is
  /// folded into dispatch during rebuild, so dispatch alone is correct.
  ///
  /// For non-channel receivers, submit the receiver directly.
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Pipe < E > pipe ( @NotNull Pipe < E > target ) {
    Objects.requireNonNull ( target, "target must not be null" );
    // Empty fiber elision — no operators means nothing to do. Returning
    // target directly skips the transit hop that wraps a no-op chain.
    if ( count == 0 ) return target;
    final Consumer < E > chain;
    if ( target instanceof FsPipe < ? > fp ) {
      final FsCircuit c = fp.circuit ();
      final Consumer < Object > targetReceiver = fp.receiver ();
      if ( targetReceiver instanceof FsChannel < ? > channel ) {
        chain = materialise ( v -> {
          Consumer < Object > d = channel.cascadeDispatch;
          c.submitTransit ( d != null ? d : channel, v );
        } );
      } else {
        chain = materialise ( v -> c.submitTransit ( targetReceiver, v ) );
      }
      return new FsPipe <> ( (Consumer < Object >) (Consumer < ? >) chain, c );
    }
    // Foreign Pipe: no access to its internals, use emit().
    chain = materialise ( target::emit );
    return new Pipe <> () {
      @Override
      public void emit ( @NotNull E emission ) {
        chain.accept ( emission );
      }
      @Override
      public io.humainary.substrates.api.Substrates.Subject < Pipe < E > > subject () {
        return target.subject ();
      }
    };
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Filtering / transforming operators
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > guard ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.Guard <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > guard ( @NotNull E initial, @NotNull BiPredicate < ? super E, ? super E > predicate ) {
    Objects.requireNonNull ( initial );
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.GuardStateful <> ( initial, predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > diff () {
    return append ( FsOperators.Diff::new );
  }

  @NotNull
  @Override
  public Fiber < E > diff ( @NotNull E initial ) {
    Objects.requireNonNull ( initial );
    return append ( d -> new FsOperators.Diff <> ( initial, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > limit ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "limit must not be negative" );
    return append ( d -> new FsOperators.Limit <> ( n, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > skip ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "skip must not be negative" );
    return append ( d -> new FsOperators.Skip <> ( n, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > replace ( @NotNull UnaryOperator < E > operator ) {
    Objects.requireNonNull ( operator );
    return append ( d -> new FsOperators.Replace <> ( operator, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > peek ( @NotNull Receptor < ? super E > receptor ) {
    Objects.requireNonNull ( receptor );
    return append ( d -> new FsOperators.Peek <> ( receptor, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > dropWhile ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.DropWhile <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > takeWhile ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.TakeWhile <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > integrate ( E initial, @NotNull BinaryOperator < E > accumulator,
    @NotNull Predicate < ? super E > fire ) {
    Objects.requireNonNull ( accumulator );
    Objects.requireNonNull ( fire );
    return append ( d -> new FsOperators.Integrate <> ( initial, accumulator, fire, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > relate ( E initial, @NotNull BinaryOperator < E > operator ) {
    Objects.requireNonNull ( operator );
    return append ( d -> new FsOperators.Relate <> ( initial, operator, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > reduce ( E initial, @NotNull BinaryOperator < E > operator ) {
    Objects.requireNonNull ( operator );
    return append ( d -> new FsOperators.Reduce <> ( initial, operator, d ) );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Comparator-based operators
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > above ( @NotNull Comparator < ? super E > comparator, @NotNull E lower ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    return append ( d -> new FsOperators.Guard <> ( v -> comparator.compare ( v, lower ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > below ( @NotNull Comparator < ? super E > comparator, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( upper );
    return append ( d -> new FsOperators.Guard <> ( v -> comparator.compare ( v, upper ) < 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > clamp ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    if ( comparator.compare ( lower, upper ) > 0 )
      throw new IllegalArgumentException ( "lower must not be greater than upper" );
    return append ( d -> new FsOperators.Clamp <> ( comparator, lower, upper, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > deadband ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    if ( comparator.compare ( lower, upper ) > 0 )
      throw new IllegalArgumentException ( "lower must not be greater than upper" );
    return append ( d -> new FsOperators.Guard <> (
      v -> comparator.compare ( v, lower ) < 0 || comparator.compare ( v, upper ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > range ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    return append ( d -> new FsOperators.Guard <> (
      v -> comparator.compare ( v, lower ) >= 0 && comparator.compare ( v, upper ) <= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > max ( @NotNull Comparator < ? super E > comparator, @NotNull E max ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( max );
    return append ( d -> new FsOperators.Guard <> ( v -> comparator.compare ( v, max ) <= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > min ( @NotNull Comparator < ? super E > comparator, @NotNull E min ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( min );
    return append ( d -> new FsOperators.Guard <> ( v -> comparator.compare ( v, min ) >= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > high ( @NotNull Comparator < ? super E > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( d -> new FsOperators.GuardStatefulNullable <> ( ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > low ( @NotNull Comparator < ? super E > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( d -> new FsOperators.GuardStatefulNullable <> ( ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) < 0, d ) );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // 2.3 operators
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > chance ( double probability ) {
    if ( Double.isNaN ( probability ) || probability < 0.0 || probability > 1.0 )
      throw new IllegalArgumentException ( "probability must be in [0.0, 1.0]" );
    if ( probability == 0.0 ) return append ( d -> v -> { /* drop all */ } );
    if ( probability == 1.0 ) return this;
    return append ( d -> new FsOperators.Chance <> ( probability, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > change ( @NotNull Function < ? super E, ? > key ) {
    Objects.requireNonNull ( key );
    return append ( d -> new FsOperators.Change <> ( key, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > delay ( int depth, @NotNull E initial ) {
    if ( depth <= 0 ) throw new IllegalArgumentException ( "depth must be positive" );
    Objects.requireNonNull ( initial );
    return append ( d -> new FsOperators.Delay <> ( depth, initial, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > edge ( @NotNull E initial, @NotNull BiPredicate < ? super E, ? super E > transition ) {
    Objects.requireNonNull ( initial );
    Objects.requireNonNull ( transition );
    return append ( d -> new FsOperators.GuardStateful <> ( initial, transition, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > every ( int interval ) {
    if ( interval <= 0 ) throw new IllegalArgumentException ( "interval must be positive" );
    return append ( d -> new FsOperators.Every <> ( interval, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > fiber ( @NotNull Fiber < E > next ) {
    Objects.requireNonNull ( next );
    if ( !( next instanceof FsFiber < E > nextFiber ) ) {
      throw new IllegalArgumentException ( "next fiber must be an FsFiber instance" );
    }
    if ( nextFiber.count == 0 ) return this;
    Wrap < ? >[] merged = new Wrap < ? >[count + nextFiber.count];
    // next runs after this — its operators are wrapped first (innermost),
    // then ours wrap that. So merged[0..nextCount) = next, merged[nextCount..) = this.
    System.arraycopy ( nextFiber.operators, 0, merged, 0, nextFiber.count );
    System.arraycopy ( operators, 0, merged, nextFiber.count, count );
    return new FsFiber <> ( merged, count + nextFiber.count );
  }

  @NotNull
  @Override
  public Fiber < E > hysteresis ( @NotNull Predicate < ? super E > enter, @NotNull Predicate < ? super E > exit ) {
    Objects.requireNonNull ( enter );
    Objects.requireNonNull ( exit );
    return append ( d -> new FsOperators.Hysteresis <> ( enter, exit, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > inhibit ( int refractory ) {
    if ( refractory < 0 ) throw new IllegalArgumentException ( "refractory must not be negative" );
    if ( refractory == 0 ) return this;
    return append ( d -> new FsOperators.Inhibit <> ( refractory, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > pulse ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.Guard <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > rolling ( int size, @NotNull BinaryOperator < E > combiner, @NotNull E identity ) {
    if ( size <= 0 ) throw new IllegalArgumentException ( "size must be positive" );
    Objects.requireNonNull ( combiner );
    Objects.requireNonNull ( identity );
    return append ( d -> new FsOperators.Rolling <> ( size, combiner, identity, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > steady ( int count ) {
    if ( count <= 0 ) throw new IllegalArgumentException ( "count must be positive" );
    return append ( d -> new FsOperators.SteadyN <> ( count, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > steady ( int count, @NotNull BiPredicate < ? super E, ? super E > predicate ) {
    if ( count <= 0 ) throw new IllegalArgumentException ( "count must be positive" );
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsOperators.SteadyPredicate <> ( count, predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > tumble ( int size, @NotNull BinaryOperator < E > combiner, @NotNull E identity ) {
    if ( size <= 0 ) throw new IllegalArgumentException ( "size must be positive" );
    Objects.requireNonNull ( combiner );
    Objects.requireNonNull ( identity );
    return append ( d -> new FsOperators.Tumble <> ( size, combiner, identity, d ) );
  }
}
