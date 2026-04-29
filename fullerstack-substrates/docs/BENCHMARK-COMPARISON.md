# Benchmark Comparison: Fullerstack vs Humainary Substrates

**Substrates/Serventis 2.3.0** · `io.fullerstack:fullerstack-substrates:2.3.0-RC1`

Full pre-2.3 → post-audit comparison for every JMH benchmark. Measured on a Codespaces 2-vCPU host with `-f 1 -wi 3 -i 5 -w 2s -r 2s -tu ns -bm avgt`. Numbers vary ±10-30% iteration-to-iteration on this host — group-level patterns and large deltas are more meaningful than any single number.

## Headline gains (cumulative across the session)

| Benchmark | Pre-2.3 baseline | Now | Δ |
|---|---:|---:|---:|
| `PipeOps.async_emit_single_await` | 2532 ns | **108 ns** | **-96%** |
| `CyclicOps.cyclic_emit_await_batch` | 50.73 ns | **19.41 ns** | **-62%** |
| `CyclicOps.cyclic_emit_deep_await` | 30.37 ns | **15.46 ns** | **-49%** |
| `PipeOps.async_emit_chained_await` | 18.90 ns | **13.45 ns** | **-29%** |
| `PipeOps.async_emit_batch_await` | 19.60 ns | **12.87 ns** | **-34%** |
| `CircuitOps.hot_pipe_async_with_flow` | 27.58 ns | **7.56 ns** | **-73%** |
| `CyclicOps.cyclic_emit_await` | 45.77 ns | **19.35 ns** | **-58%** |
| `FlowOps.flow_diff_await` | 40.33 ns | **14.77 ns** | **-63%** |
| `FlowOps.flow_sift_await` | 39.40 ns | **13.31 ns** | **-66%** |
| `SubscriberOps.close_idempotent_await` | 3689 ns | **550 ns** | **-85%** |
| `TapOps.tap_emit_identity_single_await` | 2130 ns | **139 ns** | **-93%** |

Cyclic floor on Codespaces: ~14-15 ns/cycle (`CyclicOps.cyclic_emit_deep_await` after warmup). Against Humainary's ~4.5 ns/cycle on Apple M4, the residual ~10 ns is mostly **cache-line structural** (5+ cache-line touches per cycle, JVM object-layout). Cross-host comparisons are approximate.

## Key levers

| Lever | Where | Headline impact |
|---|---|---|
| Spec-compliant FsPipe.emit thread routing | `FsPipe.java` | External → ingress, worker → transit (cascade priority per §5.3) |
| Spin-before-park in `awaitImpl` | `FsCircuit.AWAIT_SPIN_COUNT=1000` | -2400 ns on `async_emit_single_await` |
| Empty fiber/flow elision | `FsFiber.pipe`, `FsFlow.pipe` | `count==0` returns target — no transit hop |
| Channel `dispatch`/`cascadeDispatch` split | `FsChannel.java` | Skip version check on cascade path |
| Marker class split | `FsCircuit.java` | Eliminates bimorphic trap on drain loop |
| Operator extraction | `FsOperators.java` | Uniform `Wrap[]` storage; no `instanceof` in materialise |
| Transit ring (replaces chunked queue) | `TransitQueueRing.java` | Single-emission alternating cascade; `INITIAL_CAP=8` |
| QChunk capacity tuning | `QChunk.CAPACITY=128` | Larger chunks → fewer atomic-claim contention events |
| Drop redundant `Arrays.fill` on chunk recycle | `IngressQueue.java` | drainBatchLoop already nulls slots inline |

## Hardware

| | Humainary (Alpha baseline) | Fullerstack |
|---|---|---|
| **Platform** | Apple Mac mini (Mac16,10) | Azure VM (GitHub Codespaces) |
| **Chip** | Apple M4 (10 cores: 4P + 6E) | AMD EPYC 7763 (2 vCPU) |
| **Memory** | 16 GB | 8 GB |
| **JVM (baseline)** | Java 25.0.1 (HotSpot) | Java 25.0.1 (OpenJDK) |
| **JVM (current)** | n/a | Java 26-ea+35 (preview) |
| **SPI** | Alpha Provider | FsCortexProvider |

## JMH Configuration

| Parameter | Value |
|---|---|
| Forks | 1 |
| Warmup iterations | 3 |
| Measurement iterations | 5 |
| Warmup / measurement time | 2 s each |
| Mode | avgt (ns/op) |

The Humainary baselines were measured on dedicated Apple silicon while Fullerstack results are on a shared Azure VM. Absolute cross-platform timings are approximate; relative deltas are more meaningful.

## Substrates results (Pre-2.3 → Now)

All times in ns/op. **Δ vs pre-2.3** = ((now - pre-2.3) / pre-2.3 × 100), so negative is improvement. Benchmarks added or removed in 2.3 show `—` in one column.

| Benchmark | Humainary baseline | Pre-2.3 FS | Now FS (post-audit) | Δ vs pre-2.3 |
|---|---:|---:|---:|---:|
| io.humainary.substrates.jmh.CircuitOps.conduit_create_close | 309.983 | 871.961 | 786.932 | -9.8% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_named | 316.894 | 1336.338 | 676.420 | -49.4% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_with_flow | 289.073 | 997.151 | 499.506 | -49.9% |
| io.humainary.substrates.jmh.CircuitOps.create_and_close | 333.957 | 712.571 | 566.282 | -20.5% |
| io.humainary.substrates.jmh.CircuitOps.create_named_and_close | 457.365 | 878.086 | 536.400 | -38.9% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create | 16.662 | 20.520 | 20.729 | +1.0% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_named | 16.461 | 20.052 | 20.457 | +2.0% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_with_flow | 19.919 | 22.268 | 19.564 | -12.1% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async | 8.392 | 12.686 | 7.175 | -43.4% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async_with_flow | 10.268 | 27.580 | 7.564 | -72.6% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async | 341.472 | 603.912 | 461.514 | -23.6% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async_with_flow | 302.093 | 752.469 | 440.440 | -41.5% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name | 1.351 | 2.314 | 2.547 | +10.1% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name_batch | 0.961 | 2.069 | 1.895 | -8.4% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate | 1.705 | 3.583 | 3.806 | +6.2% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate_batch | 1.538 | 3.172 | 3.077 | -3.0% |
| io.humainary.substrates.jmh.ConduitOps.get_cached | 2.319 | 5.019 | 3.333 | -33.6% |
| io.humainary.substrates.jmh.ConduitOps.get_cached_batch | 2.126 | 4.729 | 3.105 | -34.3% |
| io.humainary.substrates.jmh.ConduitOps.get_varied | 3.194 | 14.757 | 11.379 | -22.9% |
| io.humainary.substrates.jmh.ConduitOps.get_varied_batch | 3.098 | 12.200 | 10.924 | -10.5% |
| io.humainary.substrates.jmh.ConduitOps.subscribe | 446.558 | 539.141 | 508.765 | -5.6% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_batch | 460.854 | 609.339 | 530.575 | -12.9% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_with_emission_await | 7162.881 | 13979.706 | 524.648 | -96.2% |
| io.humainary.substrates.jmh.CortexOps.circuit | 288.248 | 481.567 | 468.525 | -2.7% |
| io.humainary.substrates.jmh.CortexOps.circuit_batch | 290.459 | 533.220 | 353.591 | -33.7% |
| io.humainary.substrates.jmh.CortexOps.circuit_named | 291.152 | 488.376 | 366.459 | -25.0% |
| io.humainary.substrates.jmh.CortexOps.current | 1.187 | 3.558 | 3.088 | -13.2% |
| io.humainary.substrates.jmh.CortexOps.name_class | 1.647 | 3.046 | 2.650 | -13.0% |
| io.humainary.substrates.jmh.CortexOps.name_enum | 1.953 | 2.490 | 2.181 | -12.4% |
| io.humainary.substrates.jmh.CortexOps.name_iterable | 8.697 | 5.016 | 4.479 | -10.7% |
| io.humainary.substrates.jmh.CortexOps.name_path | 2.103 | 2.430 | 2.232 | -8.1% |
| io.humainary.substrates.jmh.CortexOps.name_path_batch | 1.861 | 2.184 | 1.948 | -10.8% |
| io.humainary.substrates.jmh.CortexOps.name_string | 2.935 | 2.470 | 2.181 | -11.7% |
| io.humainary.substrates.jmh.CortexOps.name_string_batch | 2.771 | 2.161 | 2.297 | +6.3% |
| io.humainary.substrates.jmh.CortexOps.scope | 9.701 | 4.927 | 9.468 | +92.2% |
| io.humainary.substrates.jmh.CortexOps.scope_batch | 8.418 | 4.744 | 9.622 | +102.8% |
| io.humainary.substrates.jmh.CortexOps.scope_named | 8.917 | 5.319 | 9.602 | +80.5% |
| io.humainary.substrates.jmh.CortexOps.slot_boolean | 1.935 | 2.751 | 2.418 | -12.1% |
| io.humainary.substrates.jmh.CortexOps.slot_double | 1.923 | 6.212 | 5.576 | -10.2% |
| io.humainary.substrates.jmh.CortexOps.slot_int | 1.932 | 2.612 | 2.463 | -5.7% |
| io.humainary.substrates.jmh.CortexOps.slot_long | 1.939 | 3.010 | 2.405 | -20.1% |
| io.humainary.substrates.jmh.CortexOps.slot_string | 1.939 | 2.914 | 2.622 | -10.0% |
| io.humainary.substrates.jmh.CortexOps.state_empty | 0.504 | 2.508 | 3.055 | +21.8% |
| io.humainary.substrates.jmh.CortexOps.state_empty_batch | 0.001 | 2.200 | 2.768 | +25.8% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await | 10.342 | 45.766 | 19.354 | -57.7% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await_batch | 10.398 | 50.735 | 19.415 | -61.7% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await | 4.399 | 30.371 | 15.461 | -49.1% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await_batch | 4.470 | 27.376 | 15.457 | -43.5% |
| io.humainary.substrates.jmh.FlowOps.baseline_no_flow_await | 18.965 | 19.532 | 14.138 | -27.6% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_guard_await | 26.317 | 39.929 | 34.852 | -12.7% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_sample_await | 19.873 | 37.821 | 21.853 | -42.2% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_guard_limit_await | 28.306 | 39.713 | 34.611 | -12.8% |
| io.humainary.substrates.jmh.FlowOps.flow_diff_await | 28.498 | 40.333 | 14.768 | -63.4% |
| io.humainary.substrates.jmh.FlowOps.flow_guard_await | 28.773 | 36.991 | 27.424 | -25.9% |
| io.humainary.substrates.jmh.FlowOps.flow_limit_await | 28.293 | 31.975 | 13.274 | -58.5% |
| io.humainary.substrates.jmh.FlowOps.flow_sample_await | 18.800 | 18.099 | 13.249 | -26.8% |
| io.humainary.substrates.jmh.FlowOps.flow_sift_await | 20.194 | 39.395 | 13.309 | -66.2% |
| io.humainary.substrates.jmh.IdOps.id_from_subject | 0.578 | 2.112 | 1.681 | -20.4% |
| io.humainary.substrates.jmh.IdOps.id_from_subject_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.IdOps.id_toString | 13.209 | 10.222 | 6.309 | -38.3% |
| io.humainary.substrates.jmh.IdOps.id_toString_batch | 14.612 | 6.306 | 6.072 | -3.7% |
| io.humainary.substrates.jmh.NameOps.name_chained_deep | 5.745 | 7.084 | 7.106 | +0.3% |
| io.humainary.substrates.jmh.NameOps.name_chaining | 9.450 | 4.412 | 4.366 | -1.0% |
| io.humainary.substrates.jmh.NameOps.name_chaining_batch | 9.038 | 4.140 | 4.103 | -0.9% |
| io.humainary.substrates.jmh.NameOps.name_compare | 0.839 | 1.711 | 1.576 | -7.9% |
| io.humainary.substrates.jmh.NameOps.name_compare_batch | 0.001 | 0.002 | 0.002 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_depth | 0.577 | 0.863 | 0.833 | -3.5% |
| io.humainary.substrates.jmh.NameOps.name_depth_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_enclosure | 0.608 | 1.282 | 1.219 | -4.9% |
| io.humainary.substrates.jmh.NameOps.name_from_enum | 2.026 | 2.272 | 2.172 | -4.4% |
| io.humainary.substrates.jmh.NameOps.name_from_iterable | 9.811 | 8.420 | 7.758 | -7.9% |
| io.humainary.substrates.jmh.NameOps.name_from_iterator | 9.671 | 10.129 | 8.270 | -18.4% |
| io.humainary.substrates.jmh.NameOps.name_from_mapped_iterable | 10.111 | 8.926 | 8.328 | -6.7% |
| io.humainary.substrates.jmh.NameOps.name_from_name | 3.988 | 3.823 | 3.705 | -3.1% |
| io.humainary.substrates.jmh.NameOps.name_from_string | 3.409 | 2.284 | 2.204 | -3.5% |
| io.humainary.substrates.jmh.NameOps.name_from_string_batch | 3.124 | 2.069 | 1.908 | -7.8% |
| io.humainary.substrates.jmh.NameOps.name_hashCode | 0.588 | 1.298 | 1.187 | -8.6% |
| io.humainary.substrates.jmh.NameOps.name_hashCode_batch | 0.001 | 1.096 | 1.053 | -3.9% |
| io.humainary.substrates.jmh.NameOps.name_interning_chained | 11.655 | 12.093 | 11.881 | -1.8% |
| io.humainary.substrates.jmh.NameOps.name_interning_same_path | 3.901 | 4.163 | 4.035 | -3.1% |
| io.humainary.substrates.jmh.NameOps.name_interning_segments | 9.706 | 5.918 | 5.772 | -2.5% |
| io.humainary.substrates.jmh.NameOps.name_iterate_hierarchy | 1.862 | 3.313 | 3.157 | -4.7% |
| io.humainary.substrates.jmh.NameOps.name_parsing | 2.096 | 2.234 | 2.181 | -2.4% |
| io.humainary.substrates.jmh.NameOps.name_parsing_batch | 1.875 | 1.964 | 1.890 | -3.8% |
| io.humainary.substrates.jmh.NameOps.name_path_generation | 0.606 | 0.863 | 0.848 | -1.7% |
| io.humainary.substrates.jmh.NameOps.name_path_generation_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_within | 1.767 | 2.993 | 2.897 | -3.2% |
| io.humainary.substrates.jmh.NameOps.name_within_batch | 1.125 | 3.309 | 3.127 | -5.5% |
| io.humainary.substrates.jmh.NameOps.name_within_false | 2.089 | 3.010 | 3.086 | +2.5% |
| io.humainary.substrates.jmh.NameOps.name_within_false_batch | 1.409 | 2.277 | 2.179 | -4.3% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch | 10.156 | 13.781 | 13.676 | -0.8% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch_await | 18.370 | 19.603 | 12.867 | -34.4% |
| io.humainary.substrates.jmh.PipeOps.async_emit_chained_await | 22.567 | 18.897 | 13.446 | -28.8% |
| io.humainary.substrates.jmh.PipeOps.async_emit_fanout_await | 19.832 | 29.048 | 19.698 | -32.2% |
| io.humainary.substrates.jmh.PipeOps.async_emit_single | 6.872 | 11.341 | 11.580 | +2.1% |
| io.humainary.substrates.jmh.PipeOps.async_emit_single_await | 6217.555 | 2531.684 | 107.583 | -95.8% |
| io.humainary.substrates.jmh.PipeOps.async_emit_with_flow_await | 19.262 | 39.286 | 33.805 | -14.0% |
| io.humainary.substrates.jmh.PipeOps.baseline_blackhole | 0.296 | 0.790 | 0.746 | -5.6% |
| io.humainary.substrates.jmh.PipeOps.baseline_counter | 1.859 | 2.888 | 2.782 | -3.7% |
| io.humainary.substrates.jmh.PipeOps.baseline_receptor | 0.546 | 0.825 | 0.743 | -9.9% |
| io.humainary.substrates.jmh.PipeOps.pipe_create | 8.567 | 11.891 | 7.306 | -38.6% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_chained | 0.931 | 1.984 | 1.927 | -2.9% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_with_flow | 14.337 | 13.579 | 22.581 | +66.3% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await | 95.215 | 48.908 | 17.560 | -64.1% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await_batch | 19.156 | 18.292 | 13.344 | -27.1% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await | 89.571 | 142.187 | 36.362 | -74.4% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await_batch | 22.238 | 46.225 | 25.996 | -43.8% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await | 89.491 | 142.166 | 34.731 | -75.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await_batch | 22.215 | 44.469 | 26.466 | -40.5% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_drain_cycles_await | 439.618 | 503.130 | 36.535 | -92.7% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await | 87.034 | 135.553 | 37.791 | -72.1% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await_batch | 23.094 | 44.303 | 26.345 | -40.5% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await | 83.096 | 145.367 | 38.671 | -73.4% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await_batch | 26.119 | 51.866 | 28.255 | -45.5% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_subjects_await | 95.337 | 140.001 | 34.416 | -75.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous | 18.916 | 18.274 | 30.879 | +69.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous_batch | 18.682 | 17.866 | 31.577 | +76.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named | 19.368 | 19.143 | 33.721 | +76.2% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named_batch | 20.021 | 19.063 | 33.434 | +75.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent | 2.738 | 0.739 | 2.957 | +300.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent_batch | 0.039 | 0.319 | 2.450 | +668.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure | 294.963 | 565.995 | 510.628 | -9.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure_batch | 306.814 | 548.742 | 606.797 | +10.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close | 2.762 | 0.751 | 3.300 | +339.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.038 | 0.320 | 2.672 | +735.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named | 2.820 | 0.834 | 2.559 | +206.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named_batch | 0.038 | 0.461 | 2.382 | +416.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy | 30.365 | 34.953 | 61.626 | +76.3% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy_batch | 31.328 | 33.915 | 62.209 | +83.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children | 47.824 | 47.131 | 82.239 | +74.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 46.967 | 46.348 | 77.928 | +68.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single | 303.114 | 398.302 | 407.525 | +2.3% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single_batch | 311.687 | 424.514 | 478.557 | +12.7% |
| io.humainary.substrates.jmh.StateOps.slot_name | 0.581 | 0.908 | 1.099 | +21.0% |
| io.humainary.substrates.jmh.StateOps.slot_name_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.slot_type | 0.497 | 0.893 | 1.075 | +20.4% |
| io.humainary.substrates.jmh.StateOps.slot_value | 0.668 | 1.065 | 1.244 | +16.8% |
| io.humainary.substrates.jmh.StateOps.slot_value_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.state_iterate_slots | 2.388 | 4.747 | 5.151 | +8.5% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int | 3.957 | 8.061 | 9.692 | +20.2% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int_batch | 4.003 | 7.703 | 9.907 | +28.6% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_long | 3.935 | 8.159 | 9.617 | +17.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object | 2.047 | 7.870 | 8.678 | +10.3% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object_batch | 2.035 | 8.402 | 9.568 | +13.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_string | 4.030 | 8.395 | 9.917 | +18.1% |
| io.humainary.substrates.jmh.StateOps.state_value_read | 1.390 | 3.317 | 3.346 | +0.9% |
| io.humainary.substrates.jmh.StateOps.state_value_read_batch | 0.001 | 0.064 | 0.003 | -95.3% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare | 4.281 | 2.421 | 2.675 | +10.5% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_batch | 2.753 | 0.003 | 0.003 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same | 0.496 | 1.335 | 1.411 | +5.7% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same_batch | 0.001 | 0.002 | 0.002 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_three_way | 12.264 | 5.289 | 5.897 | +11.5% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_conduits_await | 8944.447 | 7210.952 | 1929.528 | -73.2% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_subscriptions_await | 8802.098 | 6700.906 | 1939.015 | -71.1% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_await | 8681.928 | 3689.043 | 549.801 | -85.1% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_batch_await | 18.078 | 14.024 | 11.720 | -16.4% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_await | 8761.686 | 5114.457 | 1111.419 | -78.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_batch_await | 15.126 | 34.796 | 48.806 | +40.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_await | 8273.231 | 8085.147 | 1307.155 | -83.8% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_batch_await | 32.196 | 123.853 | 162.799 | +31.4% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_conduits_await | 8799.784 | 4814.836 | 2587.109 | -46.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_subscriptions_await | 8453.515 | 4341.104 | 2417.314 | -44.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_with_pending_emissions_await | 8809.789 | 8707.370 | 2180.361 | -75.0% |
| io.humainary.substrates.jmh.TapOps.baseline_emit_batch_await | 20.533 | 21.086 | 17.607 | -16.5% |
| io.humainary.substrates.jmh.TapOps.tap_close | 8875.453 | 2703.768 | 390.432 | -85.6% |
| io.humainary.substrates.jmh.TapOps.tap_create_batch | 574.582 | 1708.320 | 1494.104 | -12.5% |
| io.humainary.substrates.jmh.TapOps.tap_create_string | 872.824 | 1666.624 | 2144.578 | +28.7% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_batch_await | 28.876 | 43.691 | 23.418 | -46.4% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single | 31.541 | 40.808 | 23.488 | -42.4% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single_await | 6109.758 | 2130.367 | 139.340 | -93.5% |
| io.humainary.substrates.jmh.TapOps.tap_emit_multi_batch_await | 42.337 | 52.691 | 38.061 | -27.8% |
| io.humainary.substrates.jmh.TapOps.tap_emit_string_batch_await | 36.052 | 53.053 | 46.115 | -13.1% |
| io.humainary.substrates.jmh.TapOps.tap_lifecycle | 17387.271 | 13838.676 | 1911.193 | -86.2% |

## Serventis results (Now)

All times in ns/op. Serventis instrument benchmarks measured for the first time in this canonical run; no historical Humainary or pre-2.3 baseline is recorded. Most instrument emits land in the **11-15 ns** range — the same dual-queue path as substrates emit.

| Benchmark | Now FS (ns/op) |
|---|---:|
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit | 56.511 |
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit_batch | 55.547 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict | 11.716 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict_batch | 12.813 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire | 12.108 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire_batch | 11.291 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit | 13.712 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit_batch | 11.560 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup | 12.357 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup_batch | 11.552 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss | 11.245 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss_batch | 14.868 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove | 12.132 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove_batch | 16.337 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign | 15.964 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign_batch | 14.486 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store | 11.351 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store_batch | 11.423 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate | 21.550 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate_batch | 16.021 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure | 12.274 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure_batch | 15.825 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer | 12.887 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer_batch | 13.292 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint | 11.566 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint_batch | 13.432 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter | 17.260 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter_batch | 11.679 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input | 12.006 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input_batch | 12.154 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag | 11.512 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag_batch | 12.049 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output | 13.223 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output_batch | 10.633 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow | 14.927 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow_batch | 12.345 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign | 11.762 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign_batch | 12.765 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip | 12.463 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip_batch | 14.711 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform | 12.458 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform_batch | 15.114 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark | 11.861 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark_batch | 11.477 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_etl | 57.885 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_stream | 57.000 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_windowed | 61.172 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit | 67.654 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit_batch | 54.090 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue | 11.339 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue_batch | 12.784 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue | 11.713 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue_batch | 15.187 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow | 11.009 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow_batch | 14.171 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign | 14.503 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign_batch | 12.921 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow | 11.911 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow_batch | 10.946 |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit | 53.691 |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit_batch | 53.043 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow | 14.651 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow_batch | 14.304 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop | 14.418 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop_batch | 11.629 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push | 12.016 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push_batch | 14.817 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign | 12.622 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign_batch | 12.154 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow | 11.378 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow_batch | 18.953 |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit | 53.577 |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit_batch | 55.700 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash | 11.814 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash_batch | 12.590 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail | 10.975 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail_batch | 12.817 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill | 12.256 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill_batch | 12.194 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart | 12.351 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart_batch | 12.793 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume | 13.058 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume_batch | 15.455 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign | 11.831 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign_batch | 11.090 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn | 13.074 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn_batch | 11.711 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start | 14.494 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start_batch | 10.947 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop | 11.896 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop_batch | 14.535 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend | 12.165 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend_batch | 12.152 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit | 53.403 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit_batch | 53.924 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call | 12.329 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call_batch | 16.476 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called | 13.551 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called_batch | 12.335 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay | 13.504 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay_batch | 11.630 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed | 11.959 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed_batch | 12.980 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard | 13.511 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard_batch | 12.363 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded | 13.499 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded_batch | 11.779 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect | 13.236 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect_batch | 11.535 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected | 12.747 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected_batch | 11.568 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire | 12.130 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire_batch | 13.660 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired | 11.739 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired_batch | 15.963 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail | 14.102 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail_batch | 12.377 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed | 16.221 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed_batch | 12.218 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse | 15.870 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse_batch | 11.835 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed | 12.818 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed_batch | 11.578 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect | 12.343 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect_batch | 11.244 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected | 11.638 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected_batch | 11.687 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject | 11.580 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject_batch | 12.790 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected | 14.750 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected_batch | 11.745 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume | 12.018 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume_batch | 12.932 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed | 11.818 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed_batch | 15.156 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried | 12.662 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried_batch | 12.102 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry | 11.504 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry_batch | 14.175 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule | 11.774 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule_batch | 11.649 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled | 12.005 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled_batch | 11.587 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal | 11.344 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal_batch | 12.020 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start | 13.385 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start_batch | 11.518 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started | 12.567 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started_batch | 11.201 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop | 15.091 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop_batch | 10.811 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped | 12.307 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped_batch | 12.689 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded | 12.098 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded_batch | 13.114 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success | 11.668 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success_batch | 12.873 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend | 12.228 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend_batch | 14.226 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended | 12.855 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended_batch | 12.733 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit | 53.103 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit_batch | 54.987 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel | 12.073 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel_batch | 11.472 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete | 11.024 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete_batch | 10.444 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail | 12.735 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail_batch | 13.225 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress | 11.472 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress_batch | 14.948 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject | 12.422 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject_batch | 15.103 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume | 11.554 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume_batch | 10.804 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule | 13.893 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule_batch | 11.887 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign | 13.262 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign_batch | 12.394 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start | 12.807 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start_batch | 13.759 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit | 11.183 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit_batch | 15.624 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend | 11.738 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend_batch | 11.027 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout | 11.466 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout_batch | 11.536 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit | 52.858 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit_batch | 53.426 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline | 12.637 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline_batch | 14.449 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_threshold | 12.032 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_deadline | 12.609 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_threshold | 12.839 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal | 15.383 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal_batch | 10.635 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit | 52.982 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit_batch | 53.576 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator | 13.595 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator_batch | 12.741 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant | 11.821 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant_batch | 13.994 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator | 12.140 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator_batch | 12.164 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant | 12.933 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant_batch | 11.794 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator | 11.899 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator_batch | 12.473 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant | 12.302 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant_batch | 14.132 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator | 12.839 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator_batch | 14.862 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant | 15.209 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant_batch | 12.568 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator | 11.956 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator_batch | 14.168 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant | 11.733 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant_batch | 14.031 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator | 12.416 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator_batch | 13.521 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant | 12.302 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant_batch | 14.536 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator | 11.418 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator_batch | 14.135 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant | 12.406 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant_batch | 11.681 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal | 11.358 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal_batch | 13.854 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator | 12.014 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator_batch | 15.812 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant | 13.245 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant_batch | 12.615 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit | 56.532 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit_batch | 57.754 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit | 95.802 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit_batch | 57.296 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close | 11.794 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close_batch | 11.437 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open | 11.679 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open_batch | 14.008 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open | 15.033 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open_batch | 12.737 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe | 15.671 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe_batch | 10.957 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset | 12.549 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset_batch | 10.497 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign | 13.215 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign_batch | 11.548 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip | 12.289 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip_batch | 12.348 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_egress | 12.583 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_ingress | 13.347 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_transit | 12.158 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal | 11.899 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal_batch | 11.676 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_egress | 15.353 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress | 13.572 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress_batch | 11.922 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_transit | 12.853 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit | 54.213 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit_batch | 54.164 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt | 15.424 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt_batch | 11.673 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop | 11.856 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop_batch | 11.814 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward | 11.774 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward_batch | 12.507 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment | 11.535 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment_batch | 15.225 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble | 15.059 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble_batch | 11.621 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive | 13.899 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive_batch | 14.581 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder | 11.925 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder_batch | 11.657 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route | 12.208 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route_batch | 12.030 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send | 11.962 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send_batch | 13.884 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign | 11.726 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign_batch | 11.073 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit | 53.667 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit_batch | 54.107 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract | 11.827 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract_batch | 13.686 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny | 12.055 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny_batch | 12.976 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain | 11.202 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain_batch | 10.585 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop | 11.629 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop_batch | 11.183 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand | 12.216 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand_batch | 11.003 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass | 12.026 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass_batch | 11.181 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign | 11.617 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign_batch | 10.853 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit | 52.571 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit_batch | 55.101 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider | 12.585 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider_batch | 13.779 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver | 12.514 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver_batch | 12.229 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange | 11.878 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange_batch | 11.787 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal | 11.626 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal_batch | 10.998 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider | 11.707 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider_batch | 10.855 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver | 12.691 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver_batch | 12.884 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit | 53.459 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit_batch | 53.653 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire | 13.008 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire_batch | 12.720 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired | 14.638 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired_batch | 11.433 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied | 12.344 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied_batch | 11.480 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny | 22.623 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny_batch | 13.825 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire | 12.710 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire_batch | 15.240 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired | 12.082 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired_batch | 15.444 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend | 12.678 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend_batch | 11.953 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended | 11.538 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended_batch | 12.152 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant | 12.143 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant_batch | 15.625 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted | 12.574 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted_batch | 16.462 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe | 12.121 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe_batch | 12.625 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed | 11.462 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed_batch | 13.056 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release | 12.421 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release_batch | 16.563 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released | 11.459 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released_batch | 11.762 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew | 11.827 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew_batch | 13.119 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed | 12.576 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed_batch | 14.673 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke | 13.250 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke_batch | 10.821 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked | 11.634 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked_batch | 12.918 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal | 15.836 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal_batch | 11.667 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit | 53.519 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit_batch | 53.643 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow | 12.133 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow_batch | 11.776 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract | 13.450 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract_batch | 12.098 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand | 11.832 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand_batch | 13.431 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim | 14.032 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim_batch | 10.868 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign | 12.562 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign_batch | 12.378 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit | 53.349 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit_batch | 54.018 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire | 12.572 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire_batch | 11.018 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt | 13.343 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt_batch | 11.254 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny | 13.015 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny_batch | 14.862 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant | 12.987 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant_batch | 12.416 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release | 15.436 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release_batch | 12.423 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign | 12.310 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign_batch | 13.717 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout | 11.508 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout_batch | 13.776 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit | 52.878 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit_batch | 53.326 |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit | 53.693 |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit_batch | 53.833 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge | 12.608 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge_batch | 10.692 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm | 11.529 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm_batch | 11.342 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask | 11.996 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask_batch | 12.004 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify | 12.959 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify_batch | 13.359 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command | 11.246 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command_batch | 11.510 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver | 12.966 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver_batch | 12.240 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny | 13.932 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny_batch | 12.728 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain | 11.799 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain_batch | 14.019 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise | 12.653 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise_batch | 11.871 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report | 12.223 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report_batch | 12.200 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request | 11.281 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request_batch | 12.294 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign | 11.677 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign_batch | 12.889 |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit | 53.938 |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit_batch | 53.061 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept | 13.642 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept_batch | 14.765 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted | 11.364 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted_batch | 12.060 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach | 12.112 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach_batch | 11.873 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached | 11.997 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached_batch | 12.680 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend | 12.461 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend_batch | 12.227 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended | 14.141 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended_batch | 11.427 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill | 14.409 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill_batch | 13.544 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled | 11.935 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled_batch | 13.151 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire | 12.534 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire_batch | 11.983 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired | 11.795 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired_batch | 11.518 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe | 11.628 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe_batch | 13.576 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed | 15.487 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed_batch | 16.598 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer | 12.941 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer_batch | 11.756 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered | 12.090 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered_batch | 15.678 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise | 12.151 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise_batch | 12.091 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised | 14.642 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised_batch | 13.156 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract | 12.169 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract_batch | 14.391 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted | 11.925 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted_batch | 12.575 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal | 13.263 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal_batch | 10.957 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate | 12.316 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate_batch | 11.448 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated | 11.998 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated_batch | 11.399 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit | 53.096 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit_batch | 53.334 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt | 11.919 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt_batch | 10.914 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_backoff | 11.303 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_exhaust | 12.641 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail | 12.301 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail_batch | 11.066 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_park | 12.334 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign | 12.918 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign_batch | 12.056 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin | 14.938 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin_batch | 11.467 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success | 12.429 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success_batch | 10.941 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon | 14.519 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon_batch | 15.417 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive | 14.246 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive_batch | 13.055 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await | 11.696 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await_batch | 12.064 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release | 11.792 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release_batch | 13.856 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset | 11.680 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset_batch | 11.697 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign | 12.129 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign_batch | 11.072 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout | 11.397 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout_batch | 12.834 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit | 53.425 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit_batch | 54.251 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon | 15.233 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon_batch | 11.716 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire | 12.653 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire_batch | 12.645 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt | 11.708 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt_batch | 11.661 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest | 11.710 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest_batch | 11.354 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny | 10.997 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny_batch | 13.210 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade | 11.360 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade_batch | 10.739 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant | 11.579 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant_batch | 12.851 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release | 10.951 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release_batch | 11.774 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign | 11.193 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign_batch | 13.399 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout | 11.748 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout_batch | 14.176 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade | 11.675 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade_batch | 11.154 |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit | 51.881 |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit_batch | 54.747 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit | 53.381 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit_batch | 54.447 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment | 11.288 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment_batch | 11.972 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow | 11.096 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow_batch | 11.991 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset | 12.507 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset_batch | 10.751 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign | 11.103 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign_batch | 12.680 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement | 11.994 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement_batch | 10.992 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment | 17.283 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment_batch | 14.022 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow | 13.535 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow_batch | 12.050 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset | 12.303 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset_batch | 11.808 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign | 11.284 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign_batch | 12.183 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow | 12.269 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow_batch | 12.576 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit | 52.858 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit_batch | 52.895 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug | 12.231 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug_batch | 11.131 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info | 12.007 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info_batch | 17.891 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe | 18.163 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe_batch | 11.989 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign | 11.827 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign_batch | 11.638 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning | 12.589 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning_batch | 12.850 |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit | 52.822 |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit_batch | 54.764 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect | 12.758 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect_batch | 13.385 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected | 11.491 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected_batch | 14.038 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect | 15.014 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect_batch | 12.094 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected | 11.518 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected_batch | 11.540 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail | 11.901 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail_batch | 13.825 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed | 12.092 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed_batch | 11.575 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process | 12.276 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process_batch | 13.474 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed | 12.631 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed_batch | 12.148 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_receive_batch | 12.786 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_received_batch | 11.851 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal | 15.611 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal_batch | 11.311 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed | 12.336 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed_batch | 14.841 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded | 11.403 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded_batch | 11.542 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer | 12.205 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_inbound | 12.864 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_outbound | 11.901 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transferred | 12.086 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmit_batch | 11.067 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmitted_batch | 11.666 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit | 52.548 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit_batch | 53.257 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline | 12.177 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline_batch | 10.755 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target | 11.846 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target_batch | 15.474 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold | 12.354 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold_batch | 11.764 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline | 11.500 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline_batch | 10.506 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target | 19.667 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target_batch | 15.035 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold | 13.661 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold_batch | 12.082 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline | 13.313 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline_batch | 10.698 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target | 12.112 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target_batch | 11.397 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold | 12.545 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold_batch | 12.779 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal | 16.916 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal_batch | 25.020 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit | 60.383 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit_batch | 168.583 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin | 72.273 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin_batch | 95.186 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end | 13.104 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end_batch | 18.405 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign | 12.706 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign_batch | 11.841 |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit | 52.768 |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit_batch | 53.096 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail | 11.967 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail_batch | 12.191 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign | 11.451 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign_batch | 11.256 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success | 12.074 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success_batch | 12.347 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit | 53.629 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit_batch | 55.220 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_mixed_pattern | 0.325 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single | 1.008 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single_batch | 0.026 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_varied_batch | 2.080 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_worst_case | 1.740 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical | 12.429 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical_batch | 11.138 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal | 14.772 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal_batch | 11.905 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal | 12.538 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal_batch | 12.406 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning | 12.878 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning_batch | 11.396 |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit | 54.712 |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit_batch | 53.718 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed | 12.664 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed_batch | 13.127 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative | 12.834 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative_batch | 12.292 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured | 14.622 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured_batch | 11.295 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed | 12.043 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed_batch | 11.167 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal | 11.453 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal_batch | 11.522 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed | 12.386 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed_batch | 12.769 |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit | 54.083 |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit_batch | 57.513 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_fail_divided | 12.845 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal | 12.199 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal_batch | 11.636 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_majority | 13.531 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous | 12.762 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous_batch | 11.417 |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit | 152.562 |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit_batch | 153.554 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow | 11.978 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow_batch | 11.628 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link | 14.399 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link_batch | 11.464 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time | 11.672 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time_batch | 11.528 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space | 11.712 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space_batch | 11.642 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal | 11.291 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal_batch | 11.579 |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit | 53.607 |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit_batch | 54.084 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_chaos | 13.611 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_cycle | 16.740 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift | 11.115 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift_batch | 11.966 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign | 12.661 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign_batch | 11.242 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_spike | 13.281 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable | 12.559 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable_batch | 13.065 |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit | 53.303 |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit_batch | 54.647 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit | 275.060 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit_batch | 294.548 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat | 12.794 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat_batch | 13.219 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return | 12.494 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return_batch | 17.035 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal | 12.734 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal_batch | 12.523 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single | 12.738 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single_batch | 13.646 |
