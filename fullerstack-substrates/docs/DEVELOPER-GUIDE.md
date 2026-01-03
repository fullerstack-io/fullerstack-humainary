# Fullerstack Substrates - Developer Guide

**Best Practices, Semiotic Observability Patterns, Performance Tips, and Testing Strategies**

---

## Table of Contents

1. [Semiotic Observability Patterns](#semiotic-observability-patterns)
2. [Best Practices](#best-practices)
3. [Performance Guide](#performance-guide)
4. [Testing Strategies](#testing-strategies)
5. [Common Pitfalls](#common-pitfalls)

---

## Semiotic Observability Patterns

### The Core Principle: Context Creates Meaning

In traditional monitoring, a metric is just a number. In semiotic observability, a **signal gains meaning from its context** (the Subject that carries entity identity).

```
Traditional:  overflow_events = 1  (just a counter)
Semiotic:     producer-1.buffer → OVERFLOW  (backpressure from broker)
             consumer-1.lag → OVERFLOW     (data loss risk!)
```

### The Semiotic Ascent Hierarchy

Serventis implements a **semiotic ascent** architecture where raw domain signs translate upward through abstraction layers:

```
Raw Signs (Domain) → Systems (Constraint) → Statuses (Condition) → Situations (Urgency) → Actions
```

Each layer manages complexity appropriate to its reasoning tasks while preserving coherence through translation pathways.

### Pattern 1: OBSERVE Phase (Raw Sensing)

Use Serventis instrument APIs to emit domain-specific signs:

```java
import static io.humainary.substrates.api.Substrates.*;
import io.humainary.substrates.ext.serventis.opt.data.Queues;
import io.humainary.substrates.ext.serventis.opt.data.Queues.Queue;

// Create instrument conduit
var queues = circuit.conduit(
    cortex().name("queues"),
    Queues::composer
);

// Get instruments for specific entities (creates Channels with Subject)
Queue producerBuffer = queues.get(cortex().name("producer-1.buffer"));
Queue consumerLag = queues.get(cortex().name("consumer-1.lag"));

// Emit signs based on observations
if (bufferUtilization > 0.95) {
    producerBuffer.overflow();  // Raw sign: OVERFLOW
}
```

**Key Point:** At this layer, we're just sensing - no interpretation yet.

### Pattern 2: ORIENT Phase (Condition Assessment)

Subscribe to raw signs and assess their meaning using Statuses:

```java
import io.humainary.substrates.ext.serventis.sdk.Statuses;
import io.humainary.substrates.ext.serventis.sdk.Statuses.Status;

// Create status conduit for condition assessment
var statuses = circuit.conduit(
    cortex().name("statuses"),
    Statuses::composer
);

// Subscribe to queue signs and interpret based on Subject
queues.subscribe(cortex().subscriber(
    cortex().name("queue-health-assessor"),
    (subject, registrar) -> {
        // Get Status for this specific entity
        Status status = statuses.get(subject.name());

        registrar.register(sign -> {
            // CONTEXT-AWARE INTERPRETATION
            String entityType = extractEntityType(subject.name());

            if (sign == Queues.Sign.OVERFLOW) {
                if (entityType.equals("producer")) {
                    // Producer overflow = backpressure (annoying but recoverable)
                    status.degraded(Statuses.Dimension.MEASURED);
                    log.warn("Producer backpressure detected: {}", subject.name());

                } else if (entityType.equals("consumer")) {
                    // Consumer lag overflow = data loss risk (critical!)
                    status.defective(Statuses.Dimension.CONFIRMED);
                    log.error("Consumer lag critical: {}", subject.name());
                }
            }
        });
    }
));
```

**Key Point:** Same sign (`OVERFLOW`), different meanings based on Subject context.

### Pattern 3: DECIDE Phase (Situation Assessment)

Subscribe to status signals and determine urgency:

```java
import io.humainary.substrates.ext.serventis.sdk.Situations;
import io.humainary.substrates.ext.serventis.sdk.Situations.Situation;

// Create situation conduit for urgency assessment
var situations = circuit.conduit(
    cortex().name("situations"),
    Situations::composer
);

// Subscribe to status signals and assess situation urgency
statuses.subscribe(cortex().subscriber(
    cortex().name("situation-assessor"),
    (subject, registrar) -> {
        Situation situation = situations.get(extractClusterName(subject.name()));

        registrar.register(signal -> {
            if (signal.sign() == Statuses.Sign.DEFECTIVE) {
                // DEFECTIVE condition = critical situation
                situation.critical(Situations.Dimension.CONSTANT);

            } else if (signal.sign() == Statuses.Sign.DEGRADED) {
                situation.warning(Situations.Dimension.VARIABLE);
            }
        });
    }
));
```

**Key Point:** Conditions are aggregated and prioritized into actionable situations.

### Pattern 4: ACT Phase (Automated Response)

Subscribe to situations and execute steering decisions:

```java
// Subscribe to situation signals and take action
situations.subscribe(cortex().subscriber(
    cortex().name("auto-responder"),
    (subject, registrar) -> {
        registrar.register(signal -> {
            if (signal.sign() == Situations.Sign.CRITICAL) {
                // Automated remediation
                scaleUpCluster(subject.name().path('.'));
                alertOnCall("Critical situation in " + subject.name());

            } else if (signal.sign() == Situations.Sign.WARNING) {
                // Proactive measures
                notifyTeam("Warning condition in " + subject.name());
            }
        });
    }
));
```

**Key Point:** The system can now act intelligently based on understood situations.

### Complete Example: End-to-End Semiotic Flow

```java
import static io.humainary.substrates.api.Substrates.*;
import io.humainary.substrates.ext.serventis.opt.data.Queues;
import io.humainary.substrates.ext.serventis.sdk.Statuses;
import io.humainary.substrates.ext.serventis.sdk.Situations;

Circuit circuit = cortex().circuit(cortex().name("kafka-monitoring"));

// OBSERVE: Create instrument conduit for raw signs
var queues = circuit.conduit(
    cortex().name("queues"), Queues::composer);

// ORIENT: Create conduit for condition assessment
var statuses = circuit.conduit(
    cortex().name("statuses"), Statuses::composer);

// DECIDE: Create conduit for urgency assessment
var situations = circuit.conduit(
    cortex().name("situations"), Situations::composer);

// Wire up the cognitive loop
queues.subscribe(createQueueAssessor(statuses));
statuses.subscribe(createSituationAssessor(situations));
situations.subscribe(createAutoResponder());

// Now emit raw signs - the system interprets and acts
var consumerLag = queues.get(cortex().name("consumer-1.lag"));
consumerLag.overflow();  // → OBSERVE → ORIENT → DECIDE → ACT

circuit.await();
```

**Result:** A single `overflow()` sign triggers a cascade of interpretation, assessment, and automated response - all based on contextual understanding.

---

## Best Practices

### General Principles

#### 1. Cache Pipes for Repeated Emissions

```java
// GOOD
Pipe<MetricValue> pipe = conduit.get(cortex().name("kafka.broker.1.bytes-in"));

for (int i = 0; i < 1000000; i++) {
    pipe.emit(new MetricValue(System.currentTimeMillis(), bytesIn));
}

// BAD
for (int i = 0; i < 1000000; i++) {
    conduit.get(cortex().name("kafka.broker.1.bytes-in"))
        .emit(new MetricValue(System.currentTimeMillis(), bytesIn));
}
```

**Why:** `conduit.get()` involves a map lookup. Cache the Pipe once and reuse it.

---

#### 2. Use Hierarchical Names

```java
// GOOD
Name brokerName = cortex().name("kafka.broker.1");
Name metricsName = brokerName.name("metrics");
Name bytesInName = metricsName.name("bytes-in");
// Result: "kafka.broker.1.metrics.bytes-in"

// BAD
String name = "kafka.broker.1.metrics.bytes-in";
Pipe<MetricValue> pipe = conduit.get(cortex().name(name));
```

**Why:** Hierarchical names preserve parent-child relationships.

---

#### 3. Close Resources Explicitly

```java
// GOOD
try (Circuit circuit = cortex().circuit(cortex().name("my-circuit"))) {
    // Use circuit
}

// Or with Scope:
Scope scope = cortex().scope(cortex().name("transaction"));
Circuit circuit = scope.register(cortex().circuit(cortex().name("my-circuit")));
scope.close();  // Closes all registered resources

// BAD
Circuit circuit = cortex().circuit(cortex().name("my-circuit"));
// Never closed - resource leak!
```

---

### Naming Best Practices

#### Use Dot Notation for Hierarchy

```java
// Application domain hierarchy
Name kafkaName = cortex().name("kafka");
Name brokerName = kafkaName.name("broker").name("1");
Name metricsName = brokerName.name("metrics");
Name bytesInName = metricsName.name("bytes-in");
// Result: "kafka.broker.1.metrics.bytes-in"
```

#### Consistent Naming Conventions

```java
// GOOD - Consistent, hierarchical
cortex().name("kafka.broker.1.jvm.heap.used")
cortex().name("kafka.broker.1.jvm.heap.max")
cortex().name("kafka.broker.1.jvm.gc.count")

// BAD - Inconsistent structure
cortex().name("kafka_broker1_heap_used")
cortex().name("broker-1-gc-count")
cortex().name("JVM_MAX_HEAP_broker_1")
```

---

### Circuit Management

#### One Circuit Per Domain

```java
// GOOD - Separate circuits for different domains
Circuit kafkaCircuit = cortex().circuit(cortex().name("kafka"));
Circuit systemCircuit = cortex().circuit(cortex().name("system"));
Circuit appCircuit = cortex().circuit(cortex().name("application"));

// BAD
Circuit everythingCircuit = cortex().circuit(cortex().name("everything"));
```

**Why:** Better isolation, easier to reason about event ordering per domain.

---

### Conduit and Channel Patterns

#### Create Conduits by Signal Type

```java
// GOOD - One conduit per signal type
var counters = circuit.conduit(
    cortex().name("counters"), Counters::composer);

var statuses = circuit.conduit(
    cortex().name("statuses"), Statuses::composer);

// BAD
Conduit<Pipe<Object>, Object> everything =
    circuit.conduit(cortex().name("everything"), Composer.pipe());
```

**Why:** Type safety, clear separation of concerns.

---

### Flow and Sift Transformations

#### Configure Once at Conduit Creation

```java
Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
    cortex().name("numbers"),
    Composer.pipe(flow -> flow
        .sift(n -> n > 0)           // Filter: only positive
        .limit(1000)                // Max 1000 emissions
        .sample(10)                 // Every 10th emission
    )
);
```

#### Transformation Order Matters

```java
// GOOD - Filter first, then sample
flow.sift(n -> n > 100).sample(10)
// Out of 1000: ~500 pass filter → ~50 sampled

// DIFFERENT RESULT - Sample first, then filter
flow.sample(10).sift(n -> n > 100)
// Out of 1000: ~100 sampled → ~50 pass filter
```

---

### Subscriber Management

#### Subscribe Once, Process Many

```java
statuses.subscribe(
    cortex().subscriber(
        cortex().name("health-aggregator"),
        (subject, registrar) -> {
            registrar.register(signal -> aggregateHealth(signal));
        }
    )
);
```

#### Unsubscribe When Done

```java
Subscription sub = statuses.subscribe(subscriber);
// Later
sub.close();
```

---

## Performance Guide

### Performance Summary

**Test Suite:**
- 387 TCK tests passing (100% compliance)
- 846 JMH benchmarks across Substrates and Serventis

**Key Fullerstack Advantages:**
| Category | Benchmark | Improvement |
|----------|-----------|-------------|
| Subject | subject_compare | 142% faster |
| Hot Pipe | hot_pipe_async | 46% faster (4.7ns vs 8.7ns) |
| Names | name_path_generation | 3865% faster (0.5ns vs 33ns) |
| Lookups | get_by_name, get_cached | 18-36% faster |
| Circuit | create_await_close | 97% faster |

**Design Target:**
- 100k+ metrics @ 1Hz
- ~2% CPU usage (estimated)
- ~200-300MB memory

---

### Architecture Performance

#### FsCircuit - Virtual CPU Core Pattern

```
FsCircuit:
  Ingress (JCTools MPSC) → Virtual Thread → Process in Order
  Transit (Intrusive FIFO) ↗   (depth-first)

Benefits:
✓ Wait-free producer path (JCTools MPSC)
✓ Eager thread initialization
✓ Precise ordering (depth-first for cascading)
✓ Spin-then-park (low wake-up latency)
✓ VarHandle parked flag (opaque reads cheaper than volatile)
✓ Lightweight (virtual threads)
```

#### Component Caching

```java
// FsConduit caches channels internally
private final Map<Name, FsChannel<E>> channels = new ConcurrentHashMap<>();

public P get(Name name) {
    return channels.computeIfAbsent(name, n ->
        new FsChannel<>(new FsSubject<>(n, parent, Channel.class), circuit, composer, configurer)
    ).percept();
}
```

**Performance:**
- First access: ~100-200ns (create + cache)
- Cached access: ~5-10ns (ConcurrentHashMap lookup)

---

#### Pipe Emission

**Performance (after VarHandle optimization):**
- Hot path (cached pipe): ~10-18ns per emission
- With sift/limit/sample: +10-50ns
- Subscriber notification: +20-50ns per subscriber
- **Total:** ~50-150ns per emission with 1-3 subscribers

---

#### InternedName Performance

```java
public final class FsName implements Name {
    private final String cachedPath;  // Built once in constructor

    @Override
    public CharSequence path(char separator) {
        return cachedPath;  // O(1) lookup
    }
}
```

**Performance:**
- Creation: ~50-100ns
- Path access: ~0.5ns (cached string)

---

### Real-World Performance

**Kafka Monitoring Scenario:**

```
Setup:
- 100 brokers × 1,000 metrics each
- 100,000 total emissions/second @ 1Hz

Estimated Resource Usage:
- CPU: 100,000 × 200ns = 20ms/sec = 2% of one core
- Memory: 100,000 Pipes × 1KB ≈ 100MB
- Threads: 1 Circuit thread + 1 scheduler = 2-3 total
```

**Headroom:** 98% CPU available for metric collection and analysis.

---

### Performance Best Practices

#### 1. Cache Components

```java
// FAST - Cache once
Pipe<T> pipe = conduit.get(name);
for (T value : values) {
    pipe.emit(value);
}

// SLOW - Repeated lookups
for (T value : values) {
    conduit.get(name).emit(value);
}
```

---

#### 2. Batch Emissions

```java
// GOOD - Batch processing
List<Statuses.Signal> signals = collectSignals();
var status = statuses.get(name);
for (var signal : signals) {
    status.signal(signal.sign(), signal.dimension());
}
```

---

#### 3. Use Appropriate Transformations

```java
// GOOD - Filter early
flow.sift(expensiveCheck)    // Expensive filter first
    .sift(cheapCheck)        // Cheap filter second
    .limit(100)
```

---

#### 4. Avoid Blocking in Callbacks

**BAD:**
```java
conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) -> {
        registrar.register(event -> {
            Thread.sleep(1000);  // Blocks event processing!
        });
    })
);
```

**GOOD:**
```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) -> {
        registrar.register(event -> {
            executor.submit(() -> processSlowly(event));
        });
    })
);
```

---

### Scaling Considerations

#### Vertical Scaling (Single JVM)

**Current Architecture:**
- 100k metrics @ 1Hz: 2% CPU, 200MB RAM
- 1M metrics @ 1Hz: 20% CPU, 2GB RAM
- 10M metrics @ 1Hz: May need tuning

**Bottlenecks (if you reach them):**
1. Circuit queue depth (processing slower than emission)
2. Subscriber count (linear cost per subscriber)
3. Memory (100M Pipes × 1KB = 100GB)

---

#### Horizontal Scaling (Multiple JVMs)

**Partition by broker:**
```java
// JVM 1: Brokers 1-50
Circuit brokers1to50 = cortex().circuit(cortex().name("brokers-1-50"));

// JVM 2: Brokers 51-100
Circuit brokers51to100 = cortex().circuit(cortex().name("brokers-51-100"));
```

---

### Memory Characteristics

**Component Footprint (Approximate):**

```
FsName:        ~64 bytes
FsCircuit:     ~1KB (includes JCTools queue)
FsConduit:     ~512 bytes
FsChannel:     ~256 bytes
FsPipe:        ~128 bytes

Per Metric (Pipe):  ~1KB
```

**Scaling:**
```
1,000 metrics:     ~1MB
10,000 metrics:    ~10MB
100,000 metrics:   ~100MB
1,000,000 metrics: ~1GB
```

---

### When to Optimize

**DON'T optimize if:**
- Test suite runs in < 30 seconds
- Production CPU usage < 20%
- Production memory usage is stable
- No user-facing performance issues

**DO optimize if:**
- Circuit queue depth growing unbounded
- CPU usage > 80% sustained
- Memory usage growing (memory leak)
- Subscriber callbacks blocking event processing

**How to optimize:**
1. Profile first (JFR or async-profiler)
2. Optimize hot path only
3. Measure improvement
4. Document why

---

## Testing Strategies

### Unit Testing

```java
@Test
void testPipeEmission() {
    // Cortex is accessed statically
    Circuit circuit = cortex().circuit(cortex().name("test"));

    Conduit<Pipe<String>, String> conduit =
        circuit.conduit(cortex().name("messages"), Composer.pipe());

    List<String> received = new CopyOnWriteArrayList<>();

    conduit.subscribe(
        cortex().subscriber(
            cortex().name("collector"),
            (subject, registrar) -> registrar.register(received::add)
        )
    );

    Pipe<String> pipe = conduit.get(cortex().name("test-subject"));
    pipe.emit("Hello");
    pipe.emit("World");

    // CRITICAL: Allow async processing
    circuit.await();  // Use await() instead of Thread.sleep()

    assertThat(received).containsExactly("Hello", "World");

    circuit.close();
}
```

---

### Testing Serventis Instruments

```java
@Test
void testStatusEmission() {
    Circuit circuit = cortex().circuit(cortex().name("test"));

    var statuses = circuit.conduit(
        cortex().name("statuses"),
        Statuses::composer
    );

    List<Statuses.Signal> received = new CopyOnWriteArrayList<>();
    statuses.subscribe(
        cortex().subscriber(
            cortex().name("collector"),
            (subject, registrar) -> registrar.register(received::add)
        )
    );

    var status = statuses.get(cortex().name("service"));
    status.stable(Statuses.Dimension.CONFIRMED);
    status.degraded(Statuses.Dimension.MEASURED);

    circuit.await();

    assertThat(received).hasSize(2);
    assertThat(received.get(0).sign()).isEqualTo(Statuses.Sign.STABLE);
    assertThat(received.get(1).sign()).isEqualTo(Statuses.Sign.DEGRADED);

    circuit.close();
}
```

---

### Testing Transformations

```java
@Test
void testSiftTransformation() {
    Circuit circuit = cortex().circuit(cortex().name("test"));

    Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
        cortex().name("numbers"),
        Composer.pipe(flow -> flow.sift(n -> n > 0))
    );

    List<Integer> received = new CopyOnWriteArrayList<>();
    conduit.subscribe(
        cortex().subscriber(
            cortex().name("collector"),
            (subject, registrar) -> registrar.register(received::add)
        )
    );

    Pipe<Integer> pipe = conduit.get(cortex().name("test"));
    pipe.emit(-1);  // Filtered out
    pipe.emit(0);   // Filtered out
    pipe.emit(1);   // Passes
    pipe.emit(5);   // Passes

    circuit.await();  // Use await() for reliable testing

    assertThat(received).containsExactly(1, 5);

    circuit.close();
}
```

---

### Testing Resource Cleanup

```java
@Test
void testScopeCleanup() {
    Scope scope = cortex().scope(cortex().name("test-scope"));

    AtomicBoolean circuitClosed = new AtomicBoolean(false);
    Circuit circuit = scope.register(cortex().circuit(cortex().name("test")));

    // Override close to verify it's called
    // (in real code, you'd use a spy or mock)

    scope.close();

    // Verify circuit was closed
    // (depends on how you implement the spy)
}
```

---

## Common Pitfalls

### 1. Forgetting to Close Resources

**PROBLEM:**
```java
public void startMonitoring() {
    Circuit circuit = cortex().circuit(cortex().name("kafka"));
    // Use circuit
} // Circuit never closed!
```

**SOLUTION:**
```java
public class MonitoringService {
    private Circuit circuit;

    public void start() {
        circuit = cortex().circuit(cortex().name("kafka"));
    }

    public void stop() {
        if (circuit != null) {
            circuit.close();
        }
    }
}
```

---

### 2. Mixing Signal Types

**PROBLEM:**
```java
Conduit<Pipe<Object>, Object> mixed =
    circuit.conduit(cortex().name("mixed"), Composer.pipe());

mixed.get(name).emit(new StatusSignal(/* ... */));
mixed.get(name).emit("A string?");  // Type safety lost!
```

**SOLUTION:**
```java
var statuses = circuit.conduit(
    cortex().name("statuses"), Statuses::composer);

statuses.get(name).stable(Statuses.Dimension.CONFIRMED);
```

---

### 3. Creating Too Many Circuits

**PROBLEM:**
```java
// One circuit per metric!
for (String metric : metrics) {
    Circuit circuit = cortex().circuit(cortex().name(metric));
}
```

**SOLUTION:**
```java
// One circuit per domain
Circuit kafkaCircuit = cortex().circuit(cortex().name("kafka"));

// Many conduits in one circuit
var counters = kafkaCircuit.conduit(
    cortex().name("counters"), Counters::composer);

for (String metric : metrics) {
    counters.get(cortex().name(metric)).increment();
}
```

---

### 4. Not Handling Async Nature

**PROBLEM:**
```java
List<String> received = new ArrayList<>();

conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) ->
        registrar.register(received::add)
    )
);

pipe.emit("Hello");
assertEquals(1, received.size());  // FAILS! Async processing not complete
```

**SOLUTION:**
```java
List<String> received = new CopyOnWriteArrayList<>();

conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) ->
        registrar.register(received::add)
    )
);

pipe.emit("Hello");
circuit.await();  // Wait for async processing (preferred)
// Or: Thread.sleep(100);
assertEquals(1, received.size());  // Now passes
```

---

### 5. Blocking in Subscriber Callbacks

**PROBLEM:**
```java
conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) -> {
        registrar.register(event -> {
            expensiveBlockingOperation();  // BLOCKS CIRCUIT!
        });
    })
);
```

**SOLUTION:**
```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

conduit.subscribe(
    cortex().subscriber(name, (subject, registrar) -> {
        registrar.register(event -> {
            executor.submit(() -> expensiveBlockingOperation());
        });
    })
);
```

---

## Summary

### Key Takeaways

1. **Cache pipes** - Reuse for repeated emissions
2. **Use hierarchical names** - Build from parent to child
3. **Close resources** - Always clean up
4. **One circuit per domain** - Not per metric
5. **Type-safe conduits** - Use Serventis composers
6. **Configure transformations once** - At conduit creation
7. **Handle async** - Use `circuit.await()` in tests
8. **Avoid blocking** - In subscriber callbacks
9. **Profile before optimizing** - Measure actual bottlenecks
10. **Test thoroughly** - Unit, integration, resource cleanup

### Philosophy

> "Premature optimization is the root of all evil." - Donald Knuth

**Build it simple, build it correct, optimize when profiling shows actual bottlenecks.**

---

## References

- [Architecture & Concepts](ARCHITECTURE.md)
- [Async Architecture](ASYNC-ARCHITECTURE.md)
- [Circuit Design](CIRCUIT-DESIGN.md)
- [Serventis Integration](SERVENTIS.md)
- [Benchmark Comparison](BENCHMARK-COMPARISON.md)
- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java)
