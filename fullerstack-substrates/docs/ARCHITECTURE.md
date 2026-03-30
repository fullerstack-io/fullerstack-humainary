# Fullerstack Substrates — Implementation Architecture

This document describes how Fullerstack implements the [Substrates Specification](https://github.com/humainary-io/substrates-api-spec). For what Substrates is and why it works this way, read the official [Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md) and [Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md).

For a conceptual introduction, start with the [Kitchen Model](KITCHEN-MODEL.md).

---

## Implementation Decisions

The spec is language-independent. These are our Java 26 projection choices:

| Spec Concept | Our Implementation | Why |
|---|---|---|
| Execution context | Virtual thread (one per circuit) | Lightweight, no platform thread exhaustion |
| Ingress queue | Custom `IngressQueue` (wait-free MPSC linked list) | ~13ns emit, no CAS contention on producers |
| Transit queue | Custom `TransitQueue` (intrusive FIFO with pre-allocated chunk) | Zero allocation on circuit thread, priority drain |
| Memory ordering | `VarHandle` release/acquire (not volatile) | Cheaper than volatile for parked flag checks |
| False sharing | `@Contended` on `IngressQueue.claimed` | Isolate producer's atomic from consumer's cache line |
| Name interning | `ConcurrentHashMap` with hierarchical parent links | O(1) identity comparison via reference equality |
| Subject identity | `AtomicLong` counter (~5ns vs UUID's ~300ns) | Simple, fast, no collision risk in single JVM |
| Slot storage | Immutable `record` (Name + value + type) | Compact, no synchronization needed |
| Resource lifecycle | `Scope` with reverse-order close list | RAII-like structured cleanup |

## Class Map

25 classes in `io.fullerstack.substrates`:

```
FsCortexProvider (SPI entry point)
  └── FsCortex (entry point — creates circuits, scopes, names, states, slots)
        └── FsCircuit (dual-queue sequential execution engine)
              ├── IngressQueue (wait-free MPSC — external emissions)
              │     └── QChunk (64-slot interleaved [receiver, value] array)
              ├── TransitQueue (single-threaded cascade FIFO)
              ├── FsConduit (channel factory + subscriber management)
              │     └── FsChannel (named emission port)
              │           └── FsPipe (async emission carrier)
              ├── FsFlow (processing pipeline: diff, guard, limit, sample, sift, skip)
              │     └── FsSift (comparison-based filtering)
              ├── FsSubscriber (emission observer with lazy callback)
              │     └── FsSubscription (subscriber lifecycle handle)
              │           └── FsRegistrar (pipe registration during callback)
              ├── FsTap (conduit emission transformation)
              └── FsReservoir (buffered emission capture)

FsName (hierarchical dot-notation names with interning)
FsSubject (identity: Id + Name + State + Type)
FsState (slot-based state container)
  └── FsSlot (typed name-value pair)
FsScope (structured resource lifecycle)
  └── FsClosure (block-scoped resource management)
FsCurrent (circuit execution context)
FsSubstrate (base substrate implementation)
FsException (provider error handling)
```

## Queue Architecture

The core performance-critical code is in three classes. See [Circuit Design](CIRCUIT-DESIGN.md) for the full implementation details.

### IngressQueue

Wait-free MPSC (multi-producer, single-consumer) linked list. External threads enqueue emissions by atomically claiming a slot in the current `QChunk`. When a chunk fills (64 slots), a new chunk is linked and the old one is recycled via a Treiber stack free list.

**Key properties:**
- Producers never block (wait-free `getAndAdd` on claimed counter)
- Consumer reads committed slots via `getAcquire` on the receiver position
- No CAS retry loop — `getAndAdd` always succeeds
- `@Contended` on `claimed` field prevents false sharing with consumer's reads

### TransitQueue

Single-threaded FIFO for cascading emissions (emissions that occur during circuit-thread processing). Uses a pre-allocated `QChunk` that resets each drain cycle. Overflow chunks linked via `next` for cascade depth > 64.

**Key property:** Transit queue drains with priority over ingress. This ensures **causal completion** — all effects of an emission resolve before the next external emission is processed.

### QChunk

Unified 64-slot chunk used by both queues. Stores receiver-value pairs interleaved: `[r0, v0, r1, v1, ...]`. This layout keeps the receiver (callback) and value (data) in adjacent cache lines for spatial locality.

## Name Interning

`FsName` interns all name segments and caches the hierarchical structure. Two names with the same path are the same object (reference equality). This makes name comparison O(1) — critical for conduit percept lookup which happens on every emission.

```
cortex.name("kafka.broker.1")
  → FsName["kafka"]
       └── FsName["broker"] (parent = kafka)
            └── FsName["1"] (parent = broker)
```

Subsequent calls to `cortex.name("kafka.broker.1")` return the same `FsName` instance.

## Flow Pipeline

`FsFlow` implements the spec's flow operators as a chain of stateful transformations. Each operator wraps a `Receptor` and returns a new `Receptor`:

- **diff()** — suppress unchanged values (Shannon's principle: only changes carry information)
- **guard(predicate)** — filter by predicate, with optional stateful bi-predicate
- **limit(n)** — pass first n emissions, then suppress
- **sample(n)** — pass every nth emission
- **sift(comparator, configurer)** — comparison-based filtering (above, below, range, high, low)
- **skip(n)** — suppress first n emissions
- **peek(receptor)** — side-effect without consuming
- **reduce(identity, operator)** — running aggregation
- **replace(operator)** — value transformation

Flow state is safe without synchronization because all flow processing runs on the circuit's single thread.

## Lazy Rebuild

When a subscriber is added or removed from a conduit, the change is not applied immediately. Instead, the conduit's version counter increments. On the next emission, each channel checks if its cached subscriber list is stale (version mismatch) and rebuilds if needed.

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

See [Benchmark Comparison](BENCHMARK-COMPARISON.md) for full JMH results across 14 groups.

Key numbers (JDK 26, GitHub Codespaces 2 vCPU):

| Operation | ns/op | What it measures |
|---|---:|---|
| Hot pipe async emit | 13.7 | End-to-end emission through pre-warmed circuit |
| Conduit get by name | 2.8 | Percept lookup (name interning) |
| Name from string | 2.7 | Name creation + interning |
| Scope create + close | 0.95 | Resource lifecycle overhead |
| Cyclic emit | 3.8 | Recurrent pipe network emission |

## References

- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md) — formal behavioural contracts
- [Design Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md) — why determinism over throughput
- [Substrates API](https://github.com/humainary-io/substrates-api-java) — API interfaces (Javadoc)
- [Serventis API](https://github.com/humainary-io/serventis-api-java) — semiotic observability instruments
- [Circuit Design](CIRCUIT-DESIGN.md) — queue internals, VarHandle, performance optimizations
- [Async Architecture](ASYNC-ARCHITECTURE.md) — testing patterns, await() usage
- [Kitchen Model](KITCHEN-MODEL.md) — conceptual introduction as a restaurant story
