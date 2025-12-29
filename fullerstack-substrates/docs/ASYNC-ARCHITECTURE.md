# Async-First Architecture in Substrates

**Status**: Core Design Principle
**Applies To**: All emissions, subscriber callbacks, and event processing

## Executive Summary

Substrates uses an **async-first** design where ALL emissions flow through the Circuit Queue asynchronously. This differs fundamentally from reactive frameworks like RxJava which are **synchronous by default**.

**Key Insight**: When you call `pipe.emit(value)`, the emission does NOT execute immediately. Instead, it posts a Task to the Circuit's ingress queue and returns immediately. The actual processing happens later when the Circuit's virtual thread executes the Task.

## Async vs Sync: RxJava Comparison

### RxJava (Synchronous by Default)

```java
// RxJava BehaviorSubject
BehaviorSubject<String> subject = BehaviorSubject.create();

AtomicReference<String> received = new AtomicReference<>();
subject.subscribe(value -> received.set(value));

subject.onNext("hello");  // ← BLOCKS until subscriber callback completes

// received.get() is IMMEDIATELY "hello" (synchronous)
assertEquals("hello", received.get());  // ✅ Works (synchronous)
```

**Flow**:
```
onNext("hello")
  ↓ (synchronous call stack)
subscriber callback executes
  ↓ (synchronous call stack)
received.set("hello")
  ↓
onNext() returns
```

### Substrates (Asynchronous by Default)

```java
// Substrates Pipe
Circuit circuit = cortex().circuit();
Conduit<Pipe<String>, String> conduit = circuit.conduit(
    cortex().name("test"),
    Composer.pipe()
);

AtomicReference<String> received = new AtomicReference<>();
conduit.subscribe(cortex().subscriber(
    cortex().name("sub"),
    (subject, registrar) -> registrar.register(received::set)
));

Pipe<String> pipe = conduit.get(cortex().name("channel"));
pipe.emit("hello");  // ← Returns IMMEDIATELY (enqueues Task to circuit)

// ❌ WRONG: received.get() is still NULL (async hasn't executed yet)
assertNull(received.get());

// ✅ CORRECT: Wait for queue to process
circuit.await();  // Blocks until queue empty
assertEquals("hello", received.get());  // Now it's available
```

**Flow**:
```
pipe.emit("hello")
  ↓
FsPipe.emit() → circuit.enqueue(receiver, emission)
  ↓
ingress.offer(Task(receiver, emission))  // ← Returns immediately (async boundary)
  ↓
[Time passes - task queued, emitter continues]
  ↓
Circuit virtual thread: drains ingress queue
  ↓
receiver.accept(value) → conduit.dispatch(subject, value)
  ↓
subscriber callback executes
  ↓
received.set("hello")
```

## Circuit Type Configuration

Fullerstack provides two circuit implementations, selectable via system property:

```bash
# JCtools Circuit (default) - MPSC queue with wait-free producer path
-Dfullerstack.circuit.type=jctools

# Folded Circuit - Linked job cascading for optimized deep chains
-Dfullerstack.circuit.type=folded
```

| Circuit | Best For | Architecture |
|---------|----------|--------------|
| **JCtools** (default) | General workloads, high-throughput | MPSC queue + ArrayDeque transit |
| **Folded** | Deep cascade chains (10+ levels) | Linked job lists, inline emit |

Both circuits pass all 383 TCK tests and provide identical semantics.

---

## Circuit Queue Architecture - FsJctoolsCircuit

### JCTools-Based Circuit Implementation

Each Circuit uses a **JCTools MPSC queue + Transit deque + Lazy Virtual Thread**:

**Implementation (FsJctoolsCircuit):**
```java
public final class FsJctoolsCircuit implements FsInternalCircuit {
    // JCTools MPSC queue - wait-free producer path, chunked allocation
    private final MpscUnboundedArrayQueue<Task> ingress;

    // Transit queue for cascading (LIFO for true depth-first)
    private final ArrayDeque<Runnable> transit;

    // Lazy-started virtual thread
    private volatile Thread thread;       // Started on first enqueue
    private volatile boolean started;     // Fast path check
    private final AtomicBoolean parked;   // Fast unpark signaling
}
```

**Architecture**:
```
Circuit (FsJctoolsCircuit)
  ├─ Ingress (MpscUnboundedArrayQueue)  (External emissions, MPSC, wait-free)
  ├─ Transit (ArrayDeque)               (Cascading emissions, LIFO, priority)
  ├─ Virtual Thread                     (LAZY - starts on first emit)
  └─ AtomicBoolean parked               (fast unpark signaling)

All Conduits share the same Circuit:
  External Thread:
    Conduit 1: Pipes → ensureStarted() → ingress.offer(task)
    Conduit 2: Pipes → ensureStarted() → ingress.offer(task)

  Circuit Thread (cascading):
    Subscriber callbacks → transit.push(task)  (LIFO for depth-first)

Circuit processes tasks with depth-first execution:
  → Drain transit stack first (LIFO - depth-first)
  → Drain ingress queue (bulk drain with JCTools)
  → After each ingress task: drain transit again (priority)
  → Spin-then-park if no work
```

---

## Circuit Queue Architecture - FsFoldedCircuit

### Linked Job Cascading Implementation

The Folded Circuit uses a **linked job list** approach optimized for deep cascade chains:

**Implementation (FsFoldedCircuit):**
```java
public final class FsFoldedCircuit implements FsInternalCircuit {
    // JCTools MPSC queue for external emissions
    private final MpscUnboundedArrayQueue<Job> ingress;

    // Job pool for reuse (reduces allocations)
    private final ConcurrentLinkedQueue<Job> pool;

    // Lazy-started virtual thread
    private volatile Thread thread;
    private volatile boolean started;
    private final AtomicBoolean parked;

    // Job linking for cascade chains
    static final class Job implements Runnable {
        private Runnable task;
        private Job next;  // Linked list for cascading
    }
}
```

**Architecture**:
```
Circuit (FsFoldedCircuit)
  ├─ Ingress (MpscUnboundedArrayQueue<Job>)  (External emissions, MPSC)
  ├─ Job Pool (ConcurrentLinkedQueue<Job>)   (Reusable job objects)
  ├─ Virtual Thread                          (LAZY - starts on first emit)
  └─ Job Linking                             (Cascade chains as linked lists)

External Thread:
  emit(task) → Job job = acquireJob() → job.task = task
            → ingress.offer(job) → wakeIfParked()

Circuit Thread (processing):
  Job head = drain ingress (batch)
            → reverse to maintain FIFO
            → process each job
            → for cascade: job.next = currentHead; currentHead = job (inline)
```

**Key Difference from JCtools Circuit:**
- **JCtools**: Uses separate ArrayDeque for transit (cascade) emissions
- **Folded**: Links jobs together in a chain, avoiding transit queue overhead

**When Folded Excels:**
- Deep cascade chains (10+ subscriber levels)
- Systems with heavy recursive/cascade emissions
- Scenarios where transit queue contention matters

### Benefits of Async-First Design

1. **Depth-First Execution** - Transit deque has priority for recursive emissions
2. **Deterministic Ordering** - Dual-queue guarantees predictable order within Circuit
3. **Backpressure Management** - Queues prevent saturation
4. **No Blocking** - Emitters never block (post and return)
5. **Simplified Threading** - Single virtual thread per Circuit
6. **Lock-Free Concurrency** - Single-threaded execution eliminates locks

### Emission Flow (Complete Path)

```
[External Code]
     │
     ├─→ pipe.emit(value)                    // User emits to Pipe
     │        │
     │        ↓
     │   [FsPipe.emit()]
     │        │
     │        ├─→ circuit.isRunning()?        // Check circuit not closed
     │        │
     │        ├─→ circuit.enqueue(receiver, emission)
     │        │
     │        ↓
     │   ingress.offer(Task(receiver, emission))
     │        │
     │        ├─→ if (parked) unpark(thread)  // Wake processor if sleeping
     │        │
     │        └─→ RETURNS IMMEDIATELY (async boundary)
     │
     │   [Time passes - Task queued, emitter continues]
     │
     │   [FsJctoolsCircuit.loop()]            // Virtual thread processor
     │        │
     │        ├─→ transit.poll()              // Drain transit FIRST (depth-first)
     │        │
     │        ├─→ ingress.drain(task -> ...)  // Then drain ingress (bulk)
     │        │
     │        ↓
     │   [receiver.accept(value)]
     │        │
     │        ↓
     │   [FsConduit.dispatch(subject, value)]
     │        │
     │        ├─→ Iterate subscribers
     │        │
     │        └─→ subscriber.emit(value)      // Deliver to each registered subscriber
     │                 │
     │                 ↓
     │            [Subscriber Logic]          // User's consumption logic
```

## circuit.await() - Synchronization

### Purpose

`circuit.await()` blocks the calling thread until all pending work is complete.

### Implementation - CountDownLatch Sentinel

The await mechanism uses a CountDownLatch sentinel pattern:

```java
// FsJctoolsCircuit.java - CountDownLatch-based await
public void await() {
    Thread t = thread;
    if (t == null) {
        return;  // No thread started yet, nothing to await
    }
    if (Thread.currentThread() == t) {
        throw new IllegalStateException("Cannot await from circuit thread");
    }

    // Submit a sentinel task and wait for it to complete.
    // When it runs, all prior work is done (FIFO ordering).
    var latch = new CountDownLatch(1);
    submit(latch::countDown);
    try {
        latch.await();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

**How It Works:**
- A sentinel task (`latch::countDown`) is submitted to the circuit queue
- The caller blocks on `latch.await()` until the sentinel executes
- FIFO ordering guarantees all prior tasks complete before the sentinel
- Zero-polling - efficient blocking with CountDownLatch

### When to Use circuit.await()

**Use Case 1: Testing**

This is the PRIMARY use case for `circuit.await()` in tests:

```java
@Test
void testEmission() throws Exception {
    // Setup
    Circuit circuit = cortex().circuit();
    Conduit<Pipe<String>, String> conduit = circuit.conduit(
        cortex().name("test"),
        Composer.pipe()
    );

    AtomicReference<String> received = new AtomicReference<>();
    conduit.subscribe(cortex().subscriber(
        cortex().name("sub"),
        (subject, registrar) -> registrar.register(received::set)
    ));

    // Act
    Pipe<String> pipe = conduit.get(cortex().name("channel"));
    pipe.emit("hello");

    // Assert - MUST wait for async processing
    circuit.await();  // ← CRITICAL for testing async emissions
    assertEquals("hello", received.get());
}
```

**Use Case 2: Graceful Shutdown**

```java
Circuit circuit = cortex().circuit();
// ... use circuit ...

// Ensure all pending emissions are processed before closing
circuit.await();
circuit.close();
```

**Use Case 3: Synchronization Points**

```java
// Emit batch of events
for (int i = 0; i < 100; i++) {
    pipe.emit(i);
}

// Wait for batch to complete before next phase
circuit.await();

// All 100 events have been processed - safe to proceed
startNextPhase();
```

### ❌ DON'T Use circuit.await() for Every Emission

**Anti-pattern** (defeats async design):

```java
// ❌ WRONG - Defeats async benefits
for (int i = 0; i < 1000; i++) {
    pipe.emit(i);
    circuit.await();  // BAD: Blocks after every emission
}
```

**Correct pattern**:

```java
// ✅ CORRECT - Leverages async queuing
for (int i = 0; i < 1000; i++) {
    pipe.emit(i);  // Posts to queue, returns immediately
}

// Only wait at the end (or not at all if you don't need to)
circuit.await();
```

## Testing Patterns

### ❌ Wrong Pattern: Using Latches

**This pattern is INCORRECT** because it assumes subscriber callbacks execute synchronously:

```java
// ❌ WRONG - Assumes synchronous execution
@Test
void testEmission_WRONG() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> received = new AtomicReference<>();

    conduit.subscribe(cortex().subscriber(
        cortex().name("sub"),
        (subject, registrar) -> registrar.register(value -> {
            received.set(value);
            latch.countDown();  // Trying to signal completion
        })
    ));

    pipe.emit("hello");

    // This may timeout because:
    // 1. emit() returns immediately (doesn't block)
    // 2. Subscriber callback runs asynchronously on Queue thread
    // 3. Race condition: latch.await() may start before callback executes
    assertTrue(latch.await(2, TimeUnit.SECONDS));  // ❌ May fail
}
```

### ✅ Correct Pattern: Using circuit.await()

```java
// ✅ CORRECT - Waits for Queue to process
@Test
void testEmission_CORRECT() throws Exception {
    AtomicReference<String> received = new AtomicReference<>();

    conduit.subscribe(cortex().subscriber(
        cortex().name("sub"),
        (subject, registrar) -> registrar.register(received::set)
    ));

    pipe.emit("hello");

    // Wait for Circuit to process all pending Tasks
    circuit.await();

    // Now safe to assert
    assertEquals("hello", received.get());  // ✅ Always works
}
```

### When Latches ARE Appropriate

**CountDownLatch is appropriate for thread coordination**, not async queue synchronization:

```java
// ✅ CORRECT - Using latch for multi-threaded coordination
@Test
void testConcurrentAccess() throws Exception {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            try {
                startLatch.await();  // Wait for start signal
                pipe.emit("value");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        }).start();
    }

    startLatch.countDown();  // Start all threads
    assertTrue(doneLatch.await(5, TimeUnit.SECONDS));  // Wait for threads

    circuit.await();  // THEN wait for queue processing
}
```

## Performance Characteristics

### Async Queue Overhead

**Cost of async boundary**:
- Task enqueue: ~15-25ns (offer to JCTools MPSC queue)
- Context switch: ~50-100ns (virtual thread)
- **Total overhead**: ~65-125ns per emission

**Benefits**:
- No blocking on emit() - emitter continues immediately
- Ordered execution (FIFO guarantee)
- Backpressure control (bounded queue capacity if needed)
- Lock-free concurrency (single-threaded execution)

### Benchmark Results

From JMH benchmarks:

| Operation | Latency | Notes |
|-----------|---------|-------|
| Pipe.emit() (hot path) | ~17-27ns | Async enqueue (JCTools MPSC) |
| Full path (lookup + emit) | ~100-300ns | conduit → channel → pipe → emit |
| Subscriber callback | ~50-100ns | End-to-end: emit → queue → callback |
| Multi-threaded emission | Linear scaling | Wait-free producers (MPSC queue) |

## Cell Hierarchical Architecture and Async

### Cell Emission Flow

Cells also use async emission through the Circuit Queue:

```java
// Cell hierarchy: cluster → broker → partition
Cell<KafkaMetric, Alert> cluster = circuit.cell(composer);
Cell<KafkaMetric, Alert> broker1 = cluster.get(name("broker-1"));
Cell<KafkaMetric, Alert> partition0 = broker1.get(name("partition-0"));

// Emit at leaf level
partition0.emit(metric);  // ← Returns immediately (async)

// Transformation and distribution happen asynchronously:
// 1. partition0 transforms metric → alert
// 2. Emits to broker1's Source
// 3. Emits to cluster's Source
// All via Circuit Tasks

circuit.await();  // Wait for async processing
```

### Parent Broadcast

```java
// Emit to parent broadcasts to ALL children (asynchronously)
broker1.emit(metric);  // ← Returns immediately

// Async flow:
// 1. Task posted to Circuit's ingress queue
// 2. Circuit processes: broadcast to all child Cells
// 3. Each child transforms metric → alert (async)
// 4. Each child emits to broker1's Source (async)

circuit.await();  // Wait for all children to process
```

## Async-First Design Rationale

### Why Async by Default?

1. **Simplifies Concurrency** - Single virtual thread per Circuit eliminates locks
2. **Natural Backpressure** - Queue provides bounded execution context
3. **Ordering Guarantees** - FIFO queue ensures event order
4. **Non-Blocking Producers** - Emitters never wait for consumers
5. **Predictable Performance** - Consistent FIFO processing, no priority inversions

### When Synchronous Would Be Wrong

**Scenario: Multiple slow consumers**

```java
// If synchronous (like RxJava):
source.subscribe(subscriber1);  // Slow - processes 100ms
source.subscribe(subscriber2);  // Slow - processes 100ms
source.subscribe(subscriber3);  // Slow - processes 100ms

pipe.emit("value");  // Would block for 300ms! (serial execution)

// Async (Substrates):
pipe.emit("value");  // Returns immediately
// Queue processes subscribers asynchronously
// Total time: ~100ms (parallel execution on virtual threads)
```

## Debugging Async Issues

### Common Mistake: Asserting Too Early

```java
// ❌ WRONG
pipe.emit("hello");
assertEquals("hello", received.get());  // NULL - async hasn't executed

// ✅ CORRECT
pipe.emit("hello");
circuit.await();
assertEquals("hello", received.get());  // Works - async completed
```

### Tracing Async Execution

**Add logging to see async flow**:

```java
// Pipe implementation
pipe.emit(value);  // Log: "Emitting: value"

// Circuit processor
receiver.accept(value);  // Log: "Executing task"

// Subscriber callback
registrar.register(value -> {
    System.out.println("Received: " + value);  // Log: "Received: value"
});
```

**Timeline**:
```
T+0ms:   emit("hello") - enqueues Task
T+0ms:   emit() returns (immediate)
T+10μs:  Circuit picks up Task
T+10μs:  conduit.dispatch(subject, value)
T+10μs:  Subscriber receives "hello"
```

### Verifying Queue Processing

```java
Circuit circuit = cortex().circuit(cortex().name("test"));

pipe.emit("value");

// Task is queued, not yet executed

circuit.await();  // Block until all pending tasks complete

// Now all tasks are executed
```

## Summary Table

| Aspect | RxJava (Sync) | Substrates (Async) |
|--------|---------------|-------------------|
| **emit() behavior** | Blocks until subscribers complete | Returns immediately |
| **Subscriber callbacks** | Execute on calling thread | Execute on Circuit virtual thread |
| **Ordering** | Not guaranteed (multi-threaded) | Depth-first guarantee (dual-queue) |
| **Backpressure** | Manual (Flowable) | Built-in (queue capacity) |
| **Testing pattern** | No special handling | Must use circuit.await() |
| **Threading model** | Configurable schedulers | Single virtual thread per Circuit |
| **Concurrency** | Locks needed for safety | Lock-free (single-threaded) |

## Best Practices

### DO

✅ Use `circuit.await()` in tests to wait for async processing
✅ Emit in batches, then wait once at the end
✅ Trust the Queue to process events in order
✅ Use async design for non-blocking producers
✅ Use Circuit.close() to gracefully shut down

### DON'T

❌ Use latches to wait for subscriber callbacks
❌ Call circuit.await() after every emit() in production
❌ Assume emissions are processed immediately
❌ Bypass the Queue with direct method calls
❌ Create multiple Circuits for same domain (defeats ordering)

## References

- **Architecture Overview**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Developer Guide**: [DEVELOPER-GUIDE.md](DEVELOPER-GUIDE.md)
- **Humainary Circuits Article**: https://humainary.io/blog/observability-x-circuits/
- **Humainary Queues Article**: https://humainary.io/blog/observability-x-queues-scripts-and-currents/

## Key Takeaway

**Substrates is async-first by design**. Every emission posts a Task to the Circuit's ingress queue and returns immediately. Subscriber callbacks execute asynchronously on the Circuit's virtual thread with depth-first execution. Use `circuit.await()` in tests to synchronize with async processing, but avoid using it after every emit() in production - leverage the async dual-queue architecture for throughput.
