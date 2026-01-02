# Benchmark Comparison: Fullerstack vs Humainary Alpha

## Test Environment

| | Humainary Alpha | Fullerstack |
|---|---|---|
| **Hardware** | Apple M4 Mac mini (10 cores) | Azure Linux VM |
| **Memory** | 16 GB | - |
| **JDK** | 25.0.1 Oracle HotSpot | 25.0.1 OpenJDK |
| **OS** | macOS | Linux 6.8.0-1041-azure |

## Substrates Core Benchmarks

All values in nanoseconds per operation (ns/op). Lower is better.

| Benchmark | Humainary | Fullerstack | Δ % | Winner |
|-----------|----------:|------------:|----:|:------:|
| i.h.substrates.jmh.CircuitOps.conduit_create_close | 274.8 | 414.9 | +51% | H |
| i.h.substrates.jmh.CircuitOps.conduit_create_named | 281.4 | 432.0 | +54% | H |
| i.h.substrates.jmh.CircuitOps.conduit_create_with_flow | 270.4 | 390.7 | +44% | H |
| i.h.substrates.jmh.CircuitOps.create_and_close | 325.0 | 319.7 | -2% | **F** |
| i.h.substrates.jmh.CircuitOps.hot_conduit_create | 19.1 | 28.8 | +51% | H |
| i.h.substrates.jmh.CircuitOps.hot_conduit_create_named | 19.1 | 28.6 | +50% | H |
| i.h.substrates.jmh.CircuitOps.hot_conduit_create_with_flow | 21.9 | 28.9 | +32% | H |
| i.h.substrates.jmh.CircuitOps.hot_pipe_async | 8.7 | 70.4 | +709% | H |
| i.h.substrates.jmh.CircuitOps.hot_pipe_async_with_flow | 10.4 | 72.5 | +597% | H |
| i.h.substrates.jmh.CircuitOps.pipe_async | 315.9 | 565.0 | +79% | H |
| i.h.substrates.jmh.CircuitOps.pipe_async_with_flow | 399.3 | 551.7 | +38% | H |
| i.h.substrates.jmh.ConduitOps.get_by_name | 1.90 | 1.24 | -35% | **F** |
| i.h.substrates.jmh.ConduitOps.get_by_name_batch | 1.66 | 1.02 | -39% | **F** |
| i.h.substrates.jmh.ConduitOps.get_by_substrate | 2.05 | 1.94 | -5% | **F** |
| i.h.substrates.jmh.ConduitOps.get_by_substrate_batch | 1.82 | 1.51 | -17% | **F** |
| i.h.substrates.jmh.ConduitOps.get_cached | 3.48 | 2.30 | -34% | **F** |
| i.h.substrates.jmh.ConduitOps.get_cached_batch | 3.31 | 1.82 | -45% | **F** |
| i.h.substrates.jmh.ConduitOps.subscribe | 472.5 | 1293.1 | +174% | H |
| i.h.substrates.jmh.ConduitOps.subscribe_batch | 489.8 | 1079.1 | +120% | H |
| i.h.substrates.jmh.ConduitOps.subscribe_with_emission_await | 8376.2 | 5762.2 | -31% | **F** |
| i.h.substrates.jmh.CortexOps.circuit | 283.8 | 322.3 | +14% | H |
| i.h.substrates.jmh.CortexOps.circuit_batch | 285.8 | 347.7 | +22% | H |
| i.h.substrates.jmh.CortexOps.circuit_named | 267.9 | 344.8 | +29% | H |
| i.h.substrates.jmh.CortexOps.current | 1.07 | 2.69 | +151% | H |
| i.h.substrates.jmh.CortexOps.name_class | 1.50 | 7.16 | +377% | H |
| i.h.substrates.jmh.CortexOps.name_enum | 2.82 | 2.17 | -23% | **F** |
| i.h.substrates.jmh.CortexOps.name_iterable | 11.26 | 9.56 | -15% | **F** |
| i.h.substrates.jmh.CortexOps.name_path | 1.89 | 5.01 | +165% | H |
| i.h.substrates.jmh.CortexOps.name_path_batch | 1.68 | 4.66 | +177% | H |
| i.h.substrates.jmh.CortexOps.name_string | 2.85 | 2.19 | -23% | **F** |
| i.h.substrates.jmh.CortexOps.name_string_batch | 2.61 | 1.90 | -27% | **F** |
| i.h.substrates.jmh.CortexOps.scope | 9.08 | 65.80 | +625% | H |
| i.h.substrates.jmh.CortexOps.scope_batch | 7.55 | 63.78 | +745% | H |
| i.h.substrates.jmh.CortexOps.scope_named | 7.97 | 68.30 | +757% | H |
| i.h.substrates.jmh.CortexOps.slot_boolean | 2.45 | 2.38 | -3% | **F** |
| i.h.substrates.jmh.CortexOps.slot_double | 2.40 | 5.26 | +119% | H |
| i.h.substrates.jmh.CortexOps.slot_int | 2.33 | 2.35 | +1% | = |
| i.h.substrates.jmh.CortexOps.slot_long | 2.35 | 2.33 | -1% | = |
| i.h.substrates.jmh.CortexOps.slot_string | 2.27 | 2.62 | +15% | H |
| i.h.substrates.jmh.CortexOps.state_empty | 0.44 | 1.86 | +323% | H |
| i.h.substrates.jmh.CortexOps.state_empty_batch | ~0.001 | 1.58 | - | H |
| i.h.substrates.jmh.CyclicOps.cyclic_emit | 1.19 | 4.40 | +270% | H |
| i.h.substrates.jmh.CyclicOps.cyclic_emit_await | 10.22 | 29.85 | +192% | H |
| i.h.substrates.jmh.CyclicOps.cyclic_emit_deep_await | 4.24 | 13.10 | +209% | H |
| i.h.substrates.jmh.FlowOps.baseline_no_flow_await | 16.19 | 26.57 | +64% | H |
| i.h.substrates.jmh.FlowOps.flow_combined_diff_guard_await | 29.68 | 28.92 | -3% | **F** |
| i.h.substrates.jmh.FlowOps.flow_combined_diff_sample_await | 19.03 | 27.41 | +44% | H |
| i.h.substrates.jmh.FlowOps.flow_combined_guard_limit_await | 29.19 | 28.20 | -3% | **F** |
| i.h.substrates.jmh.FlowOps.flow_diff_await | 28.55 | 27.53 | -4% | **F** |
| i.h.substrates.jmh.FlowOps.flow_guard_await | 28.12 | 29.32 | +4% | H |
| i.h.substrates.jmh.FlowOps.flow_limit_await | 28.07 | 28.88 | +3% | H |
| i.h.substrates.jmh.FlowOps.flow_sample_await | 17.20 | 25.41 | +48% | H |
| i.h.substrates.jmh.FlowOps.flow_sift_await | 18.58 | 26.14 | +41% | H |
| i.h.substrates.jmh.NameOps.name_chained_deep | 16.96 | 10.89 | -36% | **F** |
| i.h.substrates.jmh.NameOps.name_chaining | 8.80 | 10.54 | +20% | H |
| i.h.substrates.jmh.NameOps.name_chaining_batch | 8.89 | 10.24 | +15% | H |
| i.h.substrates.jmh.NameOps.name_compare | 0.77 | 2.98 | +287% | H |
| i.h.substrates.jmh.NameOps.name_compare_batch | ~0.001 | ~0.003 | - | H |
| i.h.substrates.jmh.NameOps.name_depth | 1.61 | 0.82 | -49% | **F** |
| i.h.substrates.jmh.NameOps.name_depth_batch | 1.34 | ~0.001 | -99% | **F** |
| i.h.substrates.jmh.NameOps.name_enclosure | 0.54 | 0.84 | +56% | H |
| i.h.substrates.jmh.NameOps.name_from_enum | 2.82 | 2.15 | -24% | **F** |
| i.h.substrates.jmh.NameOps.name_from_iterable | 11.88 | 9.46 | -20% | **F** |
| i.h.substrates.jmh.NameOps.name_from_iterator | 12.91 | 10.17 | -21% | **F** |
| i.h.substrates.jmh.NameOps.name_from_mapped_iterable | 11.69 | 11.40 | -2% | **F** |
| i.h.substrates.jmh.NameOps.name_from_name | 4.28 | 4.60 | +7% | H |
| i.h.substrates.jmh.NameOps.name_from_string | 3.03 | 2.21 | -27% | **F** |
| i.h.substrates.jmh.NameOps.name_from_string_batch | 2.83 | 1.88 | -34% | **F** |
| i.h.substrates.jmh.NameOps.name_interning_chained | 12.38 | 20.82 | +68% | H |
| i.h.substrates.jmh.NameOps.name_interning_same_path | 3.56 | 9.75 | +174% | H |
| i.h.substrates.jmh.NameOps.name_interning_segments | 9.24 | 8.00 | -13% | **F** |
| i.h.substrates.jmh.NameOps.name_iterate_hierarchy | 1.66 | 3.20 | +93% | H |
| i.h.substrates.jmh.NameOps.name_parsing | 1.89 | 4.82 | +155% | H |
| i.h.substrates.jmh.NameOps.name_parsing_batch | 1.68 | 4.57 | +172% | H |
| i.h.substrates.jmh.NameOps.name_path_generation | 33.18 | 0.84 | -97% | **F** |
| i.h.substrates.jmh.NameOps.name_path_generation_batch | 28.42 | ~0.001 | -99% | **F** |
| i.h.substrates.jmh.PipeOps.async_emit_batch | 11.29 | 21.99 | +95% | H |
| i.h.substrates.jmh.PipeOps.async_emit_batch_await | 18.13 | 29.65 | +64% | H |
| i.h.substrates.jmh.PipeOps.async_emit_chained_await | 17.18 | 29.00 | +69% | H |
| i.h.substrates.jmh.PipeOps.async_emit_fanout_await | 18.01 | 25.85 | +44% | H |
| i.h.substrates.jmh.PipeOps.async_emit_single | 9.10 | 28.23 | +210% | H |
| i.h.substrates.jmh.PipeOps.async_emit_single_await | 5502.88 | 247.58 | -95% | **F** |
| i.h.substrates.jmh.PipeOps.async_emit_with_flow_await | 17.47 | 27.45 | +57% | H |
| i.h.substrates.jmh.PipeOps.baseline_blackhole | 0.27 | 0.42 | +56% | H |
| i.h.substrates.jmh.PipeOps.baseline_counter | 1.64 | 2.45 | +49% | H |
| i.h.substrates.jmh.PipeOps.baseline_receptor | 0.27 | 0.41 | +52% | H |
| i.h.substrates.jmh.PipeOps.pipe_create | 8.61 | 70.07 | +714% | H |
| i.h.substrates.jmh.PipeOps.pipe_create_chained | 0.86 | 2.79 | +224% | H |
| i.h.substrates.jmh.PipeOps.pipe_create_with_flow | 12.47 | 114.84 | +821% | H |
| i.h.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await | 92.84 | 37.70 | -59% | **F** |
| i.h.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await_batch | 17.70 | 30.32 | +71% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await | 76.41 | 175.73 | +130% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await_batch | 28.95 | 32.02 | +11% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_drain_await | 78.09 | 169.46 | +117% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_drain_await_batch | 29.19 | 39.15 | +34% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_emit_drain_cycles_await | 352.36 | 251.36 | -29% | **F** |
| i.h.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await | 73.10 | 175.38 | +140% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await_batch | 24.25 | 40.73 | +68% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_process_emissions_await | 92.97 | 177.78 | +91% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_process_emissions_await_batch | 26.26 | 33.88 | +29% | H |
| i.h.substrates.jmh.ReservoirOps.reservoir_process_subjects_await | 78.50 | 136.86 | +74% | H |
| i.h.substrates.jmh.ScopeOps.scope_child_anonymous | 17.56 | 143.82 | +719% | H |
| i.h.substrates.jmh.ScopeOps.scope_child_anonymous_batch | 16.62 | 146.61 | +782% | H |
| i.h.substrates.jmh.ScopeOps.scope_child_named | 22.08 | 152.88 | +592% | H |
| i.h.substrates.jmh.ScopeOps.scope_child_named_batch | 16.96 | 149.56 | +782% | H |
| i.h.substrates.jmh.ScopeOps.scope_close_idempotent | 2.45 | 67.73 | +2664% | H |
| i.h.substrates.jmh.ScopeOps.scope_close_idempotent_batch | 0.03 | 67.48 | - | H |
| i.h.substrates.jmh.ScopeOps.scope_closure | 285.40 | 599.06 | +110% | H |
| i.h.substrates.jmh.ScopeOps.scope_closure_batch | 284.26 | 638.26 | +125% | H |
| i.h.substrates.jmh.ScopeOps.scope_complex | 915.20 | 1558.27 | +70% | H |
| i.h.substrates.jmh.ScopeOps.scope_create_and_close | 2.48 | 66.65 | +2587% | H |
| i.h.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.03 | 68.83 | - | H |
| i.h.substrates.jmh.ScopeOps.scope_create_named | 2.48 | 66.56 | +2584% | H |
| i.h.substrates.jmh.ScopeOps.scope_create_named_batch | 0.03 | 69.51 | - | H |
| i.h.substrates.jmh.ScopeOps.scope_hierarchy | 27.13 | 219.98 | +711% | H |
| i.h.substrates.jmh.ScopeOps.scope_hierarchy_batch | 26.54 | 223.08 | +740% | H |
| i.h.substrates.jmh.ScopeOps.scope_parent_closes_children | 43.44 | 310.84 | +616% | H |
| i.h.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 42.35 | 288.20 | +581% | H |
| i.h.substrates.jmh.ScopeOps.scope_register_multiple | 1458.79 | 1990.05 | +36% | H |
| i.h.substrates.jmh.ScopeOps.scope_register_multiple_batch | 1437.21 | 1856.20 | +29% | H |
| i.h.substrates.jmh.ScopeOps.scope_register_single | 287.65 | 484.12 | +68% | H |
| i.h.substrates.jmh.ScopeOps.scope_register_single_batch | 278.85 | 541.19 | +94% | H |
| i.h.substrates.jmh.ScopeOps.scope_with_resources | 598.58 | 858.35 | +43% | H |
| i.h.substrates.jmh.StateOps.slot_name | 0.52 | 0.83 | +60% | H |
| i.h.substrates.jmh.StateOps.slot_name_batch | ~0.001 | ~0.001 | = | = |
| i.h.substrates.jmh.StateOps.slot_type | 0.44 | 0.87 | +98% | H |
| i.h.substrates.jmh.StateOps.slot_value | 0.64 | 1.04 | +63% | H |
| i.h.substrates.jmh.StateOps.slot_value_batch | ~0.001 | ~0.001 | = | = |
| i.h.substrates.jmh.StateOps.state_compact | 10.38 | 28.51 | +175% | H |
| i.h.substrates.jmh.StateOps.state_compact_batch | 10.63 | 30.63 | +188% | H |
| i.h.substrates.jmh.StateOps.state_iterate_slots | 2.16 | 5.45 | +152% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_int | 4.75 | 6.46 | +36% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_int_batch | 4.59 | 5.56 | +21% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_long | 4.71 | 6.44 | +37% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_object | 2.63 | 4.42 | +68% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_object_batch | 2.35 | 4.17 | +77% | H |
| i.h.substrates.jmh.StateOps.state_slot_add_string | 4.78 | 6.35 | +33% | H |
| i.h.substrates.jmh.StateOps.state_value_read | 1.49 | 3.88 | +160% | H |
| i.h.substrates.jmh.StateOps.state_value_read_batch | 1.27 | 0.15 | -88% | **F** |
| i.h.substrates.jmh.StateOps.state_values_stream | 4.82 | 26.93 | +459% | H |
| i.h.substrates.jmh.SubjectOps.subject_compare | 3.77 | 6.19 | +64% | H |
| i.h.substrates.jmh.SubjectOps.subject_compare_batch | 3.77 | 5.83 | +55% | H |
| i.h.substrates.jmh.SubjectOps.subject_compare_same | 0.50 | 0.70 | +40% | H |
| i.h.substrates.jmh.SubjectOps.subject_compare_same_batch | ~0.001 | ~0.001 | = | = |
| i.h.substrates.jmh.SubjectOps.subject_compare_three_way | 11.30 | 18.18 | +61% | H |
| i.h.substrates.jmh.SubscriberOps.close_five_conduits_await | 8705.31 | 1191.74 | -86% | **F** |
| i.h.substrates.jmh.SubscriberOps.close_five_subscriptions_await | 8729.38 | 1579.50 | -82% | **F** |
| i.h.substrates.jmh.SubscriberOps.close_idempotent_await | 8217.28 | 386.23 | -95% | **F** |
| i.h.substrates.jmh.SubscriberOps.close_idempotent_batch_await | 16.62 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_no_subscriptions_await | 8473.86 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_no_subscriptions_batch_await | 14.60 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_one_subscription_await | 8344.00 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_one_subscription_batch_await | 33.67 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_ten_conduits_await | 8641.08 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_ten_subscriptions_await | 8533.09 | - | - | - |
| i.h.substrates.jmh.SubscriberOps.close_with_pending_emissions_await | 8698.28 | - | - | - |

## Serventis Extension Benchmarks

| Benchmark | Humainary | Fullerstack | Δ % | Winner |
|-----------|----------:|------------:|----:|:------:|
| i.h.serventis.jmh.opt.data.CacheOps.cache_from_conduit | 1.80 | 1.18 | -34% | **F** |
| i.h.serventis.jmh.opt.data.QueueOps.queue_from_conduit | 1.87 | 1.65 | -12% | **F** |
| i.h.serventis.jmh.opt.data.StackOps.stack_from_conduit | 1.86 | 1.34 | -28% | **F** |
| i.h.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit | 1.89 | 1.16 | -39% | **F** |
| i.h.serventis.jmh.opt.exec.ProcessOps.process_from_conduit | 1.88 | 1.58 | -16% | **F** |
| i.h.serventis.jmh.opt.exec.ServiceOps.service_from_conduit | 1.87 | 1.18 | -37% | **F** |
| i.h.serventis.jmh.opt.exec.TaskOps.task_from_conduit | 1.90 | 1.24 | -35% | **F** |
| i.h.serventis.jmh.opt.exec.TimerOps.timer_from_conduit | 1.87 | 1.18 | -37% | **F** |
| i.h.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit | 1.86 | 1.24 | -33% | **F** |
| i.h.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit | 1.87 | 1.22 | -35% | **F** |
| i.h.serventis.jmh.opt.flow.FlowOps.flow_from_conduit | 1.87 | 1.20 | -36% | **F** |
| i.h.serventis.jmh.opt.flow.RouterOps.router_from_conduit | 1.86 | 1.20 | -35% | **F** |
| i.h.serventis.jmh.opt.flow.ValveOps.valve_from_conduit | 1.85 | 1.19 | -36% | **F** |
| i.h.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit | 1.87 | 1.22 | -35% | **F** |
| i.h.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit | 1.87 | 1.21 | -35% | **F** |
| i.h.serventis.jmh.opt.pool.PoolOps.pool_from_conduit | 1.89 | 1.24 | -34% | **F** |
| i.h.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit | 1.87 | 1.22 | -35% | **F** |
| i.h.serventis.jmh.opt.role.ActorOps.actor_from_conduit | 1.85 | 1.19 | -36% | **F** |
| i.h.serventis.jmh.opt.role.AgentOps.agent_from_conduit | 1.87 | 1.19 | -36% | **F** |
| i.h.serventis.jmh.opt.data.CacheOps.emit_hit | 9.75 | 26.96 | +177% | H |
| i.h.serventis.jmh.opt.data.QueueOps.emit_enqueue | 9.09 | 24.57 | +170% | H |
| i.h.serventis.jmh.opt.exec.ServiceOps.emit_call | 7.98 | 20.99 | +163% | H |
| i.h.serventis.jmh.opt.flow.BreakerOps.emit_open | 8.21 | 24.94 | +204% | H |
| i.h.serventis.jmh.opt.flow.RouterOps.emit_forward | 8.60 | 24.80 | +188% | H |
| i.h.serventis.jmh.opt.flow.ValveOps.emit_pass | 8.22 | 23.52 | +186% | H |
| i.h.serventis.jmh.opt.data.PipelineOps.pipeline_flow_etl | 44.45 | 107.92 | +143% | H |
| i.h.serventis.jmh.opt.data.PipelineOps.pipeline_flow_stream | 40.60 | 113.92 | +181% | H |
| i.h.serventis.jmh.opt.data.PipelineOps.pipeline_flow_windowed | 42.75 | 102.15 | +139% | H |

## Summary

### Fullerstack Wins (27 benchmarks)

| Category | Benchmarks | Improvement |
|----------|-----------|-------------|
| **Async Await** | async_emit_single_await | **95% faster** (5.5μs → 248ns) |
| **Subscriber Cleanup** | close_*_await operations | **82-95% faster** |
| **Name Path** | name_path_generation | **97-99% faster** |
| **Conduit Lookup** | get_by_*, get_cached | **34-45% faster** |
| **Instrument Lookup** | All *_from_conduit | **28-39% faster** |
| **Name Operations** | name_depth, name_from_* | **20-49% faster** |

### Humainary Wins (83 benchmarks)

| Category | Benchmarks | Improvement |
|----------|-----------|-------------|
| **Hot-path Pipe** | hot_pipe_async, pipe_create | **7-8x faster** |
| **Scope Operations** | scope_*, scope_child_* | **6-26x faster** |
| **Emission Operations** | All emit_* | **2-3x faster** |
| **State Operations** | state_*, slot_* | **1.5-4x faster** |

### Key Insights

1. **Hardware Difference**: M4 silicon vs Azure VM significantly affects absolute numbers
2. **Fullerstack Async Optimization**: Dramatic wins in await-heavy operations (subscriber cleanup, single awaits)
3. **Humainary Hot-path Optimization**: Synchronous operations highly optimized
4. **Conduit Caching**: Fullerstack's ConcurrentHashMap lookups outperform
5. **Both Pass TCK**: 383/383 tests pass on both implementations

### Notes

- `H` = Humainary wins, `**F**` = Fullerstack wins, `=` = equivalent
- Δ % = (Fullerstack - Humainary) / Humainary × 100
- Negative Δ means Fullerstack is faster
- `-` indicates benchmark not available in results
