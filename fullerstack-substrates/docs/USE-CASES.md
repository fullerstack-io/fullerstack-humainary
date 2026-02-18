# Substrates Use Cases & Problem Domains

This document outlines the problem domains where Humainary Substrates provides unique value through its semiotic observability architecture.

## 1. Semantic Observability (Post-Telemetry)

Beyond metrics/traces/logs to signal interpretation:

- **Real-time semantic signal interpretation** at computational speeds
- **Autonomous adaptation** without human dashboards
- **Closing feedback loops** at nanosecond speeds (vs second/minute alerting)
- **Reducing telemetry costs** by interpreting signals locally

Traditional observability collects data for humans to interpret. Substrates enables systems to interpret signals autonomously, closing the gap between observation and action.

## 2. Agentic AI Systems

Observability and coordination for AI agents:

- **Multi-agent workflow visibility** - See what agents are doing
- **Agent commitment tracking** (REQUEST/PROMISE/DELIVER speech acts)
- **Human-in-the-loop coordination** - Unified vocabulary for human+AI collaboration
- **Agent behavior understanding** through semantic signals

The Actors and Agents APIs provide vocabularies grounded in Speech Act Theory and Promise Theory for coordinating autonomous agents.

## 3. Task-Centric Coordination

Universal coordination across heterogeneous actors:

- **Workflow orchestration** without centralized control
- **Speech act coordination vocabulary** - ASK, PROMISE, DELIVER, ACKNOWLEDGE
- **Human + service + agent coordination** on unified platform
- **Replacing workflow engine + service mesh + task queue** with semantic signals

## 4. Digital Twin Infrastructure

High-fidelity computational mirrors with situation awareness:

- **Perception → Comprehension → Projection → Action** (Endsley's model)
- **Deterministic signal flow** for synchronization
- **Distributed twins** (factory floor + cloud + edge)
- **Moving from passive mirrors to active twins**

Substrates' deterministic event ordering enables reliable synchronization between physical systems and their digital representations.

## 5. Self-Healing Infrastructure

Autonomous systems that observe, assess, and adapt:

- **Self-assessment** without human intervention
- **Proactive incident prevention** through semiotic ascent
- **Reduced MTTR** through autonomous recovery
- **Deterministic event ordering** for reliable healing

The layered hierarchy (Raw Signs → Systems → Statuses → Situations → Actions) enables autonomous interpretation and response.

## 6. Autonomous Systems

Foundation for robotics, vehicles, drones:

- **Real-time interpretation** at computational speeds
- **Safety-critical deterministic signal flow**
- **Multi-agent fleet coordination**
- **Situation awareness hierarchy**

## 7. Human-AI Collaboration

Shared situational awareness for human-AI teams:

- **Unified coordination vocabulary** (Speech Acts)
- **Context preservation** across interactions
- **Handoff protocols** between human and AI
- **Transparent agent reasoning** through semantic signals

## 8. Event-Driven Architecture

High-performance event processing:

- **Event sourcing** with deterministic ordering
- **Stream processing pipelines** with backpressure
- **Fan-out/fan-in patterns**
- **Backpressure handling** via Valves

## When to Use Substrates

Substrates is particularly well-suited when you:

| Requirement | Substrates Solution |
|-------------|---------------------|
| Need deterministic event ordering | Virtual CPU Core pattern ensures FIFO + depth-first processing |
| Want semiotic signal interpretation | Same signal = different meaning based on Subject context |
| Building systems that must observe AND adapt autonomously | Layered hierarchy enables interpretation without human intervention |
| Require hierarchical computation | Cell-based bidirectional type transformation |
| Coordinating heterogeneous actors | Unified vocabulary for humans, services, and agents |
| Performance requirements | 100k+ metrics @ 1Hz with ~2% CPU |

## Performance Characteristics

- **High-frequency instruments** (10M-50M Hz): Counters, Gauges, Caches, Probes
- **Medium-frequency instruments** (100K-10M Hz): Services, Tasks, Resources
- **Coordination instruments** (1-1000 Hz): Agents, Actors, Statuses, Situations

All built on Substrates' deterministic event ordering via the Virtual CPU Core pattern (FsCircuit with IngressQueue + TransitQueue).

## Further Reading

- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java)
- [Serventis Documentation](SERVENTIS.md) - Semantic observability extension
- [Architecture Overview](ARCHITECTURE.md) - Implementation details
- [Observability X Blog](https://humainary.io/blog/category/observability-x/) - William Louth's vision
