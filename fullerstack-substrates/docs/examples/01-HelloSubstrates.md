# Example 1: Hello Substrates

The simplest possible Substrates example - one producer, one consumer.

## Code

```java
import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;

public class HelloSubstrates {
    public static void main(String[] args) throws InterruptedException {
        // 1. Create Circuit (central processing engine)
        Circuit circuit = cortex().circuit(cortex().name("hello-circuit"));

        // 2. Create Conduit with Pipe composer
        Conduit<Pipe<String>, String> conduit = circuit.conduit(
            cortex().name("messages"),
            Composer.pipe()
        );

        // 3. CONSUMER: Subscribe to observe emissions
        conduit.subscribe(
            circuit.subscriber(
                cortex().name("console-logger"),
                (subject, registrar) -> {
                    // Register a consumer Pipe
                    registrar.register(message -> {
                        System.out.println("Received: " + message);
                    });
                }
            )
        );

        // 4. PRODUCER: Get a pipe and emit
        Pipe<String> pipe = conduit.percept(cortex().name("producer1"));
        pipe.emit("Hello, Substrates!");
        pipe.emit("This is message 2");
        pipe.emit("And message 3");

        // 5. Wait for async processing (test pattern)
        circuit.await();

        // 6. Clean up
        circuit.close();

        System.out.println("Done!");
    }
}
```

## Expected Output

```
Received: Hello, Substrates!
Received: This is message 2
Received: And message 3
Done!
```

## What's Happening

1. **Circuit** - Central processing engine created via `cortex().circuit()`
2. **Conduit** - Routes messages from producers (Channels) to consumers (Subscribers)
3. **Subscriber** - Registers a consumer Pipe to receive messages
4. **Producer** - Gets a Pipe from conduit and emits messages
5. **Async Processing** - Circuit processes emissions on virtual thread (eager-started)
6. **circuit.await()** - Test pattern to wait for async processing
7. **Cleanup** - Circuit closes all resources

## Data Flow

```
pipe.emit("Hello")
  ↓
circuit.submitIngress(receiver, "Hello")
  ↓
Worker thread drains ingress queue
  ↓
node.run() → receiver.accept("Hello")
  ↓
Conduit dispatches to subscriber pipes
  ↓
"Received: Hello"
```

## Key Points

- **Asynchronous** - Emissions are queued and processed on background thread
- **Thread-safe** - Multiple producers can emit concurrently
- **Observable** - Multiple subscribers can observe same emissions
- **Clean separation** - Producer doesn't know about consumers
