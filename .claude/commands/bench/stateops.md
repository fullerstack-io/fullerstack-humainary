---
description: Run StateOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run StateOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh StateOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| slot_name | 0.52 | X | X% | ? |
| slot_name_batch | ~0 | X | X% | ? |
| slot_type | 0.44 | X | X% | ? |
| slot_value | 0.66 | X | X% | ? |
| slot_value_batch | ~0 | X | X% | ? |
| state_compact | 10.3 | X | X% | ? |
| state_compact_batch | 10.7 | X | X% | ? |
| state_iterate_slots | 2.16 | X | X% | ? |
| state_slot_add_int | 4.76 | X | X% | ? |
| state_slot_add_int_batch | 4.89 | X | X% | ? |
| state_slot_add_long | 4.75 | X | X% | ? |
| state_slot_add_object | 2.56 | X | X% | ? |
| state_slot_add_object_batch | 2.43 | X | X% | ? |
| state_slot_add_string | 4.73 | X | X% | ? |
| state_value_read | 1.49 | X | X% | ? |
| state_value_read_batch | 1.27 | X | X% | ? |
| state_values_stream | 4.98 | X | X% | ? |

**Summary:** X/17 Fullerstack wins, X/17 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
