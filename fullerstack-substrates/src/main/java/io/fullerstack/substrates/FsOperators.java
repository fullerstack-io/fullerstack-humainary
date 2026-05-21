package io.fullerstack.substrates;

import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Receptor;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// Operator implementations shared by FsFiber and FsFlow.
///
/// Every class here is a per-emission stateful or stateless processor that
/// implements `Consumer<E>`. Operators are constructed via {@link Wrap} factories
/// at materialise time so each pipe materialisation gets independent state.
///
/// Type-preserving by design — none of these change the emission type.
/// Type-changing belongs to FsFlow's own operators (e.g. MapWrap).
final class FsOperators {

  private FsOperators () {}

  /// Operator factory: takes the downstream consumer and returns a wrapping
  /// consumer. Single shared shape lets FsFiber and FsFlow store operators
  /// in a uniform `Wrap[]` and materialise via a single loop with no
  /// `instanceof` checks.
  @FunctionalInterface
  interface Wrap < E > {
    Consumer < E > wrap ( Consumer < E > downstream );
  }

  // ─── Filtering / transforming ───────────────────────────────────────────────

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

  /// GuardStateful that allows null initial values (high/low operators).
  static final class GuardStatefulNullable < E > implements Consumer < E > {
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E > d;
    Object prev;

    GuardStatefulNullable ( BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.p = p; this.d = d;
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
      // Single field read of prev, identity short-circuit before equals,
      // and the has=true write only fires on first emission instead of every
      // emission. Emissions are non-null per spec §1.2 — no v != null check.
      if ( has ) {
        Object p = prev;
        if ( v != p && !v.equals ( p ) ) {
          prev = v;
          d.accept ( v );
        }
      } else {
        prev = v;
        has = true;
        d.accept ( v );
      }
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

    Peek ( Receptor < ? super E > r, Consumer < E > d ) {
      this.r = r; this.d = d;
    }

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

  // ─── Comparator-based / range ───────────────────────────────────────────────

  static final class Clamp < E > implements Consumer < E > {
    final Comparator < ? super E > c;
    final E              lower, upper;
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

  // ─── 2.3 operators ──────────────────────────────────────────────────────────

  static final class Chance < E > implements Consumer < E > {
    final double         p;
    final Consumer < E > d;

    Chance ( double p, Consumer < E > d ) { this.p = p; this.d = d; }

    @Override
    public void accept ( E v ) {
      if ( ThreadLocalRandom.current ().nextDouble () < p ) d.accept ( v );
    }
  }

  static final class Change < E > implements Consumer < E > {
    final Function < ? super E, ? > key;
    final Consumer < E >            d;
    Object  prevKey;
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
    final int            depth;
    final Object[]       buffer;
    final Consumer < E > d;
    int  idx;
    long count;

    Delay ( int depth, E initial, Consumer < E > d ) {
      this.depth  = depth;
      this.buffer = new Object[depth];
      Arrays.fill ( buffer, initial );
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
    final int            n;
    final Consumer < E > d;
    int count;

    Every ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( ++count % n == 0 ) d.accept ( v ); }
  }

  /// Time-based rate limit (2.7) — per spec §6.2.3 `Fiber.every(Duration)`:
  ///
  ///   - The **first observed value anchors the interval and is dropped**.
  ///   - A later value observed after at least `durationNanos` has
  ///     elapsed is emitted.
  ///   - If more than one interval elapsed before that value arrives,
  ///     the internal clock advances by the elapsed interval slots rather
  ///     than by the late-arrival time — avoiding drift after overruns.
  static final class EveryTime < E > implements Consumer < E > {
    final long           durationNanos;
    final Consumer < E > d;
    boolean anchored = false;
    long    anchorNanos;   // start of current slot

    EveryTime ( long durationNanos, Consumer < E > d ) {
      this.durationNanos = durationNanos;
      this.d             = d;
    }

    @Override
    public void accept ( E v ) {
      final long now = System.nanoTime ();
      if ( !anchored ) {
        // First value anchors the interval and is dropped.
        anchored = true;
        anchorNanos = now;
        return;
      }
      final long elapsed = now - anchorNanos;
      if ( elapsed < durationNanos ) {
        return;   // still within the current slot
      }
      // Slot-aligned advance: skip the full slots that have elapsed.
      final long slots = elapsed / durationNanos;
      anchorNanos += slots * durationNanos;
      d.accept ( v );
    }
  }

  /// Two-state hysteresis: enters passing state when `enter` matches; exits
  /// (drops emissions) when `exit` matches.
  static final class Hysteresis < E > implements Consumer < E > {
    final Predicate < ? super E > enter;
    final Predicate < ? super E > exit;
    final Consumer < E >          d;
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
    final int            n;
    final Consumer < E > d;
    int seen;

    Inhibit ( int n, Consumer < E > d ) { this.n = n; this.d = d; }

    @Override
    public void accept ( E v ) { if ( seen < n ) seen++; else d.accept ( v ); }
  }

  /// Rolling window: combine the last `size` emissions with combiner over identity, emit running result.
  static final class Rolling < E > implements Consumer < E > {
    final int                  size;
    final BinaryOperator < E > combiner;
    final E                    identity;
    final Object[]             buffer;
    final Consumer < E >       d;
    int idx;
    int filled;

    Rolling ( int size, BinaryOperator < E > combiner, E identity, Consumer < E > d ) {
      this.size     = size;
      this.combiner = combiner;
      this.identity = identity;
      this.buffer   = new Object[size];
      this.d        = d;
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
    final int            count;
    final Consumer < E > d;
    Object prev;
    int    run;

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
    final int                                  count;
    final BiPredicate < ? super E, ? super E > p;
    final Consumer < E >                       d;
    Object  prev;
    boolean has;
    int     run;

    SteadyPredicate ( int count, BiPredicate < ? super E, ? super E > p, Consumer < E > d ) {
      this.count = count; this.p = p; this.d = d;
    }

    @Override
    @SuppressWarnings ( "unchecked" )
    public void accept ( E v ) {
      if ( has && p.test ( (E) prev, v ) ) run++;
      else run = 1;
      prev = v;
      has  = true;
      if ( run == count ) d.accept ( v );
    }
  }

  /// Tumbling (non-overlapping) window: collect `size` emissions, fold with combiner over identity, emit.
  static final class Tumble < E > implements Consumer < E > {
    final int                  size;
    final BinaryOperator < E > combiner;
    final E                    identity;
    final Consumer < E >       d;
    E   acc;
    int filled;

    Tumble ( int size, BinaryOperator < E > combiner, E identity, Consumer < E > d ) {
      this.size     = size;
      this.combiner = combiner;
      this.identity = identity;
      this.acc      = identity;
      this.d        = d;
    }

    @Override
    public void accept ( E v ) {
      acc = combiner.apply ( acc, v );
      filled++;
      if ( filled == size ) {
        d.accept ( acc );
        acc    = identity;
        filled = 0;
      }
    }
  }

  // ─── 2.5 operators ─────────────────────────────────────────────────────────

  /// Distinct (unbounded): suppresses any value previously seen. Memory grows
  /// with stream cardinality. Spec §6.2.3.
  static final class Distinct < E > implements Consumer < E > {
    final Consumer < E > d;
    final HashSet < E >  seen = new HashSet <> ();

    Distinct ( Consumer < E > d ) { this.d = d; }

    @Override
    public void accept ( E v ) { if ( seen.add ( v ) ) d.accept ( v ); }
  }

  /// Distinct (capacity): FIFO-windowed duplicate suppression. LinkedHashSet
  /// gives O(1) contains/add plus insertion order for FIFO eviction. Suppressed
  /// duplicates do NOT refresh window position. Spec §6.2.3.
  static final class DistinctCapacity < E > implements Consumer < E > {
    final int                 capacity;
    final Consumer < E >      d;
    final LinkedHashSet < E > window;

    DistinctCapacity ( int capacity, Consumer < E > d ) {
      this.capacity = capacity;
      this.d        = d;
      this.window   = new LinkedHashSet <> ();
    }

    @Override
    public void accept ( E v ) {
      if ( window.contains ( v ) ) return;
      window.add ( v );
      if ( window.size () > capacity ) {
        Iterator < E > it = window.iterator ();
        it.next ();
        it.remove ();
      }
      d.accept ( v );
    }
  }

  /// Route: predicate-matched values are diverted to `side` and dropped from
  /// the main pipeline; non-matching values pass through. Stateless. Spec §6.2.2.
  static final class Route < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Pipe < E >              side;
    final Consumer < E >          d;

    Route ( Predicate < ? super E > p, Pipe < E > side, Consumer < E > d ) {
      this.p = p; this.side = side; this.d = d;
    }

    @Override
    public void accept ( E v ) {
      if ( p.test ( v ) ) side.emit ( v );
      else                d.accept ( v );
    }
  }

  /// Tee: fan-out to `side` then continue downstream. Stateless. Spec §6.2.2.
  static final class Tee < E > implements Consumer < E > {
    final Pipe < E >     side;
    final Consumer < E > d;

    Tee ( Pipe < E > side, Consumer < E > d ) { this.side = side; this.d = d; }

    @Override
    public void accept ( E v ) {
      side.emit ( v );
      d.accept ( v );
    }
  }

  /// Streak: emit the Nth consecutive matching emission, then reset.
  /// Non-matching emissions reset the counter and are dropped. Spec §6.2.3.
  /// (required == 1 is handled at FsFiber level — degenerates to Guard.)
  static final class Streak < E > implements Consumer < E > {
    final int                     required;
    final Predicate < ? super E > matches;
    final Consumer < E >          d;
    int count;

    Streak ( int required, Predicate < ? super E > matches, Consumer < E > d ) {
      this.required = required; this.matches = matches; this.d = d;
    }

    @Override
    public void accept ( E v ) {
      if ( matches.test ( v ) ) {
        if ( ++count >= required ) {
          count = 0;
          d.accept ( v );
        }
      } else {
        count = 0;
      }
    }
  }

  /// When: predicate-matched values traverse a pre-materialised sub-fiber that
  /// terminates at `d`; non-matching values pass through directly. Stateless
  /// at this stage — the sub-fiber's per-materialization state is materialised
  /// here (once per outer materialization). Spec §6.2.2.
  static final class When < E > implements Consumer < E > {
    final Predicate < ? super E > p;
    final Consumer < E >          matched;
    final Consumer < E >          d;

    When ( Predicate < ? super E > p, FsFiber < E > sub, Consumer < E > d ) {
      this.p       = p;
      this.d       = d;
      this.matched = sub.materialise ( d );
    }

    @Override
    public void accept ( E v ) {
      if ( p.test ( v ) ) matched.accept ( v );
      else                d.accept ( v );
    }
  }
}
