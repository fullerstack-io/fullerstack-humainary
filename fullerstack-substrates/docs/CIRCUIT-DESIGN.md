# Circuit Design

Low-level implementation details of `FsCircuit`, `IngressQueue`, `TransitQueue`, and `QChunk`.

For the specification requirements these implement, see the [Substrates Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md). For the high-level architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

---

## Part 1: Specification Requirements

From the Substrates Specification:

- **One execution context per circuit** — all processing on a single virtual thread
- **Deterministic ordering** — emissions processed in strict enqueue order
- **Dual-queue model** — ingress (external) + transit (cascading) with transit priority
- **No call stack recursion** — cascading emissions enqueue, never invoke nested calls
- **Stack safe** — even deeply cascading chains don't overflow the stack
- **Wait-free producer** — callers never block when emitting

---

## Part 2: Implementation

### QChunk — The Storage Unit

Both queues store emissions in `QChunk` — a 64-slot array with interleaved `[receiver, value]` layout:

```java
final class QChunk {
  static final int CAPACITY  = 64;
  static final int ARRAY_LEN = CAPACITY << 1;  // 128

  final    Object[] slots = new Object[ARRAY_LEN];  // [r0,v0,r1,v1,...]
  volatile QChunk   next;                           // link to next chunk

  @Contended
  volatile int claimed;                             // ingress: atomic getAndAdd
  QChunk freeNext;                                  // free list link
}
```

**Why interleaved?** Receiver and value land in adjacent positions for spatial cache locality. No wrapper object per emission — the chunk IS the storage.

### IngressQueue — Wait-Free MPSC

External threads enqueue emissions via atomic `getAndAdd` on the chunk's `claimed` counter:

```java
public void enqueue(Consumer<Object> receiver, Object value) {
  QChunk chunk = tail;                                    // volatile read
  int slot = (int) QChunk.CLAIMED.getAndAdd(chunk, 1);   // wait-free claim
  if (slot < QChunk.CAPACITY) {
    int base = slot << 1;
    chunk.slots[base + 1] = value;                        // plain store (value first)
    QChunk.SLOTS.setRelease(chunk.slots, base, receiver); // release store (commit)
  } else {
    enqueueSlow(receiver, value, chunk);                  // cold path: 1 in 64 emits
  }
}
```

**Key properties:**
- `getAndAdd` always succeeds — no CAS retry loop, true wait-free
- Receiver written with `setRelease` — acts as commit signal for the consumer
- Value written before receiver — consumer sees value when it sees receiver
- When chunk fills (every 64th emit), a new chunk is linked or recycled from a Treiber stack free list
- `@Contended` on `tail` and `freeHead` prevents false sharing with consumer reads

**Consumer drain** processes committed slots with interleaved transit drain:

```java
// Simplified — actual code handles chunk transitions and markers
Consumer<Object> r = (Consumer<Object>) QChunk.SLOTS.getAcquire(slots, base);
if (r == null) break;  // not yet committed
Object v = slots[base + 1];
slots[base] = null;     // clear for GC
slots[base + 1] = null;
r.accept(v);            // execute emission

// Depth-first: drain all transit work before next ingress slot
if (circuit.transitHasWork()) {
  do {} while (circuit.drainTransit());
}
```

### TransitQueue — Single-Threaded FIFO

Cascading emissions (from within subscriber callbacks) go to the transit queue. No atomics — only the circuit thread accesses it:

```java
void enqueue(Consumer<Object> receiver, Object value) {
  int idx = writeIndex;
  if (idx < QChunk.CAPACITY) {
    int base = idx << 1;
    writeChunk.slots[base] = receiver;     // plain write
    writeChunk.slots[base + 1] = value;
    writeIndex = idx + 1;
  } else {
    enqueueSlow(receiver, value);          // overflow: cascade depth > 64
  }
}
```

**Key properties:**
- Pre-allocated `homeChunk` reused every drain cycle — zero allocation in steady state
- Overflow chunks (cascade depth > 64) are allocated and become garbage after drain
- Read cursor chases write cursor — cascades within cascades resolve in a single drain call
- Transit drains with **priority** over ingress — all cascading effects complete before the next external emission

### Worker Loop

The circuit thread runs a spin-then-park loop:

```java
private void workerLoop() {
  final IngressQueue q = ingress;

  for (;;) {
    boolean didWork = q.drainBatch(this);  // drain ingress + interleaved transit
    if (didWork) continue;
    if (shouldExit) return;

    // Spin before parking
    Object found = null;
    for (int i = 0; i < SPIN_COUNT && found == null; i++) {
      Thread.onSpinWait();
      found = q.peek();
    }

    if (found == null) {
      LockSupport.parkNanos(PARK_NANOS);   // self-waking timed park
    }
  }
}
```

**Self-waking design:** The worker uses `parkNanos` instead of `park`. Producers never call `unpark` — the worker wakes itself after the timeout. This eliminates the cost of producer-side park/unpark coordination on the hot path. The only explicit `unpark` calls are from `await()` and `close()` (cold paths).

### ReceptorReceiver — JIT Monomorphism

All `Consumer<Object>` instances in the queues are a single concrete class:

```java
static final class ReceptorReceiver<E> implements Consumer<Object> {
  final Receptor<? super E> receptor;
  @SuppressWarnings("unchecked")
  public void accept(Object o) { receptor.receive((E) o); }
}
```

**Why not lambdas?** Multiple lambda classes cause bimorphic dispatch at `r.accept(v)`. A single `ReceptorReceiver` class keeps the call site monomorphic — C2 can devirtualize and inline. Markers (await, close) are separate from the hot path via an `isMarker()` guard to preserve the type profile.

### Await Implementation

```java
public void await() {
  if (Thread.currentThread() == worker) throw new IllegalStateException();
  if (closed) return;
  awaitImpl();
}

private void awaitImpl() {
  Thread current = Thread.currentThread();
  Thread existing = (Thread) AWAITER.compareAndExchange(this, null, current);
  if (existing != null) {
    // Piggyback on existing awaiter
    while (AWAITER.getOpaque(this) == existing) LockSupport.parkNanos(1_000_000);
    return;
  }
  // Inject marker and park
  submitIngress(awaitMarkerReceiver, null);
  LockSupport.unpark(worker);  // wake worker to process marker
  while (AWAITER.getOpaque(this) == current) LockSupport.park();
}

// Marker callback — runs on circuit thread, unparks awaiter
private void onAwaitMarker(Object ignored) {
  Thread awaiter = (Thread) AWAITER.getAndSet(this, null);
  if (awaiter != null) LockSupport.unpark(awaiter);
}
```

FIFO ordering guarantees all prior emissions complete before the marker executes.

---

## Part 3: Constraints

| Constraint | Reason |
|---|---|
| Virtual threads only | Scalability — thousands of circuits without platform thread exhaustion |
| Sequential execution | Thread safety guarantee — no locks needed in callbacks |
| Transit priority | Causality preservation — cascading effects complete atomically |
| Wait-free producer | Performance — `getAndAdd` always succeeds in one atomic operation |
| No node pooling | Adds contention, breaks wait-free property |
| Eager thread start | Circuit ready immediately on construction |
| Self-waking park | Producers never pay unpark cost on hot path |

---

## Part 4: Constants

| Constant | Value | Description |
|---|---|---|
| `QChunk.CAPACITY` | 64 | Slots per chunk (receiver+value pairs) |
| `QChunk.ARRAY_LEN` | 128 | Array length (64 × 2 for interleaving) |
| `SPIN_COUNT` | 1000 | Spin iterations before parking (~100us) |
| `PARK_NANOS` | 1,000,000 | Timed park interval (1ms) |

---

## References

- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md)
- [Design Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md)
- [Architecture](ARCHITECTURE.md) — high-level implementation overview
- [Async Architecture](ASYNC-ARCHITECTURE.md) — testing patterns and await() usage
