# Bug: FsFlow/FsFiber operator-order reversed from spec

## Status
**Confirmed** with a focused reproducer (see below). All 519 tests pass
because none currently exercise an order-dependent multi-op chain on the
`Flow.fiber(Fiber<O>)` boundary.

## Reproducer

```java
import io.humainary.substrates.api.Substrates;
import io.humainary.substrates.api.Substrates.*;

var cortex  = Substrates.cortex();
var circuit = cortex.circuit();
var conduit = circuit.conduit(cortex.name("c"), Integer.class);
var output  = conduit.get(cortex.name("p"));
java.util.List<Integer> seen = new java.util.ArrayList<>();
conduit.subscribe(circuit.subscriber(cortex.name("sub"),
    (subj, reg) -> reg.register((Receptor<Integer>) seen::add)));

// 1) Multi-map order — left-to-right reading should give 30 (per SPEC §6.2.5)
Flow<Integer, Integer> f = cortex.flow(Integer.class)
    .map((Integer v) -> v + 1)
    .map((Integer v) -> v * 10);
f.pipe(output).emit(2);
circuit.await();
System.out.println(seen.get(0));   // observed: 21,  spec: 30
seen.clear();

// 2) Flow.fiber should attach Fiber<O> on the OUTPUT side
Flow<Integer, Integer> f2 = cortex.flow(Integer.class)
    .map((Integer v) -> v / 10)
    .fiber(cortex.fiber(Integer.class).diff());
Pipe<Integer> in = f2.pipe(output);
in.emit(25);   // bucket 2 → pass
in.emit(28);   // bucket 2 → should be suppressed (same as previous bucket)
in.emit(34);   // bucket 3
in.emit(36);   // bucket 3 → suppress
circuit.await();
System.out.println(seen);   // observed: [2, 2, 3, 3],  spec: [2, 3]
```

Both observations confirm: at runtime the operator at the HIGHEST index is
applied FIRST to the input — i.e. the last-added operator runs first.
SPEC §6.2.5 mandates left-to-right execution; our impl is right-to-left.

## Root cause

`FsFlow.materialise` (and `FsFiber.materialise`) iterates `operators[0..n-1]`
wrapping each around `c` (which starts as `target`). The resulting chain
fires operators in **reverse** index order at runtime — the highest-index
operator (the most recently added) ends up outermost.

```java
// FsFlow.java line 110
Consumer < I > materialise ( Consumer < O > target ) {
  Consumer c = target;
  for ( int i = 0; i < count; i++ ) c = ( (Wrap) operators[i] ).wrap ( c );
  return c;
}
```

Since `append()` adds to the END, the recently-added operator (which the
user reads as "the next stage") ends up at the HIGHEST index — which the
current materialise makes OUTERMOST. Reversed.

## Fix (small + local)

Change the materialise iteration to walk from highest index to lowest:

```java
Consumer < I > materialise ( Consumer < O > target ) {
  Consumer c = target;
  for ( int i = count - 1; i >= 0; i-- ) c = ( (Wrap) operators[i] ).wrap ( c );
  return c;
}
```

After the fix:
- operators[0] = first-added = OUTERMOST = first applied to input (matches reading order)
- operators[n-1] = last-added = INNERMOST = applied last just before target

For `Flow.fiber(Fiber<O>)`: existing impl appends fiber's ops at the END
of the array → they become INNERMOST → applied LAST = on the OUTPUT side.
That's correct under the fixed convention.

Apply the same one-line reversal in:
- `FsFlow.materialise`
- `FsFlow.materialiseFrom` (the 2.4 factory variant)
- `FsFiber.materialise`

Update the comment in each file from
```
operators[0] = first-added = innermost (closest to target)
operators[n-1] = last-added = outermost (closest to input)
```
to
```
operators[0] = first-added = outermost (closest to input)
operators[n-1] = last-added = innermost (closest to target)
```

## Test additions before fix

Add the reproducer above as `Substrates25Test$FlowOperatorOrder` so the
fix is regression-protected. Without these tests, the fix is invisible.

## Risk

The 519 existing tests passed under the BUG. There's a non-zero chance
some test was written empirically (matching observed behaviour) rather
than spec-derived, and would fail after the fix. The fix is one line —
running the full test suite after the change is the only way to know.

In our impl's own codebase the fix is needed for any composed `.map().fiber()`
or `.map().flow().fiber()` chain. In the **backtest** module's `Substrate.java`
we already routed around it (Tier 4 interpretation uses a direct receptor
instead of the Flow chain that should have worked) — once this fix lands,
the backtest can switch back to the clean substrates idiom.
