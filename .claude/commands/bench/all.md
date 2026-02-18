---
description: Run ALL JMH benchmarks (14 Substrates + 30 Serventis groups) comparing Fullerstack vs Humainary
---

Run ALL JMH benchmarks (Substrates + Serventis) using Fullerstack's jmh.sh with Fullerstack as the SPI provider.

**Substrates (14):** CircuitOps, ConduitOps, CortexOps, CyclicOps, FlowOps, IdOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubjectOps, SubscriberOps, TapOps

**Serventis (30):** See `/bench:serventis` for full module listing.

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

```bash
cd /workspaces/fullerstack-humainary/fullerstack-substrates && ./jmh.sh -rf json -rff /workspaces/fullerstack-humainary/benchmark-results/all-latest.json 2>&1 | tee /workspaces/fullerstack-humainary/benchmark-results/all-latest.txt
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.

Results saved to `/workspaces/fullerstack-humainary/benchmark-results/`
