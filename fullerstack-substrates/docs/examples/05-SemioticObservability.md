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
import io.humainary.serventis.sdk.Statuses.Dimension;

public class SemioticObservabilityExample {

    public static void main(String[] args) throws InterruptedException {
        var cortex  = cortex();
        var circuit = cortex.circuit(cortex.name("kafka-monitoring"));

        // === OBSERVE: Raw domain signs ===

        // Queues emits Queues.Sign (ENQUEUE / DEQUEUE / OVERFLOW / UNDERFLOW)
        var queues    = circuit.conduit(cortex.name("queues"), Queues.Sign.class);
        var queuePool = Queues.pool(queues);

        // Same instrument type, different Subjects = different meaning
        var producerBuffer = queuePool.get(cortex.name("producer-1.buffer"));
        var consumerLag    = queuePool.get(cortex.name("consumer-1.lag"));

        // === ORIENT: Condition assessment ===

        // Statuses emits Statuses.Signal — pre-processed via a fiber that suppresses
        // unchanged assessments (silence equals health).
        var statuses    = circuit.conduit(cortex.name("health"), Statuses.Signal.class);
        var statusFiber = cortex.fiber(Statuses.Signal.class).diff();

        // The derived pool wraps each pipe with the diff fiber
        var statusPool  = Statuses.pool(statuses).pool(p -> statusFiber.pipe(p));

        // Subscribe to queue signs and translate to health status
        queues.subscribe(circuit.subscriber(
            cortex.name("health-assessor"),
            (subject, registrar) -> {
                String name = subject.name().toString();
                registrar.register(sign -> {
                    if (sign != Queues.Sign.OVERFLOW) return;
                    if (name.contains("consumer")) {
                        // Consumer lag overflow = CRITICAL (data loss risk)
                        statusPool.get(subject.name()).defective(Dimension.CONFIRMED);
                    } else if (name.contains("producer")) {
                        // Producer buffer overflow = WARNING (backpressure)
                        statusPool.get(subject.name()).degraded(Dimension.MEASURED);
                    }
                });
            }
        ));

        // === DECIDE: Emit raw signs ===

        producerBuffer.enqueue();
        producerBuffer.overflow();
        consumerLag.overflow();

        circuit.await();
        circuit.close();
    }
}
```

## Key Points

1. **Context creates meaning**: A `Queues.Sign.OVERFLOW` on `producer-1.buffer` triggers `DEGRADED` status. The same sign on `consumer-1.lag` triggers `DEFECTIVE`.

2. **Semiotic ascent**: Raw queue signs → health status assessment → situation judgment. Each level compresses and interprets.

3. **`fiber.diff()` = silence equals health**: A fiber that drops repeated values means only *changes* in health propagate.

4. **Same API, different Subjects**: Both producer and consumer use the same `Queues` instrument. The Subject name is what determines interpretation.

5. **Instruments wrap pipes**: `Queues.of(pipe)` and `Statuses.of(pipe)` give you typed semantic methods (`enqueue()`, `degraded(Dimension)`). `Queues.pool(conduit)` and `Statuses.pool(conduit)` give you a lookup.

## Further Reading

- [Serventis API Documentation](https://github.com/humainary-io/serventis-api-java) — all 7 instruments (Operations, Outcomes, Situations, Statuses, Surveys, Systems, Trends) plus the Queues / Cache / Pipeline / Stack / Process opt-in vocabularies
- [Substrates Specification](https://github.com/humainary-io/substrates-api-spec) — formal contracts (incl. §13 Tenure, §14 Async Pipe Dispatch, §15 Error Model in 2.3)
