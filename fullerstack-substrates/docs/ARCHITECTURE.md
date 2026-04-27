# Fullerstack Substrates — Implementation Architecture

This document describes how Fullerstack implements the [Substrates Specification](https://github.com/humainary-io/substrates-api-spec). For what Substrates is and why it works this way, read the official [Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md) and [Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md).

For a conceptual introduction, start with the [Kitchen Model](KITCHEN-MODEL.md).

---

## Implementation Decisions

The spec is language-independent. These are our Java 26 projection choices:

| Spec Concept | Our Implementation | Why |
|---|---|---|
| Execution context | Virtual thread (one per circuit) | Lightweight, no platform thread exhaustion |
| Ingress queue | Custom `IngressQueue` (wait-free MPSC linked list of `QChunk`) | ~13ns emit, no CAS contention on producers |
| Transit queue | Custom `TransitQueueRing` (single-threaded power-of-2 ring) | Zero indirection on cascade hot path, automatic growth |
| Per-emission operators | `FsFiber` (immutable, reusable, 35+ ops) | Spec §6 — Fiber is the per-emission processing recipe |
| Memory ordering | `VarHandle` release/acquire (not volatile) | Cheaper than volatile for parked flag checks |
| False sharing | `@Contended` on `QChunk.claimed` | Isolate producer's atomic from consumer's cache line |
| Name interning | `ConcurrentHashMap` with hierarchical parent links | O(1) identity comparison via reference equality |
| Subject identity | `AtomicLong` counter (~5ns vs UUID's ~300ns) | Simple, fast, no collision risk in single JVM |
| Slot storage | Immutable `record` (Name + value + type) | Compact, no synchronization needed |
| Resource lifecycle | `Scope` with reverse-order close list | RAII-like structured cleanup |

## Class Map

27 classes in `io.fullerstack.substrates`:

```
FsCortexProvider (SPI entry point)
  └── FsCortex (entry point — creates circuits, scopes, names, states, slots, flows, fibers)
        └── FsCircuit (dual-queue sequential execution engine)
              ├── IngressQueue (wait-free MPSC — external emissions)
              │     └── QChunk (128-slot interleaved [receiver, value] array)
              ├── TransitQueueRing (single-threaded power-of-2 ring; cascade FIFO)
              ├── FsConduit (channel factory + subscriber management)
              │     ├── FsHub (subscriber list + version counter)
              │     ├── FsChannel (per-name dispatch — split: dispatch vs cascadeDispatch)
              │     │     └── FsPipe (async emission carrier — emit only)
              │     └── FsDerivedPool (derived view: pool(Function), pool(Flow), pool(Fiber))
              ├── FsFlow (type transformation: map / fiber / flow / pipe; hosts shared operator classes)
              ├── FsFiber (per-emission operators: ~35 — guard, diff, limit, peek, ...,
              │           plus 2.3 ops chance, change, deadband, delay, edge, every,
              │           hysteresis, inhibit, pulse, rolling, steady, tumble)
              ├── FsSubscriber (emission observer with lazy callback)
              │     └── FsSubscription (subscriber lifecycle handle)
              │           └── FsRegistrar (Consumer<Object> registration during callback)
              ├── FsTap (source emission transformation; tap(Function|Flow|Fiber))
              └── FsReservoir (buffered emission capture)

FsName (hierarchical dot-notation names with interning)
FsSubject (identity: Id + Name + State + Type)
FsState (slot-based state container)
  └── FsSlot (typed name-value pair)
FsScope (structured resource lifecycle)
  └── FsClosure (block-scoped resource management)
FsCurrent (circuit execution context)
FsSubstrate (base substrate implementation)
FsFault (provider error handling)
```

## Queue Architecture

The core performance-critical code is in three classes. See [Circuit Design](CIRCUIT-DESIGN.md) for the full implementation details.

### IngressQueue

Wait-free MPSC (multi-producer, single-consumer) linked list. External threads enqueue emissions by atomically claiming a slot in the current `QChunk`. When a chunk fills (128 slots), a new chunk is linked and the old one is recycled via a Treiber stack free list.

**Key properties:**
- Producers never block (wait-free `getAndAdd` on claimed counter)
- Consumer reads committed slots via `getAcquire` on the receiver position
- No CAS retry loop — `getAndAdd` always succeeds
- `@Contended` on `claimed` field prevents false sharing with consumer's reads

### TransitQueueRing

Single-threaded power-of-2 ring for cascading emissions (emissions that occur during circuit-thread processing). Two parallel arrays — `receivers[]` and `values[]` — addressed via `head & mask` / `tail & mask`. When the ring fills, it doubles in size with a one-shot copy.

**Key property:** Transit drains with priority over ingress. This ensures **causal completion** — all effects of an emission resolve before the next external emission is processed.

The single-entry `drain()` fast path avoids the loop-and-reset bookkeeping when a cascade emits exactly one value, which is the common case for guard/map/peek chains.

### QChunk

Unified 128-slot chunk used by `IngressQueue`. Stores receiver-value pairs interleaved: `[r0, v0, r1, v1, ...]`. This layout keeps the receiver (callback) and value (data) in adjacent cache lines for spatial locality. Capacity tuned from 64 to 128 from a benchmark sweep — fewer chunk transitions, fewer linked-list allocations, fewer release-store fences.

## Name Interning

`FsName` interns all name segments and caches the hierarchical structure. Two names with the same path are the same object (reference equality). This makes name comparison O(1) — critical for `conduit.get(name)` which happens on every emission.

```
cortex.name("kafka.broker.1")
  → FsName["kafka"]
       └── FsName["broker"] (parent = kafka)
            └── FsName["1"] (parent = broker)
```

Subsequent calls to `cortex.name("kafka.broker.1")` return the same `FsName` instance.

## Flow and Fiber

In Substrates 2.3 the per-emission processing surface lives on `Fiber<E>`; `Flow<I,O>` is reduced to type transformation only.

### `FsFiber` — per-emission operators

`FsFiber` is an immutable, reusable composition of operators (~35) that act on emissions of a single type. Each operator method returns a new fiber with the operator appended; the fiber value is reusable and may be materialised against multiple pipes, with each materialisation producing independent state.

Carryover operators (state classes shared with `FsFlow`):

- **diff()** — suppress unchanged values (Shannon's principle: only changes carry information)
- **guard(predicate)** — filter by predicate, with optional stateful bi-predicate
- **limit(n)** / **skip(n)** — windowing
- **peek(receptor)** — side-effect without consuming
- **reduce(initial, op)** / **integrate(...)** / **relate(...)** — running aggregation / windowed aggregation
- **replace(op)** — value transformation
- **takeWhile / dropWhile** — predicate-based windowing
- **above / below / clamp / range / max / min / high / low** — comparator-based sift

New 2.3 operators (defined in `FsFiber`):

- **chance, change, deadband, delay, edge, every, hysteresis, inhibit, pulse, rolling, steady, tumble**

### `FsFlow` — type transformation

`FsFlow<I,O>` provides only the type-changing surface: `map`, `flow`, `fiber`, `pipe`. `flow.fiber(fiber)` attaches a fiber at the output side; `flow.pipe(target)` materialises the chain into a new pipe whose terminal submits directly to the target's transit queue, bypassing the channel's version check on the cascade hot path (per spec §5.4.1 + §7.6.2 — subscriber state cannot change mid-cascade).

Both flow and fiber state are safe without synchronization because all processing runs on the circuit's single thread.

## Lazy Rebuild and the dispatch / cascadeDispatch split

When a subscriber is added or removed from a conduit, the change is not applied immediately. Instead, the conduit's hub version counter increments. On the next ingress emission, each channel checks if its cached subscriber list is stale (version mismatch) and rebuilds if needed.

`FsChannel` exposes two pre-built `Consumer<Object>` references after rebuild:

- **`dispatch`** — receptors only, no STEM walk. Used by ingress `receive()` (which adds the version check + STEM externally) and by `dispatchStem` when walking ancestors (so an ancestor's STEM walk is not retriggered).
- **`cascadeDispatch`** — receptors + STEM (if applicable). Submitted directly to transit by fiber/flow terminals to bypass the version check on the cascade hot path. For non-STEM channels this is the same reference as `dispatch`.

This avoids locking during subscription changes — the spec's "eventual consistency" model. A subscriber added between two emissions will see the second emission but not the first.

## Thread Safety Summary

| Component | Thread Safety | Why |
|---|---|---|
| `pipe.emit()` | Thread-safe (any thread) | Enqueues to IngressQueue via atomic getAndAdd |
| Flow operators | Circuit-thread only | State accessed only from circuit thread |
| Subscriber callbacks | Circuit-thread only | Invoked during circuit drain |
| `circuit.await()` | Thread-safe (any caller thread) | VarHandle park/unpark coordination |
| `circuit.close()` | Thread-safe, idempotent | Atomic flag + unpark |
| `cortex.name()` | Thread-safe | ConcurrentHashMap interning |
| `scope.close()` | Not thread-safe | Close from owning thread only |

## Performance

See [Benchmark Comparison](BENCHMARK-COMPARISON.md) for full JMH results across 14 groups. Those cross-platform numbers were collected with mismatched hardware and warmup parameters and are due for re-measurement on a quiet host.

Most-recent figure (JDK 26, GitHub Codespaces 2 vCPU, 10-iteration warmup):

| Operation | ns/op | What it measures |
|---|---:|---|
| `cyclic_emit_deep_await_batch` | ~12.9 | Per-cycle cost of a deep cascade through cyclic pipe networks |

## References

- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md) — formal behavioural contracts
- [Design Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md) — why determinism over throughput
- [Substrates API](https://github.com/humainary-io/substrates-api-java) — API interfaces (Javadoc)
- [Serventis API](https://github.com/humainary-io/serventis-api-java) — semiotic observability instruments
- [Circuit Design](CIRCUIT-DESIGN.md) — queue internals, VarHandle, performance optimizations
- [Async Architecture](ASYNC-ARCHITECTURE.md) — testing patterns, await() usage
- [Kitchen Model](KITCHEN-MODEL.md) — conceptual introduction as a restaurant story
