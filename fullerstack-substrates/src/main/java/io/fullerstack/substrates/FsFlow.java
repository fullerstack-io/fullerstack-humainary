package io.fullerstack.substrates;

import io.fullerstack.substrates.FsOperators.Wrap;
import io.humainary.substrates.api.Substrates.Cell;
import io.humainary.substrates.api.Substrates.Fiber;
import io.humainary.substrates.api.Substrates.Flow;
import io.humainary.substrates.api.Substrates.New;
import io.humainary.substrates.api.Substrates.NotNull;
import io.humainary.substrates.api.Substrates.Pipe;
import io.humainary.substrates.api.Substrates.Provided;
import io.humainary.substrates.api.Substrates.Subject;
import io.humainary.substrates.api.Substrates.Window;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

  /// **Scan (state-only projection)** — folds inputs into a per-materialization
  /// state slot via `step(state, input) -> state`, then emits a projection
  /// `emit(state) -> P` downstream. Spec §6.2.3:
  ///
  /// - Supplier invoked ONCE per materialization at attachment time (in `wrap`).
  /// - `step` throws → state NOT replaced; side effects in step are retained
  ///   (no rollback); next emission proceeds from prior state.
  /// - `emit` throws → state already advanced; next emission proceeds from
  ///   advanced state.
  /// - `emit` returns null → emission dropped downstream.
  /// - All throws are swallowed at this stage (external callback isolation
  ///   per §15.4); they do not propagate up the chain.
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  static final class ScanStateWrap implements Wrap < Object > {
    final Supplier < ? > initial;
    final BiFunction < Object, Object, Object > step;
    final Function < Object, Object > emit;

    ScanStateWrap ( Supplier initial, BiFunction step, Function emit ) {
      this.initial = initial;
      this.step    = (BiFunction < Object, Object, Object >) step;
      this.emit    = (Function < Object, Object >) emit;
    }

    @Override
    public Consumer < Object > wrap ( Consumer < Object > downstream ) {
      final Object[] slot = { initial.get () };
      return v -> {
        Object prev = slot[ 0 ];
        Object next;
        try {
          next = step.apply ( prev, v );
        } catch ( RuntimeException re ) {
          return;   // state unchanged, drop emission
        }
        slot[ 0 ] = next;
        Object projection;
        try {
          projection = emit.apply ( next );
        } catch ( RuntimeException re ) {
          return;   // state already advanced; drop this emission
        }
        if ( projection != null ) downstream.accept ( projection );
      };
    }
  }

  /// **Scan (input-aware projection)** — same as ScanStateWrap but emit
  /// signature is `(state, input) -> P` so the projection can blend
  /// running state with the current input (z-scores, residuals, anomaly
  /// scoring).
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  static final class ScanInputAwareWrap implements Wrap < Object > {
    final Supplier < ? > initial;
    final BiFunction < Object, Object, Object > step;
    final BiFunction < Object, Object, Object > emit;

    ScanInputAwareWrap ( Supplier initial, BiFunction step, BiFunction emit ) {
      this.initial = initial;
      this.step    = (BiFunction < Object, Object, Object >) step;
      this.emit    = (BiFunction < Object, Object, Object >) emit;
    }

    @Override
    public Consumer < Object > wrap ( Consumer < Object > downstream ) {
      final Object[] slot = { initial.get () };
      return v -> {
        Object prev = slot[ 0 ];
        Object next;
        try {
          next = step.apply ( prev, v );
        } catch ( RuntimeException re ) {
          return;
        }
        slot[ 0 ] = next;
        Object projection;
        try {
          projection = emit.apply ( next, v );
        } catch ( RuntimeException re ) {
          return;
        }
        if ( projection != null ) downstream.accept ( projection );
      };
    }
  }

  /// **Window (count-based)** — appends each surviving input to a per-
  /// materialization rolling buffer of fixed `count`, and emits an
  /// `FsWindow` view over the current buffer on every accepted input.
  ///
  /// Buffer is non-circular: when full, shift left to drop oldest. Each
  /// emit reuses the same buffer (the Window view shares it). Per spec,
  /// the view is callback-scoped — downstream MUST consume within the
  /// receiving callback before the next emission mutates the buffer.
  static final class WindowCountWrap implements Wrap < Object > {
    final int count;

    WindowCountWrap ( int count ) {
      if ( count <= 0 ) throw new IllegalArgumentException ( "count must be > 0" );
      this.count = count;
    }

    @Override
    public Consumer < Object > wrap ( Consumer < Object > downstream ) {
      final Object[] buffer = new Object[ count ];
      final int[]    sizeRef = { 0 };   // current valid length
      return v -> {
        int len = sizeRef[ 0 ];
        if ( len < count ) {
          buffer[ len ] = v;
          sizeRef[ 0 ] = len + 1;
        } else {
          // Shift left and append (drop oldest).
          System.arraycopy ( buffer, 1, buffer, 0, count - 1 );
          buffer[ count - 1 ] = v;
        }
        downstream.accept ( new FsWindow <> ( buffer, 0, sizeRef[ 0 ], false ) );
      };
    }
  }

  /// **Window (duration + capacity)** — same as count-based but also
  /// evicts entries whose capture timestamp is older than `duration`
  /// relative to the current processing time. Two parallel arrays:
  /// values + capture timestamps in nanoseconds.
  static final class WindowDurationWrap implements Wrap < Object > {
    final long durationNanos;
    final int  capacity;

    WindowDurationWrap ( Duration duration, int capacity ) {
      if ( duration == null || duration.isZero () || duration.isNegative () ) {
        throw new IllegalArgumentException ( "duration must be > 0" );
      }
      if ( capacity <= 0 ) throw new IllegalArgumentException ( "capacity must be > 0" );
      this.durationNanos = duration.toNanos ();
      this.capacity      = capacity;
    }

    @Override
    public Consumer < Object > wrap ( Consumer < Object > downstream ) {
      final Object[] values = new Object[ capacity ];
      final long[]   times  = new long[ capacity ];
      // tracked as a contiguous [0..length) range
      final int[]    sizeRef = { 0 };
      return v -> {
        final long now = System.nanoTime ();
        // Evict entries older than (now - durationNanos).
        int newStart = 0;
        final int len = sizeRef[ 0 ];
        while ( newStart < len && now - times[ newStart ] > durationNanos ) {
          newStart++;
        }
        int newLen = len - newStart;
        if ( newStart > 0 ) {
          System.arraycopy ( values, newStart, values, 0, newLen );
          System.arraycopy ( times,  newStart, times,  0, newLen );
        }
        if ( newLen < capacity ) {
          values[ newLen ] = v;
          times [ newLen ] = now;
          newLen++;
        } else {
          // Capacity-bound eviction: drop oldest.
          System.arraycopy ( values, 1, values, 0, capacity - 1 );
          System.arraycopy ( times,  1, times,  0, capacity - 1 );
          values[ capacity - 1 ] = v;
          times [ capacity - 1 ] = now;
        }
        sizeRef[ 0 ] = newLen;
        downstream.accept ( new FsWindow <> ( values, 0, newLen, false ) );
      };
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Immutable state
  // ═══════════════════════════════════════════════════════════════════════════

  private static final Wrap < ? >[]        EMPTY             = new Wrap < ? >[0];
  private static final FactoryEntry[]      NO_FACTORIES      = null;
  private static final FlowFactoryEntry[]  NO_FLOW_FACTORIES = null;

  private final Wrap < ? >[]           operators;
  private final int                    count;
  /// 2.4 per-attachment fiber factories. Null in common case (no
  /// `fiber(Function)` call). Each entry records the value of `count` at
  /// the time the factory was added; at `pipe(target)` time the factory is
  /// invoked once with `target.subject()` and the resulting fiber's
  /// operators are inlined at that recorded position.
  private final FactoryEntry[]         factories;
  /// 2.6 per-attachment flow factories. Same idea as factories but the
  /// produced value is a Flow (which itself has operators and may have
  /// its own factories — these are recursively resolved against the same
  /// target subject when this flow is materialised).
  private final FlowFactoryEntry[]     flowFactories;

  /// Records a per-attachment fiber factory and its insertion position.
  private record FactoryEntry(
    int position,
    Function < ? super Subject < ? >, ? extends Fiber < ? > > factory
  ) { }

  /// Records a per-attachment flow factory and its insertion position.
  private record FlowFactoryEntry(
    int position,
    Function < ? super Subject < ? >, ? extends Flow < ?, ? > > factory
  ) { }

  /// Identity flow — no operators.
  public FsFlow () {
    this.operators     = EMPTY;
    this.count         = 0;
    this.factories     = NO_FACTORIES;
    this.flowFactories = NO_FLOW_FACTORIES;
  }

  private FsFlow ( Wrap < ? >[] operators, int count,
                   FactoryEntry[] factories,
                   FlowFactoryEntry[] flowFactories ) {
    this.operators     = operators;
    this.count         = count;
    this.factories     = factories;
    this.flowFactories = flowFactories;
  }

  /// Backwards-compatible constructor (no flow factories).
  private FsFlow ( Wrap < ? >[] operators, int count, FactoryEntry[] factories ) {
    this ( operators, count, factories, NO_FLOW_FACTORIES );
  }

  /// Returns a new FsFlow with the given operator appended.
  @SuppressWarnings ( "unchecked" )
  private < X, Y > FsFlow < X, Y > append ( Wrap < ? > op ) {
    Wrap < ? >[] newOps = new Wrap < ? >[count + 1];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[count] = op;
    return new FsFlow <> ( newOps, count + 1, factories, flowFactories );
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Materialisation
  // ═══════════════════════════════════════════════════════════════════════════

  /// Materialises this flow into a concrete consumer chain.
  /// Each call produces independent state for stateful operators.
  ///
  /// operators[0] = first-added = outermost (closest to input, applied first)
  /// operators[n-1] = last-added = innermost (closest to target, applied last)
  ///
  /// Iterating from highest to lowest index wraps the last-added op around
  /// target first (making it innermost) and the first-added op last (making
  /// it outermost). Runtime data flow then matches the user's left-to-right
  /// reading order per SPEC §6.2.5.
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  Consumer < I > materialise ( Consumer < O > target ) {
    Consumer c = target;
    for ( int i = count - 1; i >= 0; i-- ) c = ( (Wrap) operators[i] ).wrap ( c );
    return c;
  }

  /// 2.4 — materialise an effective Wrap[] (factories already resolved).
  /// Used by `pipe(target)` when factories are present.
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  private Consumer < I > materialiseFrom ( Wrap < ? >[] effective, int effectiveCount, Consumer < O > target ) {
    Consumer c = target;
    for ( int i = effectiveCount - 1; i >= 0; i-- ) c = ( (Wrap) effective[i] ).wrap ( c );
    return c;
  }

  /// Resolves all per-attachment factories (fiber + flow) against
  /// `targetSubject` and builds the effective Wrap[] for materialisation.
  /// Flow factories may have their own nested factories; those are
  /// recursively resolved against the same subject.
  private Wrap < ? >[] resolveFactories ( Subject < ? > targetSubject ) {
    final int nFiberFactories = factories == null ? 0 : factories.length;
    final int nFlowFactories  = flowFactories == null ? 0 : flowFactories.length;

    // Resolve fiber factories (each produces a Wrap[] of fiber operators)
    Wrap < ? >[][] resolvedFiber = new Wrap < ? >[nFiberFactories][];
    int totalExtra = 0;
    for ( int i = 0; i < nFiberFactories; i++ ) {
      Fiber < ? > f = factories[i].factory.apply ( targetSubject );
      Objects.requireNonNull ( f, "fiber factory must not return null" );
      if ( !( f instanceof FsFiber < ? > fsFiber ) ) {
        throw new IllegalArgumentException ( "fiber factory must produce an FsFiber instance" );
      }
      Wrap < ? >[] ops = fsFiber.operators ();
      int fc = fsFiber.operatorCount ();
      Wrap < ? >[] copy = new Wrap < ? >[fc];
      System.arraycopy ( ops, 0, copy, 0, fc );
      resolvedFiber[i] = copy;
      totalExtra += fc;
    }

    // Resolve flow factories (each produces a Wrap[] of flow operators —
    // recursively resolved if the inner flow has its own factories)
    Wrap < ? >[][] resolvedFlow = new Wrap < ? >[nFlowFactories][];
    for ( int i = 0; i < nFlowFactories; i++ ) {
      Flow < ?, ? > f = flowFactories[i].factory.apply ( targetSubject );
      Objects.requireNonNull ( f, "flow factory must not return null" );
      if ( !( f instanceof FsFlow < ?, ? > fsFlow ) ) {
        throw new IllegalArgumentException ( "flow factory must produce an FsFlow instance" );
      }
      // Recursive resolve: if the inner flow has its own factories, resolve
      // them too; otherwise use its operators directly.
      Wrap < ? >[] innerEffective;
      if ( fsFlow.factories == null && fsFlow.flowFactories == null ) {
        innerEffective = new Wrap < ? >[fsFlow.count];
        System.arraycopy ( fsFlow.operators, 0, innerEffective, 0, fsFlow.count );
      } else {
        innerEffective = fsFlow.resolveFactories ( targetSubject );
      }
      resolvedFlow[i] = innerEffective;
      totalExtra += innerEffective.length;
    }

    // Build merged array: walk positions 0..count, inline factories at each
    // position. For positions with both fiber and flow factories at the same
    // index, fiber factories go first (preserves prior behaviour).
    Wrap < ? >[] merged = new Wrap < ? >[count + totalExtra];
    int w = 0;
    for ( int p = 0; p <= count; p++ ) {
      for ( int i = 0; i < nFiberFactories; i++ ) {
        if ( factories[i].position == p ) {
          Wrap < ? >[] r = resolvedFiber[i];
          System.arraycopy ( r, 0, merged, w, r.length );
          w += r.length;
        }
      }
      for ( int i = 0; i < nFlowFactories; i++ ) {
        if ( flowFactories[i].position == p ) {
          Wrap < ? >[] r = resolvedFlow[i];
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
    return new FsFlow <> ( merged, count + fc, factories, flowFactories );
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
    return new FsFlow <> ( operators, count, newFactories, flowFactories );
  }

  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < P > Flow < I, P > flow ( @NotNull Flow < ? super O, ? extends P > next ) {
    Objects.requireNonNull ( next );
    if ( !( next instanceof FsFlow < ?, ? > nextFlow ) ) {
      throw new IllegalArgumentException ( "next flow must be an FsFlow instance" );
    }
    if ( nextFlow.count == 0 && nextFlow.factories == null && nextFlow.flowFactories == null ) {
      return (Flow < I, P >) (Flow < ?, ? >) this;
    }
    Wrap < ? >[] merged = new Wrap < ? >[count + nextFlow.count];
    // operators[0..count) of `this` — innermost at index 0 — must remain innermost.
    // next's operators run after `this`, so they wrap further out (higher indices).
    System.arraycopy ( operators, 0, merged, 0, count );
    System.arraycopy ( nextFlow.operators, 0, merged, count, nextFlow.count );

    // Translate next's fiber factories: each next factory at position p shifts to position count+p.
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

    // Translate next's flow factories: same position shift.
    FlowFactoryEntry[] mergedFlowFactories = flowFactories;
    if ( nextFlow.flowFactories != null ) {
      int existing = flowFactories == null ? 0 : flowFactories.length;
      mergedFlowFactories = new FlowFactoryEntry[existing + nextFlow.flowFactories.length];
      if ( existing > 0 ) System.arraycopy ( flowFactories, 0, mergedFlowFactories, 0, existing );
      for ( int i = 0; i < nextFlow.flowFactories.length; i++ ) {
        FlowFactoryEntry fe = nextFlow.flowFactories[i];
        mergedFlowFactories[existing + i] = new FlowFactoryEntry ( count + fe.position, fe.factory );
      }
    }

    return new FsFlow <> ( merged, count + nextFlow.count, mergedFactories, mergedFlowFactories );
  }

  /// 2.6: Per-attachment Flow factory. Same as fiber-factory but produces
  /// a Flow (which is type-changing). Recursively resolves the inner Flow's
  /// own factories at materialisation.
  @NotNull
  @Override
  @SuppressWarnings ( "unchecked" )
  public < P > Flow < I, P > flow (
      @NotNull Function < ? super Subject < ? >, ? extends Flow < ? super O, ? extends P > > factory ) {
    Objects.requireNonNull ( factory, "factory" );
    FlowFactoryEntry entry = new FlowFactoryEntry ( count, factory );
    FlowFactoryEntry[] newFlowFactories;
    if ( flowFactories == null ) {
      newFlowFactories = new FlowFactoryEntry[]{entry};
    } else {
      newFlowFactories = new FlowFactoryEntry[flowFactories.length + 1];
      System.arraycopy ( flowFactories, 0, newFlowFactories, 0, flowFactories.length );
      newFlowFactories[flowFactories.length] = entry;
    }
    return (Flow < I, P >) (Flow) new FsFlow <> ( operators, count, factories, newFlowFactories );
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
    if ( count == 0 && factories == null && flowFactories == null ) return (Pipe < I >) (Pipe < ? >) target;

    // Resolve per-attachment factories (if any) against target.subject(). Each
    // factory is invoked once at attachment time per spec §6.2 / 2.4 / 2.6.
    final Wrap < ? >[] effective;
    final int          effectiveCount;
    if ( factories == null && flowFactories == null ) {
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

  /// 2.7: attach this flow's pipeline before a Cell's update pipe.
  @NotNull
  @Override
  public Pipe < I > pipe ( @NotNull Cell < O > cell ) {
    Objects.requireNonNull ( cell, "cell must not be null" );
    return pipe ( cell.pipe () );
  }


  /// 2.6: heterogeneous fold (scan) with state-only projection.
  @NotNull
  @Override
  public < S, P > Flow < I, P > scan (
      @NotNull Supplier < ? extends S > initial,
      @NotNull BiFunction < ? super S, ? super O, ? extends S > step,
      @NotNull Function < ? super S, ? extends P > emit ) {
    Objects.requireNonNull ( initial, "initial" );
    Objects.requireNonNull ( step,    "step" );
    Objects.requireNonNull ( emit,    "emit" );
    return appendOp ( new ScanStateWrap ( initial, step, emit ) );
  }

  /// 2.6: scan with input-aware projection.
  @NotNull
  @Override
  public < S, P > Flow < I, P > scan (
      @NotNull Supplier < ? extends S > initial,
      @NotNull BiFunction < ? super S, ? super O, ? extends S > step,
      @NotNull BiFunction < ? super S, ? super O, ? extends P > emit ) {
    Objects.requireNonNull ( initial, "initial" );
    Objects.requireNonNull ( step,    "step" );
    Objects.requireNonNull ( emit,    "emit" );
    return appendOp ( new ScanInputAwareWrap ( initial, step, emit ) );
  }

  /// 2.6: count-based windowing. Returns a flow that emits an
  /// FsWindow view of the most-recent `size` upstream values on every
  /// accepted input. The view is callback-scoped per spec §6.2.3.
  @NotNull
  @Override
  public Flow < I, Window < O > > window ( int size ) {
    if ( size <= 0 ) throw new IllegalArgumentException ( "size must be > 0" );
    return appendOp ( new WindowCountWrap ( size ) );
  }

  /// 2.7: time-based windowing with capacity bound.
  @NotNull
  @Override
  public Flow < I, Window < O > > window ( @NotNull Duration duration, int maxSize ) {
    Objects.requireNonNull ( duration, "duration" );
    if ( maxSize <= 0 ) throw new IllegalArgumentException ( "maxSize must be > 0" );
    return appendOp ( new WindowDurationWrap ( duration, maxSize ) );
  }

  /// Internal helper: returns a new FsFlow with the given op appended.
  /// The output type changes from O to whatever the op produces — at the
  /// type level we erase to Object since operators travel as Wrap[].
  @SuppressWarnings ( { "unchecked", "rawtypes" } )
  private < P > Flow < I, P > appendOp ( Wrap < ? > op ) {
    final int newCount = count + 1;
    final Wrap < ? >[] newOps = new Wrap < ? >[ newCount ];
    System.arraycopy ( operators, 0, newOps, 0, count );
    newOps[ count ] = op;
    return (Flow < I, P >) (Flow) new FsFlow <> ( newOps, newCount, factories, flowFactories );
  }
}
