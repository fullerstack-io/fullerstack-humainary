# Developer Guide

Practical patterns, best practices, and common pitfalls for building with Fullerstack Substrates.

For Serventis semiotic observability patterns, read the [official Serventis documentation](https://github.com/humainary-io/serventis-api-java). For testing async emissions, see [Async Architecture](ASYNC-ARCHITECTURE.md).

---

## Best Practices

### Cache pipes for repeated emissions

```java
// GOOD — lookup once, emit many
var pipe = conduit.percept(cortex().name("kafka.broker.1.bytes-in"));
for (int i = 0; i < 1_000_000; i++) {
    pipe.emit(new MetricValue(bytesIn));
}

// BAD — map lookup on every emit
for (int i = 0; i < 1_000_000; i++) {
    conduit.percept(cortex().name("kafka.broker.1.bytes-in"))
        .emit(new MetricValue(bytesIn));
}
```

### Use hierarchical names

```java
// GOOD — parent-child relationships preserved
var broker = cortex().name("kafka").name("broker").name("1");
var bytesIn = broker.name("metrics").name("bytes-in");

// BAD — flat string, no hierarchy
cortex().name("kafka_broker1_bytesIn");
```

Names are interned — `cortex().name("kafka.broker.1")` returns the same instance on every call. Use this for O(1) identity comparison.

### One circuit per domain

```java
// GOOD — isolation per concern
var kafkaCircuit  = cortex().circuit(cortex().name("kafka"));
var systemCircuit = cortex().circuit(cortex().name("system"));

// BAD — everything on one circuit
var everything = cortex().circuit(cortex().name("all"));
```

Each circuit has one virtual thread. Separate circuits give independent ordering domains and prevent one slow subscriber from blocking another domain.

### One conduit per signal type

```java
// GOOD — type-safe, clear separation
var counters = circuit.conduit(cortex().name("counters"), Counters::composer);
var statuses = circuit.conduit(cortex().name("statuses"), Statuses::composer);

// BAD — untyped grab bag
var everything = circuit.conduit(cortex().name("all"), Composer.pipe());
```

### Configure flows at conduit creation

```java
var conduit = circuit.conduit(
    cortex().name("metrics"),
    Composer.pipe(),
    flow -> flow
        .guard(v -> v > 0)     // filter negatives
        .sample(10)            // every 10th
        .diff()                // suppress unchanged
);
```

**Order matters:**
```java
flow.guard(v -> v > 100).sample(10)  // 1000 → ~500 pass → ~50 sampled
flow.sample(10).guard(v -> v > 100)  // 1000 → ~100 sampled → ~50 pass
```

### Close resources

```java
// GOOD — try-with-resources
try (var circuit = cortex().circuit(cortex().name("example"))) {
    // use circuit
}

// GOOD — scope for multiple resources
var scope = cortex().scope(cortex().name("transaction"));
var circuit = scope.register(cortex().circuit(cortex().name("c")));
// ... use ...
scope.close();  // closes all registered resources in reverse order
```

### Don't block in subscriber callbacks

```java
// BAD — blocks the circuit thread, stops all processing
registrar.register(event -> {
    database.save(event);  // blocking I/O!
});

// GOOD — offload blocking work
var executor = Executors.newVirtualThreadPerTaskExecutor();
registrar.register(event -> {
    executor.submit(() -> database.save(event));
});
```

The circuit thread is the bottleneck. Keep callbacks fast. Offload I/O, network calls, and heavy computation to other threads.

---

## Common Pitfalls

### 1. Asserting before await

```java
pipe.emit("hello");
assertEquals("hello", received.get());  // FAILS — null, async hasn't run

// Fix:
pipe.emit("hello");
circuit.await();
assertEquals("hello", received.get());  // Works
```

See [Async Architecture](ASYNC-ARCHITECTURE.md) for the full explanation.

### 2. Creating too many circuits

```java
// BAD — one circuit per metric (thousands of virtual threads)
for (var metric : metrics) {
    cortex().circuit(cortex().name(metric));
}

// GOOD — one circuit per domain, many conduits/percepts
var circuit = cortex().circuit(cortex().name("kafka"));
var counters = circuit.conduit(cortex().name("counters"), Counters::composer);
for (var metric : metrics) {
    counters.percept(cortex().name(metric)).increment();
}
```

### 3. Mixing signal types in one conduit

```java
// BAD — type safety lost
Conduit<Pipe<Object>, Object> mixed = circuit.conduit(
    cortex().name("mixed"), Composer.pipe());
mixed.percept(name).emit("string");
mixed.percept(name).emit(42);  // What type is this?

// GOOD — Serventis composer enforces types
var statuses = circuit.conduit(cortex().name("health"), Statuses::composer);
statuses.percept(name).stable(Statuses.Dimension.CONFIRMED);
```

### 4. Forgetting that subscriber discovery is lazy

Subscribers are not notified of existing channels when they subscribe. They only see channels that emit *after* the subscription is registered:

```java
pipe.emit("first");               // No subscribers yet
conduit.subscribe(subscriber);     // Subscribes
pipe.emit("second");              // Subscriber sees this
circuit.await();
// Subscriber received "second" but NOT "first"
```

This is the spec's lazy rebuild model — see [Architecture](ARCHITECTURE.md#lazy-rebuild).

### 5. Calling await() from the circuit thread

```java
// DEADLOCK — circuit thread waiting for itself to drain
registrar.register(event -> {
    circuit.await();  // IllegalStateException (or deadlock)
});
```

`await()` can only be called from external threads, never from within a subscriber callback.

---

## References

- [Architecture](ARCHITECTURE.md) — implementation decisions and class map
- [Async Architecture](ASYNC-ARCHITECTURE.md) — testing patterns and await() usage
- [Circuit Design](CIRCUIT-DESIGN.md) — queue internals and performance
- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec) — formal contracts
- [Serventis API](https://github.com/humainary-io/serventis-api-java) — semiotic observability instruments
