---
description: Run FlowOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run FlowOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh FlowOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| baseline_no_flow_await | 17.8 | X | X% | ? |
| flow_combined_diff_guard_await | 30.0 | X | X% | ? |
| flow_combined_diff_sample_await | 19.4 | X | X% | ? |
| flow_combined_guard_limit_await | 28.3 | X | X% | ? |
| flow_diff_await | 30.0 | X | X% | ? |
| flow_guard_await | 30.4 | X | X% | ? |
| flow_limit_await | 28.7 | X | X% | ? |
| flow_sample_await | 17.2 | X | X% | ? |
| flow_sift_await | 18.7 | X | X% | ? |

**Summary:** X/9 Fullerstack wins, X/9 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
