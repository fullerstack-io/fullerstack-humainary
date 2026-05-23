# Benchmark Comparison: Fullerstack vs Humainary Substrates

**Substrates/Serventis 2.8.0** · `io.fullerstack:fullerstack-substrates:2.8.0-RC1`

> **2.8 status:** the tables below are post-2.4 measurements. 2.5 added new API
> surface (Bank, closeAwait, 6 new Fiber operators) and tightened spec conformance with
> §15.4 callback-isolation guards. 2.6/2.7 added more API surface (Cell, Window,
> Flow.scan, Flow.window, Flow.flow(factory), Fiber.every(Duration), Circuit.cell,
> Circuit.pipe(Name, Receptor)). 2.8 adds the `Ticker` primitive
> (`Circuit.ticker(...)`, SPEC §11.5), a 2-arg `Flow.scan(initial, step)` default-method
> overload, and a sealed `Lookup` hierarchy (`Bank`/`Pool` are the only permitted
> subtypes). All new code paths — existing hot paths (FsPipe.emit, FsChannel.dispatch)
> were not modified by 2.8. The Ticker scheduler runs off the circuit thread (each
> tick enqueues onto ingress), so it doesn't compete with the cyclic dispatch loop.
> Full re-measurement of every group against the post-2.8 baseline is a follow-up
> task; see `docs/2.7-MIGRATION.md` and `docs/2.8-MIGRATION.md` for previous notes.

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
| **2.4: FsDerivedPool three-state lazy storage** | `FsDerivedPool.java` | EMPTY → SINGLE → MULTI lazy cache; only allocates when used. **`*_from_conduit` family: 60 ns → ~3 ns (-95%)** |

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
| io.humainary.substrates.jmh.CircuitOps.create_and_close_batch | 441.745 | — | 1214.599 | — |
| io.humainary.substrates.jmh.CircuitOps.create_multiple_and_close | 2545.594 | — | 5864.964 | — |
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
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit | 1.046 | — | 1.582 | — |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await | 10.342 | 45.766 | 19.459 | -57.5% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_await_batch | 10.398 | 50.735 | 19.468 | -61.6% |
| io.humainary.substrates.jmh.CyclicOps.cyclic_emit_batch | 1.223 | — | 2.069 | — |
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
| io.humainary.substrates.jmh.ScopeOps.scope_complex | 907.760 | — | 4210.770 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close | 2.762 | 0.751 | 2.501 | +233.0% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_and_close_batch | 0.038 | 0.320 | 2.185 | +582.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named | 2.820 | 0.834 | 3.320 | +298.1% |
| io.humainary.substrates.jmh.ScopeOps.scope_create_named_batch | 0.038 | 0.461 | 2.293 | +397.4% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy | 30.365 | 34.953 | 59.691 | +70.8% |
| io.humainary.substrates.jmh.ScopeOps.scope_hierarchy_batch | 31.328 | 33.915 | 59.535 | +75.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children | 47.824 | 47.131 | 77.271 | +63.9% |
| io.humainary.substrates.jmh.ScopeOps.scope_parent_closes_children_batch | 46.967 | 46.348 | 76.789 | +65.7% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple | 1479.062 | — | 3456.055 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_register_multiple_batch | 1491.609 | — | 3207.844 | — |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single | 303.114 | 398.302 | 344.404 | -13.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_register_single_batch | 311.687 | 424.514 | 337.700 | -20.5% |
| io.humainary.substrates.jmh.ScopeOps.scope_with_resources | 600.079 | — | 2585.677 | — |
| io.humainary.substrates.jmh.StateOps.slot_name | 0.581 | 0.908 | 0.869 | -4.3% |
| io.humainary.substrates.jmh.StateOps.slot_name_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.slot_type | 0.497 | 0.893 | 0.851 | -4.7% |
| io.humainary.substrates.jmh.StateOps.slot_value | 0.668 | 1.065 | 1.009 | -5.3% |
| io.humainary.substrates.jmh.StateOps.slot_value_batch | 0.001 | 0.001 | 0.001 | +0.0% |
| io.humainary.substrates.jmh.StateOps.state_compact | 9.538 | — | — | — |
| io.humainary.substrates.jmh.StateOps.state_compact_batch | 10.351 | — | — | — |
| io.humainary.substrates.jmh.StateOps.state_iterate_slots | 2.388 | 4.747 | 4.010 | -15.5% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int | 3.957 | 8.061 | 8.136 | +0.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_int_batch | 4.003 | 7.703 | 8.303 | +7.8% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_long | 3.935 | 8.159 | 8.085 | -0.9% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object | 2.047 | 7.870 | 7.290 | -7.4% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_object_batch | 2.035 | 8.402 | 7.949 | -5.4% |
| io.humainary.substrates.jmh.StateOps.state_slot_add_string | 4.030 | 8.395 | 8.176 | -2.6% |
| io.humainary.substrates.jmh.StateOps.state_value_read | 1.390 | 3.317 | 2.783 | -16.1% |
| io.humainary.substrates.jmh.StateOps.state_value_read_batch | 0.001 | 0.064 | 0.003 | -95.3% |
| io.humainary.substrates.jmh.StateOps.state_values_stream | 4.104 | — | — | — |
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
| io.humainary.substrates.jmh.TapOps.tap_create_identity | 570.646 | — | 1526.271 | — |
| io.humainary.substrates.jmh.TapOps.tap_create_string | 872.824 | 1666.624 | 1609.474 | -3.4% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_batch_await | 28.876 | 43.691 | 15.486 | -64.6% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single | 31.541 | 40.808 | 20.786 | -49.1% |
| io.humainary.substrates.jmh.TapOps.tap_emit_identity_single_await | 6109.758 | 2130.367 | 99.903 | -95.3% |
| io.humainary.substrates.jmh.TapOps.tap_emit_multi_batch_await | 42.337 | 52.691 | 21.275 | -59.6% |
| io.humainary.substrates.jmh.TapOps.tap_emit_string_batch_await | 36.052 | 53.053 | 32.407 | -38.9% |
| io.humainary.substrates.jmh.TapOps.tap_lifecycle | 17387.271 | 13838.676 | 1373.398 | -90.1% |

## Serventis results (vs Humainary baseline)

All times in ns/op. **Δ** = ((Fullerstack - Humainary) / Humainary × 100). Cross-platform: Humainary on Apple M4, Fullerstack on Codespaces 2-vCPU AMD EPYC.

| Benchmark | Humainary (ns/op) | Fullerstack 2.4 (ns/op) | Δ |
|---|---:|---:|---:|
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit | 1.353 | 3.512 | +159.6% |
| io.humainary.serventis.jmh.opt.data.CacheOps.cache_from_conduit_batch | 1.171 | 3.113 | +165.8% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict | 9.046 | 11.775 | +30.2% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_evict_batch | 7.004 | 11.274 | +61.0% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire | 8.151 | 11.507 | +41.2% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_expire_batch | 7.385 | 12.199 | +65.2% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit | 6.738 | 12.499 | +85.5% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_hit_batch | 7.358 | 11.560 | +57.1% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup | 8.853 | 11.701 | +32.2% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_lookup_batch | 6.926 | 11.632 | +67.9% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss | 7.506 | 12.888 | +71.7% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_miss_batch | 7.132 | 11.946 | +67.5% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove | 7.919 | 17.272 | +118.1% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_remove_batch | 7.343 | 11.917 | +62.3% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign | 6.856 | 12.884 | +87.9% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_sign_batch | 7.344 | 11.119 | +51.4% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store | 6.201 | 11.189 | +80.4% |
| io.humainary.serventis.jmh.opt.data.CacheOps.emit_store_batch | 6.855 | 10.901 | +59.0% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate | 7.413 | 11.686 | +57.6% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_aggregate_batch | 7.072 | 10.380 | +46.8% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure | 7.321 | 13.762 | +88.0% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_backpressure_batch | 7.583 | 12.536 | +65.3% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer | 7.576 | 12.477 | +64.7% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_buffer_batch | 6.996 | 15.074 | +115.5% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint | 7.066 | 13.203 | +86.9% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_checkpoint_batch | 6.222 | 13.931 | +123.9% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter | 7.747 | 13.495 | +74.2% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_filter_batch | 6.996 | 12.827 | +83.3% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input | 7.581 | 13.966 | +84.2% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_input_batch | 6.857 | 10.919 | +59.2% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag | 6.750 | 11.894 | +76.2% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_lag_batch | 6.372 | 13.545 | +112.6% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output | 6.333 | 12.418 | +96.1% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_output_batch | 10.399 | 12.028 | +15.7% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow | 6.644 | 11.623 | +74.9% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_overflow_batch | 6.532 | 11.412 | +74.7% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign | 7.708 | 16.259 | +110.9% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_sign_batch | 7.383 | 14.263 | +93.2% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip | 7.657 | 12.331 | +61.0% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_skip_batch | 7.195 | 13.222 | +83.8% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform | 9.077 | 13.618 | +50.0% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_transform_batch | 6.804 | 10.882 | +59.9% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark | 7.634 | 13.317 | +74.4% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.emit_watermark_batch | 8.745 | 11.175 | +27.8% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_etl | 40.895 | 59.887 | +46.4% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_stream | 40.758 | 60.595 | +48.7% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_flow_windowed | 40.524 | 59.366 | +46.5% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit | 1.348 | 3.455 | +156.3% |
| io.humainary.serventis.jmh.opt.data.PipelineOps.pipeline_from_conduit_batch | 1.136 | 3.138 | +176.2% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue | 6.863 | 14.270 | +107.9% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_dequeue_batch | 6.879 | 11.080 | +61.1% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue | 7.608 | 11.628 | +52.8% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_enqueue_batch | 8.659 | 14.101 | +62.8% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow | 6.507 | 17.891 | +175.0% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_overflow_batch | 6.781 | 13.588 | +100.4% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign | 7.695 | 11.124 | +44.6% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_sign_batch | 9.059 | 10.597 | +17.0% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow | 7.554 | 12.153 | +60.9% |
| io.humainary.serventis.jmh.opt.data.QueueOps.emit_underflow_batch | 7.490 | 12.890 | +72.1% |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit | 1.391 | 3.381 | +143.1% |
| io.humainary.serventis.jmh.opt.data.QueueOps.queue_from_conduit_batch | 1.152 | 3.142 | +172.7% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow | 7.362 | 11.484 | +56.0% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_overflow_batch | 7.407 | 11.261 | +52.0% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop | 7.294 | 11.761 | +61.2% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_pop_batch | 8.526 | 11.873 | +39.3% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push | 7.705 | 13.678 | +77.5% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_push_batch | 7.452 | 10.507 | +41.0% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign | 8.680 | 14.230 | +63.9% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_sign_batch | 6.751 | 13.295 | +96.9% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow | 9.841 | 11.189 | +13.7% |
| io.humainary.serventis.jmh.opt.data.StackOps.emit_underflow_batch | 9.139 | 10.803 | +18.2% |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit | 1.382 | 3.677 | +166.1% |
| io.humainary.serventis.jmh.opt.data.StackOps.stack_from_conduit_batch | 1.145 | 3.278 | +186.3% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash | 7.490 | 11.043 | +47.4% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_crash_batch | 7.251 | 12.222 | +68.6% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail | 7.824 | 12.921 | +65.1% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_fail_batch | 8.285 | 12.655 | +52.7% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill | 9.326 | 12.576 | +34.8% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_kill_batch | 6.952 | 11.884 | +70.9% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart | 6.641 | 11.756 | +77.0% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_restart_batch | 6.761 | 13.786 | +103.9% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume | 7.690 | 12.407 | +61.3% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_resume_batch | 7.539 | 10.922 | +44.9% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign | 7.749 | 11.215 | +44.7% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_sign_batch | 7.681 | 11.421 | +48.7% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn | 7.453 | 13.873 | +86.1% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_spawn_batch | 7.499 | 11.196 | +49.3% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start | 7.621 | 12.534 | +64.5% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_start_batch | 7.038 | 15.227 | +116.4% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop | 7.972 | 11.781 | +47.8% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_stop_batch | 6.736 | 11.458 | +70.1% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend | 6.506 | 12.617 | +93.9% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.emit_suspend_batch | 7.847 | 11.797 | +50.3% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit | 1.357 | 3.456 | +154.7% |
| io.humainary.serventis.jmh.opt.exec.ProcessOps.process_from_conduit_batch | 1.141 | 3.223 | +182.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call | 7.879 | 14.958 | +89.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_call_batch | 6.215 | 12.870 | +107.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called | 7.630 | 13.092 | +71.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_called_batch | 6.829 | 10.922 | +59.9% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay | 6.835 | 12.714 | +86.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delay_batch | 9.385 | 13.500 | +43.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed | 8.516 | 11.362 | +33.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_delayed_batch | 8.814 | 12.219 | +38.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard | 6.132 | 13.008 | +112.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discard_batch | 16.966 | 15.191 | -10.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded | 6.767 | 12.013 | +77.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_discarded_batch | 6.782 | 13.260 | +95.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect | 6.547 | 12.588 | +92.3% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnect_batch | 8.487 | 13.590 | +60.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected | 7.141 | 12.076 | +69.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_disconnected_batch | 8.594 | 12.149 | +41.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire | 6.931 | 15.018 | +116.7% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expire_batch | 7.448 | 11.767 | +58.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired | 6.568 | 16.593 | +152.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_expired_batch | 8.835 | 12.791 | +44.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail | 6.729 | 14.128 | +110.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_fail_batch | 6.811 | 19.303 | +183.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed | 7.471 | 20.407 | +173.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_failed_batch | 8.683 | 16.983 | +95.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse | 6.934 | 12.670 | +82.7% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recourse_batch | 6.356 | 12.570 | +97.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed | 7.562 | 12.375 | +63.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_recoursed_batch | 6.468 | 13.069 | +102.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect | 7.138 | 14.068 | +97.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirect_batch | 5.954 | 14.550 | +144.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected | 7.340 | 11.817 | +61.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_redirected_batch | 8.488 | 11.485 | +35.3% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject | 9.564 | 11.874 | +24.2% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_reject_batch | 6.935 | 11.570 | +66.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected | 8.050 | 11.888 | +47.7% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_rejected_batch | 7.675 | 14.830 | +93.2% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume | 6.705 | 15.750 | +134.9% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resume_batch | 9.478 | 14.858 | +56.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed | 6.463 | 13.081 | +102.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_resumed_batch | 10.307 | 11.785 | +14.3% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried | 7.773 | 12.580 | +61.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retried_batch | 7.250 | 11.471 | +58.2% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry | 6.938 | 11.821 | +70.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_retry_batch | 8.400 | 11.506 | +37.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule | 7.533 | 12.844 | +70.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_schedule_batch | 6.653 | 12.543 | +88.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled | 7.109 | 11.933 | +67.9% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_scheduled_batch | 5.994 | 15.316 | +155.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal | 7.050 | 14.516 | +105.9% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_signal_batch | 9.171 | 10.808 | +17.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start | 7.534 | 12.150 | +61.3% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_start_batch | 8.414 | 13.515 | +60.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started | 7.767 | 13.345 | +71.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_started_batch | 6.316 | 13.502 | +113.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop | 7.281 | 14.709 | +102.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stop_batch | 8.750 | 11.898 | +36.0% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped | 6.505 | 12.976 | +99.5% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_stopped_batch | 8.306 | 11.138 | +34.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded | 7.808 | 15.003 | +92.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_succeeded_batch | 8.303 | 12.501 | +50.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success | 6.718 | 11.426 | +70.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_success_batch | 7.239 | 12.175 | +68.2% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend | 7.683 | 12.362 | +60.9% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspend_batch | 6.523 | 11.751 | +80.1% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended | 6.493 | 11.339 | +74.6% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.emit_suspended_batch | 8.005 | 11.722 | +46.4% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit | 1.344 | 3.545 | +163.8% |
| io.humainary.serventis.jmh.opt.exec.ServiceOps.service_from_conduit_batch | 1.155 | 3.100 | +168.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel | 7.556 | 13.076 | +73.1% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_cancel_batch | 7.043 | 10.570 | +50.1% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete | 7.224 | 13.622 | +88.6% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_complete_batch | 5.403 | 11.019 | +103.9% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail | 6.525 | 13.733 | +110.5% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_fail_batch | 7.172 | 11.645 | +62.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress | 7.912 | 13.787 | +74.3% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_progress_batch | 7.008 | 11.865 | +69.3% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject | 7.221 | 12.497 | +73.1% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_reject_batch | 7.142 | 13.618 | +90.7% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume | 8.975 | 12.082 | +34.6% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_resume_batch | 7.098 | 14.731 | +107.5% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule | 7.783 | 11.394 | +46.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_schedule_batch | 7.009 | 14.471 | +106.5% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign | 7.682 | 11.674 | +52.0% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_sign_batch | 9.333 | 13.133 | +40.7% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start | 7.678 | 12.298 | +60.2% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_start_batch | 9.241 | 11.507 | +24.5% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit | 7.601 | 13.308 | +75.1% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_submit_batch | 7.662 | 13.407 | +75.0% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend | 7.984 | 12.355 | +54.7% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_suspend_batch | 6.845 | 14.195 | +107.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout | 7.807 | 14.946 | +91.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.emit_timeout_batch | 6.958 | 12.999 | +86.8% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit | 1.344 | 3.352 | +149.4% |
| io.humainary.serventis.jmh.opt.exec.TaskOps.task_from_conduit_batch | 1.153 | 3.128 | +171.3% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline | 7.362 | 12.707 | +72.6% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_deadline_batch | 7.005 | 13.652 | +94.9% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_meet_threshold | 7.124 | 12.778 | +79.4% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_deadline | 6.263 | 14.842 | +137.0% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_miss_threshold | 7.294 | 14.198 | +94.7% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal | 7.370 | 13.212 | +79.3% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.emit_signal_batch | 8.339 | 11.177 | +34.0% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit | 1.354 | 3.609 | +166.5% |
| io.humainary.serventis.jmh.opt.exec.TimerOps.timer_from_conduit_batch | 1.141 | 3.139 | +175.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator | 7.221 | 12.722 | +76.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_coordinator_batch | 7.097 | 13.955 | +96.6% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant | 7.002 | 12.338 | +76.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_abort_participant_batch | 9.031 | 11.832 | +31.0% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator | 8.836 | 12.295 | +39.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_coordinator_batch | 6.615 | 14.753 | +123.0% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant | 8.660 | 12.994 | +50.0% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_commit_participant_batch | 6.345 | 15.365 | +142.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator | 7.262 | 11.653 | +60.5% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_coordinator_batch | 8.405 | 13.509 | +60.7% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant | 6.847 | 12.542 | +83.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_compensate_participant_batch | 6.600 | 12.697 | +92.4% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator | 7.489 | 13.468 | +79.8% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_coordinator_batch | 7.132 | 24.621 | +245.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant | 10.811 | 11.581 | +7.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_conflict_participant_batch | 6.604 | 11.429 | +73.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator | 7.350 | 12.973 | +76.5% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_coordinator_batch | 8.636 | 11.388 | +31.9% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant | 8.278 | 13.279 | +60.4% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_expire_participant_batch | 6.538 | 13.527 | +106.9% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator | 6.756 | 12.344 | +82.7% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_coordinator_batch | 7.753 | 14.535 | +87.5% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant | 7.358 | 12.577 | +70.9% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_prepare_participant_batch | 6.765 | 14.462 | +113.8% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator | 7.906 | 11.950 | +51.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_coordinator_batch | 6.678 | 11.093 | +66.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant | 7.307 | 12.405 | +69.8% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_rollback_participant_batch | 8.569 | 12.955 | +51.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal | 5.919 | 14.207 | +140.0% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_signal_batch | 8.092 | 12.668 | +56.5% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator | 6.998 | 11.850 | +69.3% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_coordinator_batch | 9.257 | 11.038 | +19.2% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant | 7.606 | 14.106 | +85.5% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.emit_start_participant_batch | 7.003 | 11.200 | +59.9% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit | 1.364 | 3.480 | +155.1% |
| io.humainary.serventis.jmh.opt.exec.TransactionOps.transaction_from_conduit_batch | 1.147 | 3.212 | +180.0% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit | 1.348 | 3.456 | +156.4% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.breaker_from_conduit_batch | 1.149 | 3.322 | +189.1% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close | 7.780 | 12.709 | +63.4% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_close_batch | 7.777 | 12.626 | +62.4% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open | 9.152 | 13.452 | +47.0% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_half_open_batch | 7.402 | 14.490 | +95.8% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open | 6.942 | 11.823 | +70.3% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_open_batch | 7.667 | 12.118 | +58.1% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe | 8.744 | 12.166 | +39.1% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_probe_batch | 7.195 | 15.305 | +112.7% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset | 7.197 | 14.786 | +105.4% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_reset_batch | 7.049 | 14.043 | +99.2% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign | 7.790 | 12.753 | +63.7% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_sign_batch | 8.331 | 14.659 | +76.0% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip | 7.879 | 11.439 | +45.2% |
| io.humainary.serventis.jmh.opt.flow.BreakerOps.emit_trip_batch | 7.314 | 11.636 | +59.1% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_egress | 7.781 | 11.103 | +42.7% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_ingress | 8.058 | 12.145 | +50.7% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_fail_transit | 6.924 | 16.300 | +135.4% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal | 7.157 | 11.623 | +62.4% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_signal_batch | 8.547 | 11.251 | +31.6% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_egress | 7.225 | 13.114 | +81.5% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress | 8.325 | 12.515 | +50.3% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_ingress_batch | 6.571 | 12.262 | +86.6% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.emit_success_transit | 8.025 | 15.550 | +93.8% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit | 1.355 | 3.565 | +163.1% |
| io.humainary.serventis.jmh.opt.flow.FlowOps.flow_from_conduit_batch | 1.148 | 3.122 | +172.0% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt | 7.049 | 12.232 | +73.5% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_corrupt_batch | 8.520 | 11.039 | +29.6% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop | 9.104 | 11.448 | +25.7% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_drop_batch | 7.302 | 11.787 | +61.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward | 7.280 | 13.109 | +80.1% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_forward_batch | 6.910 | 11.091 | +60.5% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment | 7.040 | 13.125 | +86.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_fragment_batch | 6.543 | 12.145 | +85.6% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble | 7.018 | 12.502 | +78.1% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reassemble_batch | 7.884 | 11.147 | +41.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive | 7.471 | 13.067 | +74.9% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_receive_batch | 6.910 | 12.244 | +77.2% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder | 7.343 | 13.097 | +78.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_reorder_batch | 6.995 | 12.355 | +76.6% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route | 7.399 | 11.496 | +55.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_route_batch | 6.992 | 13.368 | +91.2% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send | 7.134 | 10.816 | +51.6% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_send_batch | 7.638 | 12.142 | +59.0% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign | 7.350 | 13.862 | +88.6% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.emit_sign_batch | 7.467 | 12.339 | +65.2% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit | 1.377 | 3.407 | +147.4% |
| io.humainary.serventis.jmh.opt.flow.RouterOps.router_from_conduit_batch | 1.170 | 3.155 | +169.7% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract | 6.541 | 12.751 | +94.9% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_contract_batch | 7.421 | 10.877 | +46.6% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny | 7.702 | 11.712 | +52.1% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_deny_batch | 7.118 | 16.638 | +133.7% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain | 7.859 | 11.648 | +48.2% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drain_batch | 7.153 | 11.976 | +67.4% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop | 6.755 | 12.740 | +88.6% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_drop_batch | 8.994 | 10.644 | +18.3% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand | 7.188 | 11.511 | +60.1% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_expand_batch | 6.608 | 12.590 | +90.5% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass | 7.247 | 11.949 | +64.9% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_pass_batch | 7.354 | 10.962 | +49.1% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign | 9.236 | 12.630 | +36.7% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.emit_sign_batch | 7.520 | 14.602 | +94.2% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit | 1.352 | 3.807 | +181.6% |
| io.humainary.serventis.jmh.opt.flow.ValveOps.valve_from_conduit_batch | 1.145 | 3.080 | +169.0% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider | 6.465 | 12.693 | +96.3% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_provider_batch | 6.841 | 14.530 | +112.4% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver | 7.100 | 13.366 | +88.3% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_contract_receiver_batch | 8.485 | 14.112 | +66.3% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange | 8.763 | 11.312 | +29.1% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_full_exchange_batch | 8.603 | 11.901 | +38.3% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal | 7.163 | 12.123 | +69.2% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_signal_batch | 6.305 | 12.985 | +105.9% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider | 7.568 | 12.607 | +66.6% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_provider_batch | 7.470 | 13.245 | +77.3% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver | 8.049 | 13.381 | +66.2% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.emit_transfer_receiver_batch | 7.510 | 12.720 | +69.4% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit | 1.359 | 3.395 | +149.8% |
| io.humainary.serventis.jmh.opt.pool.ExchangeOps.exchange_from_conduit_batch | 1.125 | 3.126 | +177.9% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire | 9.673 | 11.518 | +19.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquire_batch | 8.173 | 12.099 | +48.0% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired | 6.706 | 12.278 | +83.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_acquired_batch | 6.685 | 13.393 | +100.3% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied | 7.739 | 11.911 | +53.9% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_denied_batch | 9.201 | 11.347 | +23.3% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny | 7.008 | 11.046 | +57.6% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_deny_batch | 8.468 | 12.267 | +44.9% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire | 7.842 | 13.692 | +74.6% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expire_batch | 8.851 | 11.674 | +31.9% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired | 7.290 | 12.058 | +65.4% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_expired_batch | 8.610 | 11.940 | +38.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend | 7.073 | 12.581 | +77.9% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extend_batch | 6.219 | 11.093 | +78.4% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended | 7.619 | 12.666 | +66.2% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_extended_batch | 7.633 | 12.146 | +59.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant | 6.284 | 12.867 | +104.8% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_grant_batch | 8.552 | 13.635 | +59.4% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted | 6.538 | 12.189 | +86.4% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_granted_batch | 7.000 | 11.781 | +68.3% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe | 7.525 | 12.773 | +69.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probe_batch | 7.439 | 11.568 | +55.5% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed | 7.264 | 11.894 | +63.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_probed_batch | 6.362 | 13.340 | +109.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release | 8.706 | 12.547 | +44.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_release_batch | 6.035 | 13.264 | +119.8% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released | 6.901 | 11.489 | +66.5% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_released_batch | 6.603 | 12.018 | +82.0% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew | 7.791 | 12.160 | +56.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renew_batch | 6.419 | 12.649 | +97.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed | 8.037 | 12.842 | +59.8% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_renewed_batch | 7.063 | 13.665 | +93.5% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke | 7.082 | 12.232 | +72.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoke_batch | 6.604 | 12.841 | +94.4% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked | 8.674 | 12.887 | +48.6% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_revoked_batch | 6.644 | 12.015 | +80.8% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal | 8.179 | 12.930 | +58.1% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.emit_signal_batch | 7.145 | 13.563 | +89.8% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit | 1.347 | 3.471 | +157.7% |
| io.humainary.serventis.jmh.opt.pool.LeaseOps.lease_from_conduit_batch | 1.156 | 3.167 | +174.0% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow | 7.367 | 13.221 | +79.5% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_borrow_batch | 7.832 | 10.917 | +39.4% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract | 8.736 | 14.152 | +62.0% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_contract_batch | 7.912 | 12.825 | +62.1% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand | 7.779 | 12.049 | +54.9% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_expand_batch | 6.867 | 12.112 | +76.4% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim | 7.414 | 11.645 | +57.1% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_reclaim_batch | 8.942 | 14.306 | +60.0% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign | 7.469 | 11.491 | +53.8% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.emit_sign_batch | 7.126 | 11.694 | +64.1% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit | 1.346 | 3.437 | +155.3% |
| io.humainary.serventis.jmh.opt.pool.PoolOps.pool_from_conduit_batch | 1.137 | 3.138 | +176.0% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire | 7.375 | 12.126 | +64.4% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_acquire_batch | 7.248 | 10.973 | +51.4% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt | 7.709 | 11.473 | +48.8% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_attempt_batch | 6.947 | 11.215 | +61.4% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny | 9.681 | 13.164 | +36.0% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_deny_batch | 7.453 | 14.878 | +99.6% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant | 6.987 | 12.078 | +72.9% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_grant_batch | 7.592 | 11.258 | +48.3% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release | 7.871 | 11.183 | +42.1% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_release_batch | 6.729 | 11.737 | +74.4% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign | 7.441 | 11.364 | +52.7% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_sign_batch | 7.980 | 11.795 | +47.8% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout | 7.801 | 12.048 | +54.4% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.emit_timeout_batch | 7.249 | 13.264 | +83.0% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit | 1.342 | 3.674 | +173.8% |
| io.humainary.serventis.jmh.opt.pool.ResourceOps.resource_from_conduit_batch | 1.127 | 3.130 | +177.7% |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit | 1.373 | 3.498 | +154.8% |
| io.humainary.serventis.jmh.opt.role.ActorOps.actor_from_conduit_batch | 1.153 | 3.150 | +173.2% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge | 8.739 | 12.821 | +46.7% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_acknowledge_batch | 6.997 | 12.724 | +81.8% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm | 7.958 | 12.833 | +61.3% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_affirm_batch | 6.863 | 11.984 | +74.6% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask | 7.722 | 12.649 | +63.8% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_ask_batch | 6.219 | 11.597 | +86.5% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify | 7.784 | 12.686 | +63.0% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_clarify_batch | 6.323 | 11.280 | +78.4% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command | 7.159 | 11.232 | +56.9% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_command_batch | 8.264 | 10.887 | +31.7% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver | 8.734 | 11.793 | +35.0% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deliver_batch | 6.963 | 13.780 | +97.9% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny | 9.091 | 12.085 | +32.9% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_deny_batch | 5.871 | 11.268 | +91.9% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain | 7.668 | 10.964 | +43.0% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_explain_batch | 7.186 | 11.443 | +59.2% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise | 7.413 | 13.051 | +76.1% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_promise_batch | 7.169 | 14.263 | +99.0% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report | 7.806 | 11.089 | +42.1% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_report_batch | 6.959 | 12.174 | +74.9% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request | 6.918 | 11.647 | +68.4% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_request_batch | 7.285 | 12.677 | +74.0% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign | 7.335 | 12.073 | +64.6% |
| io.humainary.serventis.jmh.opt.role.ActorOps.emit_sign_batch | 6.385 | 14.365 | +125.0% |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit | 1.362 | 3.457 | +153.8% |
| io.humainary.serventis.jmh.opt.role.AgentOps.agent_from_conduit_batch | 1.147 | 3.058 | +166.6% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept | 7.733 | 12.332 | +59.5% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accept_batch | 8.297 | 13.203 | +59.1% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted | 7.649 | 12.586 | +64.5% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_accepted_batch | 6.068 | 13.095 | +115.8% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach | 7.254 | 12.057 | +66.2% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breach_batch | 6.532 | 12.444 | +90.5% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached | 6.931 | 12.067 | +74.1% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_breached_batch | 6.278 | 14.058 | +123.9% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend | 7.970 | 14.397 | +80.6% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depend_batch | 7.280 | 13.195 | +81.2% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended | 7.083 | 11.407 | +61.0% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_depended_batch | 8.275 | 11.610 | +40.3% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill | 7.860 | 12.409 | +57.9% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfill_batch | 6.875 | 13.261 | +92.9% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled | 6.715 | 11.708 | +74.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_fulfilled_batch | 6.201 | 17.200 | +177.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire | 7.292 | 12.084 | +65.7% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquire_batch | 7.781 | 22.350 | +187.2% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired | 6.987 | 13.436 | +92.3% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_inquired_batch | 8.541 | 12.138 | +42.1% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe | 7.376 | 16.339 | +121.5% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observe_batch | 8.519 | 11.849 | +39.1% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed | 6.607 | 12.492 | +89.1% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_observed_batch | 6.069 | 12.425 | +104.7% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer | 7.820 | 12.489 | +59.7% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offer_batch | 6.625 | 10.943 | +65.2% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered | 7.403 | 11.431 | +54.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_offered_batch | 8.797 | 11.557 | +31.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise | 6.408 | 12.352 | +92.8% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promise_batch | 6.534 | 13.787 | +111.0% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised | 6.464 | 15.099 | +133.6% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_promised_batch | 7.407 | 11.440 | +54.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract | 7.744 | 12.083 | +56.0% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retract_batch | 7.116 | 10.983 | +54.3% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted | 8.091 | 13.307 | +64.5% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_retracted_batch | 7.175 | 13.412 | +86.9% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal | 7.622 | 13.543 | +77.7% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_signal_batch | 6.622 | 10.718 | +61.9% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate | 7.928 | 13.111 | +65.4% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validate_batch | 7.590 | 15.658 | +106.3% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated | 7.222 | 11.748 | +62.7% |
| io.humainary.serventis.jmh.opt.role.AgentOps.emit_validated_batch | 7.477 | 11.493 | +53.7% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit | 1.338 | 3.589 | +168.2% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.atomic_from_conduit_batch | 1.157 | 3.223 | +178.6% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt | 7.337 | 12.444 | +69.6% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_attempt_batch | 8.069 | 11.173 | +38.5% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_backoff | 7.188 | 11.294 | +57.1% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_exhaust | 9.133 | 11.725 | +28.4% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail | 6.636 | 11.349 | +71.0% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_fail_batch | 7.468 | 12.383 | +65.8% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_park | 7.680 | 13.148 | +71.2% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign | 7.651 | 11.841 | +54.8% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_sign_batch | 8.452 | 14.081 | +66.6% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin | 9.846 | 12.742 | +29.4% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_spin_batch | 6.925 | 13.448 | +94.2% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success | 7.038 | 12.452 | +76.9% |
| io.humainary.serventis.jmh.opt.sync.AtomicOps.emit_success_batch | 7.458 | 13.517 | +81.2% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon | 7.662 | 12.052 | +57.3% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_abandon_batch | 7.373 | 12.173 | +65.1% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive | 6.551 | 11.332 | +73.0% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_arrive_batch | 7.510 | 10.576 | +40.8% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await | 6.588 | 12.252 | +86.0% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_await_batch | 7.204 | 11.452 | +59.0% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release | 7.454 | 13.357 | +79.2% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_release_batch | 6.953 | 12.243 | +76.1% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset | 6.796 | 10.968 | +61.4% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_reset_batch | 8.874 | 13.390 | +50.9% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign | 6.909 | 12.314 | +78.2% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_sign_batch | 7.597 | 12.208 | +60.7% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout | 8.869 | 11.572 | +30.5% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.emit_timeout_batch | 6.132 | 12.418 | +102.5% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit | 1.352 | 3.426 | +153.4% |
| io.humainary.serventis.jmh.opt.sync.LatchOps.latch_from_conduit_batch | 1.146 | 3.073 | +168.2% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon | 7.073 | 11.993 | +69.6% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_abandon_batch | 6.978 | 10.644 | +52.5% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire | 7.335 | 13.407 | +82.8% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_acquire_batch | 7.907 | 12.989 | +64.3% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt | 7.641 | 13.069 | +71.0% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_attempt_batch | 6.966 | 10.816 | +55.3% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest | 7.202 | 14.036 | +94.9% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_contest_batch | 7.252 | 13.527 | +86.5% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny | 7.257 | 12.292 | +69.4% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_deny_batch | 6.203 | 14.056 | +126.6% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade | 8.305 | 12.426 | +49.6% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_downgrade_batch | 7.182 | 13.676 | +90.4% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant | 7.247 | 12.052 | +66.3% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_grant_batch | 7.423 | 12.457 | +67.8% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release | 7.550 | 11.592 | +53.5% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_release_batch | 7.611 | 13.070 | +71.7% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign | 7.547 | 11.070 | +46.7% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_sign_batch | 8.317 | 13.363 | +60.7% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout | 7.192 | 11.897 | +65.4% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_timeout_batch | 6.916 | 10.861 | +57.0% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade | 7.242 | 12.270 | +69.4% |
| io.humainary.serventis.jmh.opt.sync.LockOps.emit_upgrade_batch | 7.558 | 14.467 | +91.4% |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit | 1.368 | 3.546 | +159.2% |
| io.humainary.serventis.jmh.opt.sync.LockOps.lock_from_conduit_batch | 1.138 | 3.153 | +177.1% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit | 1.365 | 3.421 | +150.6% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.counter_from_conduit_batch | 1.117 | 3.250 | +191.0% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment | 7.046 | 13.000 | +84.5% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_increment_batch | 6.537 | 11.848 | +81.2% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow | 6.952 | 12.191 | +75.4% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_overflow_batch | 7.253 | 13.544 | +86.7% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset | 7.472 | 11.804 | +58.0% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_reset_batch | 7.063 | 13.421 | +90.0% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign | 6.980 | 14.362 | +105.8% |
| io.humainary.serventis.jmh.opt.tool.CounterOps.emit_sign_batch | 6.939 | 13.947 | +101.0% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement | 7.889 | 12.139 | +53.9% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_decrement_batch | 6.537 | 12.169 | +86.2% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment | 7.800 | 11.721 | +50.3% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_increment_batch | 6.838 | 10.681 | +56.2% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow | 6.848 | 12.100 | +76.7% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_overflow_batch | 8.874 | 11.806 | +33.0% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset | 8.011 | 12.158 | +51.8% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_reset_batch | 7.112 | 12.924 | +81.7% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign | 8.725 | 15.587 | +78.6% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_sign_batch | 6.593 | 15.007 | +127.6% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow | 7.306 | 12.609 | +72.6% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.emit_underflow_batch | 7.175 | 15.080 | +110.2% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit | 1.351 | 3.802 | +181.4% |
| io.humainary.serventis.jmh.opt.tool.GaugeOps.gauge_from_conduit_batch | 1.155 | 3.125 | +170.6% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug | 6.181 | 13.136 | +112.5% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_debug_batch | 7.385 | 13.764 | +86.4% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info | 7.352 | 13.628 | +85.4% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_info_batch | 8.078 | 12.169 | +50.6% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe | 6.782 | 14.249 | +110.1% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_severe_batch | 9.783 | 13.055 | +33.4% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign | 7.261 | 12.184 | +67.8% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_sign_batch | 6.756 | 12.485 | +84.8% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning | 7.512 | 12.858 | +71.2% |
| io.humainary.serventis.jmh.opt.tool.LogOps.emit_warning_batch | 6.511 | 11.333 | +74.1% |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit | 1.355 | 3.637 | +168.4% |
| io.humainary.serventis.jmh.opt.tool.LogOps.log_from_conduit_batch | 1.140 | 3.157 | +176.9% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect | 6.544 | 12.848 | +96.3% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connect_batch | 6.815 | 11.231 | +64.8% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected | 7.465 | 13.921 | +86.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_connected_batch | 8.277 | 16.019 | +93.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect | 8.250 | 16.472 | +99.7% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnect_batch | 7.223 | 13.485 | +86.7% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected | 5.961 | 12.175 | +104.2% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_disconnected_batch | 8.650 | 13.483 | +55.9% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail | 7.272 | 11.434 | +57.2% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_fail_batch | 8.634 | 11.399 | +32.0% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed | 7.499 | 12.970 | +73.0% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_failed_batch | 7.223 | 12.739 | +76.4% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process | 7.843 | 11.759 | +49.9% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_process_batch | 6.426 | 11.959 | +86.1% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed | 7.360 | 12.520 | +70.1% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_processed_batch | 9.972 | 12.778 | +28.1% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_receive_batch | 7.994 | 13.968 | +74.7% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_received_batch | 7.359 | 12.915 | +75.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal | 9.141 | 13.024 | +42.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_signal_batch | 6.192 | 11.853 | +91.4% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed | 7.707 | 12.789 | +65.9% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeed_batch | 8.582 | 11.563 | +34.7% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded | 8.910 | 11.875 | +33.3% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_succeeded_batch | 7.332 | 12.206 | +66.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer | 7.489 | 12.162 | +62.4% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_inbound | 6.770 | 12.557 | +85.5% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transfer_outbound | 7.682 | 12.771 | +66.2% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transferred | 7.571 | 13.655 | +80.4% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmit_batch | 6.822 | 14.536 | +113.1% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.emit_transmitted_batch | 8.108 | 14.842 | +83.1% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit | 1.357 | 3.476 | +156.2% |
| io.humainary.serventis.jmh.opt.tool.ProbeOps.probe_from_conduit_batch | 1.137 | 3.103 | +172.9% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline | 7.473 | 12.663 | +69.5% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_baseline_batch | 7.918 | 12.467 | +57.5% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target | 7.433 | 13.461 | +81.1% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_target_batch | 7.046 | 11.933 | +69.4% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold | 7.652 | 13.976 | +82.6% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_above_threshold_batch | 6.669 | 11.817 | +77.2% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline | 9.666 | 13.993 | +44.8% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_baseline_batch | 6.897 | 12.985 | +88.3% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target | 8.105 | 12.996 | +60.3% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_target_batch | 7.143 | 12.190 | +70.7% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold | 7.426 | 14.732 | +98.4% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_below_threshold_batch | 8.930 | 11.011 | +23.3% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline | 6.611 | 12.533 | +89.6% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_baseline_batch | 8.779 | 12.232 | +39.3% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target | 7.184 | 14.215 | +97.9% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_target_batch | 8.369 | 11.370 | +35.9% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold | 7.987 | 11.700 | +46.5% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_nominal_threshold_batch | 9.738 | 13.748 | +41.2% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal | 6.822 | 13.672 | +100.4% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.emit_signal_batch | 7.443 | 11.289 | +51.7% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit | 1.348 | 3.428 | +154.3% |
| io.humainary.serventis.jmh.opt.tool.SensorOps.sensor_from_conduit_batch | 1.148 | 3.088 | +169.0% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin | 7.344 | 13.092 | +78.3% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_begin_batch | 10.262 | 11.306 | +10.2% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end | 7.550 | 13.658 | +80.9% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_end_batch | 9.645 | 11.389 | +18.1% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign | 7.161 | 12.593 | +75.9% |
| io.humainary.serventis.jmh.sdk.OperationOps.emit_sign_batch | 9.041 | 13.899 | +53.7% |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit | 1.341 | 3.679 | +174.3% |
| io.humainary.serventis.jmh.sdk.OperationOps.operation_from_conduit_batch | 1.141 | 3.126 | +174.0% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail | 6.960 | 11.980 | +72.1% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_fail_batch | 7.183 | 13.704 | +90.8% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign | 7.690 | 12.545 | +63.1% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_sign_batch | 5.996 | 12.875 | +114.7% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success | 8.340 | 11.893 | +42.6% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.emit_success_batch | 9.174 | 11.959 | +30.4% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit | 1.340 | 3.442 | +156.9% |
| io.humainary.serventis.jmh.sdk.OutcomeOps.outcome_from_conduit_batch | 1.137 | 3.103 | +172.9% |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_mixed_pattern | 0.199 | 0.340 | +70.9% |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single | 0.665 | 1.096 | +64.8% |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_single_batch | 0.021 | 0.028 | +33.3% |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_varied_batch | 1.560 | 2.138 | +37.1% |
| io.humainary.serventis.jmh.sdk.SignalSetOps.get_worst_case | 1.056 | 1.910 | +80.9% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical | 7.072 | 11.956 | +69.1% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_critical_batch | 7.042 | 13.109 | +86.2% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal | 8.247 | 13.293 | +61.2% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_normal_batch | 10.068 | 13.702 | +36.1% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal | 8.144 | 12.419 | +52.5% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_signal_batch | 7.036 | 12.376 | +75.9% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning | 6.536 | 12.989 | +98.7% |
| io.humainary.serventis.jmh.sdk.SituationOps.emit_warning_batch | 7.419 | 12.148 | +63.7% |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit | 1.334 | 3.454 | +158.9% |
| io.humainary.serventis.jmh.sdk.SituationOps.situation_from_conduit_batch | 1.149 | 3.119 | +171.5% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed | 6.361 | 13.413 | +110.9% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_converging_confirmed_batch | 6.897 | 14.309 | +107.5% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative | 8.180 | 12.705 | +55.3% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_defective_tentative_batch | 7.938 | 12.720 | +60.2% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured | 6.726 | 14.227 | +111.5% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_degraded_measured_batch | 23.006 | 11.913 | -48.2% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed | 7.206 | 12.934 | +79.5% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_down_confirmed_batch | 6.370 | 12.071 | +89.5% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal | 8.008 | 14.471 | +80.7% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_signal_batch | 8.521 | 11.331 | +33.0% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed | 6.636 | 12.138 | +82.9% |
| io.humainary.serventis.jmh.sdk.StatusOps.emit_stable_confirmed_batch | 7.635 | 12.039 | +57.7% |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit | 1.346 | 3.392 | +152.0% |
| io.humainary.serventis.jmh.sdk.StatusOps.status_from_conduit_batch | 1.155 | 3.093 | +167.8% |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_fail_divided | — | 12.432 | — |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal | 8.422 | 12.648 | +50.2% |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_signal_batch | 9.125 | 11.894 | +30.3% |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_majority | — | 13.373 | — |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous | — | 12.526 | — |
| io.humainary.serventis.jmh.sdk.SurveyOps.emit_success_unanimous_batch | — | 11.705 | — |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit | 1.337 | 112.125 | +8286.3% |
| io.humainary.serventis.jmh.sdk.SurveyOps.survey_from_conduit_batch | 1.150 | 114.696 | +9873.6% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow | 6.095 | 12.425 | +103.9% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_alarm_flow_batch | 6.296 | 11.552 | +83.5% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link | 7.679 | 13.071 | +70.2% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_fault_link_batch | 6.924 | 13.410 | +93.7% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time | 7.391 | 11.749 | +59.0% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_limit_time_batch | 7.271 | 14.151 | +94.6% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space | 7.089 | 13.371 | +88.6% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_normal_space_batch | 6.976 | 10.807 | +54.9% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal | 6.737 | 11.904 | +76.7% |
| io.humainary.serventis.jmh.sdk.SystemOps.emit_signal_batch | 8.477 | 10.941 | +29.1% |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit | 1.344 | 3.530 | +162.6% |
| io.humainary.serventis.jmh.sdk.SystemOps.system_from_conduit_batch | 1.142 | 3.190 | +179.3% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_chaos | 8.537 | 11.585 | +35.7% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_cycle | 7.485 | 12.135 | +62.1% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift | 6.932 | 12.925 | +86.5% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_drift_batch | 6.725 | 11.852 | +76.2% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign | 7.491 | 11.780 | +57.3% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_sign_batch | 7.348 | 11.257 | +53.2% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_spike | 6.980 | 11.640 | +66.8% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable | 8.564 | 12.752 | +48.9% |
| io.humainary.serventis.jmh.sdk.TrendOps.emit_stable_batch | 8.340 | 13.749 | +64.9% |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit | 1.347 | 3.431 | +154.7% |
| io.humainary.serventis.jmh.sdk.TrendOps.trend_from_conduit_batch | 1.146 | 3.116 | +171.9% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit | 1.352 | 235.994 | +17355.2% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.cycle_from_conduit_batch | 1.128 | 250.894 | +22142.4% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat | 7.463 | 12.296 | +64.8% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_repeat_batch | 6.634 | 12.255 | +84.7% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return | 6.926 | 12.796 | +84.8% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_return_batch | 6.438 | 14.431 | +124.2% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal | 6.054 | 12.710 | +109.9% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_signal_batch | 7.675 | 13.387 | +74.4% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single | 7.459 | 13.730 | +84.1% |
| io.humainary.serventis.jmh.sdk.meta.CycleOps.emit_single_batch | 7.991 | 12.516 | +56.6% |
