# Fullerstack Substrates - Architecture & Core Concepts

**Substrates API:** 1.0.0-PREVIEW (sealed hierarchy + Cell API + Flow)
**Serventis API:** 1.0.0-PREVIEW (24+ Instrument APIs for semiotic observability)
**Java Version:** 25 (Virtual Threads + Preview Features)
**Status:** 220+ tests passing (100% compliance)
**Benchmarks:** 150+ benchmarks across 10 groups (see [BENCHMARK-COMPARISON.md](BENCHMARK-COMPARISON.md))

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

#### SDK Category (Meta/Universal)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Statuses** | Health status | stable, degraded, defective, down × confidence |
| **Situations** | Urgency assessment | normal, warning, critical |
| **Systems** | Constraint state | normal, limit, alarm, fault × space/flow/link/time |
| **Cycles** | Timing patterns | repeat, return, single |
| **Operations** | Universal action bracketing | begin, end |
| **Outcomes** | Binary verdicts | success, fail |
| **Surveys** | Collective assessment | divided, majority, unanimous |
| **Trends** | Statistical patterns | stable, drift, spike, cycle, chaos |

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
| **Services** | Service lifecycle (16 signs) | start, stop, call, success, fail, retry, reject, etc. |
| **Tasks** | Task execution | start, complete, cancel, fail |
| **Processes** | Process lifecycle | spawn, run, exit, signal |
| **Transactions** | Transaction state | begin, commit, rollback, abort |
| **Timers** | Time constraint outcomes | meet, miss × deadline, threshold |

#### Flow Category (Flow Control)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Valves** | Flow control | open, close, throttle |
| **Breakers** | Circuit breaker | trip, reset, half-open |
| **Routers** | Message routing | route, deliver, drop, redirect |
| **Flows** | Data movement stages | success, fail × ingress, transit, egress |

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
| **Atomics** | CAS contention dynamics | attempt, success, fail, spin, yield, backoff, park, exhaust |

#### Role Category (Behavioral)

| Instrument | Purpose | Key Signals |
|------------|---------|-------------|
| **Agents** | Promise theory | promise, kept, broken |
| **Actors** | Speech acts | request, commit, execute, confirm |

### Example Usage

```java
// Tool: Counter for request counting
Conduit<Counter, Counters.Sign> counters = circuit.conduit(
    cortex().name("counters"), Counters::composer);
Counter requests = counters.percept(cortex().name("api.requests"));
requests.increment();

// Exec: Service for API calls
Conduit<Service, Services.Signal> services = circuit.conduit(
    cortex().name("services"), Services::composer);
Service api = services.percept(cortex().name("kafka.api"));
api.call();
api.succeeded();

// Flow: Circuit breaker
Conduit<Breaker, Breakers.Signal> breakers = circuit.conduit(
    cortex().name("breakers"), Breakers::composer);
Breaker kafkaBreaker = breakers.percept(cortex().name("kafka.breaker"));
kafkaBreaker.trip();  // Open circuit due to failures

// Pool: Connection pool
Conduit<Pool, Pools.Signal> pools = circuit.conduit(
    cortex().name("pools"), Pools::composer);
Pool connections = pools.percept(cortex().name("db.connections"));
connections.borrow();
connections.return_();
```

### Context Creates Meaning

The **key insight**: The same signal means different things depending on its Subject (entity context).

```java
// Same OVERFLOW signal, different meanings:
Queue producerBuffer = queues.percept(cortex().name("producer.buffer"));
Queue consumerLag = queues.percept(cortex().name("consumer.lag"));

producerBuffer.overflow();  // → Backpressure (annoying)
consumerLag.overflow();     // → Data loss risk (critical!)

// Subscribers interpret based on Subject:
queues.subscribe(circuit.subscriber(
    cortex().name("assessor"),
    (Subject<Channel<Queues.Sign>> subject, Registrar<Queues.Sign> registrar) -> {
        Monitor monitor = monitors.percept(subject.name());
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

- Type-safe event routing - From producers to consumers via Channels/Pipes
- Transformation pipelines - Filter, map, reduce, limit, sample emissions with JVM-style fusion
- Dynamic subscription - Observers subscribe/unsubscribe at runtime
- Precise ordering - Dual-queue pattern (Virtual CPU core) guarantees FIFO processing
- Event-driven synchronization - VarHandle park/unpark await() for lightweight synchronization
- Pipeline optimization - Automatic fusion of adjacent skip/limit operations
- Hierarchical naming - Dot-notation organization (kafka.broker.1.metrics)
- Resource lifecycle - Automatic cleanup with Scope
- Immutable state - Thread-safe state via Slot API

---

## Design Philosophy

**Core Principle:** Simplified, lean implementation focused on correctness, clarity, and design targets.

### Architecture Principles

1. **Simplified Design** - Flat package structure, single implementations, no factory abstractions
2. **PREVIEW Sealed Hierarchy** - Type-safe API contracts enforced by sealed interfaces
3. **Single Circuit Implementation** - FsCircuit with custom IngressQueue + TransitQueue
4. **Dual-Queue Architecture** - Ingress (external) + Transit (cascading) with priority ordering
5. **Eager Thread Initialization** - Virtual thread starts on circuit construction
6. **Pipeline Fusion** - JVM-style optimization of adjacent transformations
7. **Immutable State** - Slot-based state management with value semantics
8. **Resource Lifecycle** - Explicit cleanup via `close()` on all components
9. **Thread Safety** - Concurrent collections where needed, immutability elsewhere
10. **Clear Separation** - Public API (interfaces) vs internal implementation (Fs-prefixed classes)

### What We DO Optimize

- **VarHandle Opaque Access** - Cheaper than volatile for parked flag checks
- **Intrusive Transit Queue** - No separate queue allocation for cascading emissions
- **Pipeline Fusion** - Automatic optimization of adjacent skip/limit operations
- **Name Interning** - FsName identity-based caching with cached path generation
- **Custom IngressQueue** - Wait-free MPSC linked list with atomic getAndSet

### What We DON'T Do

- **No premature optimization** - Keep it simple first
- **No factory abstractions** - Direct component creation
- **No complex caching** - Simple ConcurrentHashMap patterns
- **No polling loops** - Spin-then-park synchronization

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

- **You CAN implement:** Circuit, Conduit, Cell, Channel, Pipe, Sink
- **You CANNOT implement:** Source, Context, Component, Container (sealed)

The API controls the type hierarchy to prevent incorrect compositions.

### Impact on Implementation

**Our classes extend the non-sealed extension points:**

```java
// FsCircuit implements Circuit directly
public final class FsCircuit implements Circuit { }  // ✓

// Conduit and Cell are non-sealed
public class FsConduit<P extends Percept, E> implements Conduit<P, E> { }  // ✓
public class FsCell<I, E> implements Cell<I, E> { }  // ✓
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
   - Subscriber can call `conduit.percept(subject.name())` to retrieve percepts
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
conduit.subscribe(circuit.subscriber(
    cortex().name("aggregator"),
    (subject, registrar) -> {
        // subject is Subject<Channel<Long>> - the channel that was created
        // We can inspect it and decide how to route

        // Get the percept for this subject (dual-key cache prevents recursion)
        Pipe<Long> pipe = conduit.percept(subject.name());

        // Register our consumer pipe
        registrar.register(value -> {
            System.out.println("Received: " + value);
        });
    }
));
```

**Two-Phase Notification:**
1. **Phase 1:** Subscriber notified when `conduit.percept(name)` creates a new Channel
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
            new FsCircuit(new FsSubject<>(n, null, Circuit.class)));
    }
}
```

---

### 2. Circuit (Event Orchestration Hub)

**Purpose:** Central processing engine with virtual CPU core pattern

**Key Features:**
- Single virtual thread processes events with depth-first execution
- Thread starts **eagerly** on circuit construction
- Contains Conduits and Cells
- Custom IngressQueue for wait-free producer path
- TransitQueue for cascading emissions
- VarHandle-based parked flag for efficient synchronization

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
External Emissions → Ingress Queue (IngressQueue, wait-free) →
                                                               → Virtual Thread Processor
Cascading Emissions → Transit Queue (Intrusive FIFO) →           → Depth-First Execution
```

**Guarantees:**
- Events processed in deterministic order
- Transit queue has priority (cascading before external)
- True depth-first execution for nested emissions
- No race conditions (single-threaded processing)
- Eager start: Thread created immediately on circuit construction

**Implementation (FsCircuit):**

```java
public final class FsCircuit implements Circuit {
    // Custom MPSC queue - wait-free producer path (atomic getAndSet)
    private final IngressQueue ingress = new IngressQueue();
    // Transit queue for cascade emissions (circuit thread only)
    private final TransitQueue transit = new TransitQueue();

    // VarHandle for opaque access to parked flag
    private static final VarHandle PARKED;
    @Contended
    private volatile boolean parked;

    // Eager-started virtual thread
    private final Thread worker;

    public FsCircuit(Subject<Circuit> subject) {
        this.subject = subject;
        this.worker = Thread.ofVirtual()
            .name("circuit-" + subject.name())
            .start(this::workerLoop);
    }
}
```

---

### 3. Name (Hierarchical Identity)

**Purpose:** Dot-notation hierarchical names (e.g., "kafka.broker.1")

**FsName Implementation:**

```java
public final class FsName implements Name {
    private final FsName parent;      // Parent in hierarchy
    private final String segment;     // This segment
    private final String cachedPath;  // Full path cached

    public static Name of(String path) {
        // Creates hierarchy from "kafka.broker.1"
    }

    @Override
    public Name name(String segment) {
        return new FsName(this, segment);  // Create child
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
Pipe<String> pipe = messages.percept(cortex().name("user.login"));
pipe.emit("User logged in");

// Subscribe to all subjects (Conduit IS-A Source in PREVIEW)
messages.subscribe(
    circuit.subscriber(
        cortex().name("logger"),
        (subject, registrar) -> registrar.register(msg -> log.info(msg))
    )
);
```

**Implementation (FsConduit):**

```java
public class FsConduit<P extends Percept, E> implements Conduit<P, E> {
    private volatile Map<Name, P> percepts = new IdentityHashMap<>();
    private volatile Map<Name, ChannelState<E>> channelStates;
    private final FsCircuit circuit;

    @Override
    public P percept(Name name) {
        // Fast path: same name as last lookup (identity check, ~2ns)
        Name last = lastLookupName;
        if (last != null && name == last) {
            return lastLookupPercept;
        }
        // Normal path: map lookup
        P cached = percepts.get(name);
        if (cached != null) {
            lastLookupName = name;
            lastLookupPercept = cached;
            return cached;
        }
        // Slow path: create and cache
        return createAndCachePercept(name);
    }

    @Override
    public Subscription subscribe(Subscriber<E> subscriber) {
        // Enqueue subscribe to circuit thread via ReceptorReceiver (preserves QNode monomorphism)
        circuit.submitIngress(new FsCircuit.ReceptorReceiver<Object>(_ -> addSubscriber(fs)), null);
        return new FsSubscription(...);
    }
}
```

---

### 5. Channel & Pipe (Emission)

**FsChannel:** Named emission port that creates pipes

```java
final class FsChannel<E> implements Channel<E> {
    private final Subject<Channel<E>> subject;
    private final FsCircuit circuit;
    private final Consumer<E> router;

    @Override
    public Pipe<E> pipe() {
        Subject<Pipe<E>> pipeSubject = new FsSubject<>(
            subject.name(), (FsSubject<?>) subject, Pipe.class);
        return new FsPipe<>(pipeSubject, circuit, router);
    }
}
```

**FsPipe:** Async pipe that routes through the circuit

```java
public final class FsPipe<E> implements Pipe<E> {
    private final Consumer<Object> receiver;
    private final FsCircuit        circuit;
    private final Thread           worker;

    @Override
    public void emit(E emission) {
        if (Thread.currentThread() == worker) {
            circuit.submitTransit(receiver, emission);   // Hot path: cascade
        } else {
            circuit.submitIngress(receiver, emission);   // Cold path: external
        }
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
| **Producer** | FsPipe | Application code | Posts to circuit queue → notifies subscribers |
| **Consumer** | Lambda via Registrar | Conduit (during dispatch) | Invokes user callback |

```java
// PRODUCER SIDE
Pipe<Long> producer = conduit.percept(cortex().name("sensor1"));
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
    circuit.subscriber(
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
    private final FsCircuit circuit;
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
   channel.pipe() → Returns FsPipe
   pipe.emit(value) → Routes to circuit

2. FsPipe:
   submitIngress(receiver, emission) / submitTransit(receiver, emission)

3. FsCircuit (virtual thread):
   Drains transit queue first (cascading priority)
   Drains ingress queue (batch of 64 via IngressQueue)
   Invokes node.run() → receiver.accept(value)

4. FsConduit.dispatch:
   Iterates subscriber pipes (cached array)
   Calls subscriber callbacks with value

5. Subscriber:
   Receives emission via registered callback
   Processes event
```

### Virtual CPU Core Pattern

```
FsCircuit:
  Ingress (IngressQueue):
    [QNode 1] → [QNode 2] → [QNode 3] → ...
        ↓
  Transit (Intrusive FIFO):
    TransitQueue (FIFO, priority)
        ↓
  Single Virtual Thread (eager-started)
        ↓
  Process in Order (depth-first)
        ↓
  Dispatch to Subscribers
```

**Critical Insight:**
- `pipe.emit(value)` returns **immediately** (async boundary)
- Thread is **eagerly started** on circuit construction
- Subscriber callbacks execute **asynchronously** on circuit thread
- **MUST use `circuit.await()` in tests** to wait for processing

---

## Circuit Implementation (FsCircuit)

**Core Concept:** Each Circuit uses a custom IngressQueue + TransitQueue + Virtual Thread that processes emissions with depth-first execution. The thread starts **eagerly** on circuit construction.

### Circuit Architecture

```java
public final class FsCircuit implements Circuit {
    // Custom MPSC linked list - wait-free producer path
    private final IngressQueue ingress = new IngressQueue();

    // Transit queue for cascade emissions (circuit thread only)
    private final TransitQueue transit = new TransitQueue();

    // VarHandle for opaque access to parked flag
    private static final VarHandle PARKED;
    @Contended
    private volatile boolean parked;

    // Eager-started virtual thread
    private final Thread worker;
}
```

### Architecture

```
Circuit (FsCircuit)
  ├── Ingress (IngressQueue)  (External emissions, MPSC, wait-free)
  ├── Transit (Intrusive FIFO)            (Cascading emissions, priority)
  ├── Virtual Thread                      (eager-started, spin-then-park)
  └── VarHandle PARKED                    (opaque read for fast signaling)

Emission Flow:
  External Thread:
    Pipe.emit(value)
      → FsPipe.emit() → submitIngress/submitTransit
      → ingress.add(receiver, value)      // External: wait-free enqueue
      → if (PARKED.getOpaque(this))       // Only if parked
          LockSupport.unpark(thread)

  Circuit Thread (cascading):
    Subscriber callback emits
      → transit.enqueue(receiver, value)   // Append to transit

  Processor Loop:
    → drain transit (FIFO - depth-first)
    → drain ingress (batch drain)
        → after each: drain transit again (priority)
    → spin-then-park if no work
```

### Await Implementation

Uses VarHandle AWAITER with park/unpark for lightweight synchronization:

```java
public void await() {
    if (Thread.currentThread() == worker) {
        throw new IllegalStateException("Cannot await from circuit thread");
    }
    if (closed) { worker.join(); return; }

    // VarHandle-based: CAS to register as awaiter, inject marker, park
    Thread current = Thread.currentThread();
    Thread existing = (Thread) AWAITER.compareAndExchange(this, null, current);
    if (existing != null) {
        // Piggyback on existing awaiter
        while (AWAITER.getOpaque(this) == existing) LockSupport.parkNanos(1_000_000);
        return;
    }
    submitIngress(awaitMarkerReceiver, null);
    LockSupport.unpark(worker);
    while (AWAITER.getOpaque(this) == current) LockSupport.park();
}
```

### Circuit Guarantees

1. **Depth-First Execution** - Transit queue processed before ingress queue
2. **Deterministic Ordering** - Nodes execute in predictable order
3. **Single-Threaded** - No concurrent execution within Circuit domain
4. **Eager Start** - Thread created immediately on circuit construction
5. **Wait-Free Producers** - Custom IngressQueue for external emissions
6. **Spin-Then-Park** - Brief spin before parking reduces wake-up latency
7. **VarHandle Optimization** - Opaque reads cheaper than volatile for parked check

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

### Current Limitations

**Implemented:**
- `skip(n1).skip(n2)` → `skip(n1+n2)`
- `limit(n1).limit(n2)` → `limit(min(n1,n2))`

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
Subject<?> milesInCircuitA = new FsSubject<>(milesName, circuitA, Person.class);
Subject<?> milesInCircuitB = new FsSubject<>(milesName, circuitB, Person.class);

// Same Name, different temporal instances:
milesInCircuitA.id() != milesInCircuitB.id()      // Different IDs
milesInCircuitA.name() == milesInCircuitB.name()  // Same Name
```

### Why This Matters

```java
// Example: "Miles" exists in multiple Circuits simultaneously

Circuit circuitA = cortex().circuit(cortex().name("circuit-A"));
Circuit circuitB = cortex().circuit(cortex().name("circuit-B"));

// Both circuits create Channels named "Miles"
Channel<Metric> milesInA = conduitA.percept(cortex().name("Miles"));
Channel<Metric> milesInB = conduitB.percept(cortex().name("Miles"));

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

FsCircuit
└── conduits: created via FsConduit

FsConduit
└── percepts: IdentityHashMap<Name, P> (copy-on-write)

FsCell
└── children: ConcurrentHashMap<Name, Cell>
```

**Key Points:**
- `computeIfAbsent()` for thread-safe lazy creation
- Copy-on-write for percepts (fast reads, rare writes)
- Fast enough for production (100k+ metrics @ 1Hz)

---

### Performance Characteristics

**Test Suite:**
- 220+ tests passing (100% compliance)
- 150+ JMH benchmarks across 10 groups

**Key Fullerstack Strengths:**
| Category | Benchmark | Notes |
|----------|-----------|-------|
| Subject | subject_compare | 142% faster after Long.compare fix |
| Hot Pipe | hot_pipe_async | 85% faster (4.7ns vs 8.7ns) |
| Flow | guard/limit | 36-46% faster |
| Names | name_path_generation | 3865% faster (0.84ns vs 33ns) |
| Lookups | get_by_name, get_cached | 18-36% faster |
| Serventis | *_from_conduit | 25-58% faster |

**Design Target:**
- 100k+ metrics @ 1Hz
- ~2% CPU usage (estimated)
- ~200-300MB memory

**Per-Operation Costs (Fullerstack):**
- Component lookup: ~5-10ns (ConcurrentHashMap)
- Pipe emission: ~10-18ns (async, with VarHandle optimization)
- Name comparison: ~1.5ns (Long.compare based)
- Path generation: ~0.8ns (cached)

---

## Thread Safety

### Concurrent Components

- **ConcurrentHashMap** - All component caches
- **IdentityHashMap (copy-on-write)** - Percept and channel state caches
- **Custom IngressQueue** - Circuit ingress queue (wait-free MPSC linked list)

### Immutable Components

- **FsName** - Immutable parent-child structure
- **State/Slot** - Immutable state management
- **Signal Types** - Immutable records (Serventis)

### Synchronization Points

- **Circuit Queue** - Single thread, FIFO ordering
- **Component Creation** - `computeIfAbsent()` handles races
- **Subscriber Registration** - Version-based lazy rebuild

---

## Resource Lifecycle

All components implement `Resource` with `close()`:

```
Scope.close()
  → FsScope.close()
    → Circuit.close() (for each registered)
      → FsCircuit.close()
        → running = false
        → LockSupport.unpark(thread)
        → thread drains and terminates
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

## Summary

**Fullerstack Substrates:**

- **Simple** - Flat package structure, Fs-prefixed classes, easy to understand
- **Correct** - 220+ tests passing (100% Humainary API compliance)
- **Lean** - Core implementation only, no application frameworks
- **Optimized** - VarHandle parked check, custom IngressQueue, TransitQueue
- **Thread-Safe** - Dual-queue pattern, proper concurrent collections
- **Clean** - Explicit resource lifecycle management
- **Benchmarked** - 150+ JMH benchmarks with documented performance

**Key Optimizations:**
- **VarHandle Opaque Access** - Reduced parked check overhead from ~11% to ~4%
- **Custom IngressQueue** - Wait-free MPSC linked list for external emissions
- **TransitQueue** - Separate queue class for cascading emissions
- **Cached Path Generation** - Name.path() returns cached string
- **Long.compare for Subject** - Fixed 183x regression in compareTo

**Philosophy:** Build it simple, build it correct, then optimize hot paths identified by profiling.

---

## References

- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java)
- [Observability X Blog Series](https://humainary.io/blog/category/observability-x/)
- [Developer Guide](DEVELOPER-GUIDE.md)
- [Async Architecture](ASYNC-ARCHITECTURE.md)
- [Circuit Design](CIRCUIT-DESIGN.md)
