# Example 3: Multiple Subscribers (Fan-out)

Demonstrates how one producer can have multiple independent consumers.

## Code

```java

import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;
import java.util.concurrent.atomic.AtomicInteger;

public class FanOutExample {
    public static void main(String[] args) throws InterruptedException {
        // Cortex accessed via static cortex() method
        Circuit circuit = cortex().circuit(cortex().name("fanout-circuit"));

        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("events"),
            Composer.pipe()
        );

        // Subscriber 1: Console logger
        conduit.subscribe(
            cortex().subscriber(
                cortex().name("console"),
                (subject, registrar) -> {
                    registrar.register(msg -> {
                        System.out.println("[Console] " + msg);
                    });
                }
            )
        );

        // Subscriber 2: Error counter
        AtomicInteger errorCount = new AtomicInteger(0);
        conduit.subscribe(
            cortex().subscriber(
                cortex().name("error-counter"),
                (subject, registrar) -> {
                    registrar.register(msg -> {
                        if (msg.contains("ERROR")) {
                            int count = errorCount.incrementAndGet();
                            System.out.println("[Counter] Errors: " + count);
                        }
                    });
                }
            )
        );

        // Subscriber 3: Length analyzer
        conduit.subscribe(
            cortex().subscriber(
                cortex().name("length-analyzer"),
                (subject, registrar) -> {
                    registrar.register(msg -> {
                        System.out.println("[Analyzer] Length: " + msg.length());
                    });
                }
            )
        );

        // Emit messages
        Pipe<String> pipe = conduit.get(cortex().name("producer"));
        pipe.emit("INFO: System started");
        pipe.emit("ERROR: Connection failed");
        pipe.emit("WARN: High memory usage");
        pipe.emit("ERROR: Timeout occurred");

        circuit.await();

        System.out.println("\nFinal error count: " + errorCount.get());
        circuit.close();
    }
}
```

## Expected Output

```
[Console] INFO: System started
[Analyzer] Length: 20
[Console] ERROR: Connection failed
[Counter] Errors: 1
[Analyzer] Length: 25
[Console] WARN: High memory usage
[Analyzer] Length: 23
[Console] ERROR: Timeout occurred
[Counter] Errors: 2
[Analyzer] Length: 23

Final error count: 2
```

## Pattern: Fan-out

One producer, multiple consumers:

```
        Producer
           │
           ▼
        Conduit
         / │ \
        /  │  \
       ▼   ▼   ▼
      C1  C2  C3
```

Each subscriber receives **all emissions**.

## Conditional Subscription

Subscribe only to specific subjects:

```java
conduit.subscribe(
    cortex().subscriber(
        cortex().name("filtered"),
        (subject, registrar) -> {
            // Only subscribe to "important" subjects
            if (subject.name().value().contains("important")) {
                registrar.register(msg -> process(msg));
            }
        }
    )
);
```

## Subscription Lifecycle

```java
// Subscribe
Subscription sub = conduit.subscribe(subscriber);

// Later, unsubscribe
sub.close();  // No more emissions received
```

## Use Cases

1. **Logging + Metrics** - Log to console, send to metrics system
2. **Audit Trail** - Process data + write audit log
3. **Multi-format Export** - JSON + CSV + XML outputs
4. **Monitoring + Alerting** - Monitor for patterns + alert on thresholds
