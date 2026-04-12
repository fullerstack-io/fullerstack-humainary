package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.NotNull;
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

/// Immutable Flow<I,O> implementation for Substrates 2.0.
///
/// Each operator method returns a NEW FsFlow with the operator appended.
/// Flows are standalone values — created via Cortex.flow(), storable in fields,
/// sharable across threads, and materialisable multiple times with independent
/// state per materialisation.
///
/// Operators are stored as an immutable array. Materialisation (via Pipe.pipe(Flow))
/// walks the array front-to-back, wrapping the target consumer with each operator.
/// operators[0] is innermost (closest to target); operators[n-1] is outermost
/// (closest to input).
///
/// @param <I> input type (what Pipe.pipe(Flow) receives)
/// @param <O> output type (what the target pipe emits)
@Provided
public final class FsFlow < I, O > implements Flow < I, O > {

  // ═══════════════════════════════════════════════════════════════════════════
  // Concrete operator classes (same as 1.0 — good JIT inlining characteristics)
  // ═══════════════════════════════════════════════════════════════════════════

  static final class Guard < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          d;

    Guard ( Predicate < ? super E > p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( p.test ( v ) ) d.accept ( v ); }
  }

  static final class GuardStateful < E > implements Consumer < E > {
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E >                       d;
    Object prev;

    GuardStateful ( E initial, BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.prev = initial; this.p = p; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      if ( p.test ( (E) prev, v ) ) { prev = v; d.accept ( v ); }
    }
  }

  static final class Diff < E > implements Consumer < E > {
    final Consumer < E > d;
    Object prev;

    Diff ( Consumer < E > d )            { this.d = d; }

    Diff ( E initial, Consumer < E > d ) { this.prev = initial; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( !Objects.equals ( v, prev ) ) { prev = v; d.accept ( v ); }
    }
  }

  static final class Limit < E > implements Consumer < E > {
    final Consumer < E > d;
    final long           max;
    long count;

    Limit ( long max, Consumer < E > d ) { this.max = max; this.d = d; }

    @Override
    public void accept ( E v ) { if ( count++ < max ) d.accept ( v ); }
  }

  static final class Skip < E > implements Consumer < E > {
    final Consumer < E > d;
    final long           n;
    long count;

    Skip ( long n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( count++ >= n ) d.accept ( v ); }
  }

  static final class SampleN < E > implements Consumer < E > {
    final Consumer < E > d;
    final int            n;
    int count;

    SampleN ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( ++count % n == 0 ) d.accept ( v ); }
  }

  static final class SampleP < E > implements Consumer < E > {
    final Consumer < E > d;
    final double         p;

    SampleP ( double p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( Math.random () < p ) d.accept ( v ); }
  }

  static final class Replace < E > implements Consumer < E > {
    final UnaryOperator < E > t;
    final Consumer < E >      d;

    Replace ( UnaryOperator < E > t, Consumer < E > d ) { this.t = t; this.d = d; }

    @Override
    public void accept ( E v ) { E r = t.apply ( v ); if ( r != null ) d.accept ( r ); }
  }

  static final class Reduce < E > implements Consumer < E > {
    final BinaryOperator < E > op;
    final Consumer < E >       d;
    Object acc;

    Reduce ( E initial, BinaryOperator < E > op, Consumer < E > d ) {
      this.acc = initial; this.op = op; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      E r = op.apply ( (E) acc, v ); acc = r; d.accept ( r );
    }
  }

  static final class Peek < E > implements Consumer < E > {
    final Receptor < ? super E > r;
    final Consumer < E >         d;

    Peek ( Receptor < ? super E > r, Consumer < E > d ) { this.r = r; this.d = d; }

    @Override
    public void accept ( E v ) { r.receive ( v ); d.accept ( v ); }
  }

  static final class DropWhile < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          d;
    boolean dropping = true;

    DropWhile ( Predicate < ? super E > p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( dropping && p.test ( v ) ) return; dropping = false; d.accept ( v ); }
  }

  static final class TakeWhile < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          d;
    boolean taking = true;

    TakeWhile ( Predicate < ? super E > p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) { if ( taking && p.test ( v ) ) d.accept ( v ); else taking = false; }
  }

  static final class Integrate < E > implements Consumer < E > {
    final BinaryOperator < E >    op;
    final Predicate < ? super E > fire;
    final Consumer < E >          d;
    final Object                  initial;
    Object acc;

    Integrate ( E initial, BinaryOperator < E > op, Predicate < ? super E > fire, Consumer < E > d ) {
      this.initial = initial; this.acc = initial; this.op = op; this.fire = fire; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      E r = op.apply ( (E) acc, v ); acc = r;
      if ( fire.test ( r ) ) { acc = initial; d.accept ( r ); }
    }
  }

  static final class Relate < E > implements Consumer < E > {
    final BinaryOperator < E > op;
    final Consumer < E >       d;
    Object prev;

    Relate ( E initial, BinaryOperator < E > op, Consumer < E > d ) {
      this.prev = initial; this.op = op; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      E r = op.apply ( (E) prev, v ); prev = v;
      if ( r != null ) d.accept ( r );
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Operator factory interface + map marker
  // ═══════════════════════════════════════════════════════════════════════════

  @FunctionalInterface
  interface Wrap < E > {
    Consumer < E > wrap ( Consumer < E > downstream );
  }

  /// Marker for a map() node in the operator chain.
  record MapOp( Function < ?, ? > fn ) {}

  // ═══════════════════════════════════════════════════════════════════════════
  // Immutable state: an array of operator factories (Wrap or MapOp)
  // ═══════════════════════════════════════════════════════════════════════════

  private static final Object[] EMPTY = new Object[0];

  private final Object[] operators;
  private final int      count;

  /// Identity flow — no operators.
  public FsFlow () {
    this.operators = EMPTY;
    this.count     = 0;
  }

  private FsFlow ( Object[] operators, int count ) {
    this.operators = operators;
    this.count     = count;
  }

  /// Returns a new FsFlow with the given operator appended.
  @SuppressWarnings ( "unchecked" )
  private < X, Y > FsFlow < X, Y > append ( Object op ) {
    Object[] newOps = new Object[count + 1];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[count] = op;
    return new FsFlow <> ( newOps, count + 1 );
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Materialisation — called from Pipe.pipe(Flow)
  // ═══════════════════════════════════════════════════════════════════════════

  /// Materialises this flow into a concrete consumer chain.
  /// Each call produces independent state for stateful operators (diff, limit, etc.).
  ///
  /// operators[0] = first-added = innermost (closest to target)
  /// operators[n-1] = last-added = outermost (closest to input)
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  Consumer < I > materialise ( Consumer < O > target ) {
    Consumer c = target;
    for ( int i = 0; i < count; i++ ) {
      Object op = operators[i];
      if ( op instanceof Wrap w ) {
        c = w.wrap ( c );
      } else if ( op instanceof MapOp m ) {
        final Consumer downstream = c;
        final Function fn = m.fn ();
        c = v -> downstream.accept ( fn.apply ( v ) );
      }
    }
    return c;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow<I,O> API — each method returns a new immutable FsFlow
  // ═══════════════════════════════════════════════════════════════════════════

  @NotNull
  @Override
  public Flow < I, O > guard ( @NotNull Predicate < ? super I > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( (Wrap < I >) ( d -> new Guard <> ( predicate, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > guard ( I initial, @NotNull BiPredicate < ? super I, ? super I > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( (Wrap < I >) ( d -> new GuardStateful <> ( initial, predicate, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > diff () {
    return append ( (Wrap < I >) Diff::new );
  }

  @NotNull
  @Override
  public Flow < I, O > diff ( @NotNull I initial ) {
    Objects.requireNonNull ( initial );
    return append ( (Wrap < I >) ( d -> new Diff <> ( initial, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > limit ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "count must not be negative" );
    return append ( (Wrap < I >) ( d -> new Limit <> ( n, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > skip ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "count must not be negative" );
    return append ( (Wrap < I >) ( d -> new Skip <> ( n, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > sample ( int n ) {
    if ( n <= 0 ) throw new IllegalArgumentException ( "sample count must be positive" );
    return append ( (Wrap < I >) ( d -> new SampleN <> ( n, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > sample ( double probability ) {
    if ( Double.isNaN ( probability ) || probability < 0.0 || probability > 1.0 )
      throw new IllegalArgumentException ( "probability must be in [0.0, 1.0]" );
    if ( probability == 0.0 || probability == 1.0 ) return this;
    return append ( (Wrap < I >) ( d -> new SampleP <> ( probability, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > replace ( @NotNull UnaryOperator < I > operator ) {
    Objects.requireNonNull ( operator );
    return append ( (Wrap < I >) ( d -> new Replace <> ( operator, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > reduce ( @NotNull I initial, @NotNull BinaryOperator < I > operator ) {
    Objects.requireNonNull ( operator );
    return append ( (Wrap < I >) ( d -> new Reduce <> ( initial, operator, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > peek ( @NotNull Receptor < ? super I > receptor ) {
    Objects.requireNonNull ( receptor );
    return append ( (Wrap < I >) ( d -> new Peek <> ( receptor, d ) ) );
  }

  // ─── New 2.0 operators ────────────────────────────────────────────────────

  @NotNull
  @Override
  public < J > Flow < J, O > map ( @NotNull Function < ? super J, ? extends I > fn ) {
    Objects.requireNonNull ( fn );
    return append ( new MapOp ( fn ) );
  }

  @NotNull
  @Override
  public Flow < I, O > above ( @NotNull Comparator < ? super I > comparator, @NotNull I lower ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    return append ( (Wrap < I >) ( d -> new Guard <> ( v -> comparator.compare ( v, lower ) > 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > below ( @NotNull Comparator < ? super I > comparator, @NotNull I upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( upper );
    return append ( (Wrap < I >) ( d -> new Guard <> ( v -> comparator.compare ( v, upper ) < 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > clamp ( @NotNull Comparator < ? super I > comparator, @NotNull I lower, @NotNull I upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    return append ( (Wrap < I >) ( d -> new Guard <> (
      v -> comparator.compare ( v, lower ) >= 0 && comparator.compare ( v, upper ) <= 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > high ( @NotNull Comparator < ? super I > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( (Wrap < I >) ( d -> new GuardStateful <> ( null, ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) > 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > low ( @NotNull Comparator < ? super I > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( (Wrap < I >) ( d -> new GuardStateful <> ( null, ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) < 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > max ( @NotNull Comparator < ? super I > comparator, @NotNull I threshold ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( threshold );
    return append ( (Wrap < I >) ( d -> new Guard <> ( v -> comparator.compare ( v, threshold ) <= 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > min ( @NotNull Comparator < ? super I > comparator, @NotNull I threshold ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( threshold );
    return append ( (Wrap < I >) ( d -> new Guard <> ( v -> comparator.compare ( v, threshold ) >= 0, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > range ( @NotNull Comparator < ? super I > comparator, @NotNull I lower, @NotNull I upper ) {
    return clamp ( comparator, lower, upper );
  }

  @NotNull
  @Override
  public Flow < I, O > dropWhile ( @NotNull Predicate < ? super I > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( (Wrap < I >) ( d -> new DropWhile <> ( predicate, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > takeWhile ( @NotNull Predicate < ? super I > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( (Wrap < I >) ( d -> new TakeWhile <> ( predicate, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > integrate ( @NotNull I initial, @NotNull BinaryOperator < I > accumulator, @NotNull Predicate < ? super I > fire ) {
    Objects.requireNonNull ( accumulator );
    Objects.requireNonNull ( fire );
    return append ( (Wrap < I >) ( d -> new Integrate <> ( initial, accumulator, fire, d ) ) );
  }

  @NotNull
  @Override
  public Flow < I, O > relate ( @NotNull I initial, @NotNull BinaryOperator < I > operator ) {
    Objects.requireNonNull ( operator );
    return append ( (Wrap < I >) ( d -> new Relate <> ( initial, operator, d ) ) );
  }

}
