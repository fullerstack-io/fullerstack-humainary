# Substrates Examples

Practical examples demonstrating common Substrates usage patterns.

**Implementation:** Fullerstack Substrates (FsCircuit)
**API Version:** 1.0.0-PREVIEW
**Java Version:** 25 (Virtual Threads + Preview Features)

## Quick Start Examples

1. **[Hello Substrates](01-HelloSubstrates.md)** - Simplest producer-consumer example
2. **[Transformations](02-Transformations.md)** - Using Flow for filtering and sampling
3. **[Multiple Subscribers](03-MultipleSubscribers.md)** - Fan-out pattern with multiple consumers
4. **[Resource Management](04-ResourceManagement.md)** - Lifecycle management with Scope and Closure
5. **[Semiotic Observability](05-SemioticObservability.md)** - Serventis instruments and context-based meaning

## Running Examples

All examples are standalone Java programs. To run:

```bash
# Compile
javac -cp "target/classes:$HOME/.m2/repository/..." Example.java

# Run
java -cp ".:target/classes:..." Example
```

Or use your IDE to run the main methods directly.

## Example Index

### Basic Patterns

- **Producer-Consumer** - [Example 1](01-HelloSubstrates.md)
- **Fan-out (1→N)** - [Example 3](03-MultipleSubscribers.md)
- **Transformations** - [Example 2](02-Transformations.md)

### Advanced Patterns

See the [Architecture Guide](../ARCHITECTURE.md#advanced-patterns) for:
- Fan-in (N→1)
- Relay pattern
- Transform pattern
- Conditional subscription

### Resource Management

- **Manual lifecycle** - [Example 4](04-ResourceManagement.md)
- **Scope-based** - [Example 4](04-ResourceManagement.md)
- **Closure (ARM)** - [Example 4](04-ResourceManagement.md)
- **Try-with-resources** - [Example 4](04-ResourceManagement.md)

## Common Use Cases

### Logging and Monitoring

```java
Conduit<Pipe<LogEvent>, LogEvent> logs = circuit.conduit(
    cortex().name("logs"),
    Composer.pipe()
);

// Console logger
logs.subscribe(consoleSubscriber);

// File logger
logs.subscribe(fileSubscriber);

// Metrics collector
logs.subscribe(metricsSubscriber);
```

### Event Processing Pipeline

```java
Conduit<Pipe<Event>, Event> events = circuit.conduit(
    cortex().name("events"),
    Composer.pipe(segment -> segment
        .guard(e -> e.isValid())        // Filter invalid
        .replace(e -> e.normalize())     // Normalize
        .limit(10000)                    // Rate limit
    )
);
```

### Hierarchical Resource Cleanup

```java
try (Scope scope = cortex().scope(cortex().name("request"))) {
    Circuit circuit = scope.register(cortex().circuit());
    // ... use circuit
} // Auto-closes all
```

## Tips

1. **Always close** Circuit, Subscription, Conduit resources
2. **Use Scope** for managing multiple related resources
3. **Subscribe early** before emitting to avoid missed events
4. **Use circuit.await()** to wait for async processing (not Thread.sleep())
5. **Check Subject.name()** in subscribers for conditional logic
6. **Cache Pipes** for repeated emissions - don't call conduit.percept() in loops
7. **Eager thread start** - Virtual thread starts immediately on circuit construction

## Key Implementation Details

- **FsCircuit** - Custom IngressQueue for wait-free producer path
- **Eager Thread** - Virtual thread starts on circuit construction
- **Depth-First** - Transit queue (FIFO) processed before ingress queue (cascading priority)
- **VarHandle park/unpark await** - Lightweight synchronization without polling

## Next Steps

- Review the [Architecture Guide](../ARCHITECTURE.md) for design details
- Read [Async Architecture](../ASYNC-ARCHITECTURE.md) for async patterns
- Understand [Circuit Design](../CIRCUIT-DESIGN.md) for implementation details
- Check the [Substrates API](https://github.com/humainary-io/substrates-api-java)
