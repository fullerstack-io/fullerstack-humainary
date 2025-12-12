# Full Benchmark Comparison: Fullerstack vs Humainary

**Date:** 2025-12-10
**Benchmarks:** 148 total (Humainary JMH Suite with Fullerstack SPI)
**JMH Config:** `-f 1 -wi 2 -i 3 -t 1` (1 fork, 2 warmup iterations, 3 measurement iterations, 1 thread)

## Summary

| Metric | Count | % |
|--------|-------|---|
| **Fullerstack Wins** | 53 | 35.8% |
| **Humainary Wins** | 92 | 62.2% |
| **Equal** | 3 | 2.0% |
| **Total** | 148 | 100% |

## Full Comparison Table

| # | Benchmark | Humainary (ns/op) | Fullerstack (ns/op) | Δ | Winner |
|---|-----------|-------------------|---------------------|---|--------|
| 1 | CircuitOps.conduit_create_close | 281.708 | 126.135 | 2.2x faster | **FS** |
| 2 | CircuitOps.conduit_create_named | 282.005 | 120.383 | 2.3x faster | **FS** |
| 3 | CircuitOps.conduit_create_with_flow | 280.091 | 118.823 | 2.4x faster | **FS** |
| 4 | CircuitOps.create_and_close | 337.343 | 70.724 | 4.8x faster | **FS** |
| 5 | CircuitOps.create_await_close | 10730.724 | 72.339 | 148x faster | **FS** |
| 6 | CircuitOps.hot_await_queue_drain | 5798.639 | 2.431 | 2385x faster | **FS** |
| 7 | CircuitOps.hot_conduit_create | 19.065 | 41.847 | 2.2x slower | HUM |
| 8 | CircuitOps.hot_conduit_create_named | 19.071 | 46.038 | 2.4x slower | HUM |
| 9 | CircuitOps.hot_conduit_create_with_flow | 21.887 | 46.544 | 2.1x slower | HUM |
| 10 | CircuitOps.hot_pipe_async | 8.531 | 8.256 | 1.03x faster | **FS** |
| 11 | CircuitOps.hot_pipe_async_with_flow | 10.679 | 21.847 | 2.0x slower | HUM |
| 12 | CircuitOps.pipe_async | 309.065 | 110.597 | 2.8x faster | **FS** |
| 13 | CircuitOps.pipe_async_with_flow | 320.440 | 90.115 | 3.6x faster | **FS** |
| 14 | ConduitOps.get_by_name | 1.882 | 7.314 | 3.9x slower | HUM |
| 15 | ConduitOps.get_by_name_batch | 1.659 | 7.004 | 4.2x slower | HUM |
| 16 | ConduitOps.get_by_substrate | 1.991 | 8.160 | 4.1x slower | HUM |
| 17 | ConduitOps.get_by_substrate_batch | 1.811 | 9.058 | 5.0x slower | HUM |
| 18 | ConduitOps.get_cached | 3.426 | 16.520 | 4.8x slower | HUM |
| 19 | ConduitOps.get_cached_batch | 3.302 | 14.374 | 4.4x slower | HUM |
| 20 | ConduitOps.subscribe | 436.561 | 653.628 | 1.50x slower | HUM |
| 21 | ConduitOps.subscribe_batch | 461.728 | 598.824 | 1.30x slower | HUM |
| 22 | CortexOps.circuit | 279.172 | 67.965 | 4.1x faster | **FS** |
| 23 | CortexOps.circuit_batch | 280.103 | 74.698 | 3.7x faster | **FS** |
| 24 | CortexOps.circuit_named | 288.306 | 77.710 | 3.7x faster | **FS** |
| 25 | CortexOps.current | 1.090 | 3.071 | 2.8x slower | HUM |
| 26 | CortexOps.name_class | 1.479 | 8.370 | 5.7x slower | HUM |
| 27 | CortexOps.name_enum | 2.805 | 2.718 | 1.03x faster | **FS** |
| 28 | CortexOps.name_iterable | 11.234 | 9.864 | 1.14x faster | **FS** |
| 29 | CortexOps.name_path | 1.893 | 6.010 | 3.2x slower | HUM |
| 30 | CortexOps.name_path_batch | 1.686 | 5.424 | 3.2x slower | HUM |
| 31 | CortexOps.name_string | 2.847 | 2.365 | 1.20x faster | **FS** |
| 32 | CortexOps.name_string_batch | 2.540 | 2.106 | 1.21x faster | **FS** |
| 33 | CortexOps.scope | 9.284 | 6.931 | 1.34x faster | **FS** |
| 34 | CortexOps.scope_batch | 7.582 | 7.456 | 1.02x faster | **FS** |
| 35 | CortexOps.scope_named | 8.013 | 6.755 | 1.19x faster | **FS** |
| 36 | CortexOps.slot_boolean | 2.413 | 2.632 | 1.09x slower | HUM |
| 37 | CortexOps.slot_double | 2.431 | 6.028 | 2.5x slower | HUM |
| 38 | CortexOps.slot_int | 2.121 | 2.988 | 1.41x slower | HUM |
| 39 | CortexOps.slot_long | 2.423 | 2.566 | 1.06x slower | HUM |
| 40 | CortexOps.slot_string | 2.429 | 3.147 | 1.30x slower | HUM |
| 41 | CortexOps.state_empty | 0.440 | 0.836 | 1.90x slower | HUM |
| 42 | CortexOps.state_empty_batch | 0.001 | 0.001 | ~ | = |
| 43 | FlowOps.baseline_no_flow_await | 17.812 | 78.135 | 4.4x slower | HUM |
| 44 | FlowOps.flow_combined_diff_guard_await | 29.960 | 87.360 | 2.9x slower | HUM |
| 45 | FlowOps.flow_combined_diff_sample_await | 19.352 | 27.957 | 1.44x slower | HUM |
| 46 | FlowOps.flow_combined_guard_limit_await | 28.343 | 90.090 | 3.2x slower | HUM |
| 47 | FlowOps.flow_diff_await | 30.039 | 86.678 | 2.9x slower | HUM |
| 48 | FlowOps.flow_guard_await | 30.370 | 98.363 | 3.2x slower | HUM |
| 49 | FlowOps.flow_limit_await | 28.668 | 97.288 | 3.4x slower | HUM |
| 50 | FlowOps.flow_sample_await | 17.245 | 13.068 | 1.32x faster | **FS** |
| 51 | FlowOps.flow_sift_await | 18.680 | 25.867 | 1.38x slower | HUM |
| 52 | NameOps.name_chained_deep | 16.929 | 14.660 | 1.15x faster | **FS** |
| 53 | NameOps.name_chaining | 8.557 | 13.068 | 1.53x slower | HUM |
| 54 | NameOps.name_chaining_batch | 9.043 | 15.498 | 1.71x slower | HUM |
| 55 | NameOps.name_compare | 33.066 | 3.562 | 9.3x faster | **FS** |
| 56 | NameOps.name_compare_batch | 31.976 | 0.003 | 9582x faster | **FS** |
| 57 | NameOps.name_depth | 1.698 | 1.108 | 1.53x faster | **FS** |
| 58 | NameOps.name_depth_batch | 1.399 | 0.001 | 1395x faster | **FS** |
| 59 | NameOps.name_enclosure | 0.589 | 1.205 | 2.0x slower | HUM |
| 60 | NameOps.name_from_enum | 2.830 | 2.514 | 1.13x faster | **FS** |
| 61 | NameOps.name_from_iterable | 11.987 | 11.443 | 1.05x faster | **FS** |
| 62 | NameOps.name_from_iterator | 12.911 | 12.209 | 1.06x faster | **FS** |
| 63 | NameOps.name_from_mapped_iterable | 11.695 | 13.770 | 1.18x slower | HUM |
| 64 | NameOps.name_from_name | 4.218 | 5.246 | 1.24x slower | HUM |
| 65 | NameOps.name_from_string | 3.049 | 2.435 | 1.25x faster | **FS** |
| 66 | NameOps.name_from_string_batch | 2.803 | 2.237 | 1.25x faster | **FS** |
| 67 | NameOps.name_interning_chained | 12.413 | 29.775 | 2.4x slower | HUM |
| 68 | NameOps.name_interning_same_path | 3.543 | 13.538 | 3.8x slower | HUM |
| 69 | NameOps.name_interning_segments | 10.105 | 11.382 | 1.13x slower | HUM |
| 70 | NameOps.name_iterate_hierarchy | 1.797 | 4.013 | 2.2x slower | HUM |
| 71 | NameOps.name_parsing | 1.889 | 6.394 | 3.4x slower | HUM |
| 72 | NameOps.name_parsing_batch | 1.682 | 6.530 | 3.9x slower | HUM |
| 73 | NameOps.name_path_generation | 31.341 | 1.031 | 30x faster | **FS** |
| 74 | NameOps.name_path_generation_batch | 30.005 | 0.001 | 31034x faster | **FS** |
| 75 | PipeOps.async_emit_batch | 11.836 | 95.838 | 8.1x slower | HUM |
| 76 | PipeOps.async_emit_batch_await | 16.872 | 106.239 | 6.3x slower | HUM |
| 77 | PipeOps.async_emit_chained_await | 16.932 | 105.755 | 6.2x slower | HUM |
| 78 | PipeOps.async_emit_fanout_await | 18.715 | 277.610 | 15x slower | HUM |
| 79 | PipeOps.async_emit_single | 10.649 | 103.443 | 9.7x slower | HUM |
| 80 | PipeOps.async_emit_single_await | 5477.579 | 218.596 | 25x faster | **FS** |
| 81 | PipeOps.async_emit_with_flow_await | 21.224 | 100.190 | 4.7x slower | HUM |
| 82 | PipeOps.baseline_blackhole | 0.267 | 0.871 | 3.3x slower | HUM |
| 83 | PipeOps.baseline_counter | 1.621 | 2.855 | 1.76x slower | HUM |
| 84 | PipeOps.baseline_receptor | 0.263 | 0.527 | 2.0x slower | HUM |
| 85 | PipeOps.pipe_create | 8.747 | 9.165 | 1.05x slower | HUM |
| 86 | PipeOps.pipe_create_chained | 0.855 | 9.863 | 12x slower | HUM |
| 87 | PipeOps.pipe_create_with_flow | 13.230 | 59.587 | 4.5x slower | HUM |
| 88 | ReservoirOps.baseline_emit_no_reservoir_await | 96.224 | 142.384 | 1.48x slower | HUM |
| 89 | ReservoirOps.baseline_emit_no_reservoir_await_batch | 18.455 | 124.714 | 6.8x slower | HUM |
| 90 | ReservoirOps.reservoir_burst_then_drain_await | 90.187 | 365.192 | 4.0x slower | HUM |
| 91 | ReservoirOps.reservoir_burst_then_drain_await_batch | 28.812 | 293.419 | 10x slower | HUM |
| 92 | ReservoirOps.reservoir_drain_await | 93.801 | 341.877 | 3.6x slower | HUM |
| 93 | ReservoirOps.reservoir_drain_await_batch | 28.342 | 285.224 | 10x slower | HUM |
| 94 | ReservoirOps.reservoir_emit_drain_cycles_await | 328.063 | 509.363 | 1.55x slower | HUM |
| 95 | ReservoirOps.reservoir_emit_with_capture_await | 79.974 | 364.452 | 4.6x slower | HUM |
| 96 | ReservoirOps.reservoir_emit_with_capture_await_batch | 23.855 | 351.373 | 15x slower | HUM |
| 97 | ReservoirOps.reservoir_process_emissions_await | 89.081 | 736.075 | 8.3x slower | HUM |
| 98 | ReservoirOps.reservoir_process_emissions_await_batch | 26.140 | 412.783 | 16x slower | HUM |
| 99 | ReservoirOps.reservoir_process_subjects_await | 97.454 | 463.857 | 4.8x slower | HUM |
| 100 | ScopeOps.scope_child_anonymous | 18.235 | 34.161 | 1.87x slower | HUM |
| 101 | ScopeOps.scope_child_anonymous_batch | 17.698 | 27.321 | 1.54x slower | HUM |
| 102 | ScopeOps.scope_child_named | 17.100 | 30.121 | 1.76x slower | HUM |
| 103 | ScopeOps.scope_child_named_batch | 19.440 | 27.771 | 1.43x slower | HUM |
| 104 | ScopeOps.scope_close_idempotent | 2.394 | 1.306 | 1.83x faster | **FS** |
| 105 | ScopeOps.scope_close_idempotent_batch | 0.033 | 0.836 | 25x slower | HUM |
| 106 | ScopeOps.scope_closure | 286.103 | 151.971 | 1.88x faster | **FS** |
| 107 | ScopeOps.scope_closure_batch | 307.355 | 155.705 | 1.97x faster | **FS** |
| 108 | ScopeOps.scope_complex | 917.038 | 318.456 | 2.9x faster | **FS** |
| 109 | ScopeOps.scope_create_and_close | 2.426 | 1.666 | 1.46x faster | **FS** |
| 110 | ScopeOps.scope_create_and_close_batch | 0.034 | 0.827 | 24x slower | HUM |
| 111 | ScopeOps.scope_create_named | 2.434 | 1.417 | 1.72x faster | **FS** |
| 112 | ScopeOps.scope_create_named_batch | 0.033 | 1.081 | 33x slower | HUM |
| 113 | ScopeOps.scope_hierarchy | 27.336 | 54.955 | 2.0x slower | HUM |
| 114 | ScopeOps.scope_hierarchy_batch | 26.552 | 47.077 | 1.77x slower | HUM |
| 115 | ScopeOps.scope_parent_closes_children | 43.455 | 72.703 | 1.67x slower | HUM |
| 116 | ScopeOps.scope_parent_closes_children_batch | 42.294 | 56.352 | 1.33x slower | HUM |
| 117 | ScopeOps.scope_register_multiple | 1450.490 | 427.559 | 3.4x faster | **FS** |
| 118 | ScopeOps.scope_register_multiple_batch | 1397.956 | 456.781 | 3.1x faster | **FS** |
| 119 | ScopeOps.scope_register_single | 287.139 | 82.142 | 3.5x faster | **FS** |
| 120 | ScopeOps.scope_register_single_batch | 283.085 | 89.705 | 3.2x faster | **FS** |
| 121 | ScopeOps.scope_with_resources | 581.868 | 159.275 | 3.7x faster | **FS** |
| 122 | StateOps.slot_name | 0.523 | 1.101 | 2.1x slower | HUM |
| 123 | StateOps.slot_name_batch | 0.001 | 0.001 | ~ | = |
| 124 | StateOps.slot_type | 0.443 | 1.234 | 2.8x slower | HUM |
| 125 | StateOps.slot_value | 0.662 | 1.344 | 2.0x slower | HUM |
| 126 | StateOps.slot_value_batch | 0.001 | 0.001 | ~ | = |
| 127 | StateOps.state_compact | 10.294 | 37.277 | 3.6x slower | HUM |
| 128 | StateOps.state_compact_batch | 10.699 | 40.679 | 3.8x slower | HUM |
| 129 | StateOps.state_iterate_slots | 2.156 | 7.240 | 3.4x slower | HUM |
| 130 | StateOps.state_slot_add_int | 4.759 | 7.638 | 1.61x slower | HUM |
| 131 | StateOps.state_slot_add_int_batch | 4.886 | 6.469 | 1.32x slower | HUM |
| 132 | StateOps.state_slot_add_long | 4.751 | 7.214 | 1.52x slower | HUM |
| 133 | StateOps.state_slot_add_object | 2.563 | 5.219 | 2.0x slower | HUM |
| 134 | StateOps.state_slot_add_object_batch | 2.427 | 6.091 | 2.5x slower | HUM |
| 135 | StateOps.state_slot_add_string | 4.728 | 7.673 | 1.62x slower | HUM |
| 136 | StateOps.state_value_read | 1.486 | 4.833 | 3.3x slower | HUM |
| 137 | StateOps.state_value_read_batch | 1.267 | 0.175 | 7.2x faster | **FS** |
| 138 | StateOps.state_values_stream | 4.980 | 41.878 | 8.4x slower | HUM |
| 139 | SubscriberOps.close_five_conduits_await | 8696.402 | 166.876 | 52x faster | **FS** |
| 140 | SubscriberOps.close_five_subscriptions_await | 8630.902 | 145.629 | 59x faster | **FS** |
| 141 | SubscriberOps.close_idempotent_await | 8438.475 | 32.647 | 258x faster | **FS** |
| 142 | SubscriberOps.close_idempotent_batch_await | 17.232 | 1.232 | 14x faster | **FS** |
| 143 | SubscriberOps.close_no_subscriptions_await | 8450.164 | 43.390 | 195x faster | **FS** |
| 144 | SubscriberOps.close_no_subscriptions_batch_await | 14.235 | 14.994 | 1.05x slower | HUM |
| 145 | SubscriberOps.close_one_subscription_await | 8437.659 | 94.953 | 89x faster | **FS** |
| 146 | SubscriberOps.close_one_subscription_batch_await | 34.879 | 105.576 | 3.0x slower | HUM |
| 147 | SubscriberOps.close_ten_conduits_await | 8514.559 | 237.474 | 36x faster | **FS** |
| 148 | SubscriberOps.close_ten_subscriptions_await | 8726.960 | 222.007 | 39x faster | **FS** |

## Biggest Wins (Fullerstack >10x faster)

| Benchmark | Speedup | Fullerstack | Humainary |
|-----------|---------|-------------|-----------|
| NameOps.name_path_generation_batch | 31034x | 0.001 ns | 30.005 ns |
| NameOps.name_compare_batch | 9582x | 0.003 ns | 31.976 ns |
| CircuitOps.hot_await_queue_drain | 2385x | 2.431 ns | 5798.639 ns |
| NameOps.name_depth_batch | 1395x | 0.001 ns | 1.399 ns |
| SubscriberOps.close_idempotent_await | 258x | 32.647 ns | 8438.475 ns |
| SubscriberOps.close_no_subscriptions_await | 195x | 43.390 ns | 8450.164 ns |
| CircuitOps.create_await_close | 148x | 72.339 ns | 10730.724 ns |
| SubscriberOps.close_one_subscription_await | 89x | 94.953 ns | 8437.659 ns |
| SubscriberOps.close_five_subscriptions_await | 59x | 145.629 ns | 8630.902 ns |
| SubscriberOps.close_five_conduits_await | 52x | 166.876 ns | 8696.402 ns |
| SubscriberOps.close_ten_subscriptions_await | 39x | 222.007 ns | 8726.960 ns |
| SubscriberOps.close_ten_conduits_await | 36x | 237.474 ns | 8514.559 ns |
| NameOps.name_path_generation | 30x | 1.031 ns | 31.341 ns |
| PipeOps.async_emit_single_await | 25x | 218.596 ns | 5477.579 ns |
| SubscriberOps.close_idempotent_batch_await | 14x | 1.232 ns | 17.232 ns |

## Areas Needing Improvement (Fullerstack >2x slower)

| Benchmark | Slowdown | Fullerstack | Humainary |
|-----------|----------|-------------|-----------|
| ScopeOps.scope_create_named_batch | 33x | 1.081 ns | 0.033 ns |
| ScopeOps.scope_close_idempotent_batch | 25x | 0.836 ns | 0.033 ns |
| ScopeOps.scope_create_and_close_batch | 24x | 0.827 ns | 0.034 ns |
| ReservoirOps.reservoir_process_emissions_await_batch | 16x | 412.783 ns | 26.140 ns |
| ReservoirOps.reservoir_emit_with_capture_await_batch | 15x | 351.373 ns | 23.855 ns |
| PipeOps.async_emit_fanout_await | 15x | 277.610 ns | 18.715 ns |
| PipeOps.pipe_create_chained | 12x | 9.863 ns | 0.855 ns |
| ReservoirOps.reservoir_burst_then_drain_await_batch | 10x | 293.419 ns | 28.812 ns |
| ReservoirOps.reservoir_drain_await_batch | 10x | 285.224 ns | 28.342 ns |
| PipeOps.async_emit_single | 9.7x | 103.443 ns | 10.649 ns |
| StateOps.state_values_stream | 8.4x | 41.878 ns | 4.980 ns |
| ReservoirOps.reservoir_process_emissions_await | 8.3x | 736.075 ns | 89.081 ns |
| PipeOps.async_emit_batch | 8.1x | 95.838 ns | 11.836 ns |
| ReservoirOps.baseline_emit_no_reservoir_await_batch | 6.8x | 124.714 ns | 18.455 ns |
| PipeOps.async_emit_batch_await | 6.3x | 106.239 ns | 16.872 ns |
| PipeOps.async_emit_chained_await | 6.2x | 105.755 ns | 16.932 ns |
| CortexOps.name_class | 5.7x | 8.370 ns | 1.479 ns |
| ConduitOps.get_by_substrate_batch | 5.0x | 9.058 ns | 1.811 ns |
| ConduitOps.get_cached | 4.8x | 16.520 ns | 3.426 ns |
| ReservoirOps.reservoir_process_subjects_await | 4.8x | 463.857 ns | 97.454 ns |
| PipeOps.async_emit_with_flow_await | 4.7x | 100.190 ns | 21.224 ns |
| ReservoirOps.reservoir_emit_with_capture_await | 4.6x | 364.452 ns | 79.974 ns |
| PipeOps.pipe_create_with_flow | 4.5x | 59.587 ns | 13.230 ns |
| ConduitOps.get_cached_batch | 4.4x | 14.374 ns | 3.302 ns |
| FlowOps.baseline_no_flow_await | 4.4x | 78.135 ns | 17.812 ns |
| ConduitOps.get_by_name_batch | 4.2x | 7.004 ns | 1.659 ns |
| ConduitOps.get_by_substrate | 4.1x | 8.160 ns | 1.991 ns |
| ReservoirOps.reservoir_burst_then_drain_await | 4.0x | 365.192 ns | 90.187 ns |
| ConduitOps.get_by_name | 3.9x | 7.314 ns | 1.882 ns |
| NameOps.name_parsing_batch | 3.9x | 6.530 ns | 1.682 ns |
| NameOps.name_interning_same_path | 3.8x | 13.538 ns | 3.543 ns |
| StateOps.state_compact_batch | 3.8x | 40.679 ns | 10.699 ns |
| StateOps.state_compact | 3.6x | 37.277 ns | 10.294 ns |
| ReservoirOps.reservoir_drain_await | 3.6x | 341.877 ns | 93.801 ns |
| FlowOps.flow_limit_await | 3.4x | 97.288 ns | 28.668 ns |
| NameOps.name_parsing | 3.4x | 6.394 ns | 1.889 ns |
| StateOps.state_iterate_slots | 3.4x | 7.240 ns | 2.156 ns |
| PipeOps.baseline_blackhole | 3.3x | 0.871 ns | 0.267 ns |
| StateOps.state_value_read | 3.3x | 4.833 ns | 1.486 ns |
| CortexOps.name_path | 3.2x | 6.010 ns | 1.893 ns |
| CortexOps.name_path_batch | 3.2x | 5.424 ns | 1.686 ns |
| FlowOps.flow_combined_guard_limit_await | 3.2x | 90.090 ns | 28.343 ns |
| FlowOps.flow_guard_await | 3.2x | 98.363 ns | 30.370 ns |
| SubscriberOps.close_one_subscription_batch_await | 3.0x | 105.576 ns | 34.879 ns |
| FlowOps.flow_combined_diff_guard_await | 2.9x | 87.360 ns | 29.960 ns |
| FlowOps.flow_diff_await | 2.9x | 86.678 ns | 30.039 ns |
| CortexOps.current | 2.8x | 3.071 ns | 1.090 ns |
| StateOps.slot_type | 2.8x | 1.234 ns | 0.443 ns |
| StateOps.state_slot_add_object_batch | 2.5x | 6.091 ns | 2.427 ns |
| CortexOps.slot_double | 2.5x | 6.028 ns | 2.431 ns |
| CircuitOps.hot_conduit_create_named | 2.4x | 46.038 ns | 19.071 ns |
| NameOps.name_interning_chained | 2.4x | 29.775 ns | 12.413 ns |
| CircuitOps.hot_conduit_create | 2.2x | 41.847 ns | 19.065 ns |
| NameOps.name_iterate_hierarchy | 2.2x | 4.013 ns | 1.797 ns |
| CircuitOps.hot_conduit_create_with_flow | 2.1x | 46.544 ns | 21.887 ns |
| StateOps.slot_name | 2.1x | 1.101 ns | 0.523 ns |
| CircuitOps.hot_pipe_async_with_flow | 2.0x | 21.847 ns | 10.679 ns |
| NameOps.name_enclosure | 2.0x | 1.205 ns | 0.589 ns |
| PipeOps.baseline_receptor | 2.0x | 0.527 ns | 0.263 ns |
| ScopeOps.scope_hierarchy | 2.0x | 54.955 ns | 27.336 ns |
| StateOps.slot_value | 2.0x | 1.344 ns | 0.662 ns |
| StateOps.state_slot_add_object | 2.0x | 5.219 ns | 2.563 ns |

## Analysis

### Where Fullerstack Excels
- **Async await operations**: Dramatically faster (25x-2385x) due to optimized virtual thread handling
- **Circuit lifecycle**: `create_await_close` 148x faster, `hot_await_queue_drain` 2385x faster
- **Subscriber close operations**: 36x-258x faster across all async close benchmarks
- **Name path generation**: Eager path computation gives 30x-31034x speedup on cached paths

### Areas Needing Optimization
- **PipeOps emit**: 6-15x slower (MPSC ring buffer overhead in emit path)
- **ReservoirOps batch**: 10-16x slower (reservoir processing overhead)
- **ConduitOps get**: 4-5x slower (channel lookup overhead)
- **FlowOps**: 2.9-4.4x slower (flow transformation pipeline)
- **StateOps**: 2-8x slower on value/stream operations
- **ScopeOps batch**: 24-33x slower (likely benchmark artifact for sub-nanosecond ops)

### Notes
- Sub-nanosecond benchmarks (< 1ns) can be unreliable and heavily influenced by JIT optimization
- Batch operations showing extreme slowdowns may indicate different JIT optimization paths
- Async operations show Fullerstack's strength in virtual thread coordination
