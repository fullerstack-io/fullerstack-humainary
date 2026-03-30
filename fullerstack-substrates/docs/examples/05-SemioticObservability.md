# Example 05: Semiotic Observability with Serventis

**Demonstrates:** How context creates meaning through progressive signal interpretation

---

## Concept

The same signal means different things in different contexts. An `OVERFLOW` on a producer buffer means backpressure (annoying). An `OVERFLOW` on a consumer lag queue means data loss (critical). The Subject provides the context that turns a raw sign into meaningful information.

This is the core of Serventis: **signals carry meaning through their Subject context, not just their type.**

## Complete Example

```java
import static io.humainary.substrates.api.Substrates.*;
import io.humainary.serventis.opt.data.Queues;
import io.humainary.serventis.sdk.Statuses;
import io.humainary.serventis.sdk.Situations;

public class SemioticObservabilityExample {

    public static void main(String[] args) {
        var cortex = cortex();

        // === OBSERVE: Raw domain signals ===

        var circuit = cortex.circuit(cortex.name("kafka-monitoring"));

        // Queue instruments for flow control signals
        var queues = circuit.conduit(
            cortex.name("queues"),
            Queues::composer
        );

        // Same instrument type, different Subjects = different meaning
        var producerBuffer = queues.percept(cortex.name("producer-1.buffer"));
        var consumerLag    = queues.percept(cortex.name("consumer-1.lag"));

        // === ORIENT: Condition assessment ===

        // Status conduit with diff() — only emit when condition CHANGES
        var statuses = circuit.conduit(
            cortex.name("health"),
            Statuses::composer,
            flow -> flow.diff()
        );

        // Subscribe to queue signals and translate to health status
        queues.subscribe(circuit.subscriber(
            cortex.name("health-assessor"),
            (subject, registrar) -> {
                String name = subject.name().toString();

                registrar.register(signal -> {
                    // Same signal, different meaning based on context
                    if (name.contains("consumer") && signal.sign() == Queues.Sign.FULL) {
                        // Consumer lag overflow = CRITICAL (data loss risk)
                        statuses.percept(subject.name())
                            .signal(Statuses.Sign.DEFECTIVE, Statuses.Dimension.CONFIRMED);
                    } else if (name.contains("producer") && signal.sign() == Queues.Sign.FULL) {
                        // Producer buffer overflow = WARNING (backpressure)
                        statuses.percept(subject.name())
                            .signal(Statuses.Sign.DEGRADED, Statuses.Dimension.MEASURED);
                    }
                });
            }
        ));

        // === DECIDE: Situation assessment ===

        var situations = circuit.conduit(
            cortex.name("alerts"),
            Situations::composer,
            flow -> flow.diff()
        );

        // Emit raw signals
        producerBuffer.signal(Queues.Sign.ENQUEUE, Queues.Dimension.INGRESS);
        producerBuffer.signal(Queues.Sign.FULL, Queues.Dimension.INGRESS);
        consumerLag.signal(Queues.Sign.FULL, Queues.Dimension.INGRESS);

        circuit.await();
        circuit.close();
    }
}
```

## Key Points

1. **Context creates meaning**: `Queues.Sign.FULL` on `producer-1.buffer` triggers DEGRADED status. The same `Queues.Sign.FULL` on `consumer-1.lag` triggers DEFECTIVE status.

2. **Semiotic ascent**: Raw queue signs → health status assessment → situation judgment. Each level compresses and interprets.

3. **diff() = silence equals health**: The status conduit uses `flow.diff()` so unchanged health assessments are suppressed. Only *changes* propagate.

4. **Same API, different Subjects**: Both producer and consumer use `Queues::composer`. The Subject name is what determines interpretation.

## Further Reading

- [Serventis API Documentation](https://github.com/humainary-io/serventis-api-java) — all 33 instrument types
- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec) — formal contracts
