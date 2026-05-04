package io.fullerstack.substrates;

import io.fullerstack.substrates.FsOperators.Wrap;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;

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

  private static final Wrap < ? >[]    EMPTY        = new Wrap < ? >[0];
  private static final FactoryEntry[]  NO_FACTORIES = null;

  private final Wrap < ? >[]    operators;
  private final int             count;
  /// 2.4 per-attachment fiber factories. Null in common case (no
  /// `fiber(Function)` call). Each entry records the value of `count` at
  /// the time the factory was added; at `pipe(target)` time the factory is
  /// invoked once with `target.subject()` and the resulting fiber's
  /// operators are inlined at that recorded position.
  private final FactoryEntry[]  factories;

  /// Records a per-attachment fiber factory and its insertion position.
  private record FactoryEntry(
    int position,
    Function < ? super Subject < ? >, ? extends Fiber < ? > > factory
  ) { }

  /// Identity flow — no operators.
  public FsFlow () {
    this.operators = EMPTY;
    this.count     = 0;
    this.factories = NO_FACTORIES;
  }

  private FsFlow ( Wrap < ? >[] operators, int count, FactoryEntry[] factories ) {
    this.operators = operators;
    this.count     = count;
    this.factories = factories;
  }

  /// Returns a new FsFlow with the given operator appended.
  @SuppressWarnings ( "unchecked" )
  private < X, Y > FsFlow < X, Y > append ( Wrap < ? > op ) {
    Wrap < ? >[] newOps = new Wrap < ? >[count + 1];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[count] = op;
    return new FsFlow <> ( newOps, count + 1, factories );
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

  /// 2.4 — materialise an effective Wrap[] (factories already resolved).
  /// Used by `pipe(target)` when factories are present.
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  private Consumer < I > materialiseFrom ( Wrap < ? >[] effective, int effectiveCount, Consumer < O > target ) {
    Consumer c = target;
    for ( int i = 0; i < effectiveCount; i++ ) c = ( (Wrap) effective[i] ).wrap ( c );
    return c;
  }

  /// Resolves all per-attachment factories against `targetSubject` and
  /// builds the effective Wrap[] for materialisation.
  private Wrap < ? >[] resolveFactories ( Subject < ? > targetSubject ) {
    // Resolve each factory and compute total size
    Wrap < ? >[][] resolved = new Wrap < ? >[factories.length][];
    int totalExtra = 0;
    for ( int i = 0; i < factories.length; i++ ) {
      Fiber < ? > f = factories[i].factory.apply ( targetSubject );
      Objects.requireNonNull ( f, "factory must not return null" );
      if ( !( f instanceof FsFiber < ? > fsFiber ) ) {
        throw new IllegalArgumentException ( "factory must produce an FsFiber instance" );
      }
      Wrap < ? >[] ops = fsFiber.operators ();
      int fc = fsFiber.operatorCount ();
      Wrap < ? >[] copy = new Wrap < ? >[fc];
      System.arraycopy ( ops, 0, copy, 0, fc );
      resolved[i] = copy;
      totalExtra += fc;
    }
    // Build merged array: walk positions 0..count, inline factories at each position
    Wrap < ? >[] merged = new Wrap < ? >[count + totalExtra];
    int w = 0;
    for ( int p = 0; p <= count; p++ ) {
      for ( int i = 0; i < factories.length; i++ ) {
        if ( factories[i].position == p ) {
          Wrap < ? >[] r = resolved[i];
          System.arraycopy ( r, 0, merged, w, r.length );
          w += r.length;
        }
      }
      if ( p < count ) merged[w++] = operators[p];
    }
    return merged;
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
    return new FsFlow <> ( merged, count + fc, factories );
  }

  /// 2.4: Per-attachment fiber factory. The factory is invoked once per
  /// `pipe(target)` call with `target.subject()`, and its returned fiber's
  /// operators are inlined at the position the factory was added.
  @NotNull
  @Override
  public Flow < I, O > fiber ( @NotNull Function < ? super Subject < ? >, ? extends Fiber < O > > factory ) {
    Objects.requireNonNull ( factory );
    FactoryEntry entry = new FactoryEntry ( count, factory );
    FactoryEntry[] newFactories;
    if ( factories == null ) {
      newFactories = new FactoryEntry[]{entry};
    } else {
      newFactories = new FactoryEntry[factories.length + 1];
      System.arraycopy ( factories, 0, newFactories, 0, factories.length );
      newFactories[factories.length] = entry;
    }
    return new FsFlow <> ( operators, count, newFactories );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < P > Flow < I, P > flow ( @NotNull Flow < ? super O, ? extends P > next ) {
    Objects.requireNonNull ( next );
    if ( !( next instanceof FsFlow < ?, ? > nextFlow ) ) {
      throw new IllegalArgumentException ( "next flow must be an FsFlow instance" );
    }
    if ( nextFlow.count == 0 && nextFlow.factories == null ) return (Flow < I, P >) (Flow < ?, ? >) this;
    Wrap < ? >[] merged = new Wrap < ? >[count + nextFlow.count];
    // operators[0..count) of `this` — innermost at index 0 — must remain innermost.
    // next's operators run after `this`, so they wrap further out (higher indices).
    System.arraycopy ( operators, 0, merged, 0, count );
    System.arraycopy ( nextFlow.operators, 0, merged, count, nextFlow.count );
    // Translate next's factories: each next factory at position p shifts to position count+p.
    FactoryEntry[] mergedFactories = factories;
    if ( nextFlow.factories != null ) {
      int existing = factories == null ? 0 : factories.length;
      mergedFactories = new FactoryEntry[existing + nextFlow.factories.length];
      if ( existing > 0 ) System.arraycopy ( factories, 0, mergedFactories, 0, existing );
      for ( int i = 0; i < nextFlow.factories.length; i++ ) {
        FactoryEntry fe = nextFlow.factories[i];
        mergedFactories[existing + i] = new FactoryEntry ( count + fe.position, fe.factory );
      }
    }
    return new FsFlow <> ( merged, count + nextFlow.count, mergedFactories );
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
    // Empty flow elision — no operators AND no pending factories means I == O.
    if ( count == 0 && factories == null ) return (Pipe < I >) (Pipe < ? >) target;

    // Resolve per-attachment factories (if any) against target.subject(). Each
    // factory is invoked once at attachment time per spec §6.2 / 2.4.
    final Wrap < ? >[] effective;
    final int          effectiveCount;
    if ( factories == null ) {
      effective      = operators;
      effectiveCount = count;
    } else {
      effective      = resolveFactories ( target.subject () );
      effectiveCount = effective.length;
    }
    if ( effectiveCount == 0 ) return (Pipe < I >) (Pipe < ? >) target;

    final Consumer < I > chain;
    if ( target instanceof FsPipe < ? > fp ) {
      final FsCircuit c = fp.circuit ();
      final Consumer < Object > targetReceiver = fp.receiver ();
      if ( targetReceiver instanceof FsChannel < ? > channel ) {
        // Submit channel.cascadeDispatch directly — receptors + STEM, no
        // version check. Falls back to channel before first rebuild.
        chain = materialiseFrom ( effective, effectiveCount, (Consumer < O >) v -> {
          Consumer < Object > d = channel.cascadeDispatch;
          c.submitTransit ( d != null ? d : channel, v );
        } );
      } else {
        chain = materialiseFrom ( effective, effectiveCount, (Consumer < O >) v -> c.submitTransit ( targetReceiver, v ) );
      }
      return new FsPipe <> ( (Consumer < Object >) (Consumer < ? >) chain, c );
    }
    // Foreign Pipe — fall through to wrapped emit
    chain = materialiseFrom ( effective, effectiveCount, target::emit );
    return new Pipe <> () {
      @Override
      public void emit ( @NotNull I emission ) {
        chain.accept ( emission );
      }
      @Override
      public Subject < Pipe < I > > subject () {
        @SuppressWarnings ( "unchecked" )
        var s = (Subject < Pipe < I > >) (Subject < ? >) target.subject ();
        return s;
      }
    };
  }
}
