# Developer Guide

Practical patterns, best practices, and common pitfalls for building with Fullerstack Substrates.

For Serventis semiotic observability patterns, read the [official Serventis documentation](https://github.com/humainary-io/serventis-api-java). For testing async emissions, see [Async Architecture](ASYNC-ARCHITECTURE.md).

---

## Best Practices

### Cache pipes for repeated emissions

```java
// GOOD — lookup once, emit many
var pipe = conduit.get(cortex().name("kafka.broker.1.bytes-in"));
for (int i = 0; i < 1_000_000; i++) {
    pipe.emit(new MetricValue(bytesIn));
}

// BAD — map lookup on every emit
for (int i = 0; i < 1_000_000; i++) {
    conduit.get(cortex().name("kafka.broker.1.bytes-in"))
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
var counters = circuit.conduit(cortex().name("counters"), Long.class);
var statuses = circuit.conduit(cortex().name("statuses"), Statuses.Signal.class);

// BAD — untyped grab bag
var everything = circuit.conduit(cortex().name("all"), Object.class);
```

### Apply per-emission operators with a Fiber

In Substrates 2.3 per-emission operators (`guard`, `diff`, `limit`, `peek`, `replace`, `every`, `hysteresis`, ...) live on `Fiber<E>`, not on `Flow`. Build a fiber once, then attach it where the data enters the system:

```java
// Fiber is reusable and immutable — operators return new fibers
var sampler = cortex().fiber(MetricValue.class)
    .guard(v -> v.value() > 0)   // filter non-positive
    .every(10)                   // every 10th
    .diff();                     // suppress unchanged

// Option A: derive a pool whose pipes pre-process emissions
Pool<Pipe<MetricValue>> filteredPool = conduit.pool(sampler);
var pipe = filteredPool.get(cortex().name("kafka.broker.1.bytes-in"));
pipe.emit(metric);   // runs through guard → every → diff before reaching the channel

// Option B: wrap an existing pipe with the fiber
var basePipe     = conduit.get(cortex().name("kafka.broker.1.bytes-in"));
var filteredPipe = sampler.pipe(basePipe);
```

**Order matters:**
```java
fiber.guard(v -> v > 100).every(10)  // 1000 → ~500 pass → ~50 sampled
fiber.every(10).guard(v -> v > 100)  // 1000 → ~100 sampled → ~50 pass
```

For type changes (e.g. extracting a field), reach for `Flow<I,O>`:

```java
Flow<MetricValue, Long> flow = cortex().flow(MetricValue.class)
    .map(MetricValue::value)
    .fiber(cortex().fiber(Long.class).diff());
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

// GOOD — one circuit per domain, many conduits/pipes
var circuit  = cortex().circuit(cortex().name("kafka"));
var counters = circuit.conduit(cortex().name("counters"), Long.class);
for (var metric : metrics) {
    counters.get(cortex().name(metric)).emit(1L);
}
```

### 3. Mixing signal types in one conduit

```java
// BAD — type safety lost
Conduit<Object> mixed = circuit.conduit(cortex().name("mixed"), Object.class);
mixed.get(name).emit("string");
mixed.get(name).emit(42);  // What type is this?

// GOOD — typed Serventis signal
var statuses = circuit.conduit(cortex().name("health"), Statuses.Signal.class);
statuses.get(name).emit(Statuses.signal(Statuses.Sign.STABLE, Statuses.Dimension.CONFIRMED));
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
