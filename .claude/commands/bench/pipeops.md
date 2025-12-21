---
description: Run PipeOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run PipeOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh PipeOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| async_emit_batch | 11.8 | X | X% | ? |
| async_emit_batch_await | 16.9 | X | X% | ? |
| async_emit_chained_await | 16.9 | X | X% | ? |
| async_emit_fanout_await | 18.7 | X | X% | ? |
| async_emit_single | 10.6 | X | X% | ? |
| async_emit_single_await | 5,478 | X | X% | ? |
| async_emit_with_flow_await | 21.2 | X | X% | ? |
| baseline_blackhole | 0.27 | X | X% | ? |
| baseline_counter | 1.62 | X | X% | ? |
| baseline_receptor | 0.26 | X | X% | ? |
| pipe_create | 8.75 | X | X% | ? |
| pipe_create_chained | 0.86 | X | X% | ? |
| pipe_create_with_flow | 13.2 | X | X% | ? |

**Summary:** X/13 Fullerstack wins, X/13 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
