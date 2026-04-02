# Agent Observability

Semiotic health monitoring and safety for AI agent workflows. Detects when your agent's behaviour changes before it causes harm.

Built on [Humainary Substrates](https://github.com/humainary-io/substrates-api-java) and [Serventis](https://github.com/humainary-io/serventis-api-java).

## What It Does

Every AI agent action (LLM call, tool invocation, reasoning step) becomes a typed signal processed through semiotic ascent — raw observations compressed into health assessments. In steady state, the system produces **zero output**. A single signal after hours of silence means something changed.

**Health monitoring**: per-model, per-tool success rate trending (STABLE → DIVERGING → DEGRADED → DOWN)

**Safety monitoring**: three layers of misalignment detection — access pattern anomalies, workflow topology deviations, and chain-of-thought reasoning analysis

**Circuit breaker**: blocks agent actions when trust degrades (TRUSTED → CAUTIOUS → SUSPICIOUS → UNTRUSTED)

**Audit trail**: append-only log of every action, every anomaly, every trust change

## Quick Start

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>agent-observability</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Spring AI — One Line Integration

```java
var chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(SafetyAdvisor.create("my-agent"))
    .build();

// That's it. Every ChatClient call is now monitored.
```

### Manual Integration

```java
var observer = AgentObserver.create("my-agent");

// Instrument LLM calls
observer.llmCallStarted("gpt-4o");
observer.llmCallSucceeded("gpt-4o", latencyMs, inputTokens, outputTokens);

// Instrument tool calls  
observer.toolCallStarted("web-search");
observer.toolCallSucceeded("web-search", latencyMs);

// Health alerts (silence = healthy, signal = something changed)
observer.onSituationChange(signal -> {
    if (signal.sign() == Situations.Sign.CRITICAL)
        pagerduty.trigger("Agent degraded");
});
```

### Safety Monitoring

```java
var observer = AgentObserver.create("my-agent");
var safety = new SafetyMonitor(observer);
var breaker = new AgentCircuitBreaker(safety);

// Access pattern monitoring
safety.recordToolAccess("email-system", AccessType.EMAIL_ACCESS);
// → NOVEL_ACCESS anomaly if email was never accessed before

// Reasoning analysis
safety.analyseReasoning(agentChainOfThought);
// → Detects self-preservation, ethical override, deceptive planning

// Circuit breaker
breaker.check("LLM call to gpt-4o");
// → Throws AgentHaltedException if trust is UNTRUSTED

// Trust change callbacks
safety.onTrustChange(event -> {
    log.warn("Trust: {} → {} reason: {}",
        event.previousLevel(), event.trustLevel(), event.summary());
});
```

## How It Works

```
Agent Action
  │
  ▼
Services signals (START/SUCCESS/FAIL per model/tool)
  │
  ▼ lossy tap (only SUCCESS/FAIL pass)
Outcomes (binary verdict)
  │
  ▼ sliding window (10 samples per channel)
Statuses (STABLE/DIVERGING/DEGRADED/DOWN per model)
  │
  ▼ diff() — only changes propagate
Situations (NORMAL/WARNING/CRITICAL)
  │
  ▼
Callbacks / Prometheus metrics / Audit trail
```

**Compression**: 100 agent actions → ~10 outcome assessments → 1 status change (if anything changed) → 0 situation signals (if status didn't change). Silence = healthy.

## Safety Detection Layers

| Layer | What It Catches | Example |
|---|---|---|
| **Access Pattern** | Tool/data access outside baseline | Agent reads emails for the first time |
| **Workflow Topology** | Unexpected action sequences | Tool called without prior reasoning |
| **Reasoning Analysis** | Self-preservation, deception, ethical override | "This is wrong but I need to protect myself" |

### Trust Levels

| Level | Meaning | Breaker Action |
|---|---|---|
| TRUSTED | Normal operation | Permit |
| CAUTIOUS | One anomaly detected | Permit with extra logging |
| SUSPICIOUS | Multiple anomalies | Require human approval |
| UNTRUSTED | Critical reasoning concern | Block all actions |

## Prometheus Metrics

```
agent_llm_calls_total{agent="my-agent",status="success"} 142
agent_llm_calls_total{agent="my-agent",status="failed"} 3
agent_llm_success_rate{agent="my-agent"} 0.979
agent_trust_level{agent="my-agent"} 0
agent_access_anomalies_total{agent="my-agent"} 0
agent_reasoning_concerns_total{agent="my-agent"} 0
```

## Architecture

All processing runs **in-process** on the same JVM as your agent. No external services, no Kafka, no analytics pipeline. 3 virtual threads (agent circuit + health circuit + yours), ~13ns per signal emission, zero allocation on the hot path.

## License

Apache License 2.0

Built on [Humainary Substrates](https://github.com/humainary-io/substrates-api-java) by [William Louth](https://humainary.io/).
