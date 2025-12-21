---
description: Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh CortexOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| circuit | 279.2 | X | X% | ? |
| circuit_batch | 280.1 | X | X% | ? |
| circuit_named | 288.3 | X | X% | ? |
| current | 1.09 | X | X% | ? |
| name_class | 1.48 | X | X% | ? |
| name_enum | 2.81 | X | X% | ? |
| name_iterable | 11.2 | X | X% | ? |
| name_path | 1.89 | X | X% | ? |
| name_path_batch | 1.69 | X | X% | ? |
| name_string | 2.85 | X | X% | ? |
| name_string_batch | 2.54 | X | X% | ? |
| scope | 9.28 | X | X% | ? |
| scope_batch | 7.58 | X | X% | ? |
| scope_named | 8.01 | X | X% | ? |
| slot_boolean | 2.41 | X | X% | ? |
| slot_double | 2.43 | X | X% | ? |
| slot_int | 2.12 | X | X% | ? |
| slot_long | 2.42 | X | X% | ? |
| slot_string | 2.43 | X | X% | ? |
| state_empty | 0.44 | X | X% | ? |
| state_empty_batch | ~0 | X | X% | ? |

**Summary:** X/21 Fullerstack wins, X/21 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
