# Circuit Design

## Part 1: Humainary Specification (from Substrates.java)

These are the **requirements** defined by the Humainary Substrates API that any implementation MUST satisfy.

### Threading Model

From Substrates.java lines 41-62:

- Every circuit owns exactly **one processing thread** (virtual thread)
- All emissions, flows, and subscriber callbacks execute **exclusively on that thread**
- **Deterministic ordering**: Emissions observed in the order they were enqueued
- **No synchronization needed**: State touched only from circuit thread requires no locks
- **Sequential execution**: Only one operation executes at a time per circuit

### Caller vs Circuit Thread

- **Caller threads**: Enqueue emissions, return immediately
- **Circuit thread**: Dequeue and process emissions sequentially
- **Performance principle**: The circuit thread is the bottleneck

### Dual Queue Model (from Substrates.java lines 507-527)

Circuits use **two queues** to manage emissions with deterministic priority ordering:

- **Ingress queue**: Shared queue for emissions from external threads
- **Transit queue**: Local queue for emissions originating from circuit thread during processing

**Key characteristics**:
- **No call stack recursion**: All emissions enqueue rather than invoke nested calls
- **Stack safe**: Even deeply cascading chains don't overflow the stack
- **Priority processing**: Transit queue drains completely before returning to ingress queue
- **Transit is FIFO**: Depth-first execution order
- **Causality preservation**: Cascading effects complete before processing next external input

### Node Allocation Model

**Every emission allocates a QNode** - this is BY DESIGN, not an optimization target:

- QNodes are the unit of work in the circuit
- The intrusive `next` pointer enables lock-free queue linkage
- Node allocation is part of the specified emission cost
- **DO NOT** attempt to pool, reuse, or eliminate node allocation

This pattern enables:
- Wait-free producer path (no contention on shared pools)
- Simple, predictable memory model
- Clean separation between emission and execution

### Guarantees

1. **Deterministic ordering**: Emissions observed in the order they were accepted
2. **Circuit-thread confinement**: State touched only from circuit thread requires no synchronization
3. **Bounded enqueue**: Caller threads don't execute circuit work; enqueue path must be short
4. **Sequential execution**: Only one emission executes at a time per circuit
5. **Memory visibility**: Circuit thread guarantees visibility of all state updates

### Performance Target

~10ns emission latency for hot path

---

## Part 2: Fullerstack Implementation (FsCircuit)

These are **our implementation choices** for satisfying the Humainary specification.

### Custom IngressQueue + TransitQueue

We use a custom wait-free MPSC linked list (`IngressQueue`) for ingress and a separate `TransitQueue` class for transit:

```
External Threads                    Circuit Virtual Thread
      │                                     │
      ├─→ IngressQueue (wait-free MPSC) ────┤
      │   ingress.add(receiver, value)      │
      │                                     ├─→ Drain ingress (batch of 64)
                                            │   Drain transit after each node
                                            │   Process sequentially
```

**Implementation details**:
- `IngressQueue ingress` - wait-free MPSC linked list with sentinel node and atomic `getAndSet`
- `TransitQueue transit` - FIFO linked list (single-threaded, circuit thread only)
- Both queues use unified `QNode` type for monomorphic `run()` call sites
- Producer calls `ingress.add(receiver, value)` followed by parked flag check

### Dual-Queue Architecture with Unified QNode

Both queues share the same `QNode` class, ensuring C2 sees a single monomorphic type at `node.run()`:

```java
// QNode: unified node shared by IngressQueue and TransitQueue
final class QNode {
    final Consumer<Object> receiver;
    final Object           value;
    QNode                  next;

    void run() { receiver.accept(value); }
}
```

Thread routing happens in `FsPipe.emit()`, not in the circuit:

```java
// FsPipe: routes emissions based on calling thread identity
public final void emit(E emission) {
    if (isOnCircuitThread()) {
        circuit.submitTransit(receiver, emission);
    } else {
        circuit.submitIngress(receiver, emission);
    }
}

// FsCircuit: submitIngress for external threads (wait-free)
final void submitIngress(Consumer<Object> receiver, Object value) {
    ingress.add(receiver, value);
    if ((boolean) PARKED.getOpaque(this)) {
        wakeWorker();
    }
}

// FsCircuit: submitTransit for circuit-thread cascades
final void submitTransit(Consumer<Object> receiver, Object value) {
    transit.enqueue(receiver, value);
}
```

**Humainary compliance**:
- **No call stack recursion**: Cascading emissions enqueue to transit, never invoke directly
- **Stack-safe**: Deep cascading chains use iteration, never recursion
- **Transit drains first**: Priority processing before returning to ingress
- **FIFO order**: Depth-first execution via head/tail pointers
- **Causality preservation**: Cascading effects complete before next ingress item
- **Wait-free producer**: `getAndSet` on ingress tail always succeeds in one atomic operation

### VarHandle for Parked Flag

We use VarHandle with opaque/release semantics for the parked flag instead of volatile:

```java
private static final VarHandle PARKED;

@SuppressWarnings("unused") // Accessed via VarHandle
@Contended
private volatile boolean parked;
```

**Access patterns**:
- **Read (producer)**: `PARKED.getOpaque(this)` - cheaper than volatile read
- **Write before park**: `PARKED.setRelease(this, true)` - ensures visibility before parking
- **Write after wake**: `PARKED.setOpaque(this, false)` - cheaper, already running
- **Wake (CAS)**: `PARKED.compareAndSet(this, true, false)` - only one producer wakes

`@Contended` prevents false sharing between parked flag and other fields.

### ReceptorReceiver Pattern (JIT Monomorphism)

All `Consumer<Object>` instances flowing through the queues use a single concrete class:

```java
static final class ReceptorReceiver<E> implements Consumer<Object> {
    final Receptor<? super E> receptor;

    ReceptorReceiver(Receptor<? super E> receptor) {
        this.receptor = receptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void accept(Object o) { receptor.receive((E) o); }
}
```

**Why not lambdas?** Each lambda creates a distinct class at the `QNode.run()` → `receiver.accept()` call site. Multiple lambda classes cause bimorphic dispatch (type check + deopt). Using a single `ReceptorReceiver` class keeps the call site monomorphic — C2 can devirtualize and inline.

Markers (await, close) also use `ReceptorReceiver` to preserve monomorphism:

```java
// Pre-allocated in constructor
Receptor<Object> awaitReceptor = this::onAwaitMarker;
this.awaitMarkerReceiver = new ReceptorReceiver<>(awaitReceptor);
```

### Thread Initialization

Thread starts **eagerly** on circuit creation:

```java
public FsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.subscribers = new ArrayList<>();

    // Pre-allocate marker receivers as ReceptorReceiver instances
    this.awaitMarkerReceiver = new ReceptorReceiver<>(this::onAwaitMarker);
    this.closeMarkerReceiver = new ReceptorReceiver<>(this::onCloseMarker);

    // Create and start worker thread
    this.worker = Thread.ofVirtual()
        .name("circuit-" + subject.name())
        .start(this::workerLoop);
}
```

### Worker Loop

```java
// Hardcoded constant — spin count before parking (~100μs with Thread.onSpinWait)
private static final int SPIN_COUNT = 1000;

private void workerLoop() {
    final IngressQueue q = ingress;

    for (;;) {
        // ALWAYS drain transit first (cascades from previous work)
        boolean didWork = drainTransit();

        // Drain up to 64 ingress nodes (bounded batch for responsiveness)
        didWork |= q.drainBatch(this);

        if (didWork) continue;

        // shouldExit set by close marker (runs as ingress node on this thread)
        if (shouldExit) return;

        // No work available — spin before parking
        Object found = null;
        for (int i = 0; i < SPIN_COUNT && found == null; i++) {
            Thread.onSpinWait();
            found = q.peek();
        }

        if (found == null) {
            // Park phase — release CPU
            PARKED.setRelease(this, true);

            // Double-check before parking (avoid missed wake-up)
            if (q.peek() == null) {
                LockSupport.park();
            }

            PARKED.setOpaque(this, false);
        }
    }
}
```

### Drain Implementations

Drain logic is delegated to the queue classes:

```java
// TransitQueue: single-threaded FIFO drain (circuit thread only)
boolean drain() {
    QNode n = head;
    if (n == null) return false;
    head = null;
    tail = null;
    do { n.run(); n = n.next; } while (n != null);
    return true;
}

// IngressQueue: bounded batch drain with interleaved transit
boolean drainBatch(FsCircuit circuit) {
    QNode next = head.next;
    if (next == null) return false;
    for (int c = 64; ; ) {
        next.run();
        do {} while (circuit.drainTransit());  // Transit after each node
        if (--c <= 0) break;
        QNode n = next.next;
        if (n == null) break;
        next = n;
    }
    head = next;  // Update head ONCE at end (not per iteration)
    return true;
}
```

### Unified QNode Pattern

A single `QNode` class is shared by both queues. This ensures C2 sees one monomorphic type for `run()`:

```java
final class QNode {
    static final VarHandle NEXT;  // Used only by IngressQueue MPSC path

    final Consumer<Object> receiver;
    final Object           value;
    QNode                  next;

    QNode(Consumer<Object> receiver, Object value) {
        this.receiver = receiver;
        this.value = value;
    }

    void run() { receiver.accept(value); }
}
```

**Key insight**: All `Consumer<Object>` instances are `ReceptorReceiver` (named class), making `receiver.accept()` monomorphic too. The bimorphism moves to `receptor.receive()` where >99.9% are the common emission type — C2 handles this efficiently with type speculation.

### Await Implementation

Uses VarHandle `AWAITER` with park/unpark for lightweight synchronization:

```java
private static final VarHandle AWAITER;  // Thread field

public void await() {
    if (Thread.currentThread() == worker) {
        throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");
    }
    if (closed) { worker.join(); return; }
    awaitImpl();
}

private void awaitImpl() {
    Thread current = Thread.currentThread();

    // Try to register as the awaiter (CAS null → current)
    Thread existing = (Thread) AWAITER.compareAndExchange(this, null, current);
    if (existing != null) {
        // Piggyback on existing awaiter
        while (AWAITER.getOpaque(this) == existing) {
            LockSupport.parkNanos(1_000_000);  // 1ms timed park
        }
        return;
    }

    // Inject await marker (uses pre-allocated ReceptorReceiver)
    submitIngress(awaitMarkerReceiver, null);
    LockSupport.unpark(worker);

    // Park until marker wakes us
    while (AWAITER.getOpaque(this) == current) {
        LockSupport.park();
    }
}

// Marker callback — runs on circuit thread, unparks awaiter
private void onAwaitMarker(Object ignored) {
    Thread awaiter = (Thread) AWAITER.getAndSet(this, null);
    if (awaiter != null) LockSupport.unpark(awaiter);
}
```

---

## Part 3: Constraints (NEVER Violate)

| Constraint | Reason | Source |
|------------|--------|--------|
| Virtual threads only | Scalability model | Humainary spec |
| Sequential execution | Thread safety guarantee | Humainary spec |
| Transit priority | Causality preservation | Humainary spec |
| Wait-free producer | Performance requirement | Humainary spec |
| Node per emission | Lock-free queue linkage | Humainary spec |
| No platform threads | Breaks scalability | Implementation constraint |
| No node pooling | Adds contention, breaks wait-free | Implementation constraint |
| Eager thread start | Ensures circuit is ready | Implementation choice |

---

## Part 4: Performance Optimizations Applied

### Optimizations

| Optimization | Description | Impact |
|--------------|-------------|--------|
| VarHandle parked flag | Opaque read instead of volatile (~4% of emit) | ~64% reduction in parked check |
| Custom IngressQueue | Wait-free MPSC with getAndSet (no chunked alloc overhead) | Simpler, lower latency |
| Separate TransitQueue | Dedicated FIFO class (single-threaded, zero sync) | Cleaner cache lines |
| Unified QNode | Single type for monomorphic `run()` dispatch | C2 devirtualization |
| ReceptorReceiver | Single Consumer class for monomorphic `accept()` | C2 inlining of receive() |
| @Contended parked | Prevents false sharing on parked flag | Reduced cache contention |
| Batch drain (64) | Bounded ingress drain per cycle | Amortized head update |

### Benchmark Results

| Benchmark | Fullerstack | Humainary | Diff |
|-----------|-------------|-----------|------|
| hot_pipe_async | 4.7ns | 8.7ns | -46% |
| create_await_close | 5.5μs | 175μs | -97% |

### Hardcoded Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `SPIN_COUNT` | 1000 | Spin iterations before parking (~100μs) |
| `DRAIN_BATCH` | 64 | Ingress nodes drained per batch (in IngressQueue) |

---

## See Also

- [Substrates.java](https://github.com/humainary-io/substrates-api-java) - Official API specification
- [ARCHITECTURE.md](ARCHITECTURE.md) - Overall Fullerstack architecture
- [ASYNC-ARCHITECTURE.md](ASYNC-ARCHITECTURE.md) - Async-first design details
- [BENCHMARK-COMPARISON.md](BENCHMARK-COMPARISON.md) - Full benchmark results
