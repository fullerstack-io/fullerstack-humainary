---
description: Run FlowOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run FlowOps JMH benchmarks using Humainary's official jmh.sh with Fullerstack as the SPI provider.

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

```bash
cd /workspaces/fullerstack-humainary/fullerstack-substrates && ./jmh.sh FlowOps 2>&1 | tee /workspaces/fullerstack-humainary/benchmark-results/flowops-latest.txt
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.
