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

### Job Allocation Model

**Every emission allocates a Job** - this is BY DESIGN, not an optimization target:

- Jobs are the unit of work in the circuit
- The intrusive `next` pointer enables lock-free queue linkage
- Job allocation is part of the specified emission cost
- **DO NOT** attempt to pool, reuse, or eliminate job allocation

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

### JCTools MPSC Queue + Intrusive Transit Queue

We use a JCTools MpscUnboundedArrayQueue for ingress and an intrusive linked list for transit:

```
External Threads                    Circuit Virtual Thread
      │                                     │
      ├─→ JCTools MPSC (wait-free) ─────────┤
      │   ingress.offer(job)                │
      │                                     ├─→ Drain ingress (batch)
                                            │   Drain transit after each job
                                            │   Process sequentially
```

**Implementation details**:
- `MpscUnboundedArrayQueue<Job> ingress` - wait-free producer path with chunked allocation
- Producer calls `ingress.offer(job)` followed by parked check
- Consumer drains in batches via `ingress.drain(consumer, DRAIN_BATCH)`
- Transit uses intrusive FIFO linked list with `transitHead`/`transitTail`

### Intrusive Transit Queue (FIFO for Cascading)

We use an intrusive FIFO linked list for cascading emissions, reusing the Job's `next` field:

```java
// Transit queue: intrusive FIFO (circuit-thread only, no sync needed)
private Job transitHead;
private Job transitTail;

public void submit(Job job) {
    if (Thread.currentThread() == thread) {
        // Cascade: enqueue to transit using intrusive FIFO
        if (!running) return;
        job.next = null;
        if (transitTail == null) {
            transitHead = transitTail = job;
        } else {
            transitTail.next = job;
            transitTail = job;
        }
    } else {
        // External: JCTools MPSC queue
        submitExternal(job);
    }
}

private void submitExternal(Job job) {
    if (!running) return;
    ingress.offer(job);
    // Only unpark if thread is actually parked (opaque read is cheaper than volatile)
    if ((boolean) PARKED.getOpaque(this)) {
        LockSupport.unpark(thread);
    }
}
```

**Key insight**: Jobs have an intrusive `next` field that both queues use. When processing ingress jobs, we save the `next` pointer before running, freeing it for transit use.

**Humainary compliance**:
- **No call stack recursion**: Cascading emissions enqueue, never invoke directly
- **Stack-safe**: Deep cascading chains use iteration, never recursion
- **Transit drains first**: Priority processing before returning to ingress
- **FIFO order**: Depth-first execution via head/tail pointers
- **Causality preservation**: Cascading effects complete before next ingress item
- **Intrusive linkage**: Both queues use the same `next` field (per William Louth's design)

### VarHandle for Parked Flag

We use VarHandle with opaque/release semantics for the parked flag instead of volatile:

```java
// VarHandle for opaque access to parked flag (cheaper than volatile)
private static final VarHandle PARKED;
@SuppressWarnings("unused") // Accessed via VarHandle
private boolean parked = false;

static {
    try {
        PARKED = MethodHandles.lookup().findVarHandle(FsCircuit.class, "parked", boolean.class);
    } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

**Access patterns**:
- **Read (producer)**: `PARKED.getOpaque(this)` - cheaper than volatile read
- **Write before park**: `PARKED.setRelease(this, true)` - ensures visibility before parking
- **Write after wake**: `PARKED.setOpaque(this, false)` - cheaper, already running

This optimization reduced parked check overhead from ~11% to ~4% of emit time (measured via JFR profiling).

### Thread Initialization

Thread starts **eagerly** on circuit creation:

```java
public FsCircuit(Subject<Circuit> subject) {
    this.subject = subject;
    this.thread = Thread.ofVirtual()
        .name("circuit-" + subject.name())
        .start(this::loop);
}
```

### Consumer Loop

```java
// Configurable via system properties
private static final int SPIN_LIMIT =
    Integer.getInteger("io.fullerstack.substrates.spinLimit", 512);
private static final int DRAIN_BATCH =
    Integer.getInteger("io.fullerstack.substrates.drainBatch", 64);
private static final int CHUNK_SIZE =
    Integer.getInteger("io.fullerstack.substrates.chunkSize", 128);

private void loop() {
    int spins = 0;
    for (;;) {
        // Priority 1: Drain transit queue completely (cascading emissions)
        if (drainTransit()) {
            spins = 0;
            continue;
        }

        // Priority 2: Drain batch from ingress, with transit drain after each job
        if (drainIngress()) {
            spins = 0;
            continue;
        }

        // Check for shutdown (both queues empty)
        if (!running && ingress.isEmpty() && transitHead == null) {
            break;
        }

        // Spin before parking
        if (spins < SPIN_LIMIT) {
            spins++;
            Thread.onSpinWait();
        } else {
            // Set parked flag before parking so external submitters know to unpark
            PARKED.setRelease(this, true);
            if (ingress.isEmpty() && transitHead == null) {
                LockSupport.park();
            }
            PARKED.setOpaque(this, false);
            spins = 0;
        }
    }
}
```

### Drain Implementations

```java
/**
 * Drain transit queue completely. Returns true if any work was done.
 */
private boolean drainTransit() {
    if (transitHead == null) {
        return false;
    }
    while (transitHead != null) {
        Job job = transitHead;
        transitHead = job.next;
        if (transitHead == null) {
            transitTail = null;
        }
        try {
            job.run();  // May add more to transit
        } catch (Exception ignored) {}
    }
    return true;
}

/**
 * Drain batch from ingress, with transit drain after each job for causality.
 * Returns true if any work was done.
 */
private boolean drainIngress() {
    return ingress.drain(job -> {
        try {
            job.run();  // May add to transit
        } catch (Exception ignored) {}
        // Drain transit after each ingress job (causality preservation)
        drainTransit();
    }, DRAIN_BATCH) > 0;
}
```

### Intrusive Job Pattern

Jobs have an intrusive `next` field for queue linkage:

```java
/**
 * Base job class with intrusive next pointer for queue linkage.
 */
public abstract class Job {
    /** Intrusive next pointer - used by BOTH ingress and transit queues. */
    Job next;

    /** Execute this job on the circuit thread. */
    public abstract void run();
}

/**
 * Job that emits a value to a consumer.
 */
public final class EmitJob extends Job {
    private final Consumer<?> consumer;
    private final Object emission;

    public EmitJob(Consumer<?> consumer, Object emission) {
        this.consumer = consumer;
        this.emission = emission;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        ((Consumer<Object>) consumer).accept(emission);
    }
}
```

### Await Implementation

Uses CountDownLatch sentinel for precise synchronization:

```java
@Override
public void await() {
    if (Thread.currentThread() == thread) {
        throw new IllegalStateException("Cannot call Circuit::await from within a circuit's thread");
    }
    if (!running) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return;
    }
    var latch = new CountDownLatch(1);
    submit(new EmitJob(ignored -> latch.countDown(), null));
    try {
        latch.await();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
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
| Job per emission | Lock-free queue linkage | Humainary spec |
| No platform threads | Breaks scalability | Implementation constraint |
| No job pooling | Adds contention, breaks wait-free | Implementation constraint |
| Eager thread start | Ensures circuit is ready | Implementation choice |

---

## Part 4: Performance Optimizations Applied

### Recent Optimizations

| Optimization | Before | After | Improvement |
|--------------|--------|-------|-------------|
| VarHandle parked flag | volatile read (~11% of emit) | opaque read (~4% of emit) | ~64% reduction |
| JCTools MPSC queue | Hand-rolled MPSC | MpscUnboundedArrayQueue | Wait-free, chunked |
| Intrusive transit | Separate queue allocation | Reuses Job.next field | Zero allocation |
| Batch drain | Single item drain | DRAIN_BATCH (64) | Amortized overhead |

### Benchmark Results

| Benchmark | Fullerstack | Humainary | Diff |
|-----------|-------------|-----------|------|
| async_emit_single | 10.3ns | 8.7ns | +18% |
| hot_pipe_async | 4.7ns | 8.7ns | -46% |
| create_await_close | 5.5μs | 175μs | -97% |

### System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `io.fullerstack.substrates.spinLimit` | 512 | Spin iterations before parking |
| `io.fullerstack.substrates.drainBatch` | 64 | Jobs drained per ingress batch |
| `io.fullerstack.substrates.chunkSize` | 128 | JCTools queue chunk size |

---

## See Also

- [Substrates.java](https://github.com/humainary-io/substrates-api-java) - Official API specification
- [ARCHITECTURE.md](ARCHITECTURE.md) - Overall Fullerstack architecture
- [ASYNC-ARCHITECTURE.md](ASYNC-ARCHITECTURE.md) - Async-first design details
- [BENCHMARK-COMPARISON.md](BENCHMARK-COMPARISON.md) - Full benchmark results
