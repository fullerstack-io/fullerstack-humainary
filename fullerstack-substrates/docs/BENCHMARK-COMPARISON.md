# Benchmark Comparison: Fullerstack vs Humainary Substrates

**Substrates/Serventis 2.4.0** · `io.fullerstack:fullerstack-substrates:2.4.0-RC1`

Full pre-2.3 → post-2.4 comparison for every JMH benchmark. Measured on a Codespaces 2-vCPU host with `-f 1 -wi 3 -i 5 -w 2s -r 2s -tu ns -bm avgt`. Numbers vary ±10-30% iteration-to-iteration on this host — group-level patterns and large deltas are more meaningful than any single number.

## Headline gains (cumulative across the upgrade)

| Benchmark | Pre-2.3 baseline | Now (2.4) | Δ |
|---|---:|---:|---:|
| `PipeOps.async_emit_single_await` | 2532 ns | **106 ns** | **-96%** |
| `CyclicOps.cyclic_emit_await_batch` | 50.73 ns | **19.47 ns** | **-62%** |
| `CyclicOps.cyclic_emit_deep_await` | 30.37 ns | **14.03 ns** | **-54%** |
| `PipeOps.async_emit_chained_await` | 18.90 ns | **13.68 ns** | **-28%** |
| `PipeOps.async_emit_batch_await` | 19.60 ns | **12.99 ns** | **-34%** |
| `CircuitOps.hot_pipe_async_with_flow` | 27.58 ns | **7.57 ns** | **-73%** |
| `CyclicOps.cyclic_emit_await` | 45.77 ns | **19.46 ns** | **-57%** |
| `FlowOps.flow_diff_await` | 40.33 ns | **13.23 ns** | **-67%** |
| `FlowOps.flow_sift_await` | 39.40 ns | **12.91 ns** | **-67%** |
| `SubscriberOps.close_idempotent_await` | 3689 ns | **245 ns** | **-93%** |
| `TapOps.tap_emit_identity_single_await` | 2130 ns | **100 ns** | **-95%** |

Cyclic floor on Codespaces: ~14 ns/cycle (`CyclicOps.cyclic_emit_deep_await` after warmup). Against Humainary's ~4.5 ns/cycle on Apple M4, the residual ~10 ns is mostly **cache-line structural** (5+ cache-line touches per cycle, JVM object-layout). Cross-host comparisons are approximate.

## Key levers (cumulative through 2.4)

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
| **2.4: Counter / CircuitStats removal** | `FsCircuit`, `IngressQueue`, `TransitQueueRing` | Removed 5 volatile counter writes from emit/drain hot paths; Pulse replaces stats |
| **2.4: FsDerivedPool spec compliance** | `FsDerivedPool.java` | ConcurrentHashMap + sentinels for null/failure caching (replaces O(n) copy-on-write) |

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

## Substrates results (Pre-2.3 → 2.4)

All times in ns/op. **Δ vs pre-2.3** = ((now - pre-2.3) / pre-2.3 × 100), so negative is improvement. Benchmarks added or removed across versions show `—` in one column.

| Benchmark | Humainary baseline | Pre-2.3 FS | 2.4 FS | Δ vs pre-2.3 |
|---|---:|---:|---:|---:|
| io.humainary.substrates.jmh.CircuitOps.conduit_create_close | 309.983 | 871.961 | 1548.658 | +77.6% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_named | 316.894 | 1336.338 | 533.056 | -60.1% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_with_flow | 289.073 | 997.151 | 784.315 | -21.3% |
| io.humainary.substrates.jmh.CircuitOps.create_and_close | 333.957 | 712.571 | 1255.647 | +76.2% |
| io.humainary.substrates.jmh.CircuitOps.create_and_close_batch | — | — | 1214.599 | — |
| io.humainary.substrates.jmh.CircuitOps.create_multiple_and_close | — | — | 5864.964 | — |
| io.humainary.substrates.jmh.CircuitOps.create_named_and_close | 457.365 | 878.086 | 1605.159 | +82.8% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create | 16.662 | 20.520 | 20.343 | -0.9% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_named | 16.461 | 20.052 | 20.760 | +3.5% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_with_flow | 19.919 | 22.268 | 20.863 | -6.3% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async | 8.392 | 12.686 | 7.538 | -40.6% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async_with_flow | 10.268 | 27.580 | 7.565 | -72.6% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async | 341.472 | 603.912 | 569.411 | -5.7% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async_with_flow | 302.093 | 752.469 | 852.388 | +13.3% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name | 1.351 | 2.314 | 2.742 | +18.5% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name_batch | 0.961 | 2.069 | 1.963 | -5.1% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate | 1.705 | 3.583 | 3.948 | +10.2% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate_batch | 1.538 | 3.172 | 3.177 | +0.2% |
| io.humainary.substrates.jmh.ConduitOps.get_cached | 2.319 | 5.019 | 3.542 | -29.4% |
| io.humainary.substrates.jmh.ConduitOps.get_cached_batch | 2.126 | 4.729 | 3.020 | -36.1% |
| io.humainary.substrates.jmh.ConduitOps.get_varied | 3.194 | 14.757 | 13.458 | -8.8% |
| io.humainary.substrates.jmh.ConduitOps.get_varied_batch | 3.098 | 12.200 | 11.226 | -8.0% |
| io.humainary.substrates.jmh.ConduitOps.subscribe | 446.558 | 539.141 | 514.150 | -4.6% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_batch | 460.854 | 609.339 | 567.901 | -6.8% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_with_emission_await | 7162.881 | 13979.706 | 551.184 | -96.1% |
| io.humainary.substrates.jmh.CortexOps.circuit | 288.248 | 481.567 | 388.670 | -19.3% |
| io.humainary.substrates.jmh.CortexOps.circuit_batch | 290.459 | 533.220 | 413.783 | -22.4% |
| io.humainary.substrates.jmh.CortexOps.circuit_named | 291.152 | 488.376 | 510.025 | +4.4% |
| io.humainary.substrates.jmh.CortexOps.current | 1.187 | 3.558 | 3.444 | -3.2% |
| io.humainary.substrates.jmh.CortexOps.name_class | 1.647 | 3.046 | 2.879 | -5.5% |
| io.humainary.substrates.jmh.CortexOps.name_enum | 1.953 | 2.490 | 2.376 | -4.6% |
| io.humainary.substrates.jmh.CortexOps.name_iterable | 8.697 | 5.016 | 4.720 | -5.9% |
| io.humainary.substrates.jmh.CortexOps.name_path | 2.103 | 2.430 | 2.299 | -5.4% |
| io.humainary.substrates.jmh.CortexOps.name_path_batch | 1.861 | 2.184 | 2.071 | -5.2% |
| io.humainary.substrates.jmh.CortexOps.name_string | 2.935 | 2.470 | 2.387 | -3.4% |
| io.humainary.substrates.jmh.CortexOps.name_string_batch | 2.771 | 2.161 | 2.289 | +5.9% |
| io.humainary.substrates.jmh.CortexOps.scope | 9.701 | 4.927 | 10.318 | +109.4% |
| io.humainary.substrates.jmh.CortexOps.scope_batch | 8.418 | 4.744 | 9.763 | +105.8% |
| io.humainary.substrates.jmh.CortexOps.scope_named | 8.917 | 5.319 | 9.790 | +84.1% |
| io.humainary.substrates.jmh.CortexOps.slot_boolean | 1.935 | 2.751 | 2.387 | -13.2% |
| io.humainary.substrates.jmh.CortexOps.slot_double | 1.923 | 6.212 | 5.377 | -13.4% |
| io.humainary.substrates.jmh.CortexOps.slot_int | 1.932 | 2.612 | 2.401 | -8.1% |
| io.humainary.substrates.jmh.CortexOps.slot_long | 1.939 | 3.010 | 2.398 | -20.3% |
| io.humainary.substrates.jmh.CortexOps.slot_string | 1.939 | 2.914 | 2.594 | -11.0% |
| io.humainary.substrates.jmh.CortexOps.state_empty | 0.504 | 2.508 | 3.002 | +19.7% |
| io.humainary.substrates.jmh.CortexOps.state_empty_batch | 0.001 | 2.200 | 2.758 | +25.4% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit | — | — | 1.582 | — |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await | 10.342 | 45.766 | 19.459 | -57.5% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await_batch | 10.398 | 50.735 | 19.468 | -61.6% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_batch | — | — | 2.069 | — |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await | 4.399 | 30.371 | 14.033 | -53.8% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await_batch | 4.470 | 27.376 | 14.531 | -46.9% |
| io.humainary.substrates.jmh.FlowOps.baseline_no_flow_await | 18.965 | 19.532 | 12.959 | -33.7% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_guard_await | 26.317 | 39.929 | 30.874 | -22.7% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_sample_await | 19.873 | 37.821 | 21.333 | -43.6% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_guard_limit_await | 28.306 | 39.713 | 32.211 | -18.9% |
| io.humainary.substrates.jmh.FlowOps.flow_diff_await | 28.498 | 40.333 | 13.230 | -67.2% |
| io.humainary.substrates.jmh.FlowOps.flow_guard_await | 28.773 | 36.991 | 13.032 | -64.8% |
| io.humainary.substrates.jmh.FlowOps.flow_limit_await | 28.293 | 31.975 | 13.194 | -58.7% |
| io.humainary.substrates.jmh.FlowOps.flow_sample_await | 18.800 | 18.099 | 12.618 | -30.3% |
| io.humainary.substrates.jmh.FlowOps.flow_sift_await | 20.194 | 39.395 | 12.912 | -67.2% |
| io.humainary.substrates.jmh.IdOps.id_from_subject | 0.578 | 2.112 | 1.640 | -22.3% |
| io.humainary.substrates.jmh.IdOps.id_from_subject_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.IdOps.id_toString | 13.209 | 10.222 | 6.221 | -39.1% |
| io.humainary.substrates.jmh.IdOps.id_toString_batch | 14.612 | 6.306 | 6.078 | -3.6% |
| io.humainary.substrates.jmh.NameOps.name_chained_deep | 5.745 | 7.084 | 6.974 | -1.6% |
| io.humainary.substrates.jmh.NameOps.name_chaining | 9.450 | 4.412 | 6.911 | +56.6% |
| io.humainary.substrates.jmh.NameOps.name_chaining_batch | 9.038 | 4.140 | 4.832 | +16.7% |
| io.humainary.substrates.jmh.NameOps.name_compare | 0.839 | 1.711 | 12.802 | +648.2% |
| io.humainary.substrates.jmh.NameOps.name_compare_batch | 0.001 | 0.002 | 0.025 | +1150.0% |
| io.humainary.substrates.jmh.NameOps.name_depth | 0.577 | 0.863 | 2.802 | +224.7% |
| io.humainary.substrates.jmh.NameOps.name_depth_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_enclosure | 0.608 | 1.282 | 1.236 | -3.6% |
| io.humainary.substrates.jmh.NameOps.name_from_enum | 2.026 | 2.272 | 2.176 | -4.2% |
| io.humainary.substrates.jmh.NameOps.name_from_iterable | 9.811 | 8.420 | 7.893 | -6.3% |
| io.humainary.substrates.jmh.NameOps.name_from_iterator | 9.671 | 10.129 | 8.718 | -13.9% |
| io.humainary.substrates.jmh.NameOps.name_from_mapped_iterable | 10.111 | 8.926 | 8.364 | -6.3% |
| io.humainary.substrates.jmh.NameOps.name_from_name | 3.988 | 3.823 | 3.605 | -5.7% |
| io.humainary.substrates.jmh.NameOps.name_from_string | 3.409 | 2.284 | 2.183 | -4.4% |
| io.humainary.substrates.jmh.NameOps.name_from_string_batch | 3.124 | 2.069 | 1.916 | -7.4% |
| io.humainary.substrates.jmh.NameOps.name_hashCode | 0.588 | 1.298 | 1.246 | -4.0% |
| io.humainary.substrates.jmh.NameOps.name_hashCode_batch | 0.001 | 1.096 | 1.039 | -5.2% |
| io.humainary.substrates.jmh.NameOps.name_interning_chained | 11.655 | 12.093 | 11.578 | -4.3% |
| io.humainary.substrates.jmh.NameOps.name_interning_same_path | 3.901 | 4.163 | 4.164 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_interning_segments | 9.706 | 5.918 | 5.774 | -2.4% |
| io.humainary.substrates.jmh.NameOps.name_iterate_hierarchy | 1.862 | 3.313 | 3.098 | -6.5% |
| io.humainary.substrates.jmh.NameOps.name_parsing | 2.096 | 2.234 | 2.159 | -3.4% |
| io.humainary.substrates.jmh.NameOps.name_parsing_batch | 1.875 | 1.964 | 2.070 | +5.4% |
| io.humainary.substrates.jmh.NameOps.name_path_generation | 0.606 | 0.863 | 0.830 | -3.8% |
| io.humainary.substrates.jmh.NameOps.name_path_generation_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_within | 1.767 | 2.993 | 2.933 | -2.0% |
| io.humainary.substrates.jmh.NameOps.name_within_batch | 1.125 | 3.309 | 3.401 | +2.8% |
| io.humainary.substrates.jmh.NameOps.name_within_false | 2.089 | 3.010 | 6.789 | +125.5% |
| io.humainary.substrates.jmh.NameOps.name_within_false_batch | 1.409 | 2.277 | 2.254 | -1.0% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch | 10.156 | 13.781 | 13.248 | -3.9% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch_await | 18.370 | 19.603 | 12.988 | -33.7% |
| io.humainary.substrates.jmh.PipeOps.async_emit_chained_await | 22.567 | 18.897 | 13.679 | -27.6% |
| io.humainary.substrates.jmh.PipeOps.async_emit_fanout_await | 19.832 | 29.048 | 19.647 | -32.4% |
| io.humainary.substrates.jmh.PipeOps.async_emit_single | 6.872 | 11.341 | 11.069 | -2.4% |
| io.humainary.substrates.jmh.PipeOps.async_emit_single_await | 6217.555 | 2531.684 | 105.775 | -95.8% |
| io.humainary.substrates.jmh.PipeOps.async_emit_with_flow_await | 19.262 | 39.286 | 32.121 | -18.2% |
| io.humainary.substrates.jmh.PipeOps.baseline_blackhole | 0.296 | 0.790 | 0.769 | -2.7% |
| io.humainary.substrates.jmh.PipeOps.baseline_counter | 1.859 | 2.888 | 2.811 | -2.7% |
| io.humainary.substrates.jmh.PipeOps.baseline_receptor | 0.546 | 0.825 | 0.734 | -11.0% |
| io.humainary.substrates.jmh.PipeOps.pipe_create | 8.567 | 11.891 | 7.182 | -39.6% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_chained | 0.931 | 1.984 | 1.926 | -2.9% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_with_flow | 14.337 | 13.579 | 21.565 | +58.8% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await | 95.215 | 48.908 | 16.520 | -66.2% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await_batch | 19.156 | 18.292 | 14.430 | -21.1% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await | 89.571 | 142.187 | 36.180 | -74.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await_batch | 22.238 | 46.225 | 26.063 | -43.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await | 89.491 | 142.166 | 34.518 | -75.7% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await_batch | 22.215 | 44.469 | 25.778 | -42.0% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_drain_cycles_await | 439.618 | 503.130 | 37.165 | -92.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await | 87.034 | 135.553 | 35.413 | -73.9% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await_batch | 23.094 | 44.303 | 26.326 | -40.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await | 83.096 | 145.367 | 37.887 | -73.9% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await_batch | 26.119 | 51.866 | 28.115 | -45.8% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_subjects_await | 95.337 | 140.001 | 36.483 | -73.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous | 18.916 | 18.274 | 31.570 | +72.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous_batch | 18.682 | 17.866 | 31.196 | +74.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named | 19.368 | 19.143 | 30.595 | +59.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named_batch | 20.021 | 19.063 | 31.262 | +64.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent | 2.738 | 0.739 | 2.492 | +237.2% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent_batch | 0.039 | 0.319 | 2.200 | +589.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure | 294.963 | 565.995 | 509.632 | -10.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure_batch | 306.814 | 548.742 | 537.505 | -2.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_complex | — | — | 4210.770 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close | 2.762 | 0.751 | 2.501 | +233.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.038 | 0.320 | 2.185 | +582.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named | 2.820 | 0.834 | 3.320 | +298.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named_batch | 0.038 | 0.461 | 2.293 | +397.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy | 30.365 | 34.953 | 59.691 | +70.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy_batch | 31.328 | 33.915 | 59.535 | +75.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children | 47.824 | 47.131 | 77.271 | +63.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 46.967 | 46.348 | 76.789 | +65.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple | — | — | 3456.055 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple_batch | — | — | 3207.844 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single | 303.114 | 398.302 | 344.404 | -13.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single_batch | 311.687 | 424.514 | 337.700 | -20.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_with_resources | — | — | 2585.677 | — |
| io.humainary.substrates.jmh.StateOps.slot_name | 0.581 | 0.908 | 0.869 | -4.3% |
| io.humainary.substrates.jmh.StateOps.slot_name_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.slot_type | 0.497 | 0.893 | 0.851 | -4.7% |
| io.humainary.substrates.jmh.StateOps.slot_value | 0.668 | 1.065 | 1.009 | -5.3% |
| io.humainary.substrates.jmh.StateOps.slot_value_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.state_iterate_slots | 2.388 | 4.747 | 4.010 | -15.5% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int | 3.957 | 8.061 | 8.136 | +0.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int_batch | 4.003 | 7.703 | 8.303 | +7.8% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_long | 3.935 | 8.159 | 8.085 | -0.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object | 2.047 | 7.870 | 7.290 | -7.4% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object_batch | 2.035 | 8.402 | 7.949 | -5.4% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_string | 4.030 | 8.395 | 8.176 | -2.6% |
| io.humainary.substrates.jmh.StateOps.state_value_read | 1.390 | 3.317 | 2.783 | -16.1% |
| io.humainary.substrates.jmh.StateOps.state_value_read_batch | 0.001 | 0.064 | 0.003 | -95.3% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare | 4.281 | 2.421 | 2.290 | -5.4% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_batch | 2.753 | 0.003 | 0.003 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same | 0.496 | 1.335 | 1.119 | -16.2% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same_batch | 0.001 | 0.002 | 0.002 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_three_way | 12.264 | 5.289 | 7.260 | +37.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_conduits_await | 8944.447 | 7210.952 | 1128.468 | -84.4% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_subscriptions_await | 8802.098 | 6700.906 | 892.167 | -86.7% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_await | 8681.928 | 3689.043 | 245.065 | -93.4% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_batch_await | 18.078 | 14.024 | 8.714 | -37.9% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_await | 8761.686 | 5114.457 | 535.131 | -89.5% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_batch_await | 15.126 | 34.796 | 26.234 | -24.6% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_await | 8273.231 | 8085.147 | 584.440 | -92.8% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_batch_await | 32.196 | 123.853 | 98.020 | -20.9% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_conduits_await | 8799.784 | 4814.836 | 1199.366 | -75.1% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_subscriptions_await | 8453.515 | 4341.104 | 1114.144 | -74.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_with_pending_emissions_await | 8809.789 | 8707.370 | 1341.336 | -84.6% |
| io.humainary.substrates.jmh.TapOps.baseline_emit_batch_await | 20.533 | 21.086 | 13.607 | -35.5% |
| io.humainary.substrates.jmh.TapOps.tap_close | 8875.453 | 2703.768 | 288.298 | -89.3% |
| io.humainary.substrates.jmh.TapOps.tap_create_batch | 574.582 | 1708.320 | 1585.802 | -7.2% |
| io.humainary.substrates.jmh.TapOps.tap_create_identity | — | — | 1526.271 | — |
| io.humainary.substrates.jmh.TapOps.tap_create_string | 872.824 | 1666.624 | 1609.474 | -3.4% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_batch_await | 28.876 | 43.691 | 15.486 | -64.6% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single | 31.541 | 40.808 | 20.786 | -49.1% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single_await | 6109.758 | 2130.367 | 99.903 | -95.3% |
| io.humainary.substrates.jmh.TapOps.tap_emit_multi_batch_await | 42.337 | 52.691 | 21.275 | -59.6% |
| io.humainary.substrates.jmh.TapOps.tap_emit_string_batch_await | 36.052 | 53.053 | 32.407 | -38.9% |
| io.humainary.substrates.jmh.TapOps.tap_lifecycle | 17387.271 | 13838.676 | 1373.398 | -90.1% |

## Serventis results (2.4)

All times in ns/op. Most instrument emits land in the **11-15 ns** range — the same dual-queue path as substrates emit.

| Benchmark | 2.4 FS (ns/op) |
|---|---:|
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit | 62.649 |
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit_batch | 60.210 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict | 11.775 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict_batch | 11.274 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire | 11.507 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire_batch | 12.199 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit | 12.499 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit_batch | 11.560 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup | 11.701 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup_batch | 11.632 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss | 12.888 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss_batch | 11.946 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove | 17.272 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove_batch | 11.917 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign | 12.884 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign_batch | 11.119 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store | 11.189 |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store_batch | 10.901 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate | 11.686 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate_batch | 10.380 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure | 13.762 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure_batch | 12.536 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer | 12.477 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer_batch | 15.074 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint | 13.203 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint_batch | 13.931 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter | 13.495 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter_batch | 12.827 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input | 13.966 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input_batch | 10.919 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag | 11.894 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag_batch | 13.545 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output | 12.418 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output_batch | 12.028 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow | 11.623 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow_batch | 11.412 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign | 16.259 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign_batch | 14.263 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip | 12.331 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip_batch | 13.222 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform | 13.618 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform_batch | 10.882 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark | 13.317 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark_batch | 11.175 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_etl | 59.887 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_stream | 60.595 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_windowed | 59.366 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit | 60.290 |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit_batch | 60.728 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue | 14.270 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue_batch | 11.080 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue | 11.628 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue_batch | 14.101 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow | 17.891 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow_batch | 13.588 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign | 11.124 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign_batch | 10.597 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow | 12.153 |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow_batch | 12.890 |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit | 59.719 |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit_batch | 60.399 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow | 11.484 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow_batch | 11.261 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop | 11.761 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop_batch | 11.873 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push | 13.678 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push_batch | 10.507 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign | 14.230 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign_batch | 13.295 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow | 11.189 |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow_batch | 10.803 |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit | 61.584 |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit_batch | 60.338 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash | 11.043 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash_batch | 12.222 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail | 12.921 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail_batch | 12.655 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill | 12.576 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill_batch | 11.884 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart | 11.756 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart_batch | 13.786 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume | 12.407 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume_batch | 10.922 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign | 11.215 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign_batch | 11.421 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn | 13.873 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn_batch | 11.196 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start | 12.534 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start_batch | 15.227 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop | 11.781 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop_batch | 11.458 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend | 12.617 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend_batch | 11.797 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit | 60.514 |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit_batch | 61.436 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call | 14.958 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call_batch | 12.870 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called | 13.092 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called_batch | 10.922 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay | 12.714 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay_batch | 13.500 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed | 11.362 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed_batch | 12.219 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard | 13.008 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard_batch | 15.191 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded | 12.013 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded_batch | 13.260 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect | 12.588 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect_batch | 13.590 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected | 12.076 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected_batch | 12.149 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire | 15.018 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire_batch | 11.767 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired | 16.593 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired_batch | 12.791 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail | 14.128 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail_batch | 19.303 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed | 20.407 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed_batch | 16.983 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse | 12.670 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse_batch | 12.570 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed | 12.375 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed_batch | 13.069 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect | 14.068 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect_batch | 14.550 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected | 11.817 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected_batch | 11.485 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject | 11.874 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject_batch | 11.570 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected | 11.888 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected_batch | 14.830 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume | 15.750 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume_batch | 14.858 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed | 13.081 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed_batch | 11.785 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried | 12.580 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried_batch | 11.471 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry | 11.821 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry_batch | 11.506 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule | 12.844 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule_batch | 12.543 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled | 11.933 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled_batch | 15.316 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal | 14.516 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal_batch | 10.808 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start | 12.150 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start_batch | 13.515 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started | 13.345 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started_batch | 13.502 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop | 14.709 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop_batch | 11.898 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped | 12.976 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped_batch | 11.138 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded | 15.003 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded_batch | 12.501 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success | 11.426 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success_batch | 12.175 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend | 12.362 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend_batch | 11.751 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended | 11.339 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended_batch | 11.722 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit | 61.065 |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit_batch | 60.609 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel | 13.076 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel_batch | 10.570 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete | 13.622 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete_batch | 11.019 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail | 13.733 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail_batch | 11.645 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress | 13.787 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress_batch | 11.865 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject | 12.497 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject_batch | 13.618 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume | 12.082 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume_batch | 14.731 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule | 11.394 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule_batch | 14.471 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign | 11.674 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign_batch | 13.133 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start | 12.298 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start_batch | 11.507 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit | 13.308 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit_batch | 13.407 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend | 12.355 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend_batch | 14.195 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout | 14.946 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout_batch | 12.999 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit | 60.185 |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit_batch | 61.433 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline | 12.707 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline_batch | 13.652 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_threshold | 12.778 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_deadline | 14.842 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_threshold | 14.198 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal | 13.212 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal_batch | 11.177 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit | 61.042 |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit_batch | 59.708 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator | 12.722 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator_batch | 13.955 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant | 12.338 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant_batch | 11.832 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator | 12.295 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator_batch | 14.753 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant | 12.994 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant_batch | 15.365 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator | 11.653 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator_batch | 13.509 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant | 12.542 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant_batch | 12.697 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator | 13.468 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator_batch | 24.621 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant | 11.581 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant_batch | 11.429 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator | 12.973 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator_batch | 11.388 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant | 13.279 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant_batch | 13.527 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator | 12.344 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator_batch | 14.535 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant | 12.577 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant_batch | 14.462 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator | 11.950 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator_batch | 11.093 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant | 12.405 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant_batch | 12.955 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal | 14.207 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal_batch | 12.668 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator | 11.850 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator_batch | 11.038 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant | 14.106 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant_batch | 11.200 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit | 61.370 |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit_batch | 60.672 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit | 60.692 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit_batch | 60.527 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close | 12.709 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close_batch | 12.626 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open | 13.452 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open_batch | 14.490 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open | 11.823 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open_batch | 12.118 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe | 12.166 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe_batch | 15.305 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset | 14.786 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset_batch | 14.043 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign | 12.753 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign_batch | 14.659 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip | 11.439 |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip_batch | 11.636 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_egress | 11.103 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_ingress | 12.145 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_transit | 16.300 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal | 11.623 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal_batch | 11.251 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_egress | 13.114 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress | 12.515 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress_batch | 12.262 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_transit | 15.550 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit | 60.722 |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit_batch | 60.430 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt | 12.232 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt_batch | 11.039 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop | 11.448 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop_batch | 11.787 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward | 13.109 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward_batch | 11.091 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment | 13.125 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment_batch | 12.145 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble | 12.502 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble_batch | 11.147 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive | 13.067 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive_batch | 12.244 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder | 13.097 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder_batch | 12.355 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route | 11.496 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route_batch | 13.368 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send | 10.816 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send_batch | 12.142 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign | 13.862 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign_batch | 12.339 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit | 61.152 |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit_batch | 58.820 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract | 12.751 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract_batch | 10.877 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny | 11.712 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny_batch | 16.638 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain | 11.648 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain_batch | 11.976 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop | 12.740 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop_batch | 10.644 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand | 11.511 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand_batch | 12.590 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass | 11.949 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass_batch | 10.962 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign | 12.630 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign_batch | 14.602 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit | 61.859 |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit_batch | 58.688 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider | 12.693 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider_batch | 14.530 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver | 13.366 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver_batch | 14.112 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange | 11.312 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange_batch | 11.901 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal | 12.123 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal_batch | 12.985 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider | 12.607 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider_batch | 13.245 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver | 13.381 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver_batch | 12.720 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit | 60.722 |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit_batch | 60.216 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire | 11.518 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire_batch | 12.099 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired | 12.278 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired_batch | 13.393 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied | 11.911 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied_batch | 11.347 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny | 11.046 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny_batch | 12.267 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire | 13.692 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire_batch | 11.674 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired | 12.058 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired_batch | 11.940 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend | 12.581 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend_batch | 11.093 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended | 12.666 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended_batch | 12.146 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant | 12.867 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant_batch | 13.635 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted | 12.189 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted_batch | 11.781 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe | 12.773 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe_batch | 11.568 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed | 11.894 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed_batch | 13.340 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release | 12.547 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release_batch | 13.264 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released | 11.489 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released_batch | 12.018 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew | 12.160 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew_batch | 12.649 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed | 12.842 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed_batch | 13.665 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke | 12.232 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke_batch | 12.841 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked | 12.887 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked_batch | 12.015 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal | 12.930 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal_batch | 13.563 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit | 60.704 |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit_batch | 60.384 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow | 13.221 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow_batch | 10.917 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract | 14.152 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract_batch | 12.825 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand | 12.049 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand_batch | 12.112 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim | 11.645 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim_batch | 14.306 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign | 11.491 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign_batch | 11.694 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit | 61.658 |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit_batch | 67.557 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire | 12.126 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire_batch | 10.973 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt | 11.473 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt_batch | 11.215 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny | 13.164 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny_batch | 14.878 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant | 12.078 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant_batch | 11.258 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release | 11.183 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release_batch | 11.737 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign | 11.364 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign_batch | 11.795 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout | 12.048 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout_batch | 13.264 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit | 59.863 |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit_batch | 62.065 |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit | 62.520 |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit_batch | 66.023 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge | 12.821 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge_batch | 12.724 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm | 12.833 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm_batch | 11.984 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask | 12.649 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask_batch | 11.597 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify | 12.686 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify_batch | 11.280 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command | 11.232 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command_batch | 10.887 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver | 11.793 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver_batch | 13.780 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny | 12.085 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny_batch | 11.268 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain | 10.964 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain_batch | 11.443 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise | 13.051 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise_batch | 14.263 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report | 11.089 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report_batch | 12.174 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request | 11.647 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request_batch | 12.677 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign | 12.073 |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign_batch | 14.365 |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit | 61.192 |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit_batch | 61.892 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept | 12.332 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept_batch | 13.203 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted | 12.586 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted_batch | 13.095 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach | 12.057 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach_batch | 12.444 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached | 12.067 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached_batch | 14.058 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend | 14.397 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend_batch | 13.195 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended | 11.407 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended_batch | 11.610 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill | 12.409 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill_batch | 13.261 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled | 11.708 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled_batch | 17.200 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire | 12.084 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire_batch | 22.350 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired | 13.436 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired_batch | 12.138 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe | 16.339 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe_batch | 11.849 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed | 12.492 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed_batch | 12.425 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer | 12.489 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer_batch | 10.943 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered | 11.431 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered_batch | 11.557 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise | 12.352 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise_batch | 13.787 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised | 15.099 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised_batch | 11.440 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract | 12.083 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract_batch | 10.983 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted | 13.307 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted_batch | 13.412 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal | 13.543 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal_batch | 10.718 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate | 13.111 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate_batch | 15.658 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated | 11.748 |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated_batch | 11.493 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit | 61.047 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit_batch | 60.233 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt | 12.444 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt_batch | 11.173 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_backoff | 11.294 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_exhaust | 11.725 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail | 11.349 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail_batch | 12.383 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_park | 13.148 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign | 11.841 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign_batch | 14.081 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin | 12.742 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin_batch | 13.448 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success | 12.452 |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success_batch | 13.517 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon | 12.052 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon_batch | 12.173 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive | 11.332 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive_batch | 10.576 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await | 12.252 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await_batch | 11.452 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release | 13.357 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release_batch | 12.243 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset | 10.968 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset_batch | 13.390 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign | 12.314 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign_batch | 12.208 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout | 11.572 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout_batch | 12.418 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit | 60.588 |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit_batch | 60.311 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon | 11.993 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon_batch | 10.644 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire | 13.407 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire_batch | 12.989 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt | 13.069 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt_batch | 10.816 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest | 14.036 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest_batch | 13.527 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny | 12.292 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny_batch | 14.056 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade | 12.426 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade_batch | 13.676 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant | 12.052 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant_batch | 12.457 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release | 11.592 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release_batch | 13.070 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign | 11.070 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign_batch | 13.363 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout | 11.897 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout_batch | 10.861 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade | 12.270 |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade_batch | 14.467 |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit | 59.784 |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit_batch | 59.500 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit | 62.487 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit_batch | 61.165 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment | 13.000 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment_batch | 11.848 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow | 12.191 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow_batch | 13.544 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset | 11.804 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset_batch | 13.421 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign | 14.362 |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign_batch | 13.947 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement | 12.139 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement_batch | 12.169 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment | 11.721 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment_batch | 10.681 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow | 12.100 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow_batch | 11.806 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset | 12.158 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset_batch | 12.924 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign | 15.587 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign_batch | 15.007 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow | 12.609 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow_batch | 15.080 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit | 59.988 |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit_batch | 60.349 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug | 13.136 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug_batch | 13.764 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info | 13.628 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info_batch | 12.169 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe | 14.249 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe_batch | 13.055 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign | 12.184 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign_batch | 12.485 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning | 12.858 |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning_batch | 11.333 |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit | 61.143 |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit_batch | 61.582 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect | 12.848 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect_batch | 11.231 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected | 13.921 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected_batch | 16.019 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect | 16.472 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect_batch | 13.485 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected | 12.175 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected_batch | 13.483 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail | 11.434 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail_batch | 11.399 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed | 12.970 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed_batch | 12.739 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process | 11.759 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process_batch | 11.959 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed | 12.520 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed_batch | 12.778 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_receive_batch | 13.968 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_received_batch | 12.915 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal | 13.024 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal_batch | 11.853 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed | 12.789 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed_batch | 11.563 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded | 11.875 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded_batch | 12.206 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer | 12.162 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_inbound | 12.557 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_outbound | 12.771 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transferred | 13.655 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmit_batch | 14.536 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmitted_batch | 14.842 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit | 66.390 |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit_batch | 64.979 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline | 12.663 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline_batch | 12.467 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target | 13.461 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target_batch | 11.933 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold | 13.976 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold_batch | 11.817 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline | 13.993 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline_batch | 12.985 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target | 12.996 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target_batch | 12.190 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold | 14.732 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold_batch | 11.011 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline | 12.533 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline_batch | 12.232 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target | 14.215 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target_batch | 11.370 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold | 11.700 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold_batch | 13.748 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal | 13.672 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal_batch | 11.289 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit | 64.338 |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit_batch | 64.650 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin | 13.092 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin_batch | 11.306 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end | 13.658 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end_batch | 11.389 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign | 12.593 |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign_batch | 13.899 |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit | 64.084 |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit_batch | 62.566 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail | 11.980 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail_batch | 13.704 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign | 12.545 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign_batch | 12.875 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success | 11.893 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success_batch | 11.959 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit | 63.688 |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit_batch | 62.915 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_mixed_pattern | 0.340 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single | 1.096 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single_batch | 0.028 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_varied_batch | 2.138 |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_worst_case | 1.910 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical | 11.956 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical_batch | 13.109 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal | 13.293 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal_batch | 13.702 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal | 12.419 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal_batch | 12.376 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning | 12.989 |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning_batch | 12.148 |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit | 63.034 |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit_batch | 63.031 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed | 13.413 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed_batch | 14.309 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative | 12.705 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative_batch | 12.720 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured | 14.227 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured_batch | 11.913 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed | 12.934 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed_batch | 12.071 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal | 14.471 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal_batch | 11.331 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed | 12.138 |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed_batch | 12.039 |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit | 63.682 |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit_batch | 63.032 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_fail_divided | 12.432 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal | 12.648 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal_batch | 11.894 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_majority | 13.373 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous | 12.526 |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous_batch | 11.705 |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit | 173.023 |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit_batch | 169.945 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow | 12.425 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow_batch | 11.552 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link | 13.071 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link_batch | 13.410 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time | 11.749 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time_batch | 14.151 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space | 13.371 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space_batch | 10.807 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal | 11.904 |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal_batch | 10.941 |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit | 64.131 |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit_batch | 62.463 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_chaos | 11.585 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_cycle | 12.135 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift | 12.925 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift_batch | 11.852 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign | 11.780 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign_batch | 11.257 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_spike | 11.640 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable | 12.752 |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable_batch | 13.749 |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit | 63.913 |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit_batch | 62.676 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit | 300.381 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit_batch | 305.210 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat | 12.296 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat_batch | 12.255 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return | 12.796 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return_batch | 14.431 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal | 12.710 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal_batch | 13.387 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single | 13.730 |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single_batch | 12.516 |
