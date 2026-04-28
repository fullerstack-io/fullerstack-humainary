package io.fullerstack.substrates;

import io.fullerstack.substrates.FsOperators.Wrap;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Immutable Flow<I,O> implementation for Substrates 2.3.
///
/// Flow carries type-changing composition: `map`, `flow`, `fiber`, `pipe`.
/// Per-emission operators (diff, guard, limit, peek, etc.) live on Fiber<E>;
/// their implementations are in {@link FsOperators}.
///
/// Operators are stored as a uniform {@code Wrap[]} array — `map` becomes
/// a {@link MapWrap}, `fiber(...)` and `flow(...)` inline the source's Wraps
/// directly into the destination's array. Materialise walks the array with
/// no `instanceof` dispatch.
///
/// @param <I> input type (what {@code Pipe.pipe(Flow)} receives)
/// @param <O> output type (what the target pipe emits)
@Provided
public final class FsFlow < I, O > implements Flow < I, O > {

  // ═══════════════════════════════════════════════════════════════════════════
  // Type-changing operator — Flow's own contribution
  // ═══════════════════════════════════════════════════════════════════════════

  /// Map operator — the only Flow-specific operator. Drops null results so
  /// `map` can act as a filter when the function returns null. Type-erased
  /// because operators travel through the {@code Wrap[]} alongside
  /// type-preserving ones.
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  static final class MapWrap implements Wrap < Object > {
    final Function < Object, Object > fn;

    MapWrap ( Function fn ) { this.fn = (Function < Object, Object >) fn; }

    @Override
    public Consumer < Object > wrap ( Consumer < Object > downstream ) {
      final Function < Object, Object > f = fn;
      return v -> {
        Object r = f.apply ( v );
        if ( r != null ) downstream.accept ( r );
      };
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Immutable state
  // ═══════════════════════════════════════════════════════════════════════════

  private static final Wrap < ? >[] EMPTY = new Wrap < ? >[0];

  private final Wrap < ? >[] operators;
  private final int          count;

  /// Identity flow — no operators.
  public FsFlow () {
    this.operators = EMPTY;
    this.count     = 0;
  }

  private FsFlow ( Wrap < ? >[] operators, int count ) {
    this.operators = operators;
    this.count     = count;
  }

  /// Returns a new FsFlow with the given operator appended.
  @SuppressWarnings ( "unchecked" )
  private < X, Y > FsFlow < X, Y > append ( Wrap < ? > op ) {
    Wrap < ? >[] newOps = new Wrap < ? >[count + 1];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[count] = op;
    return new FsFlow <> ( newOps, count + 1 );
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Materialisation
  // ═══════════════════════════════════════════════════════════════════════════

  /// Materialises this flow into a concrete consumer chain.
  /// Each call produces independent state for stateful operators.
  ///
  /// operators[0] = first-added = innermost (closest to target)
  /// operators[n-1] = last-added = outermost (closest to input)
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  Consumer < I > materialise ( Consumer < O > target ) {
    Consumer c = target;
    for ( int i = 0; i < count; i++ ) c = ( (Wrap) operators[i] ).wrap ( c );
    return c;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Flow<I,O> API (2.3) — only map/flow/fiber/pipe
  // ═══════════════════════════════════════════════════════════════════════════

  @NotNull
  @Override
  public < P > Flow < I, P > map ( @NotNull Function < ? super O, ? extends P > fn ) {
    Objects.requireNonNull ( fn );
    return append ( new MapWrap ( fn ) );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Flow < I, O > fiber ( @NotNull Fiber < O > fiber ) {
    Objects.requireNonNull ( fiber );
    if ( !( fiber instanceof FsFiber < ? > fsFiber ) ) {
      throw new IllegalArgumentException ( "fiber must be an FsFiber instance" );
    }
    int fc = fsFiber.operatorCount ();
    if ( fc == 0 ) return this;
    // Inline the fiber's Wraps directly. Fiber operators are type-preserving
    // (E→E with E ≡ O at this position), so they slot into the flow's array.
    Wrap < ? >[] merged = new Wrap < ? >[count + fc];
    System.arraycopy ( operators, 0, merged, 0, count );
    System.arraycopy ( fsFiber.operators (), 0, merged, count, fc );
    return new FsFlow <> ( merged, count + fc );
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
    Wrap < ? >[] merged = new Wrap < ? >[count + nextFlow.count];
    // operators[0..count) of `this` — innermost at index 0 — must remain innermost.
    // next's operators run after `this`, so they wrap further out (higher indices).
    System.arraycopy ( operators, 0, merged, 0, count );
    System.arraycopy ( nextFlow.operators, 0, merged, count, nextFlow.count );
    return new FsFlow <> ( merged, count + nextFlow.count );
  }

  /// When target is a same-circuit FsPipe, the flow's terminal submits to
  /// transit directly — bypassing target.emit's checks. If target's receiver
  /// is an FsChannel, submit channel.dispatch instead of channel itself,
  /// skipping the channel's version check on the cascade hot path (spec
  /// §5.4.1 + §7.6.2 — subscriber state cannot change mid-cascade).
  @New
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public Pipe < I > pipe ( @NotNull Pipe < O > target ) {
    Objects.requireNonNull ( target );
    // Empty flow elision — no operators means I == O. Skip the transit hop.
    if ( count == 0 ) return (Pipe < I >) (Pipe < ? >) target;
    final Consumer < I > chain;
    if ( target instanceof FsPipe < ? > fp ) {
      final FsCircuit c = fp.circuit ();
      final Consumer < Object > targetReceiver = fp.receiver ();
      if ( targetReceiver instanceof FsChannel < ? > channel ) {
        // Submit channel.cascadeDispatch directly — receptors + STEM, no
        // version check. Falls back to channel before first rebuild.
        chain = materialise ( (Consumer < O >) v -> {
          Consumer < Object > d = channel.cascadeDispatch;
          c.submitTransit ( d != null ? d : channel, v );
        } );
      } else {
        chain = materialise ( (Consumer < O >) v -> c.submitTransit ( targetReceiver, v ) );
      }
      return new FsPipe <> ( (Consumer < Object >) (Consumer < ? >) chain, c );
    }
    // Foreign Pipe — fall through to wrapped emit
    chain = materialise ( target::emit );
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
