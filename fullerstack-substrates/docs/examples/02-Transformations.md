# Example 2: Transformations with Sequencer

Shows how to use Sequencer to transform emissions with filter, limit, and sampling.

## Code

```java

import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;

public class TransformationsExample {
    public static void main(String[] args) throws InterruptedException {
        // Cortex accessed via static cortex() method
        Circuit circuit = cortex().circuit(cortex().name("transform-circuit"));

        // Create conduit with transformation pipeline
        Conduit<Pipe<Integer>, Integer> conduit = circuit.conduit(
            cortex().name("numbers"),
            Composer.pipe(segment -> segment
                .guard(n -> n > 0)      // Only positive numbers
                .guard(n -> n % 2 == 0) // Only even numbers
                .limit(10)              // Maximum 10 emissions
                .sample(2)              // Every 2nd emission
            )
        );

        // Subscribe to see what gets through
        conduit.subscribe(
            circuit.subscriber(
                cortex().name("consumer"),
                (subject, registrar) -> {
                    registrar.register(n -> {
                        System.out.println("Received: " + n);
                    });
                }
            )
        );

        // Emit numbers from -10 to 50
        Pipe<Integer> pipe = conduit.percept(cortex().name("counter"));
        for (int i = -10; i <= 50; i++) {
            pipe.emit(i);
        }

        // Wait for processing
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

After sample(2):
  4, 8, 12, 16, 20
```

## Transformation Types

### guard(Predicate)
Filters emissions based on condition.

```java
.guard(n -> n > 0)           // Only positive
.guard(s -> s.startsWith("A")) // Only strings starting with A
```

### limit(long)
Maximum number of emissions to pass through.

```java
.limit(100)  // At most 100 emissions
```

### sample(int)
Pass through every Nth emission.

```java
.sample(10)  // Every 10th emission (10, 20, 30, ...)
```

### reduce(initial, BinaryOperator)
Stateful aggregation - emits running total/aggregation.

```java
.reduce(0, Integer::sum)  // Running sum
```

### replace(UnaryOperator)
Transform each value.

```java
.replace(n -> n * 2)     // Double each value
.replace(String::toUpperCase)  // Convert to uppercase
```

### diff()
Only emit when value changes.

```java
.diff()  // Suppress duplicates
```

### sift(Comparator, Sequencer<Sift>)
Advanced filtering based on ordering.

```java
.sift(Integer::compareTo, sift -> sift
    .above(0)   // Greater than 0
    .max(100)   // Less than or equal to 100
)
```

## Chaining Transformations

Transformations are applied in order:

```java
segment -> segment
    .guard(n -> n > 0)      // 1. Filter
    .replace(n -> n * 2)    // 2. Transform
    .limit(100)             // 3. Limit
    .sample(10)             // 4. Sample
```

## Performance Note

Transformations execute on the **circuit thread** after dequeuing, as part of the receiver chain:
- Keep chains short for best performance
- Use `guard()` early to filter before expensive operations
- `limit()` short-circuits remaining emissions
