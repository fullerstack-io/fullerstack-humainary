---
description: Run NameOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run NameOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh NameOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| name_chained_deep | 16.9 | X | X% | ? |
| name_chaining | 8.56 | X | X% | ? |
| name_chaining_batch | 9.04 | X | X% | ? |
| name_compare | 33.1 | X | X% | ? |
| name_compare_batch | 32.0 | X | X% | ? |
| name_depth | 1.70 | X | X% | ? |
| name_depth_batch | 1.40 | X | X% | ? |
| name_enclosure | 0.59 | X | X% | ? |
| name_from_enum | 2.83 | X | X% | ? |
| name_from_iterable | 12.0 | X | X% | ? |
| name_from_iterator | 12.9 | X | X% | ? |
| name_from_mapped_iterable | 11.7 | X | X% | ? |
| name_from_name | 4.22 | X | X% | ? |
| name_from_string | 3.05 | X | X% | ? |
| name_from_string_batch | 2.80 | X | X% | ? |
| name_interning_chained | 12.4 | X | X% | ? |
| name_interning_same_path | 3.54 | X | X% | ? |
| name_interning_segments | 10.1 | X | X% | ? |
| name_iterate_hierarchy | 1.80 | X | X% | ? |
| name_parsing | 1.89 | X | X% | ? |
| name_parsing_batch | 1.68 | X | X% | ? |
| name_path_generation | 31.3 | X | X% | ? |
| name_path_generation_batch | 30.0 | X | X% | ? |

**Summary:** X/23 Fullerstack wins, X/23 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
