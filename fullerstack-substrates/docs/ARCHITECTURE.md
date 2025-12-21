# Fullerstack Substrates - Architecture & Core Concepts

**Substrates API:** 1.0.0-PREVIEW (sealed hierarchy + Cell API + Flow)
**Serventis API:** 1.0.0-PREVIEW (24+ Instrument APIs for semiotic observability)
**Java Version:** 25 (Virtual Threads + Preview Features)
**Status:** 381 TCK tests passing (100% compliance)
**Benchmarks:** 150 benchmarks across 10 groups (see [BENCHMARK-COMPARISON.md](BENCHMARK-COMPARISON.md))

---

## Table of Contents

1. [What is Substrates?](#what-is-substrates)
2. [Serventis Integration](#serventis-integration)
3. [Design Philosophy](#design-philosophy)
4. [Sealed Hierarchy](#sealed-hierarchy)
5. [Core Entities](#core-entities)
6. [Data Flow](#data-flow)
7. [Implementation Details](#implementation-details)
8. [Thread Safety](#thread-safety)
9. [Resource Lifecycle](#resource-lifecycle)

---

## What is Substrates?

**Substrates** is a framework for building event-driven observability systems based on William Louth's **semiotic observability** vision.

### The Observability Evolution

```
Metrics (traditional numbers)
    ↓
Signs (observations with meaning)
    ↓
Symptoms (patterns in signs)
    ↓
Syndromes (correlated symptoms)
    ↓
Situations (system states)
    ↓
Steering (automated responses)
```

**Substrates** provides the infrastructure layer (routing, ordering, lifecycle), **Serventis** provides the semantic instrument APIs (typed signals with meaning).

---

## Serventis Integration

Serventis extends Substrates with **typed instrument APIs** for building semiotic observability systems. Each instrument emits domain-specific signals that gain meaning through their Subject context.

### Serventis Instrument Categories (24+ Instruments)

Serventis organizes instruments into 7 categories across the OODA loop (Observe, Orient, Decide, Act):

#### Tool Category (Sensing/Measurement)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Probes** | Communication outcomes | operation, success, failure |
| **Sensors** | Environmental measurement | measure, sample, reading |
| **Counters** | Monotonic counting | increment, overflow, reset |
| **Gauges** | Bidirectional metrics | increment, decrement, set |
| **Logs** | Event logging | log, trace, debug, info, warn, error |

#### Data Category (Data Flow)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Queues** | Queue operations | enqueue, dequeue, overflow, underflow |
| **Stacks** | Stack operations | push, pop, peek |
| **Caches** | Cache operations | lookup, hit, miss, store, evict |
| **Pipelines** | Pipeline flow | input, output, stage, complete |

#### Exec Category (Execution)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Services** | Service lifecycle | call, succeeded, failed |
| **Tasks** | Task execution | start, complete, cancel, fail |
| **Processes** | Process lifecycle | spawn, run, exit, signal |
| **Transactions** | Transaction state | begin, commit, rollback, abort |

#### Flow Category (Flow Control)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Valves** | Flow control | open, close, throttle |
| **Breakers** | Circuit breaker | trip, reset, half-open |
| **Routers** | Message routing | route, deliver, drop, redirect |

#### Pool Category (Resource Management)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Resources** | Resource lifecycle | acquire, release, grant, deny |
| **Pools** | Pool management | borrow, return, create, destroy |
| **Leases** | Lease management | acquire, renew, release, expire |
| **Exchanges** | Exchange operations | offer, take, exchange |

#### Sync Category (Synchronization)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Locks** | Lock operations | acquire, release, contend |
| **Latches** | Latch operations | await, countdown, release |

#### Role Category (Behavioral)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Agents** | Promise theory | promise, kept, broken |
| **Actors** | Speech acts | request, commit, execute, confirm |

#### SDK Category (Meta)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Statuses** | Health status | stable, degraded, defective, down |
| **Situations** | Urgency assessment | normal, warning, critical |
| **Systems** | System-wide | startup, shutdown, checkpoint |
| **Cycles** | Timing cycles | tick, tock, pulse |

### Example Usage

```java
// Tool: Counter for request counting
Conduit<Counter, Counters.Sign> counters = circuit.conduit(
    cortex().name("counters"), Counters::composer);
Counter requests = counters.get(cortex().name("api.requests"));
requests.increment();

// Exec: Service for API calls
Conduit<Service, Services.Signal> services = circuit.conduit(
    cortex().name("services"), Services::composer);
Service api = services.get(cortex().name("kafka.api"));
api.call();
api.succeeded();

// Flow: Circuit breaker
Conduit<Breaker, Breakers.Signal> breakers = circuit.conduit(
    cortex().name("breakers"), Breakers::composer);
Breaker kafkaBreaker = breakers.get(cortex().name("kafka.breaker"));
kafkaBreaker.trip();  // Open circuit due to failures

// Pool: Connection pool
Conduit<Pool, Pools.Signal> pools = circuit.conduit(
    cortex().name("pools"), Pools::composer);
Pool connections = pools.get(cortex().name("db.connections"));
connections.borrow();
connections.return_();
```

### Context Creates Meaning

The **key insight**: The same signal means different things depending on its Subject (entity context).

```java
// Same OVERFLOW signal, different meanings:
Queue producerBuffer = queues.get(cortex().name("producer.buffer"));
Queue consumerLag = queues.get(cortex().name("consumer.lag"));

producerBuffer.overflow();  // → Backpressure (annoying)
consumerLag.overflow();     // → Data loss risk (critical!)

// Subscribers interpret based on Subject:
queues.subscribe(cortex().subscriber(
    cortex().name("assessor"),
    (Subject<Channel<Queues.Sign>> subject, Registrar<Queues.Sign> registrar) -> {
        Monitor monitor = monitors.get(subject.name());
        registrar.register(sign -> {
            if (sign == Queues.Sign.OVERFLOW) {
                if (subject.name().toString().contains("producer")) {
                    monitor.status(DEGRADED, HIGH);  // Backpressure
                } else if (subject.name().toString().contains("consumer")) {
                    monitor.status(DEFECTIVE, HIGH);  // Critical!
                }
            }
        });
    }
));
```

This is **semiotic observability** - meaning arises from the interplay between signal and context.

---

### Key Capabilities

- ✅ **Type-safe event routing** - From producers to consumers via Channels/Pipes
- ✅ **Transformation pipelines** - Filter, map, reduce, limit, sample emissions with JVM-style fusion
- ✅ **Dynamic subscription** - Observers subscribe/unsubscribe at runtime
- ✅ **Precise ordering** - Dual-queue pattern (Virtual CPU core) guarantees FIFO processing
- ✅ **Lazy thread initialization** - Virtual thread starts on first emit (massive await savings)
- ✅ **Event-driven synchronization** - CountDownLatch-based await() for precise synchronization
- ✅ **Pipeline optimization** - Automatic fusion of adjacent skip/limit operations
- ✅ **Hierarchical naming** - Dot-notation organization (kafka.broker.1.metrics)
- ✅ **Resource lifecycle** - Automatic cleanup with Scope
- ✅ **Immutable state** - Thread-safe state via Slot API

---

## Design Philosophy

**Core Principle:** Simplified, lean implementation focused on correctness, clarity, and design targets.

### Architecture Principles

1. **Simplified Design** - Flat package structure, single implementations, no factory abstractions
2. **PREVIEW Sealed Hierarchy** - Type-safe API contracts enforced by sealed interfaces
3. **JCTools-Based Circuit** - MpscUnboundedArrayQueue for wait-free producer path
4. **Dual-Queue Architecture** - Ingress (external) + Transit (cascading) with priority ordering
5. **Lazy Thread Initialization** - Virtual thread starts only on first emission
6. **Pipeline Fusion** - JVM-style optimization of adjacent transformations
7. **Immutable State** - Slot-based state management with value semantics
8. **Resource Lifecycle** - Explicit cleanup via `close()` on all components
9. **Thread Safety** - Concurrent collections where needed, immutability elsewhere
10. **Clear Separation** - Public API (interfaces) vs internal implementation (Fs-prefixed classes)

### What We DO Optimize

✅ **Lazy Thread Start** - Massive wins on await benchmarks (-97% to -100%)
✅ **Pipeline Fusion** - Automatic optimization of adjacent skip/limit operations
✅ **Name Interning** - FsName identity-based caching with cached path generation
✅ **JCTools MPSC** - Wait-free producer path, chunked allocation

### What We DON'T Do

❌ **No premature optimization** - Keep it simple first
❌ **No factory abstractions** - Direct component creation
❌ **No complex caching** - Simple ConcurrentHashMap patterns
❌ **No polling loops** - Spin-then-park synchronization

**Philosophy:** Build it simple, build it correct, then optimize hot paths identified by profiling or architectural insight.

---

## PREVIEW Sealed Hierarchy

### Sealed Interfaces (Java JEP 409)

PREVIEW uses sealed interfaces to restrict which classes can implement them:

```java
sealed interface Source<E> permits Context
sealed interface Context<E, S> permits Component
sealed interface Component<E, S> permits Circuit, Container
sealed interface Container<P, E, S> permits Conduit, Cell

// Non-sealed extension points (we implement these)
non-sealed interface Circuit extends Component
non-sealed interface Conduit<P, E> extends Container
non-sealed interface Cell<I, E> extends Container
non-sealed interface Channel<E>
non-sealed interface Pipe<E>
non-sealed interface Sink<E>
```

### What This Means

✅ **You CAN implement:** Circuit, Conduit, Cell, Channel, Pipe, Sink
❌ **You CANNOT implement:** Source, Context, Component, Container (sealed)

The API controls the type hierarchy to prevent incorrect compositions.

### Impact on Implementation

**Our classes extend the non-sealed extension points:**

```java
// Circuit is non-sealed, so we can implement it
public final class FsJctoolsCircuit implements FsInternalCircuit { }  // ✅

// FsInternalCircuit extends Circuit
interface FsInternalCircuit extends Circuit { }  // ✅

// Conduit and Cell are non-sealed
public class FsConduit<P extends Percept, E> implements Conduit<P, E> { }  // ✅
public class FsCell<I, E> implements Cell<I, E> { }  // ✅
```

### Everything is a Subject

**Critical Architectural Insight:** The sealed hierarchy means that **every component is a Subject**.

```
Component<E, S> extends Subject<S>
    ↓
Circuit extends Component<State, Circuit>
    → Circuit IS-A Subject<Circuit>

Conduit<P, E> extends Container<P, E, Conduit<P, E>> extends Component<E, Conduit<P, E>>
    → Conduit<P, E> IS-A Subject<Conduit<P, E>>
    → Conduit<P, E> IS-A Source<E> (can be subscribed to)
```

**What This Means:**

1. **Conduit is a subscribable Subject:**
   - `Conduit<P, E>` IS-A `Source<E>` (via sealed hierarchy)
   - You can call `conduit.subscribe(subscriber)` directly
   - Subscribers receive `Subject<Channel<E>>` (the subjects of channels created within the conduit)

2. **Subscribers see channel subjects:**
   - When subscriber is registered, it's notified when new Channels are created
   - Subscriber receives the **Channel's Subject** (not the Conduit's subject)
   - Subscriber can inspect `Subject<Channel<E>>` to determine routing logic

3. **Dynamic pipe registration:**
   - Subscriber can call `conduit.get(subject.name())` to retrieve percepts
   - Subscriber registers `Pipe<E>` instances via `Registrar<E>`
   - Registered pipes receive all future emissions from that subject

**Example:**

```java
// Create conduit (which is itself a Source<Long>)
Conduit<Pipe<Long>, Long> conduit = circuit.conduit(
    cortex().name("sensors"),
    Composer.pipe()
);

// Subscribe to the conduit (possible because Conduit IS-A Source)
conduit.subscribe(cortex().subscriber(
    cortex().name("aggregator"),
    (subject, registrar) -> {
        // subject is Subject<Channel<Long>> - the channel that was created
        // We can inspect it and decide how to route

        // Get the percept for this subject (dual-key cache prevents recursion)
        Pipe<Long> pipe = conduit.get(subject.name());

        // Register our consumer pipe
        registrar.register(value -> {
            System.out.println("Received: " + value);
        });
    }
));
```

**Two-Phase Notification:**
1. **Phase 1:** Subscriber notified when `conduit.get(name)` creates a new Channel
2. **Phase 2:** Subscriber notified (lazily) on first emission from a Subject

This design enables **dynamic, hierarchical routing** where subscribers can:
- Inspect channel subjects to determine routing strategy
- Retrieve percepts to access producer channels
- Register multiple consumer pipes per subject
- Build hierarchical aggregation pipelines

---

## Core Entities

### 1. Cortex (Entry Point)

**Purpose:** Static factory for creating Circuits and Scopes

```java
import static io.humainary.substrates.api.Substrates.*;

Circuit circuit = cortex().circuit(cortex().name("kafka"));
Name brokerName = cortex().name("kafka.broker.1");
```

**PREVIEW Change:** Cortex is now accessed statically via `Substrates.Cortex`, not instantiated.

**Implementation (FsCortex):**

```java
public class FsCortex implements Cortex {
    private final Map<Name, Circuit> circuits = new ConcurrentHashMap<>();
    private final Map<Name, Scope> scopes = new ConcurrentHashMap<>();

    @Override
    public Circuit circuit(Name name) {
        return circuits.computeIfAbsent(name, n ->
            new FsJctoolsCircuit(new FsSubject<>(n, null, Circuit.class)));
    }
}
```

---

### 2. Circuit (Event Orchestration Hub)

**Purpose:** Central processing engine with virtual CPU core pattern

**Key Features:**
- Single virtual thread processes events with depth-first execution
- **Lazy thread initialization** - thread starts only on first emission
- Contains Conduits and Cells
- JCTools MPSC queue for wait-free producer path
- Component lifecycle management

```java
Circuit circuit = cortex().circuit(cortex().name("kafka.monitoring"));

Conduit<Pipe<MonitorSignal>, MonitorSignal> monitors =
    circuit.conduit(cortex().name("monitors"), Composer.pipe());

// Or use Cells for hierarchical structure
Cell<Signal, Signal> cell = circuit.cell(
    Composer.pipe(),
    Composer.pipe(),
    outputPipe
);
```

**Virtual CPU Core Pattern (Dual-Queue Architecture):**

```
External Emissions → Ingress Queue (MPSC, wait-free) →
                                                       → Virtual Thread Processor
Cascading Emissions → Transit Deque (LIFO, priority) →   → Depth-First Execution
```

**Guarantees:**
- Events processed in deterministic order
- Transit deque has priority (cascading before external)
- True depth-first execution for nested emissions
- No race conditions (single-threaded processing)
- **Lazy start**: Thread not created until first emission

**Implementation (FsJctoolsCircuit):**

```java
public final class FsJctoolsCircuit implements FsInternalCircuit {
    // JCTools MPSC queue - wait-free producer, chunked allocation
    private final MpscUnboundedArrayQueue<Task> ingress = new MpscUnboundedArrayQueue<>(1024);

    // Transit queue for cascading (circuit thread only, LIFO for depth-first)
    private final ArrayDeque<Runnable> transit = new ArrayDeque<>();

    // Lazy thread initialization
    private volatile Thread thread;  // Started on first enqueue
    private volatile boolean started;  // Fast path check

    private void ensureStarted() {
        if (started) return;  // Fast path - no sync once started
        synchronized (startLock) {
            if (thread == null) {
                thread = Thread.ofVirtual()
                    .name("circuit-" + subject.name())
                    .start(this::loop);
                started = true;
            }
        }
    }
}
```

---

### 3. Name (Hierarchical Identity)

**Purpose:** Dot-notation hierarchical names (e.g., "kafka.broker.1")

**InternedName Implementation:**

```java
public final class InternedName implements Name {
    private final InternedName parent;       // Parent in hierarchy
    private final String segment;        // This segment
    private final String cachedPath;     // Full path cached

    public static Name of(String path) {
        // Creates hierarchy from "kafka.broker.1"
    }

    @Override
    public Name name(String segment) {
        return new InternedName(this, segment);  // Create child
    }
}
```

**Building Hierarchical Names:**

```java
// From string
Name name = cortex().name("kafka.broker.1.metrics.bytes-in");

// Hierarchically
Name kafka = cortex().name("kafka");
Name broker = kafka.name("broker").name("1");
Name metrics = broker.name("metrics");
Name bytesIn = metrics.name("bytes-in");
// Result: "kafka.broker.1.metrics.bytes-in"
```

---

### 4. Conduit (Container)

**Purpose:** Creates Channels and manages subscriber notifications

```java
Conduit<Pipe<String>, String> messages =
    circuit.conduit(cortex().name("messages"), Composer.pipe());

// Get Pipe for specific subject
Pipe<String> pipe = messages.get(cortex().name("user.login"));
pipe.emit("User logged in");

// Subscribe to all subjects (Conduit IS-A Source in PREVIEW)
messages.subscribe(
    cortex().subscriber(
        cortex().name("logger"),
        (subject, registrar) -> registrar.register(msg -> log.info(msg))
    )
);
```

**Implementation (FsConduit):**

```java
public class FsConduit<P extends Percept, E> implements Conduit<P, E> {
    private final Map<Name, FsChannel<E>> channels = new ConcurrentHashMap<>();
    private final List<Subscriber<E>> subscribers = new CopyOnWriteArrayList<>();
    private final FsInternalCircuit circuit;

    @Override
    @SuppressWarnings("unchecked")
    public P get(Name name) {
        return (P) channels.computeIfAbsent(name, n ->
            new FsChannel<>(new FsSubject<>(n, parent, Channel.class), circuit, composer, configurer)
        ).percept();
    }

    @Override
    public Subscription subscribe(Subscriber<E> subscriber) {
        subscribers.add(subscriber);
        return new FsSubscription(subject, () -> subscribers.remove(subscriber));
    }
}
```

---

### 5. Channel & Pipe (Emission)

**FsChannel:** Named emission port that creates async pipes

```java
public class FsChannel<E> implements Channel<E> {
    private final FsAsyncPipe<E> pipe;  // Cached async pipe

    @Override
    public Pipe<E> pipe() {
        return pipe;  // Returns cached FsAsyncPipe
    }
}
```

**FsAsyncPipe:** Async pipe that routes through the circuit

```java
public class FsAsyncPipe<E> implements Pipe<E> {
    private final FsInternalCircuit circuit;
    private final Consumer<Object> receptor;

    @Override
    public void emit(E value) {
        circuit.enqueue(this, value);  // ← Routes to circuit's ingress queue
    }

    void deliver(Object value) {
        receptor.accept(value);  // Called by circuit thread
    }
}
```

**Consumer Side:** Lambdas registered via `Registrar`

```java
// Registrar wraps lambda in FsConsumerPipe
conduit.subscribe((subject, registrar) -> {
    registrar.register(emission -> {
        // Lambda invoked BY Conduit when emissions arrive
        handleEmission(subject.name(), emission);
    });
});
```

**Producer-Consumer Pattern:**

The `Pipe<E>` interface serves **dual purposes** via a single `emit()` method:

| Side | Implementation | Who Calls `emit()` | What It Does |
|------|---------------|-------------------|--------------|
| **Producer** | FsAsyncPipe | Application code | Posts to circuit queue → notifies subscribers |
| **Consumer** | FsConsumerPipe | Conduit (during dispatch) | Invokes user lambda via Registrar |

```java
// PRODUCER SIDE
Pipe<Long> producer = conduit.get(cortex().name("sensor1"));
producer.emit(42L);  // ← Routes to circuit's ingress queue

// CONSUMER SIDE (registered by subscriber)
registrar.register(value -> {
    System.out.println(value);  // ← Called by circuit thread during dispatch
});
```

**With Transformations (Flow/Sift):**

```java
Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
    cortex().name("filtered-numbers"),
    Composer.pipe(flow -> flow
        .sift(n -> n > 0)     // Only positive
        .limit(100)           // Max 100 emissions
        .sample(10)           // Every 10th
    )
);
```

---

### 6. Cell (Hierarchical Transformation)

**Purpose:** Type transformation with parent-child hierarchy (I → E)

```java
// Level 1: JMX stats → Broker health
Cell<JMXStats, BrokerHealth> brokerCell = circuit.cell(
    cortex().name("broker-1"),
    stats -> assessBrokerHealth(stats)
);

// Level 2: Broker health → Cluster health
Cell<BrokerHealth, ClusterHealth> clusterCell = brokerCell.cell(
    cortex().name("cluster"),
    health -> aggregateClusterHealth(health)
);

// Subscribe to cluster health
clusterCell.subscribe(
    cortex().subscriber(
        cortex().name("alerting"),
        (subject, registrar) -> registrar.register(health -> {
            if (health.status() == ClusterStatus.CRITICAL) {
                sendAlert(health);
            }
        })
    )
);

// Input at top level
brokerCell.input(jmxClient.fetchStats());
// Transformed through hierarchy → cluster health emitted
```

**Implementation (FsCell):**

```java
public class FsCell<I, E> implements Cell<I, E> {
    private final FsInternalCircuit circuit;
    private final Composer<E, Pipe<I>> ingress;
    private final Composer<E, Pipe<E>> egress;
    private final Receptor<? super E> receptor;
    private final Map<Name, FsCell<E, ?>> children = new ConcurrentHashMap<>();

    @Override
    public <O> Cell<E, O> cell(Name name, Composer<O, Pipe<E>> ingress,
                                Composer<O, Pipe<O>> egress, Receptor<? super O> receptor) {
        return children.computeIfAbsent(name, n ->
            new FsCell<>(new FsSubject<>(n, subject, Cell.class), circuit, ingress, egress, receptor)
        );
    }
}
```

---

### 7. Scope (Resource Lifecycle)

**Purpose:** Automatic resource cleanup

```java
Scope scope = cortex().scope(cortex().name("session"));

Circuit circuit = scope.register(cortex().circuit(cortex().name("kafka")));
Conduit<Pipe<Event>, Event> events = scope.register(
    circuit.conduit(cortex().name("events"), Composer.pipe())
);

// Use resources...

scope.close();  // Closes all registered resources automatically
```

---

### 8. State & Slot (Immutable State)

**Purpose:** Thread-safe state management

```java
// Create state
State state = Cortex.state()
    .state(cortex().name("broker-id"), 1)
    .state(cortex().name("heap-used"), 850_000_000L)
    .state(cortex().name("status"), "HEALTHY");

// Access values (type-safe)
Integer brokerId = state.value(slot(cortex().name("broker-id"), 0));
Long heapUsed = state.value(slot(cortex().name("heap-used"), 0L));

// State is immutable - create new state to change
State newState = state.state(cortex().name("heap-used"), 900_000_000L);
```

**Key Features:**
- Immutable - each `state()` call returns new State
- Type-safe - matches by name AND type
- Allows duplicate names with different types

---

## Data Flow

### Producer → Consumer Path

```
1. Producer:
   conduit.get(name) → Returns FsAsyncPipe
   pipe.emit(value) → Routes to circuit

2. FsAsyncPipe:
   ensureStarted() → Lazy thread start
   ingress.offer(new Task(pipe, value)) → Wait-free enqueue

3. FsJctoolsCircuit (virtual thread):
   Drains transit stack (depth-first priority)
   Drains ingress queue (bulk drain)
   Calls FsAsyncPipe.deliver(value)

4. FsConduit.dispatch:
   Iterates subscribers (CopyOnWriteArrayList)
   Calls subscriber callbacks with value

5. Subscriber:
   Receives emission via registered callback
   Processes event
```

### Virtual CPU Core Pattern

```
FsJctoolsCircuit:
  Ingress (MpscUnboundedArrayQueue):
    [Task 1] → [Task 2] → [Task 3] → ...
        ↓
  Transit (ArrayDeque, LIFO):
    [Cascade 1] ← [Cascade 2] ← ...  (priority)
        ↓
  Single Virtual Thread (lazy-started)
        ↓
  Process in Order (depth-first)
        ↓
  Dispatch to Subscribers
```

**Critical Insight:**
- `pipe.emit(value)` returns **immediately** (async boundary)
- Thread is **lazy-started** on first emission
- Subscriber callbacks execute **asynchronously** on circuit thread
- **MUST use `circuit.await()` in tests** to wait for processing

---

## Circuit Implementation (FsJctoolsCircuit)

**Core Concept:** Each Circuit uses a JCTools MPSC queue + Transit deque + Virtual Thread that processes emissions with depth-first execution. The thread is **lazily initialized** on first emission.

### Circuit Architecture

```java
public final class FsJctoolsCircuit implements FsInternalCircuit {
    // JCTools MPSC queue - wait-free producer path
    private final MpscUnboundedArrayQueue<Task> ingress;

    // Transit queue for cascading (LIFO for true depth-first)
    private final ArrayDeque<Runnable> transit;

    // Lazy-started virtual thread
    private volatile Thread thread;
    private volatile boolean started;  // Fast path check
    private final AtomicBoolean parked = new AtomicBoolean(false);
}
```

### Architecture

```
Circuit (FsJctoolsCircuit)
  ├── Ingress (MpscUnboundedArrayQueue)  (External emissions, MPSC, wait-free)
  ├── Transit (ArrayDeque)               (Cascading emissions, LIFO, priority)
  ├── Virtual Thread                     (lazy-started, spin-then-park)
  └── AtomicBoolean parked               (fast unpark signaling)

Emission Flow:
  External Thread:
    Pipe.emit(value)
      → ensureStarted()                  // Lazy thread start
      → ingress.offer(new Task(...))     // Wait-free enqueue
      → if (parked.get() && parked.CAS)  // Fast path check
          LockSupport.unpark(thread)

  Circuit Thread (cascading):
    Subscriber callback emits
      → transit.push(task)               // LIFO for depth-first

  Processor Loop:
    → drain transit (LIFO - depth-first)
    → drain ingress (bulk drain)
        → after each: drain transit again (priority)
    → spin-then-park if no work
```

### Lazy Thread Initialization

The key optimization that enables massive benchmark wins:

```java
private void ensureStarted() {
    if (started) return;  // Fast path - volatile read only
    synchronized (startLock) {
        if (thread == null) {
            Thread t = Thread.ofVirtual()
                .name("circuit-" + subject.name())
                .start(this::loop);
            thread = t;
            threadId = t.threadId();  // Cache for fast comparison
            started = true;
        }
    }
}
```

**Benchmark Impact:**
| Benchmark | Humainary | Fullerstack | Improvement |
|-----------|-----------|-------------|-------------|
| create_await_close | 10,730ns | 303ns | **-97%** |
| hot_await_queue_drain | 5,798ns | 0.96ns | **-100%** |
| close_idempotent_await | 8,438ns | 35ns | **-100%** |

### Await Implementation

Uses CountDownLatch for precise synchronization:

```java
public void await() {
    Thread t = thread;
    if (t == null) {
        return;  // Thread never started - nothing to await
    }
    if (Thread.currentThread() == t) {
        throw new IllegalStateException("Cannot await from circuit thread");
    }
    // Submit sentinel task - when it runs, all prior work is done (FIFO)
    var latch = new CountDownLatch(1);
    submit(latch::countDown);
    latch.await();
}
```

### Circuit Guarantees

1. **Depth-First Execution** - Transit deque processed before ingress queue
2. **Deterministic Ordering** - Tasks execute in predictable order
3. **Single-Threaded** - No concurrent execution within Circuit domain
4. **Lazy Start** - Thread only created when first emission occurs
5. **Wait-Free Producers** - JCTools MPSC queue for external emissions
6. **Spin-Then-Park** - Brief spin before parking reduces wake-up latency

---

## Pipeline Fusion Optimization

**Core Concept:** Automatically combine adjacent identical transformations to reduce overhead.

### What Gets Fused?

**Skip Fusion:**
```java
flow.skip(3).skip(2).skip(1)  // 3 transformations

// Optimized to:
flow.skip(6)  // 1 transformation (sum: 3+2+1)
```

**Limit Fusion:**
```java
flow.limit(10).limit(5).limit(7)  // 3 transformations

// Optimized to:
flow.limit(5)  // 1 transformation (minimum)
```

### How It Works

```java
public Flow<E> skip(long n) {
    // Check if last transformation was also skip()
    if (!metadata.isEmpty() &&
        metadata.get(metadata.size() - 1).type == TransformType.SKIP) {

        // Fuse: remove last skip, add counts, recurse
        long existingSkip = (Long) lastMeta.metadata;
        transformations.remove(transformations.size() - 1);
        metadata.remove(metadata.size() - 1);

        return skip(existingSkip + n);  // Recursive fusion
    }

    // No fusion - add normal skip
    addTransformation(skipLogic);
    metadata.add(new TransformMetadata(SKIP, n));
    return this;
}
```

### When Does Fusion Happen?

**Fusion occurs when:**
- ✅ Multiple configuration sources add transformations
- ✅ Plugin systems independently add filters
- ✅ Inheritance hierarchies layer transformations
- ✅ Runtime conditions add dynamic limits

**Example - Config Composition:**
```java
// base-config.yaml → skip(1000)
// env-config.yaml → skip(500)
// user-prefs.json → skip(2000)

// Result: skip(1000).skip(500).skip(2000)
// Fused to: skip(3500) automatically

// Performance: 1 counter check instead of 3
```

**Example - Dynamic Limits:**
```java
// Multiple rate limiting policies
flow.limit(systemMax);      // 10,000
flow.limit(userTierLimit);  // 1,000
flow.limit(regionalLimit);  // 3,000
flow.limit(customLimit);    // 2,000

// Fused to: limit(1000) - single counter (minimum)
// Processing 1M requests: 1M checks instead of 4M
```

### Performance Impact

```
Scenario: 1M messages/sec with skip(100).skip(200).skip(300)

Without Fusion:
- 3 transformations
- 3M function calls/sec
- 3M counter increments
- 3M comparisons

With Fusion:
- 1 transformation (skip(600))
- 1M function calls/sec
- 1M counter increments
- 1M comparisons

Savings: 66% reduction in CPU cycles
```

### Current Limitations

**Implemented:**
- ✅ `skip(n1).skip(n2)` → `skip(n1+n2)`
- ✅ `limit(n1).limit(n2)` → `limit(min(n1,n2))`

**Not Yet Implemented:**
- ⏳ `replace(f1).replace(f2)` → `replace(f1.andThen(f2))`
- ⏳ `guard(p1).guard(p2)` → `guard(x -> p1.test(x) && p2.test(x))`
- ⏳ `sample(n).sample(m)` → `sample(n*m)`

**Non-Adjacent Don't Fuse:**
```java
flow.skip(100)
    .guard(x -> x.isValid())  // ← Breaks fusion chain
    .skip(200);

// Result: 2 skip transformations (correct - different semantics)
```

---

## Name vs Subject Distinction

**Key Concept:** Names are referents (identifiers), Subjects are temporal/contextual instances.

### The Distinction

```java
// NAME = Linguistic referent (like "Miles" the identifier)
Name milesName = cortex().name("Miles");

// SUBJECT = Temporal/contextual instantiation
Subject<?> milesInCircuitA = ContextualSubject.builder()
    .id(id1)                    // Unique ID
    .name(milesName)            // Same name reference
    .state(stateA)              // Different state (context A)
    .type(Person.class)
    .build();

Subject<?> milesInCircuitB = ContextualSubject.builder()
    .id(id2)                    // Different ID
    .name(milesName)            // Same name reference
    .state(stateB)              // Different state (context B)
    .type(Person.class)
    .build();

// Same Name, different temporal instances:
milesInCircuitA.id() != milesInCircuitB.id()      // Different IDs
milesInCircuitA.name() == milesInCircuitB.name()  // Same Name
milesInCircuitA.state() != milesInCircuitB.state() // Different states
```

### Why This Matters

```java
// Example: "Miles" exists in multiple Circuits simultaneously

Circuit circuitA = cortex().circuit(cortex().name("circuit-A"));
Circuit circuitB = cortex().circuit(cortex().name("circuit-B"));

// Both circuits create Channels named "Miles"
Channel<Metric> milesInA = conduitA.get(cortex().name("Miles"));
Channel<Metric> milesInB = conduitB.get(cortex().name("Miles"));

// Same Name referent, different Subject instances:
// - milesInA.subject() → Subject with unique ID in Circuit A context
// - milesInB.subject() → Subject with unique ID in Circuit B context
```

### Analogy

- **Name** = Word in dictionary ("run")
- **Subject** = Specific usage in context ("I run marathons" vs "Water runs downhill")

---

## Implementation Details

### Caching Strategy

Simple and effective - ConcurrentHashMap everywhere:

```
FsCortex
├── circuits: ConcurrentHashMap<Name, Circuit>
└── scopes: ConcurrentHashMap<Name, Scope>

FsJctoolsCircuit
└── conduits: created via FsConduit

FsConduit
└── channels: ConcurrentHashMap<Name, FsChannel>

FsCell
└── children: ConcurrentHashMap<Name, Cell>
```

**Key Points:**
- `computeIfAbsent()` for thread-safe lazy creation
- No complex optimizations - standard Java collections
- Fast enough for production (100k+ metrics @ 1Hz)

---

### Performance Characteristics

**Test Suite:**
- 381 TCK tests passing (100% compliance)
- 150 JMH benchmarks across 10 groups

**Benchmark Summary (Fullerstack vs Humainary):**
- **Fullerstack Wins:** 36 (24%)
- **Humainary Wins:** 99 (66%)
- **Ties:** 14 (9%)

**Key Fullerstack Wins:**
| Category | Benchmark | Improvement |
|----------|-----------|-------------|
| Await | create_await_close | -97% |
| Await | hot_await_queue_drain | -100% |
| Names | name_compare | -89% |
| Names | name_path_generation | -96% |
| Subscriber | close_*_await | -98% to -100% |

**Design Target:**
- 100k+ metrics @ 1Hz
- ~2% CPU usage (estimated)
- ~200-300MB memory

**Per-Operation Costs (Fullerstack):**
- Component lookup: ~5-10ns (ConcurrentHashMap)
- Pipe emission: ~17-27ns (async, with flow)
- Name comparison: ~4ns (identity-based)
- Path generation: ~1-14ns (cached)

---

## Thread Safety

### Concurrent Components

- **ConcurrentHashMap** - All component caches
- **CopyOnWriteArrayList** - Subscriber lists (read-heavy)
- **BlockingQueue** - Circuit event queue

### Immutable Components

- **InternedName** - Immutable parent-child structure
- **State/Slot** - Immutable state management
- **Signal Types** - Immutable records (Serventis)

### Synchronization Points

- **Circuit Queue** - Single thread, FIFO ordering
- **Component Creation** - `computeIfAbsent()` handles races
- **Subscriber Registration** - CopyOnWriteArrayList handles concurrent adds

---

## Resource Lifecycle

All components implement `Resource` with `close()`:

```
Scope.close()
  → FsScope.close()
    → Circuit.close() (for each registered)
      → FsJctoolsCircuit.close()
        → running = false
        → LockSupport.unpark(thread)
        → thread.join()
```

**Best Practices:**

```java
// 1. Try-with-resources
try (Circuit circuit = cortex().circuit(cortex().name("test"))) {
    // Use circuit
}

// 2. Scope for grouped cleanup
Scope scope = cortex().scope(cortex().name("session"));
Circuit circuit = scope.register(cortex().circuit(cortex().name("kafka")));
scope.close();  // Closes all registered resources

// 3. Manual cleanup
Circuit circuit = cortex().circuit(cortex().name("kafka"));
try {
    // Use circuit
} finally {
    circuit.close();
}
```

---

## Serventis Integration Example

**Example: Kafka Broker Health Monitoring (PREVIEW API)**

```java
import static io.humainary.substrates.api.Substrates.*;
import io.humainary.substrates.ext.serventis.opt.tool.*;
import io.humainary.substrates.ext.serventis.opt.exec.*;
import io.humainary.substrates.ext.serventis.opt.flow.*;
import io.humainary.substrates.ext.serventis.sdk.*;

// Create Circuit
Circuit circuit = cortex().circuit(cortex().name("kafka.monitoring"));

// Tool: Counter for request metrics
Conduit<Counter, Counters.Sign> counters = circuit.conduit(
    cortex().name("counters"), Counters::composer);
Counter requests = counters.get(cortex().name("broker-1.requests"));
requests.increment();

// Exec: Service for API health
Conduit<Service, Services.Signal> services = circuit.conduit(
    cortex().name("services"), Services::composer);
Service api = services.get(cortex().name("broker-1.api"));
api.call();
api.succeeded();

// Flow: Circuit breaker for fault tolerance
Conduit<Breaker, Breakers.Signal> breakers = circuit.conduit(
    cortex().name("breakers"), Breakers::composer);
Breaker kafkaBreaker = breakers.get(cortex().name("kafka.breaker"));
if (errorRate > 0.5) {
    kafkaBreaker.trip();  // Open circuit
}

// SDK: Status for health assessment
Conduit<Status, Statuses.Signal> statuses = circuit.conduit(
    cortex().name("statuses"), Statuses::composer);
Status health = statuses.get(cortex().name("broker-1.health"));
if (heapUsage > 85) {
    health.degraded();
}

// Subscribe to health statuses
statuses.subscribe(cortex().subscriber(
    cortex().name("health-aggregator"),
    (subject, registrar) -> registrar.register(status -> {
        if (status == Statuses.Signal.DEGRADED) {
            log.warn("Degraded: {}", subject.name());
        }
    })
));

circuit.await();  // Wait for async processing in tests
circuit.close();
```

**Key Points:**
- Use Serventis composer methods: `Counters::composer`, `Services::composer`, etc.
- 7 categories: tool, data, exec, flow, pool, sync, role (plus SDK meta)
- Call instrument methods, don't construct signals manually
- Instruments emit typed signs/signals automatically
- Subject context comes from `conduit.get(name)` - it's in the Channel

---

## Summary

**Fullerstack Substrates:**

✅ **Simple** - Flat package structure, Fs-prefixed classes, easy to understand
✅ **Correct** - 381/381 TCK tests passing (100% Humainary API compliance)
✅ **Lean** - Core implementation only, no application frameworks
✅ **Optimized** - Lazy thread start, JCTools MPSC, cached name paths
✅ **Thread-Safe** - Dual-queue pattern, proper concurrent collections
✅ **Clean** - Explicit resource lifecycle management
✅ **Benchmarked** - 150 JMH benchmarks, 24% wins vs Humainary reference

**Key Optimizations:**
- **Lazy Thread Start** - Virtual thread created only on first emission (-97% to -100% on await benchmarks)
- **JCTools MPSC Queue** - Wait-free producer path for external emissions
- **Cached Path Generation** - Name.path() returns cached string (-96% improvement)
- **Identity-Based Name Comparison** - Interned names enable fast equality checks (-89%)

**Philosophy:** Build it simple, build it correct, then optimize hot paths identified by profiling.

---

## References

- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java)
- [Observability X Blog Series](https://humainary.io/blog/category/observability-x/)
- [PREVIEW Migration Guide](../../API-ANALYSIS.md)
- [Developer Guide](DEVELOPER-GUIDE.md)
- [Async Architecture](ASYNC-ARCHITECTURE.md)
