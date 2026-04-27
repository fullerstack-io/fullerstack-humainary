# Circuit Design

Low-level implementation details of `FsCircuit`, `IngressQueue`, `TransitQueueRing`, `QChunk`, and `FsChannel`.

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

### QChunk — The Ingress Storage Unit

`IngressQueue` stores emissions in `QChunk` — a 128-slot array with interleaved `[receiver, value]` layout:

```java
final class QChunk {
  static final int CAPACITY  = 128;
  static final int ARRAY_LEN = CAPACITY << 1;  // 256

  final    Object[] slots = new Object[ARRAY_LEN];  // [r0,v0,r1,v1,...]
  volatile QChunk   next;                           // link to next chunk

  @Contended
  volatile int claimed;                             // ingress: atomic getAndAdd
  QChunk freeNext;                                  // free list link
}
```

**Why interleaved?** Receiver and value land in adjacent positions for spatial cache locality. No wrapper object per emission — the chunk IS the storage.

**Why 128?** Tuned from a benchmark sweep. Doubling capacity halves the per-chunk-transition overhead (release-store fence on `next`, free-list pop) at the cost of slightly larger chunks. On the cyclic deep-cascade benchmark, 128 won.

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
- When chunk fills (every 128th emit), a new chunk is linked or recycled from a Treiber stack free list
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

### TransitQueueRing — Single-Threaded Ring

Cascading emissions (from within subscriber callbacks) go to the transit ring. No atomics — only the circuit thread accesses it. Two parallel arrays addressed by `head & mask` / `tail & mask`:

```java
void enqueue(Consumer<Object> receiver, Object value) {
  int i = tail & mask;
  receivers[i] = receiver;
  values[i] = value;
  tail++;
  if (tail - head > mask) grow();          // double on overflow
}

boolean drain() {
  if (head == tail) return false;
  do {
    int i = head & mask;
    Consumer<Object> r = (Consumer<Object>) receivers[i];
    Object v = values[i];
    receivers[i] = null;
    values[i] = null;
    head++;
    r.accept(v);
  } while (head != tail);
  // Reset cursors back to home position — single-threaded, no synchronization.
  head = 0;
  tail = 0;
  return true;
}
```

**Key properties:**
- No chunk-advance check, no ring-reset branch, no linked-list `next` maintenance — just `& mask` and indexed array access
- Pre-allocated initial capacity (64); grows by doubling with a one-shot copy when needed
- Cursors reset to 0 after each drain so the ring stays at home position with no fragmentation
- Read cursor chases write cursor — cascades within cascades resolve in a single drain call
- Transit drains with **priority** over ingress — all cascading effects complete before the next external emission

**Single-entry fast path:** when a fiber/flow chain produces exactly one downstream emission per input (the common case for `guard`, `map`, `peek`), the drain loop runs a single iteration. The reset-to-home write at the end is unconditional and very cheap.

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

### Marker Class Split — JIT Monomorphism

User receptors flow through a single concrete class so the hot-path `r.accept(v)` site stays monomorphic:

```java
static final class ReceptorAdapter<E> implements Consumer<Object>, Receptor<E> {
  final Receptor<? super E> receptor;
  @SuppressWarnings("unchecked")
  public void accept(Object o) { receptor.receive((E) o); }
  public void receive(E emission) { receptor.receive(emission); }
}
```

Markers and circuit jobs go through their own concrete classes — distinct types from `ReceptorAdapter` so they never pollute the hot-path type profile:

```java
static final class AwaitMarker  implements Consumer<Object> { /* await marker */ }
static final class CloseMarker  implements Consumer<Object> { /* close marker */ }
static final class CircuitJob   implements Consumer<Object> { /* one-shot Runnable */ }
```

The drain loop splits the call site: an `isMarker()` identity check (compares against the two pre-allocated marker references) routes markers through a separate cold path (`fireMarker`) so they keep their own type profile. `CircuitJob` is used by `FsConduit` for `subscribe`/`unsubscribe` and similar circuit-thread-only work, again avoiding lambda capture pollution at `ReceptorAdapter.accept`.

**Why all this?** Multiple lambda classes flowing through a single virtual call would cause bimorphic or megamorphic dispatch and class_check traps. Splitting by purpose keeps each call site monomorphic — C2 can devirtualise and inline.

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

### FsChannel — dispatch / cascadeDispatch split

Each named pipe in a conduit is fronted by an `FsChannel`. Channels expose two pre-built `Consumer<Object>` references after rebuild:

- **`dispatch`** — receptors only, no STEM walk. Used by ingress `receive()` (which adds the version check + STEM externally) and by `dispatchStem` when walking ancestors (so an ancestor's STEM walk is not retriggered).
- **`cascadeDispatch`** — receptors + STEM (if applicable). Submitted directly to transit by fiber/flow terminals to bypass the channel's version check on the cascade hot path.

The cascade-side bypass is sound by spec: §5.4.1 relation 3 + §7.6.2 guarantee that no subscriber-state change can interleave during a cascade — the version check therefore only needs to fire on ingress arrival, not on every transit step. `FsFlow.pipe(target)` and `FsFiber.pipe(target)` detect a same-circuit `FsPipe` whose receiver is an `FsChannel` and submit `channel.cascadeDispatch` straight to the transit ring.

For non-STEM channels, `cascadeDispatch == dispatch`. For STEM channels, `cascadeDispatch` wraps `dispatch` with the ancestor walk.

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
| `QChunk.CAPACITY` | 128 | Slots per ingress chunk (receiver+value pairs) |
| `QChunk.ARRAY_LEN` | 256 | Array length (128 × 2 for interleaving) |
| `TransitQueueRing.INITIAL_CAP` | 64 | Initial transit ring capacity (grows by doubling) |
| `SPIN_COUNT` | 1000 | Spin iterations before parking (~100us) |
| `PARK_NANOS` | 1,000 | Timed park interval (1us — virtual-thread-friendly) |

---

## References

- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec/blob/main/SPEC.md)
- [Design Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md)
- [Architecture](ARCHITECTURE.md) — high-level implementation overview
- [Async Architecture](ASYNC-ARCHITECTURE.md) — testing patterns and await() usage
