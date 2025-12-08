---
description: Run ALL Substrates JMH benchmarks (10 groups) comparing Fullerstack vs Humainary
---

Run all Substrates JMH benchmarks comparing Fullerstack vs Humainary implementation.

**Groups:** CircuitOps, ConduitOps, CortexOps, FlowOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubscriberOps

## Steps

1. Build everything and run ALL Substrates benchmarks (output to JSON file to avoid truncation):
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff /workspaces/fullerstack-humainary/substrates-api-java/jmh/fullerstack-results.json \
  "io.humainary.substrates.jmh.*" 2>&1 | tail -20
```

2. Read the JSON results file and parse Fullerstack results:
```bash
cat /workspaces/fullerstack-humainary/substrates-api-java/jmh/fullerstack-results.json | jq -r '.[] | "\(.benchmark | split(".") | .[-2]).\(.benchmark | split(".") | .[-1]): \(.primaryMetric.score)"' | sort
```

3. Compare with Humainary baseline from BENCHMARKS.md (lines 693-842 contain Substrates results).

4. Present ALL results in a single comparison table (not grouped summaries). Format:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| CircuitOps.conduit_create_close | X.XX | X.XX | +X% | ? |
| CircuitOps.conduit_create_named | X.XX | X.XX | +X% | ? |
| ... (all 160 benchmarks) ... |

Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
Bold the Winner column value for significant wins (>50% difference).

Humainary baseline values (from BENCHMARKS.md):
- CircuitOps: conduit_create_close=281.708, conduit_create_named=282.005, conduit_create_with_flow=280.091, create_and_close=337.343, create_await_close=10730.724, hot_await_queue_drain=5798.639, hot_conduit_create=19.065, hot_conduit_create_named=19.071, hot_conduit_create_with_flow=21.887, hot_pipe_async=8.531, hot_pipe_async_with_flow=10.679, pipe_async=309.065, pipe_async_with_flow=320.440
- ConduitOps: get_by_name=1.882, get_by_name_batch=1.659, get_by_substrate=1.991, get_by_substrate_batch=1.811, get_cached=3.426, get_cached_batch=3.302, subscribe=436.561, subscribe_batch=461.728, subscribe_with_emission_await=5644.470
- CortexOps: circuit=279.172, circuit_batch=280.103, circuit_named=288.306, current=1.090, name_class=1.479, name_enum=2.805, name_iterable=11.234, name_path=1.893, name_path_batch=1.686, name_string=2.847, name_string_batch=2.540, scope=9.284, scope_batch=7.582, scope_named=8.013, slot_boolean=2.413, slot_double=2.431, slot_int=2.121, slot_long=2.423, slot_string=2.429, state_empty=0.440, state_empty_batch=0.001
- FlowOps: baseline_no_flow_await=17.812, flow_combined_diff_guard_await=29.960, flow_combined_diff_sample_await=19.352, flow_combined_guard_limit_await=28.343, flow_diff_await=30.039, flow_guard_await=30.370, flow_limit_await=28.668, flow_sample_await=17.245, flow_sift_await=18.680
- NameOps: name_chained_deep=16.929, name_chaining=8.557, name_chaining_batch=9.043, name_compare=33.066, name_compare_batch=31.976, name_depth=1.698, name_depth_batch=1.399, name_enclosure=0.589, name_from_enum=2.830, name_from_iterable=11.987, name_from_iterator=12.911, name_from_mapped_iterable=11.695, name_from_name=4.218, name_from_string=3.049, name_from_string_batch=2.803, name_interning_chained=12.413, name_interning_same_path=3.543, name_interning_segments=10.105, name_iterate_hierarchy=1.797, name_parsing=1.889, name_parsing_batch=1.682, name_path_generation=31.341, name_path_generation_batch=30.005
- PipeOps: async_emit_batch=11.836, async_emit_batch_await=16.872, async_emit_chained_await=16.932, async_emit_fanout_await=18.715, async_emit_single=10.649, async_emit_single_await=5477.579, async_emit_with_flow_await=21.224, baseline_blackhole=0.267, baseline_counter=1.621, baseline_receptor=0.263, pipe_create=8.747, pipe_create_chained=0.855, pipe_create_with_flow=13.230
- ReservoirOps: baseline_emit_no_reservoir_await=96.224, baseline_emit_no_reservoir_await_batch=18.455, reservoir_burst_then_drain_await=90.187, reservoir_burst_then_drain_await_batch=28.812, reservoir_drain_await=93.801, reservoir_drain_await_batch=28.342, reservoir_emit_drain_cycles_await=328.063, reservoir_emit_with_capture_await=79.974, reservoir_emit_with_capture_await_batch=23.855, reservoir_process_emissions_await=89.081, reservoir_process_emissions_await_batch=26.140, reservoir_process_subjects_await=97.454
- ScopeOps: scope_child_anonymous=18.235, scope_child_anonymous_batch=17.698, scope_child_named=17.100, scope_child_named_batch=19.440, scope_close_idempotent=2.394, scope_close_idempotent_batch=0.033, scope_closure=286.103, scope_closure_batch=307.355, scope_complex=917.038, scope_create_and_close=2.426, scope_create_and_close_batch=0.034, scope_create_named=2.434, scope_create_named_batch=0.033, scope_hierarchy=27.336, scope_hierarchy_batch=26.552, scope_parent_closes_children=43.455, scope_parent_closes_children_batch=42.294, scope_register_multiple=1450.490, scope_register_multiple_batch=1397.956, scope_register_single=287.139, scope_register_single_batch=283.085, scope_with_resources=581.868
- StateOps: slot_name=0.523, slot_name_batch=0.001, slot_type=0.443, slot_value=0.662, slot_value_batch=0.001, state_compact=10.294, state_compact_batch=10.699, state_iterate_slots=2.156, state_slot_add_int=4.759, state_slot_add_int_batch=4.886, state_slot_add_long=4.751, state_slot_add_object=2.563, state_slot_add_object_batch=2.427, state_slot_add_string=4.728, state_value_read=1.486, state_value_read_batch=1.267, state_values_stream=4.980
- SubscriberOps: close_five_conduits_await=8696.402, close_five_subscriptions_await=8630.902, close_idempotent_await=8438.475, close_idempotent_batch_await=17.232, close_no_subscriptions_await=8450.164, close_no_subscriptions_batch_await=14.235, close_one_subscription_await=8437.659, close_one_subscription_batch_await=34.879, close_ten_conduits_await=8514.559, close_ten_subscriptions_await=8726.960, close_with_pending_emissions_await=8713.313
