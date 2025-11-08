# Example 4: Resource Management with Scope

Shows proper resource lifecycle management using Scope and Closure.

## Code

```java

import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;

public class ResourceManagementExample {
    public static void main(String[] args) {
        // Cortex accessed via static cortex() method

        // Example 1: Manual lifecycle
        manualLifecycle(cortex);

        // Example 2: Scope-based lifecycle
        scopeBasedLifecycle(cortex);

        // Example 3: Closure pattern (ARM)
        closurePattern(cortex);

        // Example 4: Try-with-resources
        tryWithResources(cortex);
    }

    static void manualLifecycle(Cortex cortex) {
        System.out.println("=== Manual Lifecycle ===");

        Circuit circuit = cortex().circuit(cortex().name("manual"));
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        // Use resources...
        System.out.println("Using resources...");
        Pipe<String> pipe = conduit.get(cortex().name("producer"));
        pipe.emit("test");

        // Manual cleanup
        circuit.close();

        System.out.println("Manually closed\n");
    }

    static void scopeBasedLifecycle(Cortex cortex) {
        System.out.println("=== Scope-based Lifecycle ===");

        Scope scope = cortex().scope(cortex().name("transaction"));

        // Register resources with scope
        Circuit circuit = scope.register(
            cortex().circuit(cortex().name("scoped"))
        );

        Conduit<Pipe<String>, String> conduit = scope.register(
            circuit.conduit(cortex().name("messages"), Composer.pipe())
        );

        // Use resources...
        System.out.println("Using scoped resources...");
        Pipe<String> pipe = conduit.get(cortex().name("producer"));
        pipe.emit("scoped message");

        // Close scope → closes all registered resources
        scope.close();

        System.out.println("Scope closed (auto-closed circuit and conduit)\n");
    }

    static void closurePattern(Cortex cortex) {
        System.out.println("=== Closure Pattern (ARM) ===");

        Scope scope = cortex().scope(cortex().name("closure-scope"));
        Circuit circuit = cortex().circuit(cortex().name("arm"));

        // Use closure for automatic cleanup
        scope.closure(circuit).consume(c -> {
            System.out.println("Using circuit in closure...");

            Conduit<Pipe<String>, String> conduit = c.conduit(
                cortex().name("temp"),
                Composer.pipe()
            );

            Pipe<String> pipe = conduit.get(cortex().name("p"));
            pipe.emit("Temporary message");

            // Circuit automatically closed when this block exits
        });

        System.out.println("Closure exited (circuit auto-closed)\n");
        scope.close();
    }

    static void tryWithResources(Cortex cortex) {
        System.out.println("=== Try-with-Resources ===");

        try (Scope scope = cortex().scope(cortex().name("try-scope"))) {
            Circuit circuit = scope.register(
                cortex().circuit(cortex().name("try"))
            );

            System.out.println("Using resources in try block...");

            Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
                cortex().name("numbers"),
                Composer.pipe()
            );

            Pipe<Integer> pipe = conduit.get(cortex().name("counter"));
            pipe.emit(42);

        } // Scope auto-closes, cleaning up circuit

        System.out.println("Try block exited (scope auto-closed)\n");
    }
}
```

## Expected Output

```
=== Manual Lifecycle ===
Using resources...
Manually closed

=== Scope-based Lifecycle ===
Using scoped resources...
Scope closed (auto-closed circuit and conduit)

=== Closure Pattern (ARM) ===
Using circuit in closure...
Closure exited (circuit auto-closed)

=== Try-with-Resources ===
Using resources in try block...
Try block exited (scope auto-closed)
```

## Resource Lifecycle Patterns

### 1. Manual Cleanup

```java
Circuit circuit = cortex().circuit();
try {
    // Use circuit
} finally {
    circuit.close();
}
```

**Pros:** Simple, explicit
**Cons:** Easy to forget, verbose with multiple resources

### 2. Scope Registration

```java
Scope scope = cortex().scope();
Circuit circuit = scope.register(cortex().circuit());
Conduit<Pipe<String>, String> conduit = scope.register(
    circuit.conduit(cortex().name("messages"), Composer.pipe())
);
// ...
scope.close();  // Closes all
```

**Pros:** Centralized cleanup, hierarchical
**Cons:** Need to remember to close scope

### 3. Closure (ARM Pattern)

```java
scope.closure(resource).consume(r -> {
    // Use resource
});  // Auto-closes
```

**Pros:** Automatic, safe
**Cons:** Limited to single resource per closure

### 4. Try-with-Resources

```java
try (Scope scope = cortex().scope()) {
    // Register and use resources
}  // Auto-closes
```

**Pros:** Idiomatic Java, automatic
**Cons:** Requires Scope to be AutoCloseable

## Hierarchical Scopes

```java
Scope parent = cortex().scope(cortex().name("parent"));
Scope child1 = parent.scope(cortex().name("child1"));
Scope child2 = parent.scope(cortex().name("child2"));

Circuit c1 = child1.register(cortex().circuit());
Circuit c2 = child2.register(cortex().circuit());

parent.close();
// → Closes child1 and child2
// → Which closes c1 and c2
```

## Scope Navigation (Extent Interface)

```java
Scope parent = cortex().scope(cortex().name("parent"));
Scope child = parent.scope(cortex().name("child"));

// Navigate up
child.enclosure().ifPresent(p -> {
    System.out.println("Parent: " + p.subject().name());
});

// Navigate to root
Scope root = child.extremity();

// Check hierarchy
boolean isChild = child.within(parent);  // true
```

## Best Practices

1. **Prefer try-with-resources** for automatic cleanup
2. **Use Scope** when managing multiple related resources
3. **Use Closure** for temporary resource usage
4. **Always close** Circuit, Conduit, Subscription, Sink
5. **Create hierarchies** with child scopes for complex lifecycles
