# Example 2: Per-Emission Transformations with Fiber

Shows how to use `Fiber<E>` to filter, sample, and transform emissions in Substrates 2.3. In 2.3, all per-emission operators (`guard`, `every`, `limit`, `diff`, `replace`, `reduce`, ...) live on `Fiber<E>`. `Flow<I,O>` is reserved for type transformation (`map`, `flow`, `fiber`, `pipe`).

## Code

```java
import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;

public class TransformationsExample {
    public static void main(String[] args) throws InterruptedException {
        var cortex  = cortex();
        var circuit = cortex.circuit(cortex.name("transform-circuit"));

        // Typed conduit
        Conduit<Integer> conduit = circuit.conduit(cortex.name("numbers"), Integer.class);

        // Build a reusable fiber that defines the transformation chain
        Fiber<Integer> fiber = cortex.fiber(Integer.class)
            .guard(n -> n > 0)        // Only positive numbers
            .guard(n -> n % 2 == 0)   // Only even numbers
            .limit(10)                // Maximum 10 emissions
            .every(2);                // Every 2nd emission

        // Subscribe to see what gets through
        conduit.subscribe(
            circuit.subscriber(
                cortex.name("consumer"),
                (subject, registrar) -> registrar.register(
                    n -> System.out.println("Received: " + n)
                )
            )
        );

        // Wrap the conduit's source pipe with the fiber
        Pipe<Integer> base   = conduit.get(cortex.name("counter"));
        Pipe<Integer> source = fiber.pipe(base);

        // Emit numbers from -10 to 50
        for (int i = -10; i <= 50; i++) {
            source.emit(i);
        }

        circuit.await();
        circuit.close();
    }
}
```

## Expected Output

```
Received: 4
Received: 8
Received: 12
Received: 16
Received: 20
```

## Transformation Pipeline

```
Input: -10, -9, ..., 0, 1, 2, 3, 4, 5, ..., 50

After guard(n > 0):
  1, 2, 3, 4, 5, 6, 7, 8, ..., 50

After guard(n % 2 == 0):
  2, 4, 6, 8, 10, 12, ..., 50

After limit(10):
  2, 4, 6, 8, 10, 12, 14, 16, 18, 20

After every(2):
  4, 8, 12, 16, 20
```

## Common Fiber Operators

### guard(Predicate)
Filters emissions based on a condition.

```java
.guard(n -> n > 0)              // Only positive
.guard(s -> s.startsWith("A"))  // Only strings starting with A
```

### limit(long) / skip(long)
Window the emission stream.

```java
.limit(100)  // At most 100 emissions
.skip(10)    // Drop the first 10
```

### every(int)
Pass every Nth emission.

```java
.every(10)  // Every 10th emission (10, 20, 30, ...)
```

### reduce(initial, BinaryOperator)
Stateful aggregation - emits a running total.

```java
.reduce(0, Integer::sum)  // Running sum
```

### replace(UnaryOperator)
Same-type value transformation.

```java
.replace(n -> n * 2)            // Double each value
.replace(String::toUpperCase)   // Convert to uppercase
```

### diff()
Only emit when the value changes.

```java
.diff()  // Suppress duplicates
```

### Comparator-based sift

```java
.above(Integer::compareTo, 0)        // Strictly greater than 0
.below(Integer::compareTo, 100)      // Strictly less than 100
.clamp(Integer::compareTo, 2, 8)     // Constrain to [2, 8]
.high(Integer::compareTo)            // Running maximum
.low (Integer::compareTo)            // Running minimum
```

### 2.3 temporal/numeric operators

`Fiber` adds: `chance, change, deadband, delay, edge, every, hysteresis, inhibit, pulse, rolling, steady, tumble`. Reach for these for sampling, debouncing, and edge detection on signal streams.

## Type Transformations with Flow

When you need to change the value's *type* (e.g. extract a field), use `Flow`:

```java
Flow<Order, Long> orderToTotal = cortex.flow(Order.class)
    .map(Order::totalCents)                    // Order  -> Long
    .fiber(cortex.fiber(Long.class).diff());    // dedupe successive equal totals

Pipe<Order> ordersIn = orderToTotal.pipe(totalsPipe);
```

## Chaining

Operators apply in order. Reusing a fiber across multiple pipes produces independent state per materialisation:

```java
Fiber<Integer> sampler = cortex.fiber(Integer.class)
    .guard(n -> n > 0)      // 1. Filter
    .replace(n -> n * 2)    // 2. Transform
    .limit(100)             // 3. Limit
    .every(10);             // 4. Sample

// Same fiber, two independent state instances
Pipe<Integer> a = sampler.pipe(conduit.get(cortex.name("a")));
Pipe<Integer> b = sampler.pipe(conduit.get(cortex.name("b")));
```

## Performance Note

Operators execute on the **circuit thread** after dequeuing, as part of the receiver chain:
- Keep chains short for best performance
- Use `guard()` early to filter before expensive operations
- `limit()` short-circuits remaining emissions on a per-materialisation basis
