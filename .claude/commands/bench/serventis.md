---
description: Run ALL Serventis JMH benchmarks (30 groups) comparing Fullerstack vs Humainary
---

Run Serventis JMH benchmarks using Fullerstack's jmh.sh with Fullerstack as the SPI provider.

## Usage

- `/bench:serventis` - Run ALL Serventis benchmarks (~60+ minutes)
- `/bench:serventis CacheOps` - Run specific benchmark

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

Use the pattern from $ARGUMENTS, or if empty use `io.humainary.serventis.jmh` for all Serventis benchmarks.

```bash
cd /workspaces/fullerstack-humainary/fullerstack-substrates && ./jmh.sh ${ARGUMENTS:-io.humainary.serventis.jmh} -rf json -rff /workspaces/fullerstack-humainary/benchmark-results/serventis-latest.json 2>&1 | tee /workspaces/fullerstack-humainary/benchmark-results/serventis-latest.txt
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.

Results saved to `/workspaces/fullerstack-humainary/benchmark-results/`

## Serventis Modules (30 groups)

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
