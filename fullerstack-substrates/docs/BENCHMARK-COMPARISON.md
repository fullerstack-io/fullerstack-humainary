# Benchmark Comparison: Fullerstack vs Humainary Substrates

**Date:** 2026-02-17

## JMH Configuration

| Parameter | Fullerstack | Humainary |
|-----------|:-----------:|:---------:|
| Forks (`-f`) | 1 | 1 |
| Warmup iterations (`-wi`) | 3 | 5 |
| Measurement iterations (`-i`) | 5 | 5 |
| Mode | avgt | avgt |
| Units | ns/op | ns/op |
| Blackholes | Compiler (experimental) | Compiler (experimental) |

## Hardware

| | Humainary | Fullerstack |
|---|-----------|-------------|
| **Platform** | Apple Mac mini (Mac16,10) | Azure VM (GitHub Codespaces) |
| **Chip** | Apple M4 (10 cores: 4P + 6E) | AMD EPYC 7763 (2 vCPU) |
| **Memory** | 16 GB | 8 GB |
| **JVM** | Java 25.0.1 LTS (HotSpot) | Java 25.0.1 LTS (HotSpot) |
| **SPI** | Alpha Provider | Alpha Provider |

## Important Note on Hardware Differences

The Humainary baselines were collected on dedicated Apple M4 silicon, while the Fullerstack results were collected on a shared Azure VM (GitHub Codespaces). These are fundamentally different hardware platforms with different CPU architectures, memory subsystems, and levels of resource contention. **Absolute timing comparisons are approximate at best.** The delta percentages below reflect the combined effect of code changes AND hardware differences. Relative patterns within each benchmark suite (e.g., hot-path vs. cold-path ratios) are more meaningful than absolute cross-platform comparisons.

---

## Substrates Core Benchmarks

### CircuitOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| conduit_create_close | 2470.165 | 309.983 | +696.9% |
| conduit_create_named | 599.615 | 316.894 | +89.2% |
| conduit_create_with_flow | 568.504 | 289.073 | +96.7% |
| create_and_close | 630.362 | 333.957 | +88.8% |
| create_and_close_batch | 592.203 | 441.745 | +34.1% |
| create_multiple_and_close | 3407.104 | 2545.594 | +33.8% |
| create_named_and_close | 601.224 | 457.365 | +31.5% |
| hot_conduit_create | 20.054 | 16.662 | +20.4% |
| hot_conduit_create_named | 19.234 | 16.461 | +16.8% |
| hot_conduit_create_with_flow | 21.604 | 19.919 | +8.5% |
| hot_pipe_async | 12.236 | 8.392 | +45.8% |
| hot_pipe_async_with_flow | 21.958 | 10.268 | +113.8% |
| pipe_async | 612.013 | 341.472 | +79.2% |
| pipe_async_with_flow | 883.469 | 302.093 | +192.4% |

### ConduitOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| get_by_name | 3.395 | 1.351 | +151.3% |
| get_by_name_batch | 3.112 | 0.961 | +223.8% |
| get_by_substrate | 4.167 | 1.705 | +144.4% |
| get_by_substrate_batch | 4.147 | 1.538 | +169.6% |
| get_cached | 4.934 | 2.319 | +112.8% |
| get_cached_batch | 4.903 | 2.126 | +130.6% |
| get_varied | 14.301 | 3.194 | +347.7% |
| get_varied_batch | 13.621 | 3.098 | +339.7% |
| subscribe | 652.238 | 446.558 | +46.1% |
| subscribe_batch | 569.265 | 460.854 | +23.5% |
| subscribe_with_emission_await | 12098.438 | 7162.881 | +68.9% |

### CortexOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| circuit | 488.560 | 288.248 | +69.5% |
| circuit_batch | 410.974 | 290.459 | +41.5% |
| circuit_named | 628.724 | 291.152 | +115.9% |
| current | 3.892 | 1.187 | +227.9% |
| name_class | 8.324 | 1.647 | +405.4% |
| name_enum | 2.380 | 1.953 | +21.9% |
| name_iterable | 16.840 | 8.697 | +93.6% |
| name_path | 5.901 | 2.103 | +180.6% |
| name_path_batch | 5.623 | 1.861 | +202.1% |
| name_string | 2.474 | 2.935 | -15.7% |
| name_string_batch | 2.001 | 2.771 | -27.8% |
| scope | 4.956 | 9.701 | -48.9% |
| scope_batch | 4.620 | 8.418 | -45.1% |
| scope_named | 5.100 | 8.917 | -42.8% |
| slot_boolean | 2.606 | 1.935 | +34.7% |
| slot_double | 6.598 | 1.923 | +243.1% |
| slot_int | 2.557 | 1.932 | +32.3% |
| slot_long | 2.646 | 1.939 | +36.5% |
| slot_string | 2.659 | 1.939 | +37.1% |
| state_empty | 2.114 | 0.504 | +319.4% |
| state_empty_batch | 1.652 | ~0.001 | N/A |

### CyclicOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| cyclic_emit | 2.600 | 1.046 | +148.6% |
| cyclic_emit_await | 46.458 | 10.342 | +349.2% |
| cyclic_emit_await_batch | 44.674 | 10.398 | +329.6% |
| cyclic_emit_batch | 2.426 | 1.223 | +98.4% |
| cyclic_emit_deep_await | 25.021 | 4.399 | +468.8% |
| cyclic_emit_deep_await_batch | 25.893 | 4.470 | +479.3% |

### FlowOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| baseline_no_flow_await | 18.564 | 18.965 | -2.1% |
| flow_combined_diff_guard_await | 44.901 | 26.317 | +70.6% |
| flow_combined_diff_sample_await | 38.091 | 19.873 | +91.7% |
| flow_combined_guard_limit_await | 36.649 | 28.306 | +29.5% |
| flow_diff_await | 41.105 | 28.498 | +44.2% |
| flow_guard_await | 36.437 | 28.773 | +26.6% |
| flow_limit_await | 36.500 | 28.293 | +29.0% |
| flow_sample_await | 17.198 | 18.800 | -8.5% |
| flow_sift_await | 41.042 | 20.194 | +103.2% |

### IdOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| id_from_subject | 1.673 | 0.578 | +189.4% |
| id_from_subject_batch | 0.001 | ~0.001 | N/A |
| id_toString | 5.888 | 13.209 | -55.4% |
| id_toString_batch | 5.539 | 14.612 | -62.1% |

### NameOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| name_chained_deep | 10.319 | 5.745 | +79.6% |
| name_chaining | 15.063 | 9.450 | +59.4% |
| name_chaining_batch | 14.503 | 9.038 | +60.5% |
| name_compare | 3.123 | 0.839 | +272.2% |
| name_compare_batch | 0.003 | ~0.001 | N/A |
| name_depth | 0.833 | 0.577 | +44.4% |
| name_depth_batch | 0.001 | ~0.001 | N/A |
| name_enclosure | 0.868 | 0.608 | +42.8% |
| name_from_enum | 2.167 | 2.026 | +7.0% |
| name_from_iterable | 15.930 | 9.811 | +62.4% |
| name_from_iterator | 21.272 | 9.671 | +120.0% |
| name_from_mapped_iterable | 19.048 | 10.111 | +88.4% |
| name_from_name | 4.511 | 3.988 | +13.1% |
| name_from_string | 2.191 | 3.409 | -35.7% |
| name_from_string_batch | 1.886 | 3.124 | -39.6% |
| name_hashCode | 0.848 | 0.588 | +44.2% |
| name_hashCode_batch | 0.001 | ~0.001 | N/A |
| name_interning_chained | 24.194 | 11.655 | +107.6% |
| name_interning_same_path | 10.091 | 3.901 | +158.7% |
| name_interning_segments | 10.392 | 9.706 | +7.1% |
| name_iterate_hierarchy | 3.164 | 1.862 | +69.9% |
| name_parsing | 5.409 | 2.096 | +158.1% |
| name_parsing_batch | 4.878 | 1.875 | +160.2% |
| name_path_generation | 0.811 | 0.606 | +33.8% |
| name_path_generation_batch | 0.001 | ~0.001 | N/A |
| name_within | 9.594 | 1.767 | +443.0% |
| name_within_batch | 9.914 | 1.125 | +781.2% |
| name_within_false | 10.909 | 2.089 | +422.2% |
| name_within_false_batch | 10.503 | 1.409 | +645.4% |

### PipeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| async_emit_batch | 14.733 | 10.156 | +45.1% |
| async_emit_batch_await | 18.024 | 18.370 | -1.9% |
| async_emit_chained_await | 18.991 | 22.567 | -15.8% |
| async_emit_fanout_await | 29.729 | 19.832 | +49.9% |
| async_emit_single | 11.036 | 6.872 | +60.6% |
| async_emit_single_await | 2571.311 | 6217.555 | -58.6% |
| async_emit_with_flow_await | 38.908 | 19.262 | +102.0% |
| baseline_blackhole | 0.732 | 0.296 | +147.3% |
| baseline_counter | 2.777 | 1.859 | +49.4% |
| baseline_receptor | 0.738 | 0.546 | +35.2% |
| pipe_create | 9.816 | 8.567 | +14.6% |
| pipe_create_chained | 2.541 | 0.931 | +172.9% |
| pipe_create_with_flow | 16.633 | 14.337 | +16.0% |

### ReservoirOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| baseline_emit_no_reservoir_await | 38.220 | 95.215 | -59.9% |
| baseline_emit_no_reservoir_await_batch | 18.775 | 19.156 | -2.0% |
| reservoir_burst_then_drain_await | 166.424 | 89.571 | +85.8% |
| reservoir_burst_then_drain_await_batch | 46.069 | 22.238 | +107.2% |
| reservoir_drain_await | 153.048 | 89.491 | +71.0% |
| reservoir_drain_await_batch | 47.503 | 22.215 | +113.8% |
| reservoir_emit_drain_cycles_await | 388.310 | 439.618 | -11.7% |
| reservoir_emit_with_capture_await | 200.495 | 87.034 | +130.4% |
| reservoir_emit_with_capture_await_batch | 45.508 | 23.094 | +97.1% |
| reservoir_process_emissions_await | 235.061 | 83.096 | +182.9% |
| reservoir_process_emissions_await_batch | 54.327 | 26.119 | +108.0% |
| reservoir_process_subjects_await | 212.725 | 95.337 | +123.1% |

### ScopeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| scope_child_anonymous | 16.598 | 18.916 | -12.3% |
| scope_child_anonymous_batch | 16.978 | 18.682 | -9.1% |
| scope_child_named | 16.817 | 19.368 | -13.2% |
| scope_child_named_batch | 17.079 | 20.021 | -14.7% |
| scope_close_idempotent | 0.695 | 2.738 | -74.6% |
| scope_close_idempotent_batch | 0.348 | 0.039 | +792.3% |
| scope_closure | 474.495 | 294.963 | +60.9% |
| scope_closure_batch | 440.637 | 306.814 | +43.6% |
| scope_complex | 4418.123 | 907.760 | +386.7% |
| scope_create_and_close | 0.686 | 2.762 | -75.2% |
| scope_create_and_close_batch | 0.352 | 0.038 | +826.3% |
| scope_create_named | 0.791 | 2.820 | -72.0% |
| scope_create_named_batch | 0.456 | 0.038 | +1100.0% |
| scope_hierarchy | 30.770 | 30.365 | +1.3% |
| scope_hierarchy_batch | 31.920 | 31.328 | +1.9% |
| scope_parent_closes_children | 45.526 | 47.824 | -4.8% |
| scope_parent_closes_children_batch | 43.078 | 46.967 | -8.3% |
| scope_register_multiple | 2432.762 | 1479.062 | +64.5% |
| scope_register_multiple_batch | 4200.673 | 1491.609 | +181.6% |
| scope_register_single | 477.105 | 303.114 | +57.4% |
| scope_register_single_batch | 513.497 | 311.687 | +64.7% |
| scope_with_resources | 2915.704 | 600.079 | +385.9% |

### StateOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| slot_name | 1.008 | 0.581 | +73.5% |
| slot_name_batch | 0.001 | ~0.001 | N/A |
| slot_type | 1.075 | 0.497 | +116.3% |
| slot_value | 1.206 | 0.668 | +80.5% |
| slot_value_batch | 0.001 | ~0.001 | N/A |
| state_compact | 24.296 | 9.538 | +154.7% |
| state_compact_batch | 22.467 | 10.351 | +117.1% |
| state_iterate_slots | 4.790 | 2.388 | +100.6% |
| state_slot_add_int | 9.536 | 3.957 | +141.0% |
| state_slot_add_int_batch | 9.794 | 4.003 | +144.7% |
| state_slot_add_long | 10.064 | 3.935 | +155.8% |
| state_slot_add_object | 8.264 | 2.047 | +303.7% |
| state_slot_add_object_batch | 8.795 | 2.035 | +332.2% |
| state_slot_add_string | 9.786 | 4.030 | +142.8% |
| state_value_read | 3.536 | 1.390 | +154.4% |
| state_value_read_batch | 0.074 | ~0.001 | N/A |
| state_values_stream | 25.665 | 4.104 | +525.4% |

### SubjectOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| subject_compare | 2.911 | 4.281 | -32.0% |
| subject_compare_batch | 0.003 | 2.753 | -99.9% |
| subject_compare_same | 1.331 | 0.496 | +168.3% |
| subject_compare_same_batch | 0.001 | ~0.001 | N/A |
| subject_compare_three_way | 7.335 | 12.264 | -40.2% |

### SubscriberOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| close_five_conduits_await | 11187.353 | 8944.447 | +25.1% |
| close_five_subscriptions_await | 9798.185 | 8802.098 | +11.3% |
| close_idempotent_await | 6364.154 | 8681.928 | -26.7% |
| close_idempotent_batch_await | 18.779 | 18.078 | +3.9% |
| close_no_subscriptions_await | 9603.696 | 8761.686 | +9.6% |
| close_no_subscriptions_batch_await | 53.822 | 15.126 | +255.8% |
| close_one_subscription_await | 9092.310 | 8273.231 | +9.9% |
| close_one_subscription_batch_await | 170.442 | 32.196 | +429.4% |
| close_ten_conduits_await | 6495.267 | 8799.784 | -26.2% |
| close_ten_subscriptions_await | 7169.337 | 8453.515 | -15.2% |
| close_with_pending_emissions_await | 14755.641 | 8809.789 | +67.5% |

### TapOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| baseline_emit_batch_await | 34.010 | 20.533 | +65.6% |
| tap_close | 3705.375 | 8875.453 | -58.3% |
| tap_create_batch | 1515.645 | 574.582 | +163.8% |
| tap_create_identity | 1287.733 | 570.646 | +125.7% |
| tap_create_string | 1433.241 | 872.824 | +64.2% |
| tap_emit_identity_batch_await | 63.612 | 28.876 | +120.3% |
| tap_emit_identity_single | 41.522 | 31.541 | +31.6% |
| tap_emit_identity_single_await | 4077.489 | 6109.758 | -33.3% |
| tap_emit_multi_batch_await | 69.804 | 42.337 | +64.9% |
| tap_emit_string_batch_await | 70.121 | 36.052 | +94.5% |
| tap_lifecycle | 22562.595 | 17387.271 | +29.8% |

---

## Serventis Extension Benchmarks (700 benchmarks, 30 instrument groups)

### ActorOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| actor_from_conduit | 2.688 | 1.364 | +97.1% |
| actor_from_conduit_batch | 3.840 | 1.178 | +226.0% |
| emit_acknowledge | 27.002 | 7.657 | +252.6% |
| emit_acknowledge_batch | 24.450 | 7.177 | +240.7% |
| emit_affirm | 26.256 | 6.635 | +295.7% |
| emit_affirm_batch | 23.691 | 6.864 | +245.1% |
| emit_ask | 25.842 | 7.401 | +249.2% |
| emit_ask_batch | 45.445 | 6.903 | +558.3% |
| emit_clarify | 21.746 | 8.692 | +150.2% |
| emit_clarify_batch | 31.962 | 6.796 | +370.3% |
| emit_command | 18.785 | 7.844 | +139.5% |
| emit_command_batch | 16.648 | 7.359 | +126.2% |
| emit_deliver | 24.057 | 7.553 | +218.5% |
| emit_deliver_batch | 19.230 | 6.939 | +177.1% |
| emit_deny | 14.840 | 7.281 | +103.8% |
| emit_deny_batch | 16.129 | 6.610 | +144.0% |
| emit_explain | 16.275 | 7.682 | +111.9% |
| emit_explain_batch | 16.371 | 6.623 | +147.2% |
| emit_promise | 15.232 | 7.531 | +102.3% |
| emit_promise_batch | 17.486 | 7.109 | +146.0% |
| emit_report | 14.762 | 7.286 | +102.6% |
| emit_report_batch | 19.760 | 7.691 | +156.9% |
| emit_request | 15.902 | 7.932 | +100.5% |
| emit_request_batch | 15.092 | 6.667 | +126.4% |
| emit_sign | 12.864 | 7.282 | +76.7% |
| emit_sign_batch | 11.999 | 6.771 | +77.2% |

### AgentOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| agent_from_conduit | 2.113 | 1.342 | +57.5% |
| agent_from_conduit_batch | 2.939 | 1.144 | +156.9% |
| emit_accept | 13.422 | 7.462 | +79.9% |
| emit_accept_batch | 13.340 | 6.385 | +108.9% |
| emit_accepted | 14.868 | 7.637 | +94.7% |
| emit_accepted_batch | 14.542 | 6.788 | +114.2% |
| emit_breach | 14.002 | 7.080 | +97.8% |
| emit_breach_batch | 15.526 | 7.042 | +120.5% |
| emit_breached | 21.627 | 7.635 | +183.3% |
| emit_breached_batch | 16.214 | 6.660 | +143.5% |
| emit_depend | 13.693 | 7.702 | +77.8% |
| emit_depend_batch | 12.049 | 8.159 | +47.7% |
| emit_depended | 14.156 | 10.685 | +32.5% |
| emit_depended_batch | 15.874 | 7.084 | +124.1% |
| emit_fulfill | 27.255 | 6.724 | +305.3% |
| emit_fulfill_batch | 18.136 | 6.111 | +196.8% |
| emit_fulfilled | 19.374 | 6.931 | +179.5% |
| emit_fulfilled_batch | 22.860 | 9.656 | +136.7% |
| emit_inquire | 20.906 | 6.497 | +221.8% |
| emit_inquire_batch | 20.860 | 7.164 | +191.2% |
| emit_inquired | 18.678 | 6.445 | +189.8% |
| emit_inquired_batch | 18.018 | 7.111 | +153.4% |
| emit_observe | 20.198 | 8.216 | +145.8% |
| emit_observe_batch | 18.316 | 6.435 | +184.6% |
| emit_observed | 16.894 | 7.811 | +116.3% |
| emit_observed_batch | 16.732 | 7.156 | +133.8% |
| emit_offer | 19.600 | 6.213 | +215.5% |
| emit_offer_batch | 20.953 | 8.953 | +134.0% |
| emit_offered | 30.597 | 7.995 | +282.7% |
| emit_offered_batch | 21.213 | 6.279 | +237.8% |
| emit_promise | 21.024 | 6.415 | +227.7% |
| emit_promise_batch | 15.946 | 6.638 | +140.2% |
| emit_promised | 19.317 | 7.084 | +172.7% |
| emit_promised_batch | 17.060 | 7.497 | +127.6% |
| emit_retract | 22.310 | 7.269 | +206.9% |
| emit_retract_batch | 17.980 | 6.976 | +157.7% |
| emit_retracted | 18.703 | 7.031 | +166.0% |
| emit_retracted_batch | 19.731 | 7.371 | +167.7% |
| emit_signal | 25.093 | 8.187 | +206.5% |
| emit_signal_batch | 26.212 | 6.423 | +308.1% |
| emit_validate | 20.736 | 8.146 | +154.6% |
| emit_validate_batch | 35.679 | 8.227 | +333.7% |
| emit_validated | 56.194 | 7.882 | +612.9% |
| emit_validated_batch | 20.604 | 6.490 | +217.5% |

### AtomicOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| atomic_from_conduit | 6.541 | 1.361 | +380.6% |
| atomic_from_conduit_batch | 3.584 | 1.188 | +201.7% |
| emit_attempt | 33.470 | 7.575 | +341.8% |
| emit_attempt_batch | 38.815 | 7.156 | +442.4% |
| emit_backoff | 39.428 | 7.734 | +409.8% |
| emit_backoff_batch | 33.797 | 7.165 | +371.7% |
| emit_exhaust | 18.682 | 7.031 | +165.7% |
| emit_exhaust_batch | 22.612 | 8.305 | +172.3% |
| emit_fail | 25.703 | 8.665 | +196.6% |
| emit_fail_batch | 39.096 | 7.486 | +422.3% |
| emit_park | 19.955 | 8.727 | +128.7% |
| emit_park_batch | 19.062 | 7.222 | +163.9% |
| emit_sign | 20.130 | 7.432 | +170.9% |
| emit_sign_batch | 16.995 | 7.981 | +112.9% |
| emit_spin | 21.715 | 7.710 | +181.6% |
| emit_spin_batch | 16.269 | 6.382 | +154.9% |
| emit_success | 20.838 | 7.053 | +195.4% |
| emit_success_batch | 19.986 | 7.214 | +177.0% |
| emit_yield | 26.070 | 7.578 | +244.0% |
| emit_yield_batch | 18.787 | 6.684 | +181.1% |

### BreakerOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| breaker_from_conduit | 2.618 | 1.337 | +95.8% |
| breaker_from_conduit_batch | 3.104 | 1.165 | +166.4% |
| emit_close | 15.536 | 8.633 | +80.0% |
| emit_close_batch | 14.543 | 7.059 | +106.0% |
| emit_half_open | 18.471 | 7.968 | +131.8% |
| emit_half_open_batch | 16.022 | 7.373 | +117.3% |
| emit_open | 17.414 | 7.391 | +135.6% |
| emit_open_batch | 19.128 | 6.687 | +186.0% |
| emit_probe | 20.255 | 7.022 | +188.5% |
| emit_probe_batch | 16.487 | 7.017 | +135.0% |
| emit_reset | 17.553 | 7.447 | +135.7% |
| emit_reset_batch | 16.693 | 7.082 | +135.7% |
| emit_sign | 16.066 | 7.353 | +118.5% |
| emit_sign_batch | 14.978 | 7.004 | +113.8% |
| emit_trip | 13.827 | 7.835 | +76.5% |
| emit_trip_batch | 13.700 | 6.811 | +101.1% |

### CacheOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| cache_from_conduit | 2.616 | 1.324 | +97.6% |
| cache_from_conduit_batch | 3.066 | 1.155 | +165.5% |
| emit_evict | 16.410 | 7.536 | +117.8% |
| emit_evict_batch | 15.355 | 7.365 | +108.5% |
| emit_expire | 23.095 | 7.594 | +204.1% |
| emit_expire_batch | 16.849 | 6.761 | +149.2% |
| emit_hit | 15.609 | 7.395 | +111.1% |
| emit_hit_batch | 15.404 | 7.100 | +117.0% |
| emit_lookup | 40.888 | 8.258 | +395.1% |
| emit_lookup_batch | 15.461 | 7.385 | +109.4% |
| emit_miss | 18.450 | 6.860 | +169.0% |
| emit_miss_batch | 18.320 | 7.482 | +144.9% |
| emit_remove | 16.541 | 7.267 | +127.6% |
| emit_remove_batch | 21.166 | 6.579 | +221.7% |
| emit_sign | 17.309 | 8.019 | +115.8% |
| emit_sign_batch | 22.351 | 7.859 | +184.4% |
| emit_store | 19.179 | 7.336 | +161.4% |
| emit_store_batch | 20.154 | 7.376 | +173.2% |

### CounterOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| counter_from_conduit | 2.567 | 1.384 | +85.5% |
| counter_from_conduit_batch | 3.133 | 1.167 | +168.5% |
| emit_increment | 14.749 | 6.897 | +113.8% |
| emit_increment_batch | 13.069 | 7.394 | +76.8% |
| emit_overflow | 15.276 | 7.191 | +112.4% |
| emit_overflow_batch | 15.849 | 6.923 | +128.9% |
| emit_reset | 14.144 | 7.530 | +87.8% |
| emit_reset_batch | 18.252 | 7.052 | +158.8% |
| emit_sign | 13.810 | 7.603 | +81.6% |
| emit_sign_batch | 13.311 | 6.406 | +107.8% |

### CycleOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| cycle_from_conduit | 2.049 | 1.224 | +67.4% |
| cycle_from_conduit_batch | 2.687 | 1.060 | +153.5% |
| emit_repeat | 15.671 | 8.364 | +87.4% |
| emit_repeat_batch | 13.275 | 8.091 | +64.1% |
| emit_return | 17.018 | 7.835 | +117.2% |
| emit_return_batch | 12.732 | 7.266 | +75.2% |
| emit_signal | 14.431 | 8.847 | +63.1% |
| emit_signal_batch | 13.762 | 6.044 | +127.7% |
| emit_single | 13.658 | 7.121 | +91.8% |
| emit_single_batch | 13.644 | 6.206 | +119.9% |

### ExchangeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_contract_provider | 15.690 | 6.760 | +132.1% |
| emit_contract_provider_batch | 19.916 | 7.046 | +182.7% |
| emit_contract_receiver | 17.379 | 7.055 | +146.3% |
| emit_contract_receiver_batch | 16.525 | 7.041 | +134.7% |
| emit_full_exchange | 18.995 | 9.181 | +106.9% |
| emit_full_exchange_batch | 17.934 | 9.226 | +94.4% |
| emit_signal | 18.847 | 7.706 | +144.6% |
| emit_signal_batch | 19.212 | 7.048 | +172.6% |
| emit_transfer_provider | 23.828 | 7.530 | +216.4% |
| emit_transfer_provider_batch | 17.317 | 5.923 | +192.4% |
| emit_transfer_receiver | 44.845 | 7.060 | +535.2% |
| emit_transfer_receiver_batch | 19.876 | 8.574 | +131.8% |
| exchange_from_conduit | 5.722 | 1.352 | +323.2% |
| exchange_from_conduit_batch | 8.993 | 1.162 | +673.9% |

### FlowOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_fail_egress | 17.685 | 8.143 | +117.2% |
| emit_fail_egress_batch | 16.967 | 8.168 | +107.7% |
| emit_fail_ingress | 18.697 | 8.337 | +124.3% |
| emit_fail_ingress_batch | 17.136 | 6.173 | +177.6% |
| emit_fail_transit | 17.683 | 6.683 | +164.6% |
| emit_fail_transit_batch | 16.371 | 8.206 | +99.5% |
| emit_signal | 16.214 | 7.583 | +113.8% |
| emit_signal_batch | 16.631 | 8.813 | +88.7% |
| emit_success_egress | 19.599 | 7.323 | +167.6% |
| emit_success_egress_batch | 19.205 | 6.226 | +208.5% |
| emit_success_ingress | 16.259 | 6.627 | +145.3% |
| emit_success_ingress_batch | 17.903 | 7.240 | +147.3% |
| emit_success_transit | 19.212 | 6.666 | +188.2% |
| emit_success_transit_batch | 18.149 | 8.226 | +120.6% |
| flow_from_conduit | 2.572 | 1.374 | +87.2% |
| flow_from_conduit_batch | 3.713 | 1.172 | +216.8% |

### GaugeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_decrement | 15.230 | 7.391 | +106.1% |
| emit_decrement_batch | 14.598 | 8.418 | +73.4% |
| emit_increment | 14.071 | 7.186 | +95.8% |
| emit_increment_batch | 13.798 | 6.874 | +100.7% |
| emit_overflow | 14.844 | 7.274 | +104.1% |
| emit_overflow_batch | 13.078 | 6.730 | +94.3% |
| emit_reset | 12.106 | 8.646 | +40.0% |
| emit_reset_batch | 11.999 | 6.931 | +73.1% |
| emit_sign | 13.144 | 7.283 | +80.5% |
| emit_sign_batch | 11.312 | 7.304 | +54.9% |
| emit_underflow | 11.816 | 7.152 | +65.2% |
| emit_underflow_batch | 15.205 | 9.278 | +63.9% |
| gauge_from_conduit | 2.205 | 1.349 | +63.5% |
| gauge_from_conduit_batch | 2.664 | 1.163 | +129.1% |

### LatchOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_abandon | 16.903 | 7.309 | +131.3% |
| emit_abandon_batch | 19.398 | 6.239 | +210.9% |
| emit_arrive | 29.230 | 5.971 | +389.5% |
| emit_arrive_batch | 18.219 | 9.062 | +101.0% |
| emit_await | 20.713 | 6.719 | +208.3% |
| emit_await_batch | 23.466 | 6.207 | +278.1% |
| emit_release | 21.631 | 7.549 | +186.5% |
| emit_release_batch | 39.147 | 6.796 | +476.0% |
| emit_reset | 19.992 | 6.820 | +193.1% |
| emit_reset_batch | 36.751 | 6.854 | +436.2% |
| emit_sign | 17.484 | 7.546 | +131.7% |
| emit_sign_batch | 20.405 | 6.929 | +194.5% |
| emit_timeout | 17.797 | 7.657 | +132.4% |
| emit_timeout_batch | 22.907 | 7.890 | +190.3% |
| latch_from_conduit | 2.885 | 1.359 | +112.3% |
| latch_from_conduit_batch | 3.660 | 1.162 | +215.0% |

### LeaseOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_acquire | 67.192 | 6.995 | +860.6% |
| emit_acquire_batch | 26.201 | 8.619 | +204.0% |
| emit_acquired | 43.606 | 7.274 | +499.5% |
| emit_acquired_batch | 23.128 | 6.159 | +275.5% |
| emit_denied | 50.548 | 8.276 | +510.8% |
| emit_denied_batch | 27.525 | 7.838 | +251.2% |
| emit_deny | 18.817 | 7.152 | +163.1% |
| emit_deny_batch | 35.688 | 8.418 | +323.9% |
| emit_expire | 29.493 | 6.310 | +367.4% |
| emit_expire_batch | 19.274 | 8.928 | +115.9% |
| emit_expired | 19.212 | 8.381 | +129.2% |
| emit_expired_batch | 19.253 | 6.727 | +186.2% |
| emit_extend | 16.713 | 7.611 | +119.6% |
| emit_extend_batch | 16.673 | 6.157 | +170.8% |
| emit_extended | 40.550 | 6.608 | +513.7% |
| emit_extended_batch | 30.876 | 8.631 | +257.7% |
| emit_grant | 36.583 | 6.862 | +433.1% |
| emit_grant_batch | 20.447 | 6.365 | +221.2% |
| emit_granted | 36.855 | 7.225 | +410.1% |
| emit_granted_batch | 17.508 | 5.999 | +191.8% |
| emit_probe | 16.690 | 8.242 | +102.5% |
| emit_probe_batch | 15.251 | 7.334 | +107.9% |
| emit_probed | 18.335 | 9.587 | +91.2% |
| emit_probed_batch | 17.344 | 6.940 | +149.9% |
| emit_release | 16.839 | 8.232 | +104.6% |
| emit_release_batch | 19.479 | 7.271 | +167.9% |
| emit_released | 20.011 | 6.549 | +205.6% |
| emit_released_batch | 24.293 | 6.865 | +253.9% |
| emit_renew | 29.142 | 7.091 | +311.0% |
| emit_renew_batch | 23.613 | 6.069 | +289.1% |
| emit_renewed | 50.551 | 11.004 | +359.4% |
| emit_renewed_batch | 46.002 | 7.087 | +549.1% |
| emit_revoke | 41.980 | 6.618 | +534.3% |
| emit_revoke_batch | 24.181 | 8.227 | +193.9% |
| emit_revoked | 19.237 | 8.435 | +128.1% |
| emit_revoked_batch | 17.683 | 7.319 | +141.6% |
| emit_signal | 17.283 | 6.911 | +150.1% |
| emit_signal_batch | 15.528 | 8.699 | +78.5% |
| lease_from_conduit | 2.686 | 1.355 | +98.2% |
| lease_from_conduit_batch | 3.294 | 1.172 | +181.1% |

### LockOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_abandon | 21.167 | 7.103 | +198.0% |
| emit_abandon_batch | 18.392 | 7.150 | +157.2% |
| emit_acquire | 16.890 | 7.605 | +122.1% |
| emit_acquire_batch | 17.846 | 8.556 | +108.6% |
| emit_attempt | 19.621 | 7.687 | +155.2% |
| emit_attempt_batch | 21.741 | 6.553 | +231.8% |
| emit_contest | 19.938 | 7.578 | +163.1% |
| emit_contest_batch | 24.988 | 6.770 | +269.1% |
| emit_deny | 41.508 | 6.488 | +539.8% |
| emit_deny_batch | 45.679 | 10.053 | +354.4% |
| emit_downgrade | 15.084 | 7.564 | +99.4% |
| emit_downgrade_batch | 21.595 | 7.067 | +205.6% |
| emit_grant | 19.199 | 7.040 | +172.7% |
| emit_grant_batch | 19.235 | 6.010 | +220.0% |
| emit_release | 16.652 | 7.341 | +126.8% |
| emit_release_batch | 15.379 | 7.408 | +107.6% |
| emit_sign | 18.148 | 7.261 | +149.9% |
| emit_sign_batch | 22.502 | 7.816 | +187.9% |
| emit_timeout | 17.794 | 7.553 | +135.6% |
| emit_timeout_batch | 17.070 | 6.427 | +165.6% |
| emit_upgrade | 17.453 | 6.369 | +174.0% |
| emit_upgrade_batch | 15.876 | 6.965 | +127.9% |
| lock_from_conduit | 2.389 | 1.370 | +74.4% |
| lock_from_conduit_batch | 3.032 | 1.171 | +158.9% |

### LogOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_debug | 13.533 | 7.580 | +78.5% |
| emit_debug_batch | 11.768 | 6.873 | +71.2% |
| emit_info | 12.070 | 8.678 | +39.1% |
| emit_info_batch | 13.258 | 7.362 | +80.1% |
| emit_severe | 14.283 | 7.404 | +92.9% |
| emit_severe_batch | 12.547 | 6.834 | +83.6% |
| emit_sign | 11.219 | 7.700 | +45.7% |
| emit_sign_batch | 12.357 | 6.985 | +76.9% |
| emit_warning | 11.181 | 7.774 | +43.8% |
| emit_warning_batch | 16.147 | 6.380 | +153.1% |
| log_from_conduit | 2.198 | 1.365 | +61.0% |
| log_from_conduit_batch | 2.759 | 1.157 | +138.5% |

### OperationOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_begin | 40.206 | 7.770 | +417.5% |
| emit_begin_batch | 18.264 | 7.249 | +152.0% |
| emit_end | 19.101 | 8.802 | +117.0% |
| emit_end_batch | 16.747 | 7.505 | +123.1% |
| emit_sign | 15.942 | 7.113 | +124.1% |
| emit_sign_batch | 15.111 | 6.739 | +124.2% |
| operation_from_conduit | 2.412 | 1.346 | +79.2% |
| operation_from_conduit_batch | 3.246 | 1.164 | +178.9% |

### OutcomeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_fail | 14.924 | 7.360 | +102.8% |
| emit_fail_batch | 12.846 | 7.698 | +66.9% |
| emit_sign | 13.122 | 6.876 | +90.8% |
| emit_sign_batch | 15.485 | 6.567 | +135.8% |
| emit_success | 15.597 | 7.873 | +98.1% |
| emit_success_batch | 12.760 | 7.078 | +80.3% |
| outcome_from_conduit | 2.136 | 1.350 | +58.2% |
| outcome_from_conduit_batch | 2.908 | 1.184 | +145.6% |

### PipelineOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_aggregate | 22.025 | 7.655 | +187.7% |
| emit_aggregate_batch | 16.577 | 7.260 | +128.3% |
| emit_backpressure | 18.044 | 7.244 | +149.1% |
| emit_backpressure_batch | 22.017 | 9.409 | +134.0% |
| emit_buffer | 25.794 | 6.942 | +271.6% |
| emit_buffer_batch | 16.887 | 8.065 | +109.4% |
| emit_checkpoint | 18.530 | 6.595 | +181.0% |
| emit_checkpoint_batch | 14.492 | 6.642 | +118.2% |
| emit_filter | 17.242 | 7.822 | +120.4% |
| emit_filter_batch | 16.028 | 6.991 | +129.3% |
| emit_input | 16.684 | 8.516 | +95.9% |
| emit_input_batch | 29.627 | 8.878 | +233.7% |
| emit_lag | 16.297 | 7.115 | +129.1% |
| emit_lag_batch | 17.058 | 6.858 | +148.7% |
| emit_output | 15.882 | 7.251 | +119.0% |
| emit_output_batch | 17.708 | 7.059 | +150.9% |
| emit_overflow | 21.766 | 7.378 | +195.0% |
| emit_overflow_batch | 14.980 | 6.931 | +116.1% |
| emit_sign | 19.099 | 8.383 | +127.8% |
| emit_sign_batch | 18.739 | 7.043 | +166.1% |
| emit_skip | 17.059 | 8.638 | +97.5% |
| emit_skip_batch | 20.632 | 7.024 | +193.7% |
| emit_transform | 19.915 | 7.107 | +180.2% |
| emit_transform_batch | 19.242 | 7.821 | +146.0% |
| emit_watermark | 17.450 | 7.175 | +143.2% |
| emit_watermark_batch | 17.404 | 7.082 | +145.7% |
| pipeline_flow_etl | 72.716 | 41.096 | +76.9% |
| pipeline_flow_stream | 99.548 | 40.757 | +144.2% |
| pipeline_flow_windowed | 84.647 | 37.593 | +125.2% |
| pipeline_from_conduit | 2.340 | 1.359 | +72.2% |
| pipeline_from_conduit_batch | 3.225 | 1.174 | +174.7% |

### PoolOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_borrow | 17.000 | 7.148 | +137.8% |
| emit_borrow_batch | 16.248 | 6.929 | +134.5% |
| emit_contract | 15.469 | 7.386 | +109.4% |
| emit_contract_batch | 16.716 | 6.982 | +139.4% |
| emit_expand | 17.115 | 7.014 | +144.0% |
| emit_expand_batch | 21.133 | 7.687 | +174.9% |
| emit_reclaim | 18.606 | 7.639 | +143.6% |
| emit_reclaim_batch | 24.131 | 7.186 | +235.8% |
| emit_sign | 22.793 | 7.326 | +211.1% |
| emit_sign_batch | 16.998 | 6.842 | +148.4% |
| pool_from_conduit | 2.767 | 1.355 | +104.2% |
| pool_from_conduit_batch | 3.542 | 1.169 | +203.0% |

### ProbeOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_connect | 15.009 | 6.192 | +142.4% |
| emit_connect_batch | 11.364 | 6.394 | +77.7% |
| emit_connected | 12.146 | 7.137 | +70.2% |
| emit_connected_batch | 12.087 | 6.049 | +99.8% |
| emit_disconnect | 13.254 | 6.740 | +96.6% |
| emit_disconnect_batch | 14.823 | 7.404 | +100.2% |
| emit_disconnected | 18.930 | 7.537 | +151.2% |
| emit_disconnected_batch | 12.258 | 6.452 | +90.0% |
| emit_fail | 14.953 | 8.308 | +80.0% |
| emit_fail_batch | 12.363 | 7.702 | +60.5% |
| emit_failed | 14.368 | 6.352 | +126.2% |
| emit_failed_batch | 12.378 | 6.852 | +80.6% |
| emit_process | 13.419 | 7.316 | +83.4% |
| emit_process_batch | 12.447 | 7.395 | +68.3% |
| emit_processed | 13.975 | 6.768 | +106.5% |
| emit_processed_batch | 14.168 | 8.307 | +70.6% |
| emit_receive_batch | 21.161 | 7.582 | +179.1% |
| emit_received_batch | 15.472 | 8.735 | +77.1% |
| emit_signal | 13.988 | 7.655 | +82.7% |
| emit_signal_batch | 12.026 | 7.209 | +66.8% |
| emit_succeed | 13.737 | 7.307 | +88.0% |
| emit_succeed_batch | 13.123 | 8.615 | +52.3% |
| emit_succeeded | 13.996 | 7.568 | +84.9% |
| emit_succeeded_batch | 20.254 | 7.942 | +155.0% |
| emit_transfer | 13.828 | 6.306 | +119.3% |
| emit_transfer_inbound | 13.884 | 7.854 | +76.8% |
| emit_transfer_outbound | 39.808 | 7.303 | +445.1% |
| emit_transferred | 52.742 | 6.302 | +736.9% |
| emit_transmit_batch | 40.954 | 8.252 | +396.3% |
| emit_transmitted_batch | 81.056 | 8.094 | +901.4% |
| probe_from_conduit | 8.832 | 1.363 | +548.0% |
| probe_from_conduit_batch | 8.460 | 1.171 | +622.5% |

### ProcessOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_crash | 15.014 | 6.891 | +117.9% |
| emit_crash_batch | 17.454 | 7.322 | +138.4% |
| emit_fail | 23.657 | 7.962 | +197.1% |
| emit_fail_batch | 16.131 | 9.002 | +79.2% |
| emit_kill | 19.463 | 7.284 | +167.2% |
| emit_kill_batch | 16.790 | 8.116 | +106.9% |
| emit_restart | 19.020 | 7.037 | +170.3% |
| emit_restart_batch | 15.621 | 7.781 | +100.8% |
| emit_resume | 16.472 | 7.112 | +131.6% |
| emit_resume_batch | 16.034 | 7.366 | +117.7% |
| emit_sign | 17.076 | 7.768 | +119.8% |
| emit_sign_batch | 17.063 | 7.176 | +137.8% |
| emit_spawn | 14.403 | 6.898 | +108.8% |
| emit_spawn_batch | 16.018 | 7.242 | +121.2% |
| emit_start | 18.302 | 8.889 | +105.9% |
| emit_start_batch | 16.836 | 7.988 | +110.8% |
| emit_stop | 15.830 | 7.323 | +116.2% |
| emit_stop_batch | 22.760 | 8.489 | +168.1% |
| emit_suspend | 16.472 | 7.359 | +123.8% |
| emit_suspend_batch | 15.988 | 6.874 | +132.6% |
| process_from_conduit | 2.477 | 1.385 | +78.8% |
| process_from_conduit_batch | 3.240 | 1.162 | +178.8% |

### QueueOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_dequeue | 14.631 | 7.480 | +95.6% |
| emit_dequeue_batch | 19.755 | 8.222 | +140.3% |
| emit_enqueue | 21.560 | 7.149 | +201.6% |
| emit_enqueue_batch | 18.128 | 8.993 | +101.6% |
| emit_overflow | 15.360 | 7.281 | +111.0% |
| emit_overflow_batch | 18.276 | 6.475 | +182.3% |
| emit_sign | 15.261 | 7.169 | +112.9% |
| emit_sign_batch | 17.593 | 7.975 | +120.6% |
| emit_underflow | 17.116 | 7.326 | +133.6% |
| emit_underflow_batch | 15.205 | 7.406 | +105.3% |
| queue_from_conduit | 2.524 | 1.351 | +86.8% |
| queue_from_conduit_batch | 3.225 | 1.165 | +176.8% |

### ResourceOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_acquire | 15.187 | 6.414 | +136.8% |
| emit_acquire_batch | 17.765 | 6.471 | +174.5% |
| emit_attempt | 28.121 | 7.165 | +292.5% |
| emit_attempt_batch | 26.461 | 9.263 | +185.7% |
| emit_deny | 18.475 | 7.109 | +159.9% |
| emit_deny_batch | 22.326 | 9.266 | +140.9% |
| emit_grant | 41.152 | 9.296 | +342.7% |
| emit_grant_batch | 33.894 | 6.931 | +389.0% |
| emit_release | 47.795 | 7.297 | +555.0% |
| emit_release_batch | 29.527 | 6.870 | +329.8% |
| emit_sign | 59.807 | 7.691 | +677.6% |
| emit_sign_batch | 19.570 | 8.608 | +127.3% |
| emit_timeout | 19.146 | 9.272 | +106.5% |
| emit_timeout_batch | 36.239 | 7.094 | +410.8% |
| resource_from_conduit | 4.590 | 1.362 | +237.0% |
| resource_from_conduit_batch | 4.743 | 1.151 | +312.1% |

### RouterOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_corrupt | 21.762 | 6.998 | +211.0% |
| emit_corrupt_batch | 14.545 | 7.093 | +105.1% |
| emit_drop | 22.797 | 6.763 | +237.1% |
| emit_drop_batch | 42.550 | 9.945 | +327.9% |
| emit_forward | 31.837 | 7.616 | +318.0% |
| emit_forward_batch | 40.764 | 7.460 | +446.4% |
| emit_fragment | 31.130 | 5.920 | +425.8% |
| emit_fragment_batch | 16.254 | 7.144 | +127.5% |
| emit_reassemble | 30.157 | 6.517 | +362.7% |
| emit_reassemble_batch | 29.701 | 6.992 | +324.8% |
| emit_receive | 38.702 | 6.960 | +456.1% |
| emit_receive_batch | 31.324 | 7.583 | +313.1% |
| emit_reorder | 57.312 | 7.255 | +690.0% |
| emit_reorder_batch | 30.286 | 7.001 | +332.6% |
| emit_route | 32.556 | 7.884 | +312.9% |
| emit_route_batch | 19.387 | 8.084 | +139.8% |
| emit_send | 17.930 | 8.826 | +103.1% |
| emit_send_batch | 16.180 | 7.315 | +121.2% |
| emit_sign | 18.730 | 7.702 | +143.2% |
| emit_sign_batch | 28.009 | 7.231 | +287.3% |
| router_from_conduit | 2.631 | 1.340 | +96.3% |
| router_from_conduit_batch | 3.317 | 1.169 | +183.7% |

### SensorOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_above_baseline | 84.828 | 7.690 | +1003.1% |
| emit_above_baseline_batch | 58.913 | 6.809 | +765.2% |
| emit_above_target | 57.159 | 6.285 | +809.5% |
| emit_above_target_batch | 47.949 | 6.307 | +660.3% |
| emit_above_threshold | 29.883 | 6.907 | +332.6% |
| emit_above_threshold_batch | 32.482 | 7.347 | +342.1% |
| emit_below_baseline | 34.661 | 8.545 | +305.6% |
| emit_below_baseline_batch | 75.708 | 6.805 | +1012.5% |
| emit_below_target | 110.254 | 8.916 | +1136.6% |
| emit_below_target_batch | 101.727 | 8.517 | +1094.4% |
| emit_below_threshold | 71.990 | 7.250 | +893.0% |
| emit_below_threshold_batch | 84.518 | 6.852 | +1133.5% |
| emit_nominal_baseline | 78.356 | 8.470 | +825.1% |
| emit_nominal_baseline_batch | 59.557 | 6.562 | +807.6% |
| emit_nominal_target | 36.793 | 8.079 | +355.4% |
| emit_nominal_target_batch | 28.622 | 7.810 | +266.5% |
| emit_nominal_threshold | 39.747 | 7.808 | +409.1% |
| emit_nominal_threshold_batch | 70.386 | 7.028 | +901.5% |
| emit_signal | 76.075 | 7.605 | +900.3% |
| emit_signal_batch | 50.324 | 6.296 | +699.3% |
| sensor_from_conduit | 6.038 | 1.373 | +339.8% |
| sensor_from_conduit_batch | 13.403 | 1.177 | +1038.7% |

### ServiceOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_call | 16.260 | 8.273 | +96.5% |
| emit_call_batch | 15.396 | 6.038 | +155.0% |
| emit_called | 16.203 | 6.861 | +136.2% |
| emit_called_batch | 18.532 | 6.792 | +172.9% |
| emit_delay | 17.357 | 7.701 | +125.4% |
| emit_delay_batch | 15.735 | 10.591 | +48.6% |
| emit_delayed | 17.330 | 7.911 | +119.1% |
| emit_delayed_batch | 16.362 | 9.947 | +64.5% |
| emit_discard | 17.589 | 8.481 | +107.4% |
| emit_discard_batch | 14.779 | 8.771 | +68.5% |
| emit_discarded | 17.182 | 7.558 | +127.3% |
| emit_discarded_batch | 16.525 | 7.331 | +125.4% |
| emit_disconnect | 15.782 | 7.512 | +110.1% |
| emit_disconnect_batch | 16.669 | 8.688 | +91.9% |
| emit_disconnected | 17.638 | 7.731 | +128.1% |
| emit_disconnected_batch | 14.844 | 5.956 | +149.2% |
| emit_expire | 17.394 | 8.674 | +100.5% |
| emit_expire_batch | 20.682 | 9.538 | +116.8% |
| emit_expired | 18.004 | 7.309 | +146.3% |
| emit_expired_batch | 16.841 | 8.018 | +110.0% |
| emit_fail | 15.503 | 8.411 | +84.3% |
| emit_fail_batch | 17.202 | 8.143 | +111.2% |
| emit_failed | 17.656 | 8.495 | +107.8% |
| emit_failed_batch | 22.362 | 8.395 | +166.4% |
| emit_recourse | 15.666 | 7.477 | +109.5% |
| emit_recourse_batch | 16.834 | 6.854 | +145.6% |
| emit_recoursed | 17.791 | 7.108 | +150.3% |
| emit_recoursed_batch | 19.714 | 9.225 | +113.7% |
| emit_redirect | 16.654 | 6.198 | +168.7% |
| emit_redirect_batch | 17.175 | 7.773 | +121.0% |
| emit_redirected | 18.162 | 7.909 | +129.6% |
| emit_redirected_batch | 16.772 | 9.575 | +75.2% |
| emit_reject | 16.067 | 7.378 | +117.8% |
| emit_reject_batch | 15.597 | 6.607 | +136.1% |
| emit_rejected | 17.606 | 9.490 | +85.5% |
| emit_rejected_batch | 16.877 | 6.681 | +152.6% |
| emit_resume | 16.128 | 8.160 | +97.6% |
| emit_resume_batch | 15.292 | 7.212 | +112.0% |
| emit_resumed | 15.852 | 6.833 | +132.0% |
| emit_resumed_batch | 16.241 | 9.916 | +63.8% |
| emit_retried | 16.886 | 8.247 | +104.8% |
| emit_retried_batch | 14.768 | 7.591 | +94.5% |
| emit_retry | 15.502 | 6.915 | +124.2% |
| emit_retry_batch | 16.338 | 6.892 | +137.1% |
| emit_schedule | 19.252 | 8.406 | +129.0% |
| emit_schedule_batch | 16.963 | 7.121 | +138.2% |
| emit_scheduled | 22.084 | 7.139 | +209.3% |
| emit_scheduled_batch | 16.891 | 7.391 | +128.5% |
| emit_signal | 15.476 | 7.358 | +110.3% |
| emit_signal_batch | 17.680 | 7.216 | +145.0% |
| emit_start | 15.901 | 7.064 | +125.1% |
| emit_start_batch | 14.435 | 6.833 | +111.3% |
| emit_started | 15.440 | 7.519 | +105.3% |
| emit_started_batch | 13.379 | 7.342 | +82.2% |
| emit_stop | 17.316 | 7.021 | +146.6% |
| emit_stop_batch | 23.407 | 6.841 | +242.2% |
| emit_stopped | 15.142 | 7.465 | +102.8% |
| emit_stopped_batch | 16.097 | 8.316 | +93.6% |
| emit_succeeded | 15.820 | 6.362 | +148.7% |
| emit_succeeded_batch | 14.234 | 7.760 | +83.4% |
| emit_success | 15.107 | 6.234 | +142.3% |
| emit_success_batch | 13.337 | 7.330 | +82.0% |
| emit_suspend | 14.324 | 7.777 | +84.2% |
| emit_suspend_batch | 12.196 | 8.626 | +41.4% |
| emit_suspended | 13.601 | 6.589 | +106.4% |
| emit_suspended_batch | 13.154 | 6.553 | +100.7% |
| service_from_conduit | 2.132 | 1.364 | +56.3% |
| service_from_conduit_batch | 2.732 | 1.175 | +132.5% |

### SignalSetOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| get_mixed_pattern | 0.333 | 0.201 | +65.7% |
| get_single | 1.060 | 0.677 | +56.6% |
| get_single_batch | 0.027 | 0.021 | +28.6% |
| get_varied_batch | 2.031 | 1.541 | +31.8% |
| get_worst_case | 1.850 | 1.048 | +76.5% |

### SituationOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_critical | 15.403 | 7.365 | +109.1% |
| emit_critical_batch | 12.773 | 7.383 | +73.0% |
| emit_normal | 15.825 | 9.160 | +72.8% |
| emit_normal_batch | 11.659 | 6.062 | +92.3% |
| emit_signal | 13.234 | 7.089 | +86.7% |
| emit_signal_batch | 15.450 | 7.204 | +114.5% |
| emit_warning | 14.084 | 7.148 | +97.0% |
| emit_warning_batch | 13.735 | 8.917 | +54.0% |
| situation_from_conduit | 2.141 | 1.361 | +57.3% |
| situation_from_conduit_batch | 2.571 | 1.172 | +119.4% |

### StackOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_overflow | 17.581 | 9.002 | +95.3% |
| emit_overflow_batch | 20.224 | 6.379 | +217.0% |
| emit_pop | 15.907 | 8.817 | +80.4% |
| emit_pop_batch | 19.240 | 7.066 | +172.3% |
| emit_push | 16.507 | 7.536 | +119.0% |
| emit_push_batch | 16.329 | 6.898 | +136.7% |
| emit_sign | 16.974 | 7.131 | +138.0% |
| emit_sign_batch | 16.093 | 6.836 | +135.4% |
| emit_underflow | 17.763 | 6.572 | +170.3% |
| emit_underflow_batch | 17.714 | 6.938 | +155.3% |
| stack_from_conduit | 2.579 | 1.374 | +87.7% |
| stack_from_conduit_batch | 3.362 | 1.173 | +186.6% |

### StatusOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_converging_confirmed | 15.409 | 6.837 | +125.4% |
| emit_converging_confirmed_batch | 15.942 | 7.049 | +126.2% |
| emit_defective_tentative | 18.274 | 7.407 | +146.7% |
| emit_defective_tentative_batch | 15.591 | 7.279 | +114.2% |
| emit_degraded_measured | 13.478 | 7.445 | +81.0% |
| emit_degraded_measured_batch | 12.271 | 6.129 | +100.2% |
| emit_down_confirmed | 14.473 | 6.767 | +113.9% |
| emit_down_confirmed_batch | 12.544 | 6.083 | +106.2% |
| emit_signal | 13.804 | 8.533 | +61.8% |
| emit_signal_batch | 14.232 | 8.009 | +77.7% |
| emit_stable_confirmed | 19.893 | 6.827 | +191.4% |
| emit_stable_confirmed_batch | 14.069 | 7.593 | +85.3% |
| status_from_conduit | 2.405 | 1.335 | +80.1% |
| status_from_conduit_batch | 3.016 | 1.158 | +160.4% |

### SurveyOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_divided | 18.065 | 8.635 | +109.2% |
| emit_divided_batch | 15.447 | 7.230 | +113.7% |
| emit_majority | 16.420 | 8.795 | +86.7% |
| emit_majority_batch | 12.503 | 7.746 | +61.4% |
| emit_signal | 14.124 | 7.691 | +83.6% |
| emit_signal_batch | 13.096 | 6.045 | +116.6% |
| emit_unanimous | 15.938 | 7.376 | +116.1% |
| emit_unanimous_batch | 13.550 | 6.118 | +121.5% |
| survey_from_conduit | 2.102 | 1.355 | +55.1% |
| survey_from_conduit_batch | 2.636 | 1.164 | +126.5% |

### SystemOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_alarm_flow | 16.172 | 7.364 | +119.6% |
| emit_alarm_flow_batch | 19.451 | 6.403 | +203.8% |
| emit_fault_link | 19.894 | 6.738 | +195.3% |
| emit_fault_link_batch | 14.283 | 7.178 | +99.0% |
| emit_limit_time | 18.245 | 7.512 | +142.9% |
| emit_limit_time_batch | 12.816 | 8.426 | +52.1% |
| emit_normal_space | 12.810 | 7.579 | +69.0% |
| emit_normal_space_batch | 23.629 | 6.691 | +253.1% |
| emit_signal | 13.715 | 7.628 | +79.8% |
| emit_signal_batch | 11.425 | 8.474 | +34.8% |
| system_from_conduit | 2.069 | 1.289 | +60.5% |
| system_from_conduit_batch | 2.741 | 1.104 | +148.3% |

### TaskOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_cancel | 12.166 | 7.247 | +67.9% |
| emit_cancel_batch | 11.955 | 7.033 | +70.0% |
| emit_complete | 11.844 | 6.868 | +72.5% |
| emit_complete_batch | 11.801 | 8.391 | +40.6% |
| emit_fail | 12.506 | 6.640 | +88.3% |
| emit_fail_batch | 17.662 | 8.237 | +114.4% |
| emit_progress | 19.633 | 9.492 | +106.8% |
| emit_progress_batch | 12.697 | 7.332 | +73.2% |
| emit_reject | 17.285 | 8.708 | +98.5% |
| emit_reject_batch | 13.173 | 7.205 | +82.8% |
| emit_resume | 12.776 | 7.882 | +62.1% |
| emit_resume_batch | 11.044 | 6.809 | +62.2% |
| emit_schedule | 11.454 | 7.312 | +56.6% |
| emit_schedule_batch | 13.732 | 6.792 | +102.2% |
| emit_sign | 12.486 | 7.053 | +77.0% |
| emit_sign_batch | 12.342 | 9.202 | +34.1% |
| emit_start | 13.033 | 7.413 | +75.8% |
| emit_start_batch | 12.513 | 6.977 | +79.3% |
| emit_submit | 12.163 | 7.257 | +67.6% |
| emit_submit_batch | 17.739 | 8.059 | +120.1% |
| emit_suspend | 13.691 | 8.046 | +70.2% |
| emit_suspend_batch | 12.880 | 7.561 | +70.3% |
| emit_timeout | 17.400 | 7.076 | +145.9% |
| emit_timeout_batch | 12.445 | 8.861 | +40.4% |
| task_from_conduit | 2.074 | 1.349 | +53.7% |
| task_from_conduit_batch | 2.593 | 1.171 | +121.4% |

### TimerOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_meet_deadline | 13.583 | 6.914 | +96.5% |
| emit_meet_deadline_batch | 11.689 | 6.643 | +76.0% |
| emit_meet_threshold | 12.983 | 7.322 | +77.3% |
| emit_meet_threshold_batch | 11.675 | 8.329 | +40.2% |
| emit_miss_deadline | 12.964 | 6.849 | +89.3% |
| emit_miss_deadline_batch | 12.629 | 9.563 | +32.1% |
| emit_miss_threshold | 14.682 | 8.181 | +79.5% |
| emit_miss_threshold_batch | 13.861 | 8.436 | +64.3% |
| emit_signal | 12.745 | 7.142 | +78.5% |
| emit_signal_batch | 11.136 | 9.771 | +14.0% |
| timer_from_conduit | 2.096 | 1.362 | +53.9% |
| timer_from_conduit_batch | 2.705 | 1.171 | +131.0% |

### TransactionOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_abort_coordinator | 13.148 | 7.651 | +71.8% |
| emit_abort_coordinator_batch | 12.151 | 6.668 | +82.2% |
| emit_abort_participant | 13.359 | 6.913 | +93.2% |
| emit_abort_participant_batch | 20.832 | 9.352 | +122.8% |
| emit_commit_coordinator | 18.836 | 7.852 | +139.9% |
| emit_commit_coordinator_batch | 20.043 | 6.752 | +196.8% |
| emit_commit_participant | 22.564 | 7.008 | +222.0% |
| emit_commit_participant_batch | 16.649 | 9.462 | +76.0% |
| emit_compensate_coordinator | 18.038 | 6.868 | +162.6% |
| emit_compensate_coordinator_batch | 21.713 | 6.076 | +257.4% |
| emit_compensate_participant | 22.004 | 6.511 | +238.0% |
| emit_compensate_participant_batch | 14.335 | 6.683 | +114.5% |
| emit_conflict_coordinator | 18.624 | 7.646 | +143.6% |
| emit_conflict_coordinator_batch | 22.113 | 7.289 | +203.4% |
| emit_conflict_participant | 17.473 | 7.301 | +139.3% |
| emit_conflict_participant_batch | 15.586 | 9.401 | +65.8% |
| emit_expire_coordinator | 17.519 | 8.254 | +112.2% |
| emit_expire_coordinator_batch | 20.607 | 5.921 | +248.0% |
| emit_expire_participant | 17.629 | 7.883 | +123.6% |
| emit_expire_participant_batch | 19.646 | 8.617 | +128.0% |
| emit_prepare_coordinator | 15.766 | 8.598 | +83.4% |
| emit_prepare_coordinator_batch | 16.816 | 6.375 | +163.8% |
| emit_prepare_participant | 30.451 | 6.794 | +348.2% |
| emit_prepare_participant_batch | 18.749 | 7.537 | +148.8% |
| emit_rollback_coordinator | 29.304 | 7.165 | +309.0% |
| emit_rollback_coordinator_batch | 18.850 | 7.503 | +151.2% |
| emit_rollback_participant | 17.696 | 6.772 | +161.3% |
| emit_rollback_participant_batch | 15.633 | 7.019 | +122.7% |
| emit_signal | 17.231 | 6.765 | +154.7% |
| emit_signal_batch | 18.074 | 6.894 | +162.2% |
| emit_start_coordinator | 20.110 | 7.161 | +180.8% |
| emit_start_coordinator_batch | 16.068 | 9.874 | +62.7% |
| emit_start_participant | 15.489 | 7.657 | +102.3% |
| emit_start_participant_batch | 16.056 | 8.475 | +89.5% |
| transaction_from_conduit | 2.570 | 1.361 | +88.8% |
| transaction_from_conduit_batch | 3.155 | 1.171 | +169.4% |

### TrendOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_chaos | 11.911 | 8.280 | +43.9% |
| emit_chaos_batch | 13.549 | 7.717 | +75.6% |
| emit_cycle | 14.571 | 7.591 | +92.0% |
| emit_cycle_batch | 11.360 | 7.989 | +42.2% |
| emit_drift | 12.540 | 7.240 | +73.2% |
| emit_drift_batch | 13.385 | 7.442 | +79.9% |
| emit_sign | 12.894 | 7.827 | +64.7% |
| emit_sign_batch | 12.070 | 8.122 | +48.6% |
| emit_spike | 11.882 | 8.514 | +39.6% |
| emit_spike_batch | 11.031 | 7.434 | +48.4% |
| emit_stable | 13.302 | 8.172 | +62.8% |
| emit_stable_batch | 10.726 | 7.504 | +42.9% |
| trend_from_conduit | 2.163 | 1.234 | +75.3% |
| trend_from_conduit_batch | 2.779 | 1.066 | +160.7% |

### ValveOps

| Benchmark | Fullerstack (ns/op) | Humainary (ns/op) | Delta % |
|-----------|--------------------:|-------------------:|--------:|
| emit_contract | 15.556 | 6.863 | +126.7% |
| emit_contract_batch | 18.119 | 6.911 | +162.2% |
| emit_deny | 17.942 | 7.160 | +150.6% |
| emit_deny_batch | 23.213 | 7.537 | +208.0% |
| emit_drain | 15.513 | 7.469 | +107.7% |
| emit_drain_batch | 20.548 | 7.066 | +190.8% |
| emit_drop | 15.273 | 7.642 | +99.9% |
| emit_drop_batch | 16.943 | 8.877 | +90.9% |
| emit_expand | 19.555 | 7.182 | +172.3% |
| emit_expand_batch | 16.146 | 7.149 | +125.8% |
| emit_pass | 15.307 | 8.682 | +76.3% |
| emit_pass_batch | 15.891 | 6.856 | +131.8% |
| emit_sign | 20.319 | 7.627 | +166.4% |
| emit_sign_batch | 30.214 | 6.662 | +353.5% |
| valve_from_conduit | 2.551 | 1.369 | +86.3% |
| valve_from_conduit_batch | 3.288 | 1.160 | +183.4% |

---

## Notes

- **Delta %** is calculated as `((Fullerstack - Humainary) / Humainary) x 100`.
  - Positive values indicate Fullerstack recorded a higher (slower) time on this hardware configuration.
  - Negative values indicate Fullerstack recorded a lower time on this hardware configuration.
- Values at or near the JMH measurement floor (~0.001 ns/op) represent fully elided operations and are not meaningful for comparison.
- High error margins (large confidence intervals relative to the mean) indicate noisy measurements; treat those delta values with caution.
- Batch (`_batch`) variants use `@OperationsPerInvocation(1000)` to amortize per-invocation overhead.
- The hardware difference between platforms (M4 dedicated silicon vs shared VM) means delta percentages reflect combined code and hardware effects.
