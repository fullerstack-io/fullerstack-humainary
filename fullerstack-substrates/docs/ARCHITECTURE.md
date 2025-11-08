# Fullerstack Substrates - Architecture & Core Concepts

**Substrates API:** 1.0.0-PREVIEW (sealed hierarchy + Cell API + Flow)
**Serventis API:** 1.0.0-PREVIEW (12 Instrument APIs for semiotic observability)
**Java Version:** 25 (Virtual Threads)
**Status:** 381 TCK tests passing (100% compliance)

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

### The Twelve Instrument APIs (PREVIEW)

#### OBSERVE Phase (Sensing)

**1. Probes** - Communication outcomes
```java
Conduit<Probe, Probes.Signal> probes = circuit.conduit(name, Probes::composer);
Probe probe = probes.get(cortex().name("kafka.broker-1.network"));
probe.operation(Operation.CONNECT, Origin.CLIENT, Outcome.SUCCESS);
```

**2. Services** - Interaction lifecycle
```java
Conduit<Service, Services.Signal> services = circuit.conduit(name, Services::composer);
Service service = services.get(cortex().name("kafka.broker-1.api"));
service.call();
service.succeeded();  // or service.failed()
```

**3. Queues** - Flow control events
```java
Conduit<Queue, Queues.Sign> queues = circuit.conduit(name, Queues::composer);
Queue queue = queues.get(cortex().name("producer-1.buffer"));
queue.enqueue();    // Item added to queue
queue.dequeue();    // Item removed from queue
queue.overflow();   // Buffer capacity exceeded
queue.underflow();  // Take from empty queue
```

**4. Gauges** - Bidirectional metrics 
```java
Conduit<Gauge, Gauges.Sign> gauges = circuit.conduit(name, Gauges::composer);
Gauge gauge = gauges.get(cortex().name("broker-1.connections"));
gauge.increment();  // Connections increased
gauge.decrement();  // Connections decreased
gauge.overflow();   // Max capacity exceeded
gauge.underflow();  // Min threshold breached
gauge.reset();      // Reset to baseline
```

**5. Counters** - Monotonic metrics 
```java
Conduit<Counter, Counters.Sign> counters = circuit.conduit(name, Counters::composer);
Counter counter = counters.get(cortex().name("broker-1.requests"));
counter.increment();  // Request count increased
counter.overflow();   // Counter wrapped around
counter.underflow();  // Invalid decrement attempted
counter.reset();      // Explicitly zeroed
```

**6. Caches** - Hit/miss tracking 
```java
Conduit<Cache, Caches.Sign> caches = circuit.conduit(name, Caches::composer);
Cache cache = caches.get(cortex().name("metadata-cache"));
cache.lookup();  // Attempt to retrieve entry
cache.hit();     // Lookup succeeded
cache.miss();    // Lookup failed
cache.store();   // Entry added/updated
cache.evict();   // Automatic removal due to capacity
cache.expire();  // Removal due to TTL
cache.remove();  // Explicit invalidation
```

#### ORIENT Phase (Understanding)

**7. Monitors** - Condition assessment
```java
Conduit<Monitor, Monitors.Status> monitors = circuit.conduit(name, Monitors::composer);
Monitor monitor = monitors.get(cortex().name("broker-1.health"));
monitor.degraded(Monitors.Confidence.HIGH);  // Convenience method
// Or: monitor.stable(), .converging(), .diverging(), .erratic(), .defective(), .down()
// Or generic: monitor.status(Monitors.Condition.DEGRADED, Monitors.Confidence.HIGH)
```

**8. Resources** - Capacity tracking
```java
Conduit<Resource, Resources.Sign> resources = circuit.conduit(name, Resources::composer);
Resource resource = resources.get(cortex().name("thread-pool"));
resource.attempt();  // Non-blocking request
resource.acquire();  // Blocking/wait-based request
resource.grant();    // Successful allocation
resource.deny();     // Insufficient capacity
resource.timeout();  // Wait period exceeded
resource.release();  // Return granted units
```

#### DECIDE Phase (Urgency)

**9. Reporters** - Situation assessment
```java
Conduit<Reporter, Reporters.Situation> reporters = circuit.conduit(name, Reporters::composer);
Reporter reporter = reporters.get(cortex().name("cluster-health"));
reporter.critical();  // Serious situation demanding intervention
// Or: reporter.normal(), reporter.warning()
```

#### ACT Phase (Promise Theory & Speech Act Theory)

**10. Agents** - Promise-based autonomy (Promise Theory)
```java
Conduit<Agent, Agents.Promise> agents = circuit.conduit(name, Agents::composer);
Agent agent = agents.get(cortex().name("auto-scaler"));
agent.promise();   // Agent commits to maintaining a promise
agent.kept();      // Promise successfully maintained
agent.broken();    // Promise violated, action needed
```

**11. Actors** - Message-based interaction (Speech Act Theory)
```java
Conduit<Actor, Actors.Act> actors = circuit.conduit(name, Actors::composer);
Actor actor = actors.get(cortex().name("remediation-service"));
actor.request();   // Initiate action request
actor.commit();    // Commit to performing action
actor.execute();   // Perform the action
actor.confirm();   // Acknowledge completion
actor.cancel();    // Cancel pending action
```

**12. Routers** - Message routing decisions
```java
Conduit<Router, Routers.Route> routers = circuit.conduit(name, Routers::composer);
Router router = routers.get(cortex().name("message-router"));
router.route();    // Route message to destination
router.deliver();  // Successful delivery
router.drop();     // Message dropped
router.redirect(); // Reroute to alternate path
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
- ✅ **Precise ordering** - Valve pattern (Virtual CPU core) guarantees FIFO processing
- ✅ **Event-driven synchronization** - Zero-latency await() with wait/notify (no polling)
- ✅ **Pipeline optimization** - Automatic fusion of adjacent skip/limit operations
- ✅ **Hierarchical naming** - Dot-notation organization (kafka.broker.1.metrics)
- ✅ **Resource lifecycle** - Automatic cleanup with Scope
- ✅ **Immutable state** - Thread-safe state via Slot API

---

## Design Philosophy

**Core Principle:** Simplified, lean implementation focused on correctness, clarity, and design targets.

### Architecture Principles

1. **Simplified Design** - Single implementations, no factory abstractions
2. **PREVIEW Sealed Hierarchy** - Type-safe API contracts enforced by sealed interfaces
3. **Valve Pattern** (William's architecture) - Dual-queue (Ingress + Transit) + Virtual Thread per Circuit
4. **Event-Driven Synchronization** - Zero-latency await() using wait/notify (no polling)
5. **Pipeline Fusion** - JVM-style optimization of adjacent transformations
6. **Immutable State** - Slot-based state management with value semantics
7. **Resource Lifecycle** - Explicit cleanup via `close()` on all components
8. **Thread Safety** - Concurrent collections where needed, immutability elsewhere
9. **Clear Separation** - Public API (interfaces) vs internal implementation (concrete classes)

### What We DO Optimize

✅ **Pipeline Fusion** - Automatic optimization of adjacent skip/limit operations
✅ **Event-Driven Await** - Zero-latency synchronization (no polling)
✅ **Name Interning** - InternedName identity-based caching

### What We DON'T Do

❌ **No premature optimization** - Keep it simple first
❌ **No factory abstractions** - Direct component creation
❌ **No complex caching** - Simple ConcurrentHashMap patterns
❌ **No polling loops** - Event-driven synchronization instead

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

**internal subscriber management doesn't implement Source:**

```java
// Source is sealed, so this won't compile:
public class internal subscriber management<E> implements Source<E> { }  // ❌

// Instead, internal subscriber management is an internal utility:
public class internal subscriber management<E> {
    public Subscription subscribe(Subscriber<E> subscriber) { }  // ✅
}
```

**Circuit/Conduit/Cell extend sealed types:**

```java
// These extend Context (which extends Source), so they inherit subscribe()
public class SequentialCircuit implements Circuit { }  // ✅
public class RoutingConduit<P, E> implements Conduit<P, E> { }  // ✅
public class CellNode<I, E> implements Cell<I, E> { }  // ✅
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

**Implementation:**

```java
public class CortexRuntime implements Cortex {
    private final Map<Name, Circuit> circuits = new ConcurrentHashMap<>();
    private final Map<Name, Scope> scopes = new ConcurrentHashMap<>();

    @Override
    public Circuit circuit(Name name) {
        return circuits.computeIfAbsent(name, SequentialCircuit::new);
    }
}
```

---

### 2. Circuit (Event Orchestration Hub)

**Purpose:** Central processing engine with virtual CPU core pattern

**Key Features:**
- Single virtual thread processes events with depth-first execution
- Contains Conduits and Cells
- Dual-queue Valve for deterministic ordering
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
External Emissions → Ingress Queue (FIFO) →
                                            → Valve Processor (Virtual Thread)
Recursive Emissions → Transit Deque (FIFO) →   → Depth-First Execution
                     (Priority)
```

**Guarantees:**
- Events processed in deterministic order
- Transit deque has priority (recursive before external)
- Depth-first execution for nested emissions
- No race conditions

**Implementation:**

```java
public class SequentialCircuit implements Circuit {
    private final Valve valve;  // Dual-queue + Virtual Thread
    private final Map<Name, Conduit<?, ?>> conduits = new ConcurrentHashMap<>();

    public SequentialCircuit(Name name) {
        this.valve = new Valve("circuit-" + name.part());
    }

    public void schedule(Runnable task) {
        valve.submit(task);  // Routes to Ingress or Transit based on thread
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

**Implementation:**

```java
public class RoutingConduit<P, E> implements Conduit<P, E> {
    private final internal subscriber management<E> source;  // Internal subscriber management
    private final Map<Name, EmissionChannel<E>> channels = new ConcurrentHashMap<>();
    private final Consumer<Flow<E>> flowConfigurer;  // Transformations

    @Override
    public P get(Name subject) {
        EmissionChannel<E> channel = channels.computeIfAbsent(
            subject,
            s -> new EmissionChannel<>(s, circuit.scheduler(), source, flowConfigurer)
        );
        return (P) channel.pipe();
    }

    @Override
    public Subscription subscribe(Subscriber<E> subscriber) {
        return source.subscribe(subscriber);  // Delegate to internal subscriber management
    }
}
```

---

### 5. Channel & Pipe (Emission)

**Channel:** Named emission port

```java
public class EmissionChannel<E> implements Channel<E> {
    private final ProducerPipe<E> cachedPipe;

    @Override
    public Pipe<E> pipe() {
        return cachedPipe;  // Returns cached ProducerPipe
    }
}
```

**ProducerPipe:** Producer-side pipe that emits INTO the conduit system

```java
public class ProducerPipe<E> implements Pipe<E> {
    private final Subject<Channel<E>> channelSubject;
    private final Consumer<Capture<E, Channel<E>>> subscriberNotifier;
    private final TransformationPipeline<E> flow;  // Optional transformations

    @Override
    public void emit(E value) {
        // Apply transformations (if configured)
        E transformed = flow != null ? flow.apply(value) : value;
        if (transformed == null) return;  // Filtered out

        // Post to Circuit queue → notifies subscribers
        scheduler.schedule(() -> {
            Capture<E, Channel<E>> capture = new SubjectCapture<>(channelSubject, transformed);
            subscriberNotifier.accept(capture);
        });
    }
}
```

**ConsumerPipe:** Consumer-side pipe that receives FROM the conduit system

```java
public class ConsumerPipe<E> implements Pipe<E> {
    private final Consumer<E> consumer;

    // Called BY Conduit when routing emissions to subscribers
    @Override
    public void emit(E emission) {
        consumer.accept(emission);  // Invoke consumer lambda
    }

    // Factory methods
    public static <E> ConsumerPipe<E> of(Consumer<E> consumer) { ... }
    public static <E> ConsumerPipe<E> of(Name name, Consumer<E> consumer) { ... }
}
```

**Producer-Consumer Pattern:**

The `Pipe<E>` interface serves **dual purposes** via a single `emit()` method:

| Pipe Type | Role | Who Calls `emit()` | What It Does |
|-----------|------|-------------------|--------------|
| **ProducerPipe** | Producer | Application code | Posts to circuit queue → notifies subscribers |
| **ConsumerPipe** | Consumer | Conduit (during dispatch) | Invokes consumer lambda |

```java
// PRODUCER SIDE
ProducerPipe<Long> producer = conduit.get("sensor1");
producer.emit(42L);  // ← Application calls emit() to produce INTO system

// CONSUMER SIDE (registered by subscriber)
registrar.register(ConsumerPipe.of(value -> {
    System.out.println(value);  // ← Conduit calls emit() to deliver FROM system
}));
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

**Implementation:**

```java
public class CellNode<I, E> implements Cell<I, E> {
    private final Function<I, E> transformer;  // I → E transformation
    private final internal subscriber management<E> source;
    private final Map<Name, CellNode<E, ?>> children = new ConcurrentHashMap<>();

    @Override
    public <O> Cell<E, O> cell(Name name, Function<E, O> transformer) {
        return children.computeIfAbsent(name, n ->
            new CellNode<>(n, this, transformer, circuit)
        );
    }

    public void input(I value) {
        E transformed = transformer.apply(value);
        source.emit(transformed);  // Emit to subscribers
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

### 9. internal subscriber management (Internal Subscriber Management)

**Purpose:** Internal utility for managing subscribers (does NOT implement Source)

```java
public class internal subscriber management<E> {
    private final List<Subscriber<E>> subscribers = new CopyOnWriteArrayList<>();

    public Subscription subscribe(Subscriber<E> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public void emit(E event) {
        for (Subscriber<E> subscriber : subscribers) {
            // Notify all subscribers
        }
    }
}
```

**Why CopyOnWriteArrayList?**
- Read-heavy workload (many emits, few subscribes)
- Emissions happen millions/second
- Subscriptions happen rarely (at startup)
- No lock contention during hot path

---

### 10. State & Slot (Immutable State)

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
   conduit.get(name) → Returns Pipe
   pipe.emit(value) → Applies transformations

2. Pipe:
   Transformations applied (sift, limit, sample)
   Transformed value → internal subscriber management.emit()

3. internal subscriber management:
   Iterates subscribers (CopyOnWriteArrayList)
   Calls subscriber callbacks

4. Subscriber:
   Receives emission via registered Pipe
   Processes event
```

### Virtual CPU Core Pattern

```
Circuit Queue (FIFO):
  [Event 1] → [Event 2] → [Event 3] → ...
      ↓
  Single Virtual Thread (daemon)
      ↓
  Process in Order (no race conditions)
      ↓
  Emit to Subscribers
```

**Critical Insight:**
- `pipe.emit(value)` returns **immediately** (async boundary)
- Subscriber callbacks execute **asynchronously** on Queue thread
- **MUST use `circuit.await()` in tests** to wait for processing

---

## Valve Pattern (Virtual CPU Core)

**Core Concept:** Each Circuit contains a Valve - a dual-queue architecture (Ingress + Transit) + Virtual Thread that processes emissions with depth-first execution.

### What is a Valve?

```java
public class Valve implements AutoCloseable {
    private final BlockingQueue<Runnable> ingressQueue;  // External emissions (FIFO)
    private final BlockingDeque<Runnable> transitDeque;  // Recursive emissions (priority, FIFO)
    private final Thread processor;                       // Virtual thread
    private final Object idleLock;                        // Event-driven synchronization

    // Emissions → Tasks (routed based on calling thread)
    public boolean submit(Runnable task);

    // Event-driven await (zero-latency, no polling)
    public void await(String contextName);
}
```

### Architecture

```
Circuit
  └── Valve ("circuit-name")
        ├── Ingress Queue<Runnable>     (External emissions, FIFO)
        ├── Transit Deque<Runnable>     (Recursive emissions, FIFO, priority)
        ├── Virtual Thread              (parks when both empty, unparks on task)
        └── Object idleLock             (wait/notify synchronization)

Emission Flow:
  External Thread:
    Pipe.emit(value)
      → Valve.submit(task)              // Route to Ingress
        → ingressQueue.offer(task)      // FIFO enqueue

  Circuit Thread (recursive):
    Pipe.emit(value)
      → Valve.submit(task)              // Route to Transit
        → transitDeque.offerLast(task)  // Append to Transit (FIFO within batch)

  Processor:
    → transitDeque.pollFirst()          // Check Transit first (priority)
    → ingressQueue.take()               // If Transit empty, take from Ingress
      → task.run()                      // Execute
        → notifyAll()                   // Wake awaiting threads
```

### Event-Driven Synchronization

**Before (Polling):**
```java
// Old approach - polling with Thread.sleep(10)
while (running && (executing || !queue.isEmpty())) {
    Thread.sleep(10);  // ❌ 0-10ms latency, CPU waste
}
```

**After (Event-Driven):**
```java
// New approach - wait/notify
synchronized (idleLock) {
    // Check BOTH queues
    while (running && (executing || !ingressQueue.isEmpty() || !transitDeque.isEmpty())) {
        idleLock.wait();  // ✅ <1ms latency, zero CPU
    }
}

// Processor notifies when idle:
executing = false;
if (ingressQueue.isEmpty() && transitDeque.isEmpty()) {
    synchronized (idleLock) {
        idleLock.notifyAll();  // Wake all waiters
    }
}
```

**Benefits:**
- ✅ **Zero latency** - Threads wake immediately when valve is idle
- ✅ **Zero CPU waste** - No polling loops consuming cycles
- ✅ **Scalable** - 1000 circuits don't create 1000 polling threads
- ✅ **Precise** - Deterministic notification vs random 0-10ms delay

### Virtual CPU Core Guarantees

1. **Depth-First Execution** - Transit deque has priority over Ingress queue
2. **Deterministic Ordering** - Tasks execute in predictable order (Transit FIFO, then Ingress FIFO)
3. **Single-Threaded** - No concurrent execution within Circuit domain
4. **Thread Isolation** - Each Circuit has independent Valve
5. **Lock-Free** - Dual-queue handles concurrency, no locks in Circuit code
6. **Event-Driven** - Parking/unparking via BlockingQueue.take() and wait/notify

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
CortexRuntime
├── circuits: ConcurrentHashMap<Name, Circuit>
└── scopes: ConcurrentHashMap<Name, Scope>

SequentialCircuit
└── conduits: ConcurrentHashMap<Name, Conduit>

RoutingConduit
└── channels: ConcurrentHashMap<Name, Channel>

CellNode
└── children: ConcurrentHashMap<Name, Cell>
```

**Key Points:**
- `computeIfAbsent()` for thread-safe lazy creation
- No complex optimizations - standard Java collections
- Fast enough for production (100k+ metrics @ 1Hz)

---

### Performance Characteristics

**Test Suite:**
- 497 tests in ~12 seconds
- 0 failures, 0 errors
- Includes 308 tests from official Substrates testkit

**Design Target:**
- 100k+ metrics @ 1Hz
- ~2% CPU usage (estimated)
- ~200-300MB memory

**Per-Operation Costs:**
- Component lookup: ~5-10ns (ConcurrentHashMap)
- Pipe emission: ~100-300ns (with transformations)
- Subscriber notification: ~20-50ns per subscriber

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
  → Circuit.close()
    → Conduit.close()
      → Channel.close()
    → Valve.close()
      → Shutdown virtual thread processor
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
import io.humainary.substrates.ext.serventis.ext.Monitors;

// Create Circuit
Circuit circuit = cortex().circuit(cortex().name("kafka.monitoring"));

// Create Conduit using Serventis Monitors composer
Conduit<Monitor, Monitors.Status> monitors = circuit.conduit(
    cortex().name("monitors"),
    Monitors::composer  // Uses Serventis composer
);

// Get Monitor instrument for JVM heap
Monitor heapMonitor = monitors.get(cortex().name("broker-1.jvm.heap"));

// Emit status using Monitor methods (no manual signal construction!)
if (heapUsagePercent > 85) {
    heapMonitor.degraded(Monitors.Confidence.HIGH);  // Convenience method
    // Or: heapMonitor.status(Monitors.Condition.DEGRADED, Monitors.Confidence.HIGH);
}

// Subscribe to all monitor statuses
monitors.subscribe(
    cortex().subscriber(
        cortex().name("health-aggregator"),
        (Subject<Channel<Monitors.Status>> subject, Registrar<Monitors.Status> registrar) -> {
            registrar.register(status -> {
                // status is Monitors.Status record with condition and confidence
                if (status.condition() == Monitors.Condition.DEGRADED) {
                    log.warn("Degraded: {} (confidence: {})",
                        subject.name(), status.confidence());
                }
            });
        }
    )
);

circuit.await();  // Wait for async processing in tests
circuit.close();
```

**Key Points:**
- Use Serventis composer methods: `Monitors::composer`, `Queues::composer`, etc.
- Call instrument methods, don't construct signals manually
- Instruments emit signs/statuses automatically
- Subject context comes from `monitors.get(name)` - it's in the Channel
- Subscribers receive typed signs/statuses, not generic signals

---

## Summary

**Fullerstack Substrates:**

✅ **Simple** - No complex optimizations, easy to understand
✅ **Correct** - 381/381 TCK tests passing (100% Humainary API compliance)
✅ **Lean** - Core implementation only, no application frameworks
✅ **Thread-Safe** - Dual-queue Valve pattern, proper concurrent collections
✅ **Clean** - Explicit resource lifecycle management
✅ **Maintainable** - Clear architecture, good documentation

**Philosophy:** Build it simple, build it correct, optimize if needed.

---

## References

- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java)
- [Observability X Blog Series](https://humainary.io/blog/category/observability-x/)
- [PREVIEW Migration Guide](../../API-ANALYSIS.md)
- [Developer Guide](DEVELOPER-GUIDE.md)
- [Async Architecture](ASYNC-ARCHITECTURE.md)
