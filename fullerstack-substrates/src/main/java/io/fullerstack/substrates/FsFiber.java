package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Receptor;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
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
/// Operators are stored in an immutable Object[] (Wrap factories). Materialisation
/// (via `pipe(Pipe<E>)`) walks the array front-to-back, wrapping the target pipe's
/// receiver with each operator. The resulting consumer chain is what `Pipe.emit`
/// drives.
///
/// Reuses the operator classes defined inside FsFlow (Guard, Diff, Limit, Skip,
/// Replace, Reduce, Peek, DropWhile, TakeWhile, Integrate, Relate). New 2.3
/// operators (chance, change, deadband, delay, edge, every, hysteresis, inhibit,
/// pulse, rolling, steady, tumble) are defined here.
@Provided
public final class FsFiber < E > implements Fiber < E > {

  // ─────────────────────────────────────────────────────────────────────────────
  // Operator factory shape
  // ─────────────────────────────────────────────────────────────────────────────

  @FunctionalInterface
  interface Wrap < E > {
    Consumer < E > wrap ( Consumer < E > downstream );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Immutable state
  // ─────────────────────────────────────────────────────────────────────────────

  private static final Object[] EMPTY = new Object[0];

  private final Object[] operators;
  private final int      count;

  /// Identity fiber — no operators.
  public FsFiber () {
    this.operators = EMPTY;
    this.count = 0;
  }

  private FsFiber ( Object[] operators, int count ) {
    this.operators = operators;
    this.count = count;
  }

  /// Returns a new fiber with the given operator appended.
  @SuppressWarnings ( "unchecked" )
  private FsFiber < E > append ( Wrap < E > op ) {
    Object[] newOps = new Object[count + 1];
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
    for ( int i = 0; i < count; i++ ) {
      Wrap w = (Wrap) operators[i];
      c = w.wrap ( c );
    }
    return c;
  }

  /// Internal accessor: returns the operator array for FsFlow composition.
  Object[] operators () { return operators; }
  int operatorCount () { return count; }

  // ─────────────────────────────────────────────────────────────────────────────
  // Composition with a pipe
  // ─────────────────────────────────────────────────────────────────────────────

  /// Returns a pipe that processes emissions through this fiber before reaching `target`.
  ///
  /// Each call materialises a fresh consumer chain with independent stateful state.
  /// The fiber chain runs on the circuit thread (after dequeue). When target is
  /// a same-circuit FsPipe wrapping an FsChannel, the terminal submits
  /// {@code channel.dispatch} (the pre-built dispatch consumer) to transit —
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
    final Consumer < E > chain;
    if ( target instanceof FsPipe < ? > fp ) {
      final FsCircuit c = fp.circuit ();
      final Consumer < Object > targetReceiver = fp.receiver ();
      if ( targetReceiver instanceof FsChannel < ? > channel ) {
        // Channel receiver: submit channel.cascadeDispatch to transit. This
        // skips channel.receive's version check on the cascade hot path.
        // cascadeDispatch already includes STEM behavior if channel is STEM.
        // Falls back to the channel itself before first rebuild (cascadeDispatch
        // is null until rebuild runs during the first ingress emission).
        chain = materialise ( v -> {
          Consumer < Object > d = channel.cascadeDispatch;
          c.submitTransit ( d != null ? d : channel, v );
        } );
      } else {
        // Non-channel receiver (e.g., ReceptorAdapter for circuit.pipe(receptor))
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
  // Filtering / transforming operators (reuse FsFlow operator classes)
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > guard ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsFlow.Guard <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > guard ( @NotNull E initial, @NotNull BiPredicate < ? super E, ? super E > predicate ) {
    Objects.requireNonNull ( initial );
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsFlow.GuardStateful <> ( initial, predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > diff () {
    return append ( FsFlow.Diff::new );
  }

  @NotNull
  @Override
  public Fiber < E > diff ( @NotNull E initial ) {
    Objects.requireNonNull ( initial );
    return append ( d -> new FsFlow.Diff <> ( initial, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > limit ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "limit must not be negative" );
    return append ( d -> new FsFlow.Limit <> ( n, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > skip ( long n ) {
    if ( n < 0 ) throw new IllegalArgumentException ( "skip must not be negative" );
    return append ( d -> new FsFlow.Skip <> ( n, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > replace ( @NotNull UnaryOperator < E > operator ) {
    Objects.requireNonNull ( operator );
    return append ( d -> new FsFlow.Replace <> ( operator, d ) );
  }


  @NotNull
  @Override
  public Fiber < E > peek ( @NotNull Receptor < ? super E > receptor ) {
    Objects.requireNonNull ( receptor );
    return append ( d -> new FsFlow.Peek <> ( receptor, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > dropWhile ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsFlow.DropWhile <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > takeWhile ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    return append ( d -> new FsFlow.TakeWhile <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > integrate ( E initial, @NotNull BinaryOperator < E > accumulator,
    @NotNull Predicate < ? super E > fire ) {
    Objects.requireNonNull ( accumulator );
    Objects.requireNonNull ( fire );
    return append ( d -> new FsFlow.Integrate <> ( initial, accumulator, fire, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > relate ( E initial, @NotNull BinaryOperator < E > operator ) {
    Objects.requireNonNull ( operator );
    return append ( d -> new FsFlow.Relate <> ( initial, operator, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > reduce ( E initial, @NotNull BinaryOperator < E > operator ) {
    // Override the earlier @NotNull E version
    Objects.requireNonNull ( operator );
    return append ( d -> new FsFlow.Reduce <> ( initial, operator, d ) );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Comparator-based operators
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > above ( @NotNull Comparator < ? super E > comparator, @NotNull E lower ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    return append ( d -> new FsFlow.Guard <> ( v -> comparator.compare ( v, lower ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > below ( @NotNull Comparator < ? super E > comparator, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( upper );
    return append ( d -> new FsFlow.Guard <> ( v -> comparator.compare ( v, upper ) < 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > clamp ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    if ( comparator.compare ( lower, upper ) > 0 )
      throw new IllegalArgumentException ( "lower must not be greater than upper" );
    return append ( d -> new Clamp <> ( comparator, lower, upper, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > deadband ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    if ( comparator.compare ( lower, upper ) > 0 )
      throw new IllegalArgumentException ( "lower must not be greater than upper" );
    return append ( d -> new FsFlow.Guard <> (
      v -> comparator.compare ( v, lower ) < 0 || comparator.compare ( v, upper ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > range ( @NotNull Comparator < ? super E > comparator, @NotNull E lower, @NotNull E upper ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( lower );
    Objects.requireNonNull ( upper );
    return append ( d -> new FsFlow.Guard <> (
      v -> comparator.compare ( v, lower ) >= 0 && comparator.compare ( v, upper ) <= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > max ( @NotNull Comparator < ? super E > comparator, @NotNull E max ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( max );
    return append ( d -> new FsFlow.Guard <> ( v -> comparator.compare ( v, max ) <= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > min ( @NotNull Comparator < ? super E > comparator, @NotNull E min ) {
    Objects.requireNonNull ( comparator );
    Objects.requireNonNull ( min );
    return append ( d -> new FsFlow.Guard <> ( v -> comparator.compare ( v, min ) >= 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > high ( @NotNull Comparator < ? super E > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( d -> new GuardStatefulNullable <> ( ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) > 0, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > low ( @NotNull Comparator < ? super E > comparator ) {
    Objects.requireNonNull ( comparator );
    return append ( d -> new GuardStatefulNullable <> ( ( prev, curr ) ->
      prev == null || comparator.compare ( curr, prev ) < 0, d ) );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // 2.3 new operators
  // ─────────────────────────────────────────────────────────────────────────────

  @NotNull
  @Override
  public Fiber < E > chance ( double probability ) {
    if ( Double.isNaN ( probability ) || probability < 0.0 || probability > 1.0 )
      throw new IllegalArgumentException ( "probability must be in [0.0, 1.0]" );
    if ( probability == 0.0 ) return append ( d -> v -> { /* drop all */ } );
    if ( probability == 1.0 ) return this;
    return append ( d -> new Chance <> ( probability, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > change ( @NotNull Function < ? super E, ? > key ) {
    Objects.requireNonNull ( key );
    return append ( d -> new Change <> ( key, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > delay ( int depth, @NotNull E initial ) {
    if ( depth <= 0 ) throw new IllegalArgumentException ( "depth must be positive" );
    Objects.requireNonNull ( initial );
    return append ( d -> new Delay <> ( depth, initial, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > edge ( @NotNull E initial, @NotNull BiPredicate < ? super E, ? super E > transition ) {
    Objects.requireNonNull ( initial );
    Objects.requireNonNull ( transition );
    return append ( d -> new FsFlow.GuardStateful <> ( initial, transition, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > every ( int interval ) {
    if ( interval <= 0 ) throw new IllegalArgumentException ( "interval must be positive" );
    return append ( d -> new Every <> ( interval, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > fiber ( @NotNull Fiber < E > next ) {
    Objects.requireNonNull ( next );
    if ( !( next instanceof FsFiber < E > nextFiber ) ) {
      throw new IllegalArgumentException ( "next fiber must be an FsFiber instance" );
    }
    if ( nextFiber.count == 0 ) return this;
    Object[] merged = new Object[count + nextFiber.count];
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
    return append ( d -> new Hysteresis <> ( enter, exit, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > inhibit ( int refractory ) {
    if ( refractory < 0 ) throw new IllegalArgumentException ( "refractory must not be negative" );
    if ( refractory == 0 ) return this;
    return append ( d -> new Inhibit <> ( refractory, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > pulse ( @NotNull Predicate < ? super E > predicate ) {
    Objects.requireNonNull ( predicate );
    // pulse: emit value when predicate matches, otherwise drop. Stateful in spirit
    // (the next emission is unconstrained), but each evaluation is self-contained.
    return append ( d -> new FsFlow.Guard <> ( predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > rolling ( int size, @NotNull BinaryOperator < E > combiner, @NotNull E identity ) {
    if ( size <= 0 ) throw new IllegalArgumentException ( "size must be positive" );
    Objects.requireNonNull ( combiner );
    Objects.requireNonNull ( identity );
    return append ( d -> new Rolling <> ( size, combiner, identity, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > steady ( int count ) {
    if ( count <= 0 ) throw new IllegalArgumentException ( "count must be positive" );
    return append ( d -> new SteadyN <> ( count, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > steady ( int count, @NotNull BiPredicate < ? super E, ? super E > predicate ) {
    if ( count <= 0 ) throw new IllegalArgumentException ( "count must be positive" );
    Objects.requireNonNull ( predicate );
    return append ( d -> new SteadyPredicate <> ( count, predicate, d ) );
  }

  @NotNull
  @Override
  public Fiber < E > tumble ( int size, @NotNull BinaryOperator < E > combiner, @NotNull E identity ) {
    if ( size <= 0 ) throw new IllegalArgumentException ( "size must be positive" );
    Objects.requireNonNull ( combiner );
    Objects.requireNonNull ( identity );
    return append ( d -> new Tumble <> ( size, combiner, identity, d ) );
  }

  // ─────────────────────────────────────────────────────────────────────────────
  // Operator implementations new in 2.3
  // ─────────────────────────────────────────────────────────────────────────────

  /// GuardStateful that allows null initial values.
  static final class GuardStatefulNullable < E > implements Consumer < E > {
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E > d;
    Object prev;

    GuardStatefulNullable ( BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.p = p; this.d = d;
    }

    GuardStatefulNullable ( BiPredicate < ? super E, ? super E > p, Consumer < E > d, Object initial ) {
      this.p = p; this.d = d; this.prev = initial;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      if ( p.test ( (E) prev, v ) ) { prev = v; d.accept ( v ); }
    }
  }

  static final class Clamp < E > implements Consumer < E > {
    final Comparator < ? super E > c;
    final E lower, upper;
    final Consumer < E > d;

    Clamp ( Comparator < ? super E > c, E lower, E upper, Consumer < E > d ) {
      this.c = c; this.lower = lower; this.upper = upper; this.d = d;
    }

    @Override
    public void accept ( E v ) {
      if ( c.compare ( v, lower ) < 0 ) d.accept ( lower );
      else if ( c.compare ( v, upper ) > 0 ) d.accept ( upper );
      else d.accept ( v );
    }
  }

  static final class Chance < E > implements Consumer < E > {
    final double p;
    final Consumer < E > d;

    Chance ( double p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( ThreadLocalRandom.current ().nextDouble () < p ) d.accept ( v );
    }
  }

  static final class Change < E > implements Consumer < E > {
    final Function < ? super E, ? > key;
    final Consumer < E > d;
    Object prevKey;
    boolean has;

    Change ( Function < ? super E, ? > key, Consumer < E > d ) { this.key = key; this.d = d; }

    @Override
    public void accept ( E v ) {
      Object k = key.apply ( v );
      if ( !has || !Objects.equals ( prevKey, k ) ) { prevKey = k; has = true; d.accept ( v ); }
    }
  }

  /// Ring-buffer lookback; emits initial for the first `depth` emissions.
  static final class Delay < E > implements Consumer < E > {
    final int depth;
    final Object[] buffer;
    final Consumer < E > d;
    int idx;
    long count;

    Delay ( int depth, E initial, Consumer < E > d ) {
      this.depth = depth;
      this.buffer = new Object[depth];
      java.util.Arrays.fill ( buffer, initial );
      this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      E out = (E) buffer[idx];
      buffer[idx] = v;
      idx = ( idx + 1 ) % depth;
      count++;
      d.accept ( out );
    }
  }

  /// Periodic sampling — emit every Nth value.
  static final class Every < E > implements Consumer < E > {
    final int n;
    final Consumer < E > d;
    int count;

    Every ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( ++count % n == 0 ) d.accept ( v ); }
  }

  /// Two-state hysteresis: enters passing state when `enter` matches; exits
  /// (drops emissions) when `exit` matches.
  static final class Hysteresis < E > implements Consumer < E > {
    final Predicate < ? super E > enter;
    final Predicate < ? super E > exit;
    final Consumer < E > d;
    boolean passing;

    Hysteresis ( Predicate < ? super E > enter, Predicate < ? super E > exit, Consumer < E > d ) {
      this.enter = enter; this.exit = exit; this.d = d;
    }

    @Override
    public void accept ( E v ) {
      if ( passing ) {
        if ( exit.test ( v ) ) passing = false;
        else d.accept ( v );
      } else {
        if ( enter.test ( v ) ) { passing = true; d.accept ( v ); }
      }
    }
  }

  /// Drop the first n emissions, then pass everything.
  static final class Inhibit < E > implements Consumer < E > {
    final int n;
    final Consumer < E > d;
    int seen;

    Inhibit ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( seen < n ) seen++; else d.accept ( v ); }
  }

  /// Rolling window: combine the last `size` emissions with combiner over identity, emit running result.
  static final class Rolling < E > implements Consumer < E > {
    final int size;
    final BinaryOperator < E > combiner;
    final E identity;
    final Object[] buffer;
    final Consumer < E > d;
    int idx;
    int filled;

    Rolling ( int size, BinaryOperator < E > combiner, E identity, Consumer < E > d ) {
      this.size = size;
      this.combiner = combiner;
      this.identity = identity;
      this.buffer = new Object[size];
      this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      buffer[idx] = v;
      idx = ( idx + 1 ) % size;
      if ( filled < size ) filled++;
      E acc = identity;
      for ( int i = 0; i < filled; i++ ) acc = combiner.apply ( acc, (E) buffer[i] );
      d.accept ( acc );
    }
  }

  /// Steady run length: emit when the same value (Objects.equals) repeats `count` times.
  static final class SteadyN < E > implements Consumer < E > {
    final int count;
    final Consumer < E > d;
    Object prev;
    int run;

    SteadyN ( int count, Consumer < E > d ) { this.count = count; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( Objects.equals ( v, prev ) ) {
        run++;
      } else {
        prev = v;
        run = 1;
      }
      if ( run == count ) d.accept ( v );
    }
  }

  /// Steady run length: emit when the (prev, curr) bi-predicate has held `count` times in a row.
  static final class SteadyPredicate < E > implements Consumer < E > {
    final int count;
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E > d;
    Object prev;
    boolean has;
    int run;

    SteadyPredicate ( int count, BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.count = count; this.p = p; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      if ( has && p.test ( (E) prev, v ) ) run++;
      else run = 1;
      prev = v;
      has = true;
      if ( run == count ) d.accept ( v );
    }
  }

  /// Tumbling (non-overlapping) window: collect `size` emissions, fold with combiner over identity, emit.
  static final class Tumble < E > implements Consumer < E > {
    final int size;
    final BinaryOperator < E > combiner;
    final E identity;
    final Consumer < E > d;
    E acc;
    int filled;

    Tumble ( int size, BinaryOperator < E > combiner, E identity, Consumer < E > d ) {
      this.size = size;
      this.combiner = combiner;
      this.identity = identity;
      this.acc = identity;
      this.d = d;
    }

    @Override
    public void accept ( E v ) {
      acc = combiner.apply ( acc, v );
      filled++;
      if ( filled == size ) {
        d.accept ( acc );
        acc = identity;
        filled = 0;
      }
    }
  }

}
