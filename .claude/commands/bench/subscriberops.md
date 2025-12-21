---
description: Run SubscriberOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run SubscriberOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh SubscriberOps
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| close_five_conduits_await | 8,696 | X | X% | ? |
| close_five_subscriptions_await | 8,631 | X | X% | ? |
| close_idempotent_await | 8,438 | X | X% | ? |
| close_idempotent_batch_await | 17.2 | X | X% | ? |
| close_no_subscriptions_await | 8,450 | X | X% | ? |
| close_no_subscriptions_batch_await | 14.2 | X | X% | ? |
| close_one_subscription_await | 8,438 | X | X% | ? |
| close_one_subscription_batch_await | 34.9 | X | X% | ? |
| close_ten_conduits_await | 8,515 | X | X% | ? |
| close_ten_subscriptions_await | 8,727 | X | X% | ? |
| close_with_pending_emissions_await | 8,713 | X | X% | ? |

**Summary:** X/11 Fullerstack wins, X/11 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
