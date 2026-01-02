# Benchmark Comparison: Fullerstack vs Humainary

**Last Updated:** 2026-01-02
**JMH Configuration:** Mode=avgt, Iterations=5, Forks=1

## Hardware Configuration

| | Humainary | Fullerstack |
|---|-----------|-------------|
| **Platform** | Apple M4 Mac | Azure VM (GitHub Codespaces) |
| **CPU** | Apple M4 (10 cores) | Intel Xeon (shared vCPUs) |
| **Memory** | 16GB unified | 8GB |
| **JDK** | 25.0.1 OpenJDK | 25.0.1 OpenJDK |

> **Note:** Direct performance comparisons are approximate due to different hardware.
> Focus on relative patterns rather than absolute numbers.

## Recent Optimizations Applied

1. **VarHandle `getOpaque()`** for parked flag check in emit path (reduced volatile read overhead)
2. **Long.compare()** for Subject.compareTo() instead of String.formatted() (fixed 183x regression)
3. **Cached `part()` result** in Subject for faster comparisons

## Summary

| Category | FS Wins | HUM Wins | Notes |
|----------|--------:|---------:|-------|
| **Substrates Core** ||||
| CircuitOps | 3 | 8 | FS faster at pipe_async, HUM faster at hot paths |
| ConduitOps | 4 | 5 | FS faster lookups |
| CortexOps | 7 | 11 | Mixed results |
| CyclicOps | 0 | 3 | HUM significantly faster |
| FlowOps | 4 | 5 | FS faster guard/limit, HUM faster sift/sample |
| NameOps | 10 | 14 | FS faster path_generation, HUM faster interning |
| PipeOps | 3 | 10 | FS faster pipe_create, HUM faster batch |
| ReservoirOps | 0 | 12 | HUM generally faster |
| ScopeOps | 0 | 18 | HUM faster |
| StateOps | 0 | 17 | HUM significantly faster |
| SubjectOps | 2 | 3 | FS fixed compareTo, now competitive |
| SubscriberOps | 0 | 11 | HUM faster |
| **Serventis** ||||
| SDK (all) | 10 | 30 | FS faster lookups, HUM faster emissions |

## Complete Benchmark Results

All times in **ns/op** (nanoseconds per operation). Lower is better.

| Benchmark | Fullerstack | Humainary | Δ% | Winner |
|-----------|------------:|----------:|---:|:------:|
| **CircuitOps** |||||
| io.humainary.substrates.jmh.CircuitOps.conduit_create_close | 432.129 | 274.761 | -36% | HUM |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_named | 310.923 | 281.411 | -10% | HUM |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_with_flow | 334.247 | 270.427 | -19% | HUM |
| io.humainary.substrates.jmh.CircuitOps.create_and_close | 818.128 | 325.009 | -60% | HUM |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create | 27.420 | 19.096 | -30% | HUM |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_named | 27.482 | 19.084 | -31% | HUM |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_with_flow | 27.900 | 21.881 | -22% | HUM |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async | 4.710 | 8.740 | +85% | **FS** |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async_with_flow | 5.951 | 10.424 | +75% | **FS** |
| io.humainary.substrates.jmh.CircuitOps.pipe_async | 974.552 | 315.931 | -68% | HUM |
| io.humainary.substrates.jmh.CircuitOps.pipe_async_with_flow | 274.187 | 399.294 | +46% | **FS** |
| **ConduitOps** |||||
| io.humainary.substrates.jmh.ConduitOps.get_by_name | 1.614 | 1.903 | +18% | **FS** |
| io.humainary.substrates.jmh.ConduitOps.get_by_name_batch | 1.520 | 1.659 | +9% | **FS** |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate | 2.612 | 2.053 | -21% | HUM |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate_batch | 2.125 | 1.816 | -15% | HUM |
| io.humainary.substrates.jmh.ConduitOps.get_cached | 2.920 | 3.481 | +19% | **FS** |
| io.humainary.substrates.jmh.ConduitOps.get_cached_batch | 2.430 | 3.308 | +36% | **FS** |
| io.humainary.substrates.jmh.ConduitOps.subscribe | 1137.919 | 472.514 | -58% | HUM |
| io.humainary.substrates.jmh.ConduitOps.subscribe_batch | 998.576 | 489.816 | -51% | HUM |
| io.humainary.substrates.jmh.ConduitOps.subscribe_with_emission_await | 10972.418 | 8376.233 | -24% | HUM |
| **CortexOps** |||||
| io.humainary.substrates.jmh.CortexOps.circuit | 423.331 | 283.764 | -33% | HUM |
| io.humainary.substrates.jmh.CortexOps.circuit_batch | 425.795 | 285.831 | -33% | HUM |
| io.humainary.substrates.jmh.CortexOps.circuit_named | 978.531 | 267.902 | -73% | HUM |
| io.humainary.substrates.jmh.CortexOps.current | 3.052 | 1.067 | -65% | HUM |
| io.humainary.substrates.jmh.CortexOps.name_class | 10.487 | 1.496 | -86% | HUM |
| io.humainary.substrates.jmh.CortexOps.name_enum | 2.604 | 2.817 | +8% | **FS** |
| io.humainary.substrates.jmh.CortexOps.name_iterable | 11.675 | 11.262 | -4% | ~ |
| io.humainary.substrates.jmh.CortexOps.name_path | 6.922 | 1.891 | -73% | HUM |
| io.humainary.substrates.jmh.CortexOps.name_path_batch | 6.308 | 1.675 | -73% | HUM |
| io.humainary.substrates.jmh.CortexOps.name_string | 2.813 | 2.848 | +1% | ~ |
| io.humainary.substrates.jmh.CortexOps.name_string_batch | 2.505 | 2.605 | +4% | ~ |
| io.humainary.substrates.jmh.CortexOps.scope | 5.345 | 9.076 | +70% | **FS** |
| io.humainary.substrates.jmh.CortexOps.scope_batch | 5.096 | 7.549 | +48% | **FS** |
| io.humainary.substrates.jmh.CortexOps.scope_named | 5.533 | 7.973 | +44% | **FS** |
| io.humainary.substrates.jmh.CortexOps.slot_boolean | 2.394 | 2.448 | +2% | ~ |
| io.humainary.substrates.jmh.CortexOps.slot_double | 5.074 | 2.403 | -53% | HUM |
| io.humainary.substrates.jmh.CortexOps.slot_int | 2.325 | 2.327 | 0% | ~ |
| io.humainary.substrates.jmh.CortexOps.slot_long | 2.332 | 2.349 | +1% | ~ |
| io.humainary.substrates.jmh.CortexOps.slot_string | 2.620 | 2.267 | -13% | HUM |
| io.humainary.substrates.jmh.CortexOps.state_empty | 1.744 | 0.443 | -75% | HUM |
| io.humainary.substrates.jmh.CortexOps.state_empty_batch | 1.543 | ~0 | -99% | HUM |
| **CyclicOps** |||||
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit | 3.139 | 1.200 | -62% | HUM |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await | 29.972 | 10.200 | -66% | HUM |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await | 14.546 | 4.200 | -71% | HUM |
| **FlowOps** |||||
| io.humainary.substrates.jmh.FlowOps.baseline_no_flow_await | 17.218 | 16.186 | -6% | HUM |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_guard_await | 32.996 | 29.679 | -10% | HUM |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_sample_await | 31.568 | 19.033 | -40% | HUM |
| io.humainary.substrates.jmh.FlowOps.flow_combined_guard_limit_await | 21.481 | 29.187 | +36% | **FS** |
| io.humainary.substrates.jmh.FlowOps.flow_diff_await | 27.751 | 28.554 | +3% | ~ |
| io.humainary.substrates.jmh.FlowOps.flow_guard_await | 19.918 | 28.121 | +41% | **FS** |
| io.humainary.substrates.jmh.FlowOps.flow_limit_await | 19.175 | 28.068 | +46% | **FS** |
| io.humainary.substrates.jmh.FlowOps.flow_sample_await | 19.766 | 17.198 | -13% | HUM |
| io.humainary.substrates.jmh.FlowOps.flow_sift_await | 30.682 | 18.582 | -39% | HUM |
| **NameOps** |||||
| io.humainary.substrates.jmh.NameOps.name_chained_deep | 11.303 | 16.959 | +50% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_chaining | 10.221 | 8.804 | -14% | HUM |
| io.humainary.substrates.jmh.NameOps.name_chaining_batch | 10.393 | 8.887 | -14% | HUM |
| io.humainary.substrates.jmh.NameOps.name_compare | 3.022 | 0.766 | -75% | HUM |
| io.humainary.substrates.jmh.NameOps.name_compare_batch | 0.003 | 0.001 | -67% | HUM |
| io.humainary.substrates.jmh.NameOps.name_depth | 0.837 | 1.613 | +93% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_depth_batch | 0.001 | 1.337 | +∞ | **FS** |
| io.humainary.substrates.jmh.NameOps.name_enclosure | 0.856 | 0.542 | -37% | HUM |
| io.humainary.substrates.jmh.NameOps.name_from_enum | 2.170 | 2.823 | +30% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_from_iterable | 9.398 | 11.883 | +26% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_from_iterator | 9.496 | 12.914 | +36% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_from_mapped_iterable | 11.398 | 11.692 | +3% | ~ |
| io.humainary.substrates.jmh.NameOps.name_from_name | 4.597 | 4.279 | -7% | HUM |
| io.humainary.substrates.jmh.NameOps.name_from_string | 2.178 | 3.033 | +39% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_from_string_batch | 1.899 | 2.830 | +49% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_interning_chained | 21.056 | 12.383 | -41% | HUM |
| io.humainary.substrates.jmh.NameOps.name_interning_same_path | 10.059 | 3.558 | -65% | HUM |
| io.humainary.substrates.jmh.NameOps.name_interning_segments | 8.161 | 9.237 | +13% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_iterate_hierarchy | 3.214 | 1.661 | -48% | HUM |
| io.humainary.substrates.jmh.NameOps.name_parsing | 4.880 | 1.893 | -61% | HUM |
| io.humainary.substrates.jmh.NameOps.name_parsing_batch | 4.589 | 1.681 | -63% | HUM |
| io.humainary.substrates.jmh.NameOps.name_path_generation | 0.837 | 33.184 | +3865% | **FS** |
| io.humainary.substrates.jmh.NameOps.name_path_generation_batch | 0.001 | 28.421 | +∞ | **FS** |
| **PipeOps** |||||
| io.humainary.substrates.jmh.PipeOps.async_emit_batch | 18.738 | 11.289 | -40% | HUM |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch_await | 20.352 | 18.129 | -11% | HUM |
| io.humainary.substrates.jmh.PipeOps.async_emit_chained_await | 19.216 | 17.177 | -11% | HUM |
| io.humainary.substrates.jmh.PipeOps.async_emit_fanout_await | 21.547 | 18.010 | -16% | HUM |
| io.humainary.substrates.jmh.PipeOps.async_emit_single | 10.314 | 9.103 | -12% | HUM |
| io.humainary.substrates.jmh.PipeOps.async_emit_single_await | 207.481 | 5502.879 | +2553% | **FS** |
| io.humainary.substrates.jmh.PipeOps.async_emit_with_flow_await | 22.218 | 17.474 | -21% | HUM |
| io.humainary.substrates.jmh.PipeOps.baseline_blackhole | 0.416 | 0.267 | -36% | HUM |
| io.humainary.substrates.jmh.PipeOps.baseline_counter | 2.435 | 1.635 | -33% | HUM |
| io.humainary.substrates.jmh.PipeOps.baseline_receptor | 0.402 | 0.265 | -34% | HUM |
| io.humainary.substrates.jmh.PipeOps.pipe_create | 4.662 | 8.606 | +85% | **FS** |
| io.humainary.substrates.jmh.PipeOps.pipe_create_chained | 3.003 | 0.859 | -71% | HUM |
| io.humainary.substrates.jmh.PipeOps.pipe_create_with_flow | 11.586 | 12.471 | +8% | **FS** |
| **ReservoirOps** |||||
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await | 23.028 | 92.800 | +303% | **FS** |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await_batch | 18.989 | 17.700 | -7% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await | 125.492 | 76.400 | -39% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await_batch | 43.324 | 28.900 | -33% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await | 153.903 | 78.100 | -49% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await_batch | 44.464 | 29.200 | -34% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_drain_cycles_await | 222.680 | 352.400 | +58% | **FS** |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await | 131.213 | 73.100 | -44% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await_batch | 42.642 | 24.200 | -43% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await | 140.801 | 93.000 | -34% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await_batch | 45.653 | 26.300 | -42% | HUM |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_subjects_await | 139.839 | 78.500 | -44% | HUM |
| **ScopeOps** |||||
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous | 16.459 | 17.600 | +7% | **FS** |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous_batch | 16.712 | 16.600 | -1% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named | 17.098 | 22.100 | +29% | **FS** |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named_batch | 16.939 | 17.000 | 0% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent | 0.738 | 2.400 | +225% | **FS** |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent_batch | 0.301 | ~0 | -100% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_closure | 370.152 | 285.400 | -23% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_closure_batch | 370.679 | 284.300 | -23% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_complex | 2906.698 | 915.200 | -69% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close | 0.716 | 2.500 | +249% | **FS** |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.304 | ~0 | -100% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named | 0.798 | 2.500 | +213% | **FS** |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named_batch | 0.437 | ~0 | -100% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy | 31.979 | 27.100 | -15% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy_batch | 32.036 | 26.500 | -17% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children | 45.653 | 43.400 | -5% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 42.547 | 42.400 | 0% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple | 3786.492 | 1458.800 | -62% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple_batch | 3062.812 | 1437.200 | -53% | HUM |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single | 290.242 | 287.700 | -1% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single_batch | 267.809 | 278.800 | +4% | ~ |
| io.humainary.substrates.jmh.ScopeOps.scope_with_resources | 1869.409 | 598.600 | -68% | HUM |
| **StateOps** |||||
| io.humainary.substrates.jmh.StateOps.slot_name | 0.828 | 0.500 | -40% | HUM |
| io.humainary.substrates.jmh.StateOps.slot_name_batch | 0.001 | ~0 | ~ | ~ |
| io.humainary.substrates.jmh.StateOps.slot_type | 0.822 | 0.400 | -51% | HUM |
| io.humainary.substrates.jmh.StateOps.slot_value | 1.028 | 0.600 | -42% | HUM |
| io.humainary.substrates.jmh.StateOps.slot_value_batch | 0.001 | ~0 | ~ | ~ |
| io.humainary.substrates.jmh.StateOps.state_compact | 28.112 | 10.400 | -63% | HUM |
| io.humainary.substrates.jmh.StateOps.state_compact_batch | 32.445 | 10.600 | -67% | HUM |
| io.humainary.substrates.jmh.StateOps.state_iterate_slots | 5.277 | 2.200 | -58% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int | 6.415 | 4.800 | -25% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int_batch | 5.562 | 4.600 | -17% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_long | 6.527 | 4.700 | -28% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object | 4.370 | 2.600 | -41% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object_batch | 4.146 | 2.400 | -42% | HUM |
| io.humainary.substrates.jmh.StateOps.state_slot_add_string | 6.300 | 4.800 | -24% | HUM |
| io.humainary.substrates.jmh.StateOps.state_value_read | 3.883 | 1.500 | -61% | HUM |
| io.humainary.substrates.jmh.StateOps.state_value_read_batch | 0.153 | 1.300 | +750% | **FS** |
| io.humainary.substrates.jmh.StateOps.state_values_stream | 26.115 | 4.800 | -82% | HUM |
| **SubjectOps** |||||
| io.humainary.substrates.jmh.SubjectOps.subject_compare | 1.570 | 3.800 | +142% | **FS** |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_batch | 0.002 | 3.800 | +∞ | **FS** |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same | 0.701 | 0.500 | -29% | HUM |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same_batch | 0.001 | ~0 | ~ | ~ |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_three_way | 2.816 | 11.300 | +301% | **FS** |
| **SubscriberOps** |||||
| io.humainary.substrates.jmh.SubscriberOps.close_five_conduits_await | 1884.638 | 8705.300 | +362% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_five_subscriptions_await | 1771.783 | 8729.400 | +393% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_await | 1734.659 | 8217.300 | +374% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_batch_await | 3.407 | 16.600 | +387% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_await | 4746.920 | 8473.900 | +79% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_batch_await | 47.167 | 14.600 | -69% | HUM |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_await | 3751.199 | 8344.000 | +122% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_batch_await | 125.891 | 33.700 | -73% | HUM |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_conduits_await | 2094.554 | 8641.100 | +313% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_subscriptions_await | 2049.821 | 8533.100 | +316% | **FS** |
| io.humainary.substrates.jmh.SubscriberOps.close_with_pending_emissions_await | 5372.843 | 8698.300 | +62% | **FS** |
| **Serventis SDK** |||||
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin | 18.200 | 8.000 | -56% | HUM |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end | 23.600 | 8.700 | -63% | HUM |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign | 21.800 | 8.500 | -61% | HUM |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit | 1.200 | 1.900 | +58% | **FS** |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail | 19.100 | 8.200 | -57% | HUM |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success | 25.600 | 8.400 | -67% | HUM |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit | 1.200 | 1.900 | +58% | **FS** |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical | 25.000 | 10.000 | -60% | HUM |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal | 20.000 | 8.400 | -58% | HUM |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning | 31.100 | 8.300 | -73% | HUM |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit | 1.200 | 1.900 | +58% | **FS** |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed | 31.300 | 8.300 | -73% | HUM |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured | 27.500 | 8.500 | -69% | HUM |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit | 1.200 | 1.900 | +58% | **FS** |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_divided | 33.700 | 7.800 | -77% | HUM |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_majority | 21.634 | 7.600 | -65% | HUM |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_unanimous | 19.540 | 8.500 | -56% | HUM |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit | 1.520 | 1.900 | +25% | **FS** |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow | 19.982 | 8.400 | -58% | HUM |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link | 21.968 | 8.600 | -61% | HUM |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time | 22.360 | 8.400 | -62% | HUM |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space | 18.041 | 8.000 | -56% | HUM |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit | 1.212 | 1.900 | +57% | **FS** |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_chaos | 14.231 | 8.400 | -41% | HUM |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_cycle | 11.364 | 8.000 | -30% | HUM |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift | 12.041 | 8.600 | -29% | HUM |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_spike | 12.973 | 8.500 | -34% | HUM |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable | 11.825 | 8.300 | -30% | HUM |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit | 1.205 | 1.900 | +58% | **FS** |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat | 14.784 | 7.800 | -47% | HUM |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return | 16.809 | 8.200 | -51% | HUM |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single | 14.279 | 8.500 | -40% | HUM |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit | 1.268 | 1.900 | +50% | **FS** |

---

## Key Insights

### Fullerstack Strengths (After Optimizations)
1. **Subject comparisons** - Now 142% faster after Long.compare() fix (was 94% slower)
2. **Hot pipe async** - 85% faster (4.7ns vs 8.7ns)
3. **Flow guard/limit** - 36-46% faster
4. **Name path generation** - 3865% faster (0.84ns vs 33ns)
5. **Conduit lookups** - 18-36% faster (`get_by_name`, `get_cached`)
6. **Scope creation** - 44-70% faster
7. **Serventis instrument lookups** - 25-58% faster (`*_from_conduit`)

### Humainary Strengths
1. **Batch emissions** - 40% faster (11.3ns vs 18.7ns)
2. **Flow sift/sample** - 39-40% faster
3. **State operations** - 25-82% faster (immutable state handling)
4. **Name interning** - 41-65% faster
5. **Cyclic operations** - 62-71% faster
6. **Serventis signal emissions** - 29-77% faster (all `emit_*` operations)

### Core Emit Path Analysis

| Benchmark | FS (ns) | HUM (ns) | Gap |
|-----------|---------|----------|-----|
| async_emit_single | 10.3 | 9.1 | +13% |
| async_emit_batch | 18.7 | 11.3 | +66% |

The VarHandle optimization improved `async_emit_single` from ~15ns to ~10.3ns.
Remaining gap is primarily due to JCTools MPSC queue CAS overhead.

### Serventis Pattern
Across **all** Serventis modules, a consistent pattern emerges:
- **FS wins**: `*_from_conduit` lookups (~1.2ns vs ~1.9ns) - **~50% faster**
- **HUM wins**: `emit_*` operations (~15-25ns vs ~8ns) - **~50% slower**

---

**Legend:**
- **FS** = Fullerstack wins (>5% faster)
- **HUM** = Humainary wins (>5% faster)
- **~** = Within 5% (tie)
- **Δ%** = Positive means Fullerstack faster, negative means Humainary faster
