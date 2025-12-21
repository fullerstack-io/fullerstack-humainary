---
description: Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh ScopeOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| scope_child_anonymous | 18.2 | X | X% | ? |
| scope_child_anonymous_batch | 17.7 | X | X% | ? |
| scope_child_named | 17.1 | X | X% | ? |
| scope_child_named_batch | 19.4 | X | X% | ? |
| scope_close_idempotent | 2.39 | X | X% | ? |
| scope_close_idempotent_batch | 0.03 | X | X% | ? |
| scope_closure | 286.1 | X | X% | ? |
| scope_closure_batch | 307.4 | X | X% | ? |
| scope_complex | 917.0 | X | X% | ? |
| scope_create_and_close | 2.43 | X | X% | ? |
| scope_create_and_close_batch | 0.03 | X | X% | ? |
| scope_create_named | 2.43 | X | X% | ? |
| scope_create_named_batch | 0.03 | X | X% | ? |
| scope_hierarchy | 27.3 | X | X% | ? |
| scope_hierarchy_batch | 26.6 | X | X% | ? |
| scope_parent_closes_children | 43.5 | X | X% | ? |
| scope_parent_closes_children_batch | 42.3 | X | X% | ? |
| scope_register_multiple | 1,450 | X | X% | ? |
| scope_register_multiple_batch | 1,398 | X | X% | ? |
| scope_register_single | 287.1 | X | X% | ? |
| scope_register_single_batch | 283.1 | X | X% | ? |
| scope_with_resources | 581.9 | X | X% | ? |

**Summary:** X/22 Fullerstack wins, X/22 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
