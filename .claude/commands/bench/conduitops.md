---
description: Run ConduitOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run ConduitOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh ConduitOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| get_by_name | 1.88 | X | X% | ? |
| get_by_name_batch | 1.66 | X | X% | ? |
| get_by_substrate | 1.99 | X | X% | ? |
| get_by_substrate_batch | 1.81 | X | X% | ? |
| get_cached | 3.43 | X | X% | ? |
| get_cached_batch | 3.30 | X | X% | ? |
| subscribe | 436.6 | X | X% | ? |
| subscribe_batch | 461.7 | X | X% | ? |
| subscribe_with_emission_await | 5,644 | X | X% | ? |

**Summary:** X/9 Fullerstack wins, X/9 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
