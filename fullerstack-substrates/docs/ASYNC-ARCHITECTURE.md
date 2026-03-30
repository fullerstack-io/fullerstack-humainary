# Async-First Design & Testing Patterns

Substrates is async-first — `pipe.emit(value)` enqueues and returns immediately. Processing happens later on the circuit's virtual thread. This is the opposite of RxJava (synchronous by default).

This document covers the practical consequences: how to test, how to synchronize, and what not to do.

For why Substrates chose determinism over throughput, read the [Design Rationale](https://github.com/humainary-io/substrates-api-spec/blob/main/RATIONALE.md). For how the queues work internally, see [Circuit Design](CIRCUIT-DESIGN.md).

---

## RxJava vs Substrates

### RxJava (synchronous by default)

```java
BehaviorSubject<String> subject = BehaviorSubject.create();
AtomicReference<String> received = new AtomicReference<>();
subject.subscribe(value -> received.set(value));

subject.onNext("hello");  // BLOCKS until callback completes
assertEquals("hello", received.get());  // Works immediately
```

### Substrates (asynchronous by default)

```java
var circuit = cortex().circuit(cortex().name("test"));
var conduit = circuit.conduit(cortex().name("test"), Composer.pipe());

AtomicReference<String> received = new AtomicReference<>();
conduit.subscribe(circuit.subscriber(
    cortex().name("sub"),
    (subject, registrar) -> registrar.register(received::set)
));

conduit.percept(cortex().name("ch")).emit("hello");

assertNull(received.get());   // Still null — async hasn't run
circuit.await();               // Block until queue drained
assertEquals("hello", received.get());  // Now available
```

**The difference**: `emit()` posts to the ingress queue and returns in ~13ns. The callback runs later on the circuit's virtual thread. If you assert before `await()`, you get null.

---

## Testing with circuit.await()

This is the most important pattern in Substrates testing.

### Correct pattern

```java
@Test
void testEmission () {
    var circuit = cortex().circuit(cortex().name("test"));
    var conduit = circuit.conduit(cortex().name("c"), Composer.pipe());

    AtomicReference<String> received = new AtomicReference<>();
    conduit.subscribe(circuit.subscriber(
        cortex().name("sub"),
        (subject, registrar) -> registrar.register(received::set)
    ));

    conduit.percept(cortex().name("ch")).emit("hello");

    circuit.await();  // Wait for all pending emissions to process
    assertEquals("hello", received.get());

    circuit.close();
}
```

### Wrong pattern: latches

```java
// WRONG — race condition between emit() and latch.await()
CountDownLatch latch = new CountDownLatch(1);
registrar.register(value -> {
    received.set(value);
    latch.countDown();
});
pipe.emit("hello");
assertTrue(latch.await(2, TimeUnit.SECONDS));  // May timeout
```

Latches work for thread coordination (starting N threads at once). They don't work for async queue synchronization. Use `circuit.await()`.

### When latches ARE appropriate

```java
// Correct — latch coordinates threads, await() drains the queue
int threads = 10;
CountDownLatch startLatch = new CountDownLatch(1);
CountDownLatch doneLatch = new CountDownLatch(threads);

for (int i = 0; i < threads; i++) {
    Thread.startVirtualThread(() -> {
        try {
            startLatch.await();
            pipe.emit("value");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneLatch.countDown();
        }
    });
}

startLatch.countDown();
doneLatch.await(5, TimeUnit.SECONDS);
circuit.await();  // THEN drain the queue
```

---

## Anti-patterns

### Don't await() after every emit

```java
// WRONG — defeats async design
for (int i = 0; i < 1000; i++) {
    pipe.emit(i);
    circuit.await();  // Serializes everything, 1000x overhead
}

// CORRECT — batch emit, await once
for (int i = 0; i < 1000; i++) {
    pipe.emit(i);
}
circuit.await();
```

### Don't assert before await

```java
// WRONG
pipe.emit("hello");
assertEquals("hello", received.get());  // NULL

// CORRECT
pipe.emit("hello");
circuit.await();
assertEquals("hello", received.get());
```

---

## Cross-circuit synchronization

When signals cross circuit boundaries (e.g., a tap emitting from one circuit into another), you need to await both circuits:

```java
var circuit1 = cortex().circuit(cortex().name("source"));
var circuit2 = cortex().circuit(cortex().name("target"));

// ... wire tap from circuit1 to circuit2 ...

pipe.emit("value");
circuit1.await();  // Drain source circuit (tap fires)
circuit2.await();  // Drain target circuit (tap emission processed)
```

For deep chains (3+ circuits), multiple rounds may be needed:

```java
for (int round = 0; round < 3; round++) {
    circuit1.await();
    circuit2.await();
    circuit3.await();
}
```

---

## How await() works

`await()` injects a marker node into the ingress queue and parks the calling thread. When the circuit thread processes the marker (after all preceding nodes), it unparks the caller. This guarantees all emissions submitted before `await()` have been fully processed, including any cascading transit emissions they triggered.

Cannot be called from the circuit thread (would deadlock).

---

## Summary

| | RxJava | Substrates |
|---|---|---|
| `emit()` | Blocks until callback completes | Returns immediately (~13ns) |
| Callbacks | Execute on calling thread | Execute on circuit virtual thread |
| Testing | Assert directly after emit | Must `circuit.await()` first |
| Ordering | Depends on scheduler | Deterministic FIFO + depth-first |
| Concurrency | Locks needed | Lock-free (single-threaded) |
