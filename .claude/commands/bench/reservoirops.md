---
description: Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh ReservoirOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| baseline_emit_no_reservoir_await | 96.2 | X | X% | ? |
| baseline_emit_no_reservoir_await_batch | 18.5 | X | X% | ? |
| reservoir_burst_then_drain_await | 90.2 | X | X% | ? |
| reservoir_burst_then_drain_await_batch | 28.8 | X | X% | ? |
| reservoir_drain_await | 93.8 | X | X% | ? |
| reservoir_drain_await_batch | 28.3 | X | X% | ? |
| reservoir_emit_drain_cycles_await | 328.1 | X | X% | ? |
| reservoir_emit_with_capture_await | 80.0 | X | X% | ? |
| reservoir_emit_with_capture_await_batch | 23.9 | X | X% | ? |
| reservoir_process_emissions_await | 89.1 | X | X% | ? |
| reservoir_process_emissions_await_batch | 26.1 | X | X% | ? |
| reservoir_process_subjects_await | 97.5 | X | X% | ? |

**Summary:** X/12 Fullerstack wins, X/12 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
