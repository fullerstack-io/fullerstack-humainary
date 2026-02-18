# Example 05: Semiotic Observability with Serventis

**Demonstrates:** How context creates meaning through progressive signal interpretation

---

## Concept

This example shows the core insight of **semiotic observability**: the same signal means different things in different contexts. We'll build a Kafka monitoring system that demonstrates how raw signals (OBSERVE) become meaningful conditions (ORIENT) and actionable situations (DECIDE).

## The Scenario

We're monitoring a Kafka cluster with:
- **Producer buffer queues** - Track backpressure
- **Consumer lag queues** - Track processing delays

**Key Insight:** An `OVERFLOW` signal means different things depending on context:
- `producer.buffer → OVERFLOW` = Backpressure (annoying, degraded performance)
- `consumer.lag → OVERFLOW` = Data loss risk (critical, requires immediate action)

## Complete Example

```java
package io.fullerstack.substrates.examples;

import io.humainary.substrates.api.Substrates.*;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.Serventis.*;

public class SemioticObservabilityExample {

    public static void main(String[] args) throws InterruptedException {
        // Create monitoring circuit
        Circuit circuit = cortex().circuit(cortex().name("kafka-monitoring"));

        // ===== OBSERVE PHASE: Raw Sensing =====

        // Create Queue instrument conduit for flow control signals
        Conduit<Queue, Queues.Signal> queues = circuit.conduit(
            cortex().name("queues"),
            Queues::composer  // Serventis composer creates Channels
        );

        // Get Queue instruments for specific entities
        // Each get() creates a Channel with a unique Subject (entity identity)
        Queue producerBuffer = queues.percept(cortex().name("producer-1.buffer"));
        Queue consumerLag = queues.percept(cortex().name("consumer-1.lag"));

        // ===== ORIENT PHASE: Condition Assessment =====

        // Create Monitor conduit for health assessment
        Conduit<Monitor, Monitors.Status> monitors = circuit.conduit(
            cortex().name("monitors"),
            Monitors::composer
        );

        // Subscribe to Queue signals and assess conditions BASED ON CONTEXT
        queues.subscribe(circuit.subscriber(
            cortex().name("queue-health-assessor"),
            (Subject<Channel<Queues.Signal>> subject, Registrar<Queues.Signal> registrar) -> {
                // Get Monitor for this specific entity
                Monitor monitor = monitors.percept(subject.name());

                registrar.register(signal -> {
                    String entityName = subject.name().toString();
                    System.out.println("📊 Received " + signal + " from " + entityName);

                    // CONTEXT CREATES MEANING!
                    // Same signal, different interpretations:

                    if (signal == Queues.Signal.OVERFLOW) {
                        if (entityName.contains("producer")) {
                            // Producer buffer overflow = backpressure from broker
                            // Annoying but not critical - broker is slow
                            monitor.degraded(Monitors.Confidence.HIGH);
                            System.out.println("⚠️  Producer backpressure detected");
                            System.out.println("    Interpretation: Broker is slow, producer is waiting");
                            System.out.println("    Condition: DEGRADED (performance impact)");

                        } else if (entityName.contains("consumer")) {
                            // Consumer lag overflow = falling behind, data loss risk
                            // CRITICAL - consumer can't keep up, data will be lost!
                            monitor.defective(Monitors.Confidence.HIGH);
                            System.out.println("🚨 Consumer lag critical!");
                            System.out.println("    Interpretation: Consumer is drowning, data loss imminent");
                            System.out.println("    Condition: DEFECTIVE (requires immediate action)");
                        }
                    }

                    if (signal == Queues.Signal.PUT) {
                        // Normal operation
                        monitor.stable(Monitors.Confidence.HIGH);
                    }
                });
            }
        ));

        // ===== DECIDE PHASE: Situation Assessment =====

        // Create Reporter conduit for situation urgency
        Conduit<Reporter, Reporters.Situation> reporters = circuit.conduit(
            cortex().name("reporters"),
            Reporters::composer
        );

        // Subscribe to Monitor status and determine urgency
        monitors.subscribe(circuit.subscriber(
            cortex().name("situation-assessor"),
            (Subject<Channel<Monitors.Status>> subject, Registrar<Monitors.Status> registrar) -> {
                // Extract cluster name from entity path
                String clusterName = "kafka-cluster-1";  // Simplified
                Reporter reporter = reporters.percept(cortex().name(clusterName));

                registrar.register(status -> {
                    System.out.println("🔍 Assessing condition: " + status.condition() +
                                     " (confidence: " + status.confidence() + ")");

                    // Aggregate conditions into cluster-level situations
                    if (status.condition() == Monitors.Condition.DEFECTIVE) {
                        reporter.critical();
                        System.out.println("🚨 CRITICAL situation reported for cluster");

                    } else if (status.condition() == Monitors.Condition.DEGRADED) {
                        reporter.warning();
                        System.out.println("⚠️  WARNING situation reported for cluster");

                    } else if (status.condition() == Monitors.Condition.STABLE) {
                        reporter.normal();
                        System.out.println("✅ NORMAL situation - all healthy");
                    }
                });
            }
        ));

        // ===== ACT PHASE: Automated Response =====

        // Subscribe to situation reports and take action
        reporters.subscribe(circuit.subscriber(
            cortex().name("auto-responder"),
            (Subject<Channel<Reporters.Situation>> subject, Registrar<Reporters.Situation> registrar) -> {
                registrar.register(situation -> {
                    String clusterName = subject.name().toString();
                    System.out.println("\n🎯 ACT: Situation urgency = " + situation.urgency());

                    if (situation.urgency() == Reporters.Urgency.CRITICAL) {
                        // Automated remediation
                        System.out.println("🔧 AUTO-RESPONSE: Scaling up " + clusterName);
                        System.out.println("📞 AUTO-RESPONSE: Alerting on-call engineer");
                        // In real system: scaleUpCluster(clusterName);
                        // In real system: alertOnCall("Critical: " + clusterName);

                    } else if (situation.urgency() == Reporters.Urgency.WARNING) {
                        System.out.println("📧 AUTO-RESPONSE: Notifying team about " + clusterName);
                        // In real system: notifyTeam("Warning: " + clusterName);
                    }
                });
            }
        ));

        // ===== EMIT SIGNALS =====

        System.out.println("\n" + "=".repeat(70));
        System.out.println("DEMONSTRATION: Same signal, different meanings");
        System.out.println("=".repeat(70));

        // Emit same signal from different contexts
        System.out.println("\n--- Scenario 1: Producer buffer overflow ---");
        producerBuffer.overflow(95L);  // Backpressure

        circuit.await();  // Let signals flow through the system
        circuit.await();

        System.out.println("\n--- Scenario 2: Consumer lag overflow ---");
        consumerLag.overflow(95L);  // Data loss risk!

        circuit.await();
        circuit.await();

        System.out.println("\n--- Scenario 3: Normal operation ---");
        producerBuffer.enqueue();
        consumerLag.enqueue();

        circuit.await();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("RESULT: Context transformed signals into meaningful actions");
        System.out.println("=".repeat(70));

        circuit.close();
    }
}
```

## Output

```
======================================================================
DEMONSTRATION: Same signal, different meanings
======================================================================

--- Scenario 1: Producer buffer overflow ---
📊 Received OVERFLOW from producer-1.buffer
⚠️  Producer backpressure detected
    Interpretation: Broker is slow, producer is waiting
    Condition: DEGRADED (performance impact)
🔍 Assessing condition: DEGRADED (confidence: HIGH)
⚠️  WARNING situation reported for cluster

🎯 ACT: Situation urgency = WARNING
📧 AUTO-RESPONSE: Notifying team about kafka-cluster-1

--- Scenario 2: Consumer lag overflow ---
📊 Received OVERFLOW from consumer-1.lag
🚨 Consumer lag critical!
    Interpretation: Consumer is drowning, data loss imminent
    Condition: DEFECTIVE (requires immediate action)
🔍 Assessing condition: DEFECTIVE (confidence: HIGH)
🚨 CRITICAL situation reported for cluster

🎯 ACT: Situation urgency = CRITICAL
🔧 AUTO-RESPONSE: Scaling up kafka-cluster-1
📞 AUTO-RESPONSE: Alerting on-call engineer

--- Scenario 3: Normal operation ---
📊 Received PUT from producer-1.buffer
🔍 Assessing condition: STABLE (confidence: HIGH)
✅ NORMAL situation - all healthy
📊 Received PUT from consumer-1.lag
🔍 Assessing condition: STABLE (confidence: HIGH)
✅ NORMAL situation - all healthy

🎯 ACT: Situation urgency = NORMAL

======================================================================
RESULT: Context transformed signals into meaningful actions
======================================================================
```

## Key Takeaways

### 1. Signals Are Meaningless Without Context

```java
// Just a signal - what does it mean?
signal = OVERFLOW;  // ❌ No context, no meaning

// Signal + Subject = Meaning
subject = "producer-1.buffer"
signal = OVERFLOW
→ Meaning: Backpressure from broker (annoying, not critical)

subject = "consumer-1.lag"
signal = OVERFLOW
→ Meaning: Data loss risk (critical, immediate action needed!)
```

### 2. Progressive Interpretation (Semiotic Layers)

```
OBSERVE:  producer-1.buffer → OVERFLOW (raw signal)
    ↓
ORIENT:   monitor.status(DEGRADED, HIGH) (interpreted condition)
    ↓
DECIDE:   reporter.warning() (assessed urgency)
    ↓
ACT:      notifyTeam() (intelligent response)
```

Each layer adds interpretation, building from raw data to actionable intelligence.

### 3. The Subject Carries Context

The `Subject<Channel<Signal>>` parameter in subscribers provides:
- **Entity identity** - Which specific thing emitted this signal?
- **Hierarchy** - Where does this entity fit in the system?
- **Type information** - What kind of entity is this?

This allows subscribers to interpret signals differently based on who emitted them.

### 4. Automated Intelligence

Once the system understands context:
- Producer overflow → Notify team (warning)
- Consumer overflow → Scale cluster + alert on-call (critical)

The **same signal** triggers **different responses** based on **understood meaning**.

## Comparison to Traditional Monitoring

### Traditional Approach (Metrics)

```java
overflow_events_total++;  // Just a counter
                          // No context, no meaning
                          // Same action for all overflows
```

### Semiotic Approach (Signals + Context)

```java
if (entityType.equals("producer") && signal == OVERFLOW) {
    // Context: Producer buffer
    // Meaning: Backpressure
    // Action: Notify team
}

if (entityType.equals("consumer") && signal == OVERFLOW) {
    // Context: Consumer lag
    // Meaning: Data loss risk
    // Action: Scale cluster + alert on-call
}
```

## Next Steps

See:
- **README.md** - Quick start guide
- **ARCHITECTURE.md** - Complete Serventis API documentation
- **DEVELOPER-GUIDE.md** - Semiotic observability patterns
