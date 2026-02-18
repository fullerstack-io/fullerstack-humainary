---
description: Run ALL Substrates JMH benchmarks (14 groups) comparing Fullerstack vs Humainary
---

Run all 14 Substrates JMH benchmark groups using Fullerstack's jmh.sh with Fullerstack as the SPI provider.

**Groups:** CircuitOps, ConduitOps, CortexOps, CyclicOps, FlowOps, IdOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubjectOps, SubscriberOps, TapOps

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

```bash
cd /workspaces/fullerstack-humainary/fullerstack-substrates && ./jmh.sh io.humainary.substrates.jmh -rf json -rff /workspaces/fullerstack-humainary/benchmark-results/substrates-latest.json 2>&1 | tee /workspaces/fullerstack-humainary/benchmark-results/substrates-latest.txt
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.

Results saved to `/workspaces/fullerstack-humainary/benchmark-results/`
