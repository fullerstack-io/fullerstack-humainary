---
description: Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh CircuitOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| conduit_create_close | 281.7 | X | X% | ? |
| conduit_create_named | 282.0 | X | X% | ? |
| conduit_create_with_flow | 280.1 | X | X% | ? |
| create_and_close | 337.3 | X | X% | ? |
| create_await_close | 10,731 | X | X% | ? |
| hot_await_queue_drain | 5,799 | X | X% | ? |
| hot_conduit_create | 19.1 | X | X% | ? |
| hot_conduit_create_named | 19.1 | X | X% | ? |
| hot_conduit_create_with_flow | 21.9 | X | X% | ? |
| hot_pipe_async | 8.5 | X | X% | ? |
| hot_pipe_async_with_flow | 10.7 | X | X% | ? |
| pipe_async | 309.1 | X | X% | ? |
| pipe_async_with_flow | 320.4 | X | X% | ? |

**Summary:** X/13 Fullerstack wins, X/13 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
