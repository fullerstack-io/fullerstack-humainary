---
description: Run ALL Substrates JMH benchmarks (10 groups) comparing Fullerstack vs Humainary
---

**IMPORTANT: This benchmark takes 12-15 minutes. ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run all 10 Substrates JMH benchmark groups comparing Fullerstack vs Humainary implementation.

**Groups:** CircuitOps, ConduitOps, CortexOps, FlowOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubscriberOps

## Steps

1. Run the benchmark script (no pattern = all benchmarks):

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh
```

2. Present ALL results in a single comparison table with Humainary baselines.

**Key Humainary Baselines (from BENCHMARKS.md):**
- CircuitOps: hot_pipe_async=8.5ns, hot_conduit_create=19.1ns
- ConduitOps: get_by_name=1.9ns, subscribe=436.6ns
- CortexOps: circuit=279.2ns, current=1.1ns
- FlowOps: baseline_no_flow_await=17.8ns
- NameOps: name_from_string=3.0ns, name_compare=33.1ns
- PipeOps: async_emit_single=10.6ns
- ReservoirOps: baseline_emit=96.2ns
- ScopeOps: scope_create_and_close=2.4ns
- StateOps: slot_value=0.66ns
- SubscriberOps: close_no_subscriptions_await=8,450ns

## Finding Results

All benchmark results are saved to `/workspaces/fullerstack-humainary/benchmark-results/` with naming pattern:
- `{group}-{circuit_type}-{timestamp}.json` - Machine-readable JSON
- `{group}-{circuit_type}-{timestamp}.txt` - Human-readable text output

List recent results:
```bash
ls -lt /workspaces/fullerstack-humainary/benchmark-results/ | head -20
```

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
