package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// Immutable Flow<I,O> implementation for Substrates 2.3.
///
/// Flow now carries only type-changing composition: `map`, `flow`, `fiber`, `pipe`.
/// Per-emission operators (diff, guard, limit, peek, etc.) live on Fiber<E>.
/// Each method returns a new immutable FsFlow.
///
/// The shared operator classes (Guard, Diff, Limit, Skip, Replace, Reduce, Peek,
/// DropWhile, TakeWhile, Integrate, Relate, GuardStateful) are kept here as
/// package-private nested classes because both FsFlow's `fiber` composition and
/// FsFiber's per-emission operators reuse them.
///
/// @param <I> input type (what Pipe.pipe(Flow) receives)
/// @param <O> output type (what the target pipe emits)
@Provided
public final class FsFlow < I, O > implements Flow < I, O > {

  // ═══════════════════════════════════════════════════════════════════════════
  // Shared operator classes (used by FsFlow.fiber composition + FsFiber)
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
    boolean has;

    Diff ( Consumer < E > d )            { this.d = d; }

    Diff ( E initial, Consumer < E > d ) { this.prev = initial; this.has = true; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( !has || !Objects.equals ( v, prev ) ) { prev = v; has = true; d.accept ( v ); }
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
  // Operator factory interface + map marker + fiber-attachment marker
  // ═══════════════════════════════════════════════════════════════════════════

  @FunctionalInterface
  interface Wrap < E > {
    Consumer < E > wrap ( Consumer < E > downstream );
  }

  /// Marker for a map() node in the operator chain.
  record MapOp( Function < ?, ? > fn ) {}

  /// Marker for an attached Fiber<O>. Fiber.materialise() runs at the output
  /// side of the flow.
  record FiberOp( FsFiber < ? > fiber ) {}

  // ═══════════════════════════════════════════════════════════════════════════
  // Immutable state
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
  // Materialisation — called from Pipe.pipe(Flow) [legacy] or Flow.pipe(Pipe)
  // ═══════════════════════════════════════════════════════════════════════════

  /// Materialises this flow into a concrete consumer chain.
  /// Each call produces independent state for stateful operators.
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
        c = v -> {
          Object r = fn.apply ( v );
          if ( r != null ) downstream.accept ( r );
        };
      } else if ( op instanceof FiberOp fo ) {
        FsFiber f = (FsFiber) fo.fiber ();
        c = f.materialise ( c );
      }
    }
    return c;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow<I,O> API (2.3) — only map/flow/fiber/pipe
  // ═══════════════════════════════════════════════════════════════════════════

  @NotNull
  @Override
  public < P > Flow < I, P > map ( @NotNull Function < ? super O, ? extends P > fn ) {
    Objects.requireNonNull ( fn );
    return append ( new MapOp ( fn ) );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Flow < I, O > fiber ( @NotNull Fiber < O > fiber ) {
    Objects.requireNonNull ( fiber );
    if ( !( fiber instanceof FsFiber < ? > fsFiber ) ) {
      throw new IllegalArgumentException ( "fiber must be an FsFiber instance" );
    }
    return append ( new FiberOp ( fsFiber ) );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < P > Flow < I, P > flow ( @NotNull Flow < ? super O, ? extends P > next ) {
    Objects.requireNonNull ( next );
    if ( !( next instanceof FsFlow < ?, ? > nextFlow ) ) {
      throw new IllegalArgumentException ( "next flow must be an FsFlow instance" );
    }
    if ( nextFlow.count == 0 ) return (Flow < I, P >) (Flow < ?, ? >) this;
    Object[] merged = new Object[count + nextFlow.count];
    // operators[0..count) of `this` — innermost at index 0 — must remain innermost.
    // next's operators run after `this`, so they wrap further out (higher indices).
    System.arraycopy ( operators, 0, merged, 0, count );
    System.arraycopy ( nextFlow.operators, 0, merged, count, nextFlow.count );
    return new FsFlow <> ( merged, count + nextFlow.count );
  }

  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Pipe < I > pipe ( @NotNull Pipe < O > target ) {
    Objects.requireNonNull ( target );
    final Consumer < I > chain = materialise ( target::emit );
    if ( target instanceof FsPipe < ? > fp ) {
      return new FsPipe <> ( (Consumer < Object >) (Consumer < ? >) chain, fp.circuit () );
    }
    // Foreign Pipe — wrap synchronously
    return new Pipe <> () {
      @Override
      public void emit ( @NotNull I emission ) {
        chain.accept ( emission );
      }
      @Override
      public io.humainary.substrates.api.Substrates.Subject < Pipe < I > > subject () {
        @SuppressWarnings ( "unchecked" )
        var s = (io.humainary.substrates.api.Substrates.Subject < Pipe < I > >) (io.humainary.substrates.api.Substrates.Subject < ? >) target.subject ();
        return s;
      }
    };
  }
}
