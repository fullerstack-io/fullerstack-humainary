---
description: Run ALL Serventis JMH benchmarks comparing Fullerstack vs Humainary
---

Run Serventis JMH benchmarks using Humainary's official jmh.sh with Fullerstack as the SPI provider.

## Usage

- `/bench:serventis` - Run ALL Serventis benchmarks (~60+ minutes)
- `/bench:serventis CacheOps` - Run specific benchmark

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

Use the pattern from $ARGUMENTS, or if empty use `io.humainary.serventis.jmh` for all Serventis benchmarks.

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
cd /workspaces/fullerstack-humainary/fullerstack-substrates && mvn clean install -DskipTests -q && \
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC1 \
/workspaces/fullerstack-humainary/substrates-api-java/jmh.sh $ARGUMENTS 2>&1 | tee /workspaces/fullerstack-humainary/benchmark-results/serventis-latest.txt
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.

## Serventis Modules

| Module | Instruments |
|--------|-------------|
| **sdk** | CycleOps, OperationOps, OutcomeOps, SignalSetOps, SituationOps, StatusOps, SurveyOps, SystemOps, TrendOps |
| **opt/tool** | CounterOps, GaugeOps, LogOps, ProbeOps, SensorOps |
| **opt/data** | CacheOps, PipelineOps, QueueOps, StackOps |
| **opt/flow** | BreakerOps, FlowOps, RouterOps, ValveOps |
| **opt/sync** | AtomicOps, LatchOps, LockOps |
| **opt/pool** | ExchangeOps, LeaseOps, PoolOps, ResourceOps |
| **opt/exec** | ProcessOps, ServiceOps, TaskOps, TimerOps, TransactionOps |
| **opt/role** | ActorOps, AgentOps |
