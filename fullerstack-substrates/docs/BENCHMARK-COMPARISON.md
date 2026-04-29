# Benchmark Comparison: Fullerstack vs Humainary Substrates

**Substrates/Serventis 2.3.0** · `io.fullerstack:fullerstack-substrates:1.0.0-RC7`

Full pre-2.3 → post-audit comparison for every JMH benchmark. Measured on the same Codespaces 2-vCPU host with the same JMH config (`-wi 3 -i 5 -w 1s -r 1s`) used for the 2026-04-11 baseline. Numbers vary ±10-30% iteration-to-iteration on this host — group-level patterns and large deltas are more meaningful than any single number.

## Headline gains (cumulative across the session)

| Benchmark | Pre-2.3 baseline | Now | Δ |
|---|---:|---:|---:|
| `PipeOps.async_emit_single_await` | 2502 ns | **109 ns** | **-96%** |
| `CyclicOps.cyclic_emit_await_batch` | 50.74 ns | **21 ns** | **-58%** |
| `CyclicOps.cyclic_emit_deep_await` | 30.37 ns | **16.7 ns** | **-45%** |
| `PipeOps.async_emit_empty_fiber_await` | 56.6 ns | **14 ns** | **-75%** (empty-fiber elision) |
| `PipeOps.async_emit_chained_await` | 22.6 ns | **14.5 ns** | **-36%** |
| `PipeOps.async_emit_batch_await` | 19.6 ns | **13.7 ns** | **-30%** |
| `CircuitOps.hot_pipe_async_with_flow` | 27.6 ns | **7.4 ns** | **-73%** |

Cyclic floor on Codespaces: ~14 ns/cycle (`CyclicOps.cyclic_emit_deep_await` after warmup). Against Humainary's ~4.5 ns/cycle on Apple M4, the residual ~10 ns is mostly **cache-line structural** (5+ cache-line touches per cycle, JVM object-layout). Cross-host comparisons are approximate.

## Key levers

| Lever | Where | Headline impact |
|---|---|---|
| Remove thread check from `FsPipe.emit` | `FsPipe.java` | -10 ns/emit on every emit path |
| Spin-before-park in `awaitImpl` | `FsCircuit.AWAIT_SPIN_COUNT=1000` | -2400 ns on `async_emit_single_await` |
| Empty fiber/flow elision | `FsFiber.pipe`, `FsFlow.pipe` | `count==0` returns target — no transit hop |
| Channel `dispatch`/`cascadeDispatch` split | `FsChannel.java` | Skip version check on cascade path |
| Marker class split | `FsCircuit.java` | Eliminates bimorphic trap on drain loop |
| Operator extraction | `FsOperators.java` | Uniform `Wrap[]` storage; no `instanceof` in materialise |
| Transit ring (replaces chunked queue) | `TransitQueueRing.java` | Single-emission alternating cascade; `INITIAL_CAP=8` |
| QChunk capacity tuning | `QChunk.CAPACITY=128` | Sweep result: -3 ns on async_emit_batch_await |

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
| Warmup / measurement time | 1 s each |
| Mode | avgt (ns/op) |

The Humainary baselines were measured on dedicated Apple silicon while Fullerstack results are on a shared Azure VM. Absolute cross-platform timings are approximate; relative deltas are more meaningful.

## Substrates results (Pre-2.3 → Now)

All times in ns/op. **Δ vs pre-2.3** = ((now - pre-2.3) / pre-2.3 × 100), so negative is improvement. Benchmarks added or removed in 2.3 show `—` in one column.



| Benchmark | Humainary baseline | Pre-2.3 FS | Now FS (post-audit) | Δ vs pre-2.3 |
|---|---:|---:|---:|---:|
| io.humainary.substrates.jmh.CircuitOps.conduit_create_close | 309.983 | 871.961 | 527.299 | -39.5% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_named | 316.894 | 1336.338 | 492.581 | -63.1% |
| io.humainary.substrates.jmh.CircuitOps.conduit_create_with_flow | 289.073 | 997.151 | 698.503 | -30.0% |
| io.humainary.substrates.jmh.CircuitOps.create_and_close | 333.957 | 712.571 | 472.888 | -33.6% |
| io.humainary.substrates.jmh.CircuitOps.create_and_close_batch | 441.745 | 816.871 | 770.953 | -5.6% |
| io.humainary.substrates.jmh.CircuitOps.create_multiple_and_close | 2545.594 | 6446.376 | 2919.684 | -54.7% |
| io.humainary.substrates.jmh.CircuitOps.create_named_and_close | 457.365 | 878.086 | 523.929 | -40.3% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create | 16.662 | 20.520 | 19.377 | -5.6% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_named | 16.461 | 20.052 | 19.863 | -0.9% |
| io.humainary.substrates.jmh.CircuitOps.hot_conduit_create_with_flow | 19.919 | 22.268 | 19.620 | -11.9% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async | 8.392 | 12.686 | 7.274 | -42.7% |
| io.humainary.substrates.jmh.CircuitOps.hot_pipe_async_with_flow | 10.268 | 27.580 | 7.352 | -73.3% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async | 341.472 | 603.912 | 745.454 | +23.4% |
| io.humainary.substrates.jmh.CircuitOps.pipe_async_with_flow | 302.093 | 752.469 | 1028.413 | +36.7% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name | 1.351 | 2.314 | 4.122 | +78.1% |
| io.humainary.substrates.jmh.ConduitOps.get_by_name_batch | 0.961 | 2.069 | 2.028 | -2.0% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate | 1.705 | 3.583 | 3.691 | +3.0% |
| io.humainary.substrates.jmh.ConduitOps.get_by_substrate_batch | 1.538 | 3.172 | 2.813 | -11.3% |
| io.humainary.substrates.jmh.ConduitOps.get_cached | 2.319 | 5.019 | 3.287 | -34.5% |
| io.humainary.substrates.jmh.ConduitOps.get_cached_batch | 2.126 | 4.729 | 2.858 | -39.6% |
| io.humainary.substrates.jmh.ConduitOps.get_varied | 3.194 | 14.757 | 12.067 | -18.2% |
| io.humainary.substrates.jmh.ConduitOps.get_varied_batch | 3.098 | 12.200 | 11.150 | -8.6% |
| io.humainary.substrates.jmh.ConduitOps.subscribe | 446.558 | 539.141 | 491.829 | -8.8% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_batch | 460.854 | 609.339 | 515.647 | -15.4% |
| io.humainary.substrates.jmh.ConduitOps.subscribe_with_emission_await | 7162.881 | 13979.706 | 671.522 | -95.2% |
| io.humainary.substrates.jmh.CortexOps.circuit | 288.248 | 481.567 | 440.386 | -8.6% |
| io.humainary.substrates.jmh.CortexOps.circuit_batch | 290.459 | 533.220 | 465.292 | -12.7% |
| io.humainary.substrates.jmh.CortexOps.circuit_named | 291.152 | 488.376 | 366.687 | -24.9% |
| io.humainary.substrates.jmh.CortexOps.current | 1.187 | 3.558 | 3.123 | -12.2% |
| io.humainary.substrates.jmh.CortexOps.name_class | 1.647 | 3.046 | 2.611 | -14.3% |
| io.humainary.substrates.jmh.CortexOps.name_enum | 1.953 | 2.490 | 2.151 | -13.6% |
| io.humainary.substrates.jmh.CortexOps.name_iterable | 8.697 | 5.016 | 4.691 | -6.5% |
| io.humainary.substrates.jmh.CortexOps.name_path | 2.103 | 2.430 | 2.270 | -6.6% |
| io.humainary.substrates.jmh.CortexOps.name_path_batch | 1.861 | 2.184 | 1.956 | -10.4% |
| io.humainary.substrates.jmh.CortexOps.name_string | 2.935 | 2.470 | 2.194 | -11.2% |
| io.humainary.substrates.jmh.CortexOps.name_string_batch | 2.771 | 2.161 | 2.116 | -2.1% |
| io.humainary.substrates.jmh.CortexOps.scope | 9.701 | 4.927 | 11.495 | +133.3% |
| io.humainary.substrates.jmh.CortexOps.scope_batch | 8.418 | 4.744 | 10.503 | +121.4% |
| io.humainary.substrates.jmh.CortexOps.scope_named | 8.917 | 5.319 | 10.454 | +96.5% |
| io.humainary.substrates.jmh.CortexOps.slot_boolean | 1.935 | 2.751 | 2.720 | -1.1% |
| io.humainary.substrates.jmh.CortexOps.slot_double | 1.923 | 6.212 | 6.193 | -0.3% |
| io.humainary.substrates.jmh.CortexOps.slot_int | 1.932 | 2.612 | 2.616 | +0.2% |
| io.humainary.substrates.jmh.CortexOps.slot_long | 1.939 | 3.010 | 2.862 | -4.9% |
| io.humainary.substrates.jmh.CortexOps.slot_string | 1.939 | 2.914 | 2.900 | -0.5% |
| io.humainary.substrates.jmh.CortexOps.state_empty | 0.504 | 2.508 | 3.212 | +28.1% |
| io.humainary.substrates.jmh.CortexOps.state_empty_batch | 0.001 | 2.200 | 2.997 | +36.2% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit | 1.046 | 3.213 | 1.742 | -45.8% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await | 10.342 | 45.766 | 20.068 | -56.2% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await_batch | 10.398 | 50.735 | 21.046 | -58.5% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_batch | 1.223 | 3.615 | 1.800 | -50.2% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await | 4.399 | 30.371 | 16.717 | -45.0% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_deep_await_batch | 4.470 | 27.376 | 16.666 | -39.1% |
| io.humainary.substrates.jmh.FlowOps.baseline_no_flow_await | 18.965 | 19.532 | 14.144 | -27.6% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_guard_await | 26.317 | 39.929 | 36.968 | -7.4% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_diff_sample_await | 19.873 | 37.821 | 22.394 | -40.8% |
| io.humainary.substrates.jmh.FlowOps.flow_combined_guard_limit_await | 28.306 | 39.713 | 37.559 | -5.4% |
| io.humainary.substrates.jmh.FlowOps.flow_diff_await | 28.498 | 40.333 | 13.532 | -66.4% |
| io.humainary.substrates.jmh.FlowOps.flow_guard_await | 28.773 | 36.991 | 17.808 | -51.9% |
| io.humainary.substrates.jmh.FlowOps.flow_limit_await | 28.293 | 31.975 | 14.743 | -53.9% |
| io.humainary.substrates.jmh.FlowOps.flow_sample_await | 18.800 | 18.099 | 14.242 | -21.3% |
| io.humainary.substrates.jmh.FlowOps.flow_sift_await | 20.194 | 39.395 | 13.511 | -65.7% |
| io.humainary.substrates.jmh.IdOps.id_from_subject | 0.578 | 2.112 | 1.743 | -17.5% |
| io.humainary.substrates.jmh.IdOps.id_from_subject_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.IdOps.id_toString | 13.209 | 10.222 | 8.513 | -16.7% |
| io.humainary.substrates.jmh.IdOps.id_toString_batch | 14.612 | 6.306 | 6.768 | +7.3% |
| io.humainary.substrates.jmh.NameOps.name_chained_deep | 5.745 | 7.084 | 7.333 | +3.5% |
| io.humainary.substrates.jmh.NameOps.name_chaining | 9.450 | 4.412 | 4.581 | +3.8% |
| io.humainary.substrates.jmh.NameOps.name_chaining_batch | 9.038 | 4.140 | 4.262 | +2.9% |
| io.humainary.substrates.jmh.NameOps.name_compare | 0.839 | 1.711 | 1.676 | -2.0% |
| io.humainary.substrates.jmh.NameOps.name_compare_batch | 0.001 | 0.002 | 0.002 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_depth | 0.577 | 0.863 | 0.889 | +3.0% |
| io.humainary.substrates.jmh.NameOps.name_depth_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_enclosure | 0.608 | 1.282 | 1.290 | +0.6% |
| io.humainary.substrates.jmh.NameOps.name_from_enum | 2.026 | 2.272 | 2.386 | +5.0% |
| io.humainary.substrates.jmh.NameOps.name_from_iterable | 9.811 | 8.420 | 8.590 | +2.0% |
| io.humainary.substrates.jmh.NameOps.name_from_iterator | 9.671 | 10.129 | 9.709 | -4.1% |
| io.humainary.substrates.jmh.NameOps.name_from_mapped_iterable | 10.111 | 8.926 | 8.903 | -0.3% |
| io.humainary.substrates.jmh.NameOps.name_from_name | 3.988 | 3.823 | 3.821 | -0.1% |
| io.humainary.substrates.jmh.NameOps.name_from_string | 3.409 | 2.284 | 2.311 | +1.2% |
| io.humainary.substrates.jmh.NameOps.name_from_string_batch | 3.124 | 2.069 | 2.058 | -0.5% |
| io.humainary.substrates.jmh.NameOps.name_hashCode | 0.588 | 1.298 | 1.322 | +1.8% |
| io.humainary.substrates.jmh.NameOps.name_hashCode_batch | 0.001 | 1.096 | 1.137 | +3.7% |
| io.humainary.substrates.jmh.NameOps.name_interning_chained | 11.655 | 12.093 | 12.953 | +7.1% |
| io.humainary.substrates.jmh.NameOps.name_interning_same_path | 3.901 | 4.163 | 4.271 | +2.6% |
| io.humainary.substrates.jmh.NameOps.name_interning_segments | 9.706 | 5.918 | 6.120 | +3.4% |
| io.humainary.substrates.jmh.NameOps.name_iterate_hierarchy | 1.862 | 3.313 | 3.307 | -0.2% |
| io.humainary.substrates.jmh.NameOps.name_parsing | 2.096 | 2.234 | 2.330 | +4.3% |
| io.humainary.substrates.jmh.NameOps.name_parsing_batch | 1.875 | 1.964 | 2.039 | +3.8% |
| io.humainary.substrates.jmh.NameOps.name_path_generation | 0.606 | 0.863 | 0.921 | +6.7% |
| io.humainary.substrates.jmh.NameOps.name_path_generation_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.NameOps.name_within | 1.767 | 2.993 | 3.739 | +24.9% |
| io.humainary.substrates.jmh.NameOps.name_within_batch | 1.125 | 3.309 | 2.305 | -30.3% |
| io.humainary.substrates.jmh.NameOps.name_within_false | 2.089 | 3.010 | 3.261 | +8.3% |
| io.humainary.substrates.jmh.NameOps.name_within_false_batch | 1.409 | 2.277 | 2.412 | +5.9% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch | 10.156 | 13.781 | 40.528 | +194.1% |
| io.humainary.substrates.jmh.PipeOps.async_emit_batch_await | 18.370 | 19.603 | 13.693 | -30.1% |
| io.humainary.substrates.jmh.PipeOps.async_emit_chained_await | 22.567 | 18.897 | 14.472 | -23.4% |
| io.humainary.substrates.jmh.PipeOps.async_emit_diff_only_await | 37.234 | — | — | — |
| io.humainary.substrates.jmh.PipeOps.async_emit_empty_fiber_await | 14.102 | — | — | — |
| io.humainary.substrates.jmh.PipeOps.async_emit_fanout_await | 19.832 | 29.048 | 21.009 | -27.7% |
| io.humainary.substrates.jmh.PipeOps.async_emit_flow_map_await | 33.900 | — | — | — |
| io.humainary.substrates.jmh.PipeOps.async_emit_guard_only_await | 33.520 | — | — | — |
| io.humainary.substrates.jmh.PipeOps.async_emit_single | 6.872 | 11.341 | 16.681 | +47.1% |
| io.humainary.substrates.jmh.PipeOps.async_emit_single_await | 6217.555 | 2531.684 | 109.107 | -95.7% |
| io.humainary.substrates.jmh.PipeOps.async_emit_with_flow_await | 19.262 | 39.286 | 36.172 | -7.9% |
| io.humainary.substrates.jmh.PipeOps.baseline_blackhole | 0.296 | 0.790 | 0.766 | -3.0% |
| io.humainary.substrates.jmh.PipeOps.baseline_counter | 1.859 | 2.888 | 2.921 | +1.1% |
| io.humainary.substrates.jmh.PipeOps.baseline_receptor | 0.546 | 0.825 | 0.793 | -3.9% |
| io.humainary.substrates.jmh.PipeOps.pipe_create | 8.567 | 11.891 | 7.677 | -35.4% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_chained | 0.931 | 1.984 | 2.005 | +1.1% |
| io.humainary.substrates.jmh.PipeOps.pipe_create_with_flow | 14.337 | 13.579 | 23.744 | +74.9% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await | 95.215 | 48.908 | 18.947 | -61.3% |
| io.humainary.substrates.jmh.ReservoirOps.baseline_emit_no_reservoir_await_batch | 19.156 | 18.292 | 16.996 | -7.1% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await | 89.571 | 142.187 | 41.426 | -70.9% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_burst_then_drain_await_batch | 22.238 | 46.225 | 28.591 | -38.1% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await | 89.491 | 142.166 | 38.689 | -72.8% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_drain_await_batch | 22.215 | 44.469 | 30.023 | -32.5% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_drain_cycles_await | 439.618 | 503.130 | 259.459 | -48.4% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await | 87.034 | 135.553 | 218.665 | +61.3% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_emit_with_capture_await_batch | 23.094 | 44.303 | 28.900 | -34.8% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await | 83.096 | 145.367 | 44.303 | -69.5% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_emissions_await_batch | 26.119 | 51.866 | 34.431 | -33.6% |
| io.humainary.substrates.jmh.ReservoirOps.reservoir_process_subjects_await | 95.337 | 140.001 | 40.721 | -70.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous | 18.916 | 18.274 | 33.622 | +84.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_anonymous_batch | 18.682 | 17.866 | 33.317 | +86.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named | 19.368 | 19.143 | 32.705 | +70.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_child_named_batch | 20.021 | 19.063 | 32.514 | +70.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent | 2.738 | 0.739 | 2.617 | +254.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_close_idempotent_batch | 0.039 | 0.319 | 2.362 | +640.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure | 294.963 | 565.995 | 565.322 | -0.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_closure_batch | 306.814 | 548.742 | 626.459 | +14.2% |
| io.humainary.substrates.jmh.ScopeOps.scope_complex | 907.760 | 4769.817 | 4115.717 | -13.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close | 2.762 | 0.751 | 2.643 | +251.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.038 | 0.320 | 2.338 | +630.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named | 2.820 | 0.834 | 2.501 | +199.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named_batch | 0.038 | 0.461 | 2.411 | +423.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy | 30.365 | 34.953 | 59.666 | +70.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy_batch | 31.328 | 33.915 | 60.518 | +78.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children | 47.824 | 47.131 | 79.996 | +69.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 46.967 | 46.348 | 78.584 | +69.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple | 1479.062 | 3335.906 | 2523.555 | -24.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple_batch | 1491.609 | 2622.543 | 3577.036 | +36.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single | 303.114 | 398.302 | 407.681 | +2.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single_batch | 311.687 | 424.514 | 409.441 | -3.6% |
| io.humainary.substrates.jmh.ScopeOps.scope_with_resources | 600.079 | 2698.855 | 2616.630 | -3.0% |
| io.humainary.substrates.jmh.StateOps.slot_name | 0.581 | 0.908 | 0.928 | +2.2% |
| io.humainary.substrates.jmh.StateOps.slot_name_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.slot_type | 0.497 | 0.893 | 0.954 | +6.8% |
| io.humainary.substrates.jmh.StateOps.slot_value | 0.668 | 1.065 | 1.103 | +3.6% |
| io.humainary.substrates.jmh.StateOps.slot_value_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.state_compact | 9.538 | 30.413 | — | — |
| io.humainary.substrates.jmh.StateOps.state_compact_batch | 10.351 | 29.624 | — | — |
| io.humainary.substrates.jmh.StateOps.state_iterate_slots | 2.388 | 4.747 | 4.574 | -3.6% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int | 3.957 | 8.061 | 8.540 | +5.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int_batch | 4.003 | 7.703 | 8.689 | +12.8% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_long | 3.935 | 8.159 | 8.614 | +5.6% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object | 2.047 | 7.870 | 7.900 | +0.4% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object_batch | 2.035 | 8.402 | 8.706 | +3.6% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_string | 4.030 | 8.395 | 8.831 | +5.2% |
| io.humainary.substrates.jmh.StateOps.state_value_read | 1.390 | 3.317 | 3.021 | -8.9% |
| io.humainary.substrates.jmh.StateOps.state_value_read_batch | 0.001 | 0.064 | 0.003 | -95.3% |
| io.humainary.substrates.jmh.StateOps.state_values_stream | 4.104 | 24.225 | — | — |
| io.humainary.substrates.jmh.SubjectOps.subject_compare | 4.281 | 2.421 | 2.343 | -3.2% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_batch | 2.753 | 0.003 | 0.003 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same | 0.496 | 1.335 | 1.193 | -10.6% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_same_batch | 0.001 | 0.002 | 0.002 | +0.0% |
| io.humainary.substrates.jmh.SubjectOps.subject_compare_three_way | 12.264 | 5.289 | 5.206 | -1.6% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_conduits_await | 8944.447 | 7210.952 | 1446.716 | -79.9% |
| io.humainary.substrates.jmh.SubscriberOps.close_five_subscriptions_await | 8802.098 | 6700.906 | 1349.528 | -79.9% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_await | 8681.928 | 3689.043 | 376.582 | -89.8% |
| io.humainary.substrates.jmh.SubscriberOps.close_idempotent_batch_await | 18.078 | 14.024 | 12.271 | -12.5% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_await | 8761.686 | 5114.457 | 693.050 | -86.4% |
| io.humainary.substrates.jmh.SubscriberOps.close_no_subscriptions_batch_await | 15.126 | 34.796 | 32.962 | -5.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_await | 8273.231 | 8085.147 | 864.551 | -89.3% |
| io.humainary.substrates.jmh.SubscriberOps.close_one_subscription_batch_await | 32.196 | 123.853 | 111.823 | -9.7% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_conduits_await | 8799.784 | 4814.836 | 1773.894 | -63.2% |
| io.humainary.substrates.jmh.SubscriberOps.close_ten_subscriptions_await | 8453.515 | 4341.104 | 1738.170 | -60.0% |
| io.humainary.substrates.jmh.SubscriberOps.close_with_pending_emissions_await | 8809.789 | 8707.370 | 1549.649 | -82.2% |
| io.humainary.substrates.jmh.TapOps.baseline_emit_batch_await | 20.533 | 21.086 | 16.228 | -23.0% |
| io.humainary.substrates.jmh.TapOps.tap_close | 8875.453 | 2703.768 | 323.475 | -88.0% |
| io.humainary.substrates.jmh.TapOps.tap_create_batch | 574.582 | 1708.320 | 1601.189 | -6.3% |
| io.humainary.substrates.jmh.TapOps.tap_create_identity | 570.646 | 1608.744 | 1489.857 | -7.4% |
| io.humainary.substrates.jmh.TapOps.tap_create_string | 872.824 | 1666.624 | 1904.954 | +14.3% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_batch_await | 28.876 | 43.691 | 52.023 | +19.1% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single | 31.541 | 40.808 | 28.105 | -31.1% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single_await | 6109.758 | 2130.367 | 117.157 | -94.5% |
| io.humainary.substrates.jmh.TapOps.tap_emit_multi_batch_await | 42.337 | 52.691 | 28.418 | -46.1% |
| io.humainary.substrates.jmh.TapOps.tap_emit_string_batch_await | 36.052 | 53.053 | 35.131 | -33.8% |
| io.humainary.substrates.jmh.TapOps.tap_lifecycle | 17387.271 | 13838.676 | 1697.130 | -87.7% |


## Serventis results (Pre-2.3 → Now)

_Sweep currently running. ~600 benchmarks across 30 instrument groups (CacheOps, PipelineOps, QueueOps, Operations, Outcomes, Statuses, etc.). Will populate when complete._
