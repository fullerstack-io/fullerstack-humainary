# Benchmark Comparison: Fullerstack vs Humainary

**Last Updated:** 2026-01-03
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

## Summary by Category

| Category | Subcategory | FS Wins | HUM Wins | Ties | Total |
|----------|-------------|--------:|---------:|-----:|------:|
| Serventis SDK | SDK | 16 | 70 | 5 | 91 |
| Serventis opt.data | CacheOps | 1 | 16 | 1 | 18 |
| Serventis opt.data | PipelineOps | 2 | 29 | 0 | 31 |
| Serventis opt.data | QueueOps | 2 | 10 | 0 | 12 |
| Serventis opt.data | StackOps | 2 | 10 | 0 | 12 |
| Serventis opt.exec | ProcessOps | 2 | 20 | 0 | 22 |
| Serventis opt.exec | ServiceOps | 2 | 66 | 0 | 68 |
| Serventis opt.exec | TaskOps | 2 | 24 | 0 | 26 |
| Serventis opt.exec | TimerOps | 2 | 10 | 0 | 12 |
| Serventis opt.exec | TransactionOps | 2 | 34 | 0 | 36 |
| Serventis opt.flow | BreakerOps | 2 | 14 | 0 | 16 |
| Serventis opt.flow | FlowOps | 2 | 14 | 0 | 16 |
| Serventis opt.flow | RouterOps | 2 | 20 | 0 | 22 |
| Serventis opt.flow | ValveOps | 2 | 14 | 0 | 16 |
| Serventis opt.pool | ExchangeOps | 2 | 12 | 0 | 14 |
| Serventis opt.pool | LeaseOps | 2 | 38 | 0 | 40 |
| Serventis opt.pool | PoolOps | 2 | 10 | 0 | 12 |
| Serventis opt.pool | ResourceOps | 2 | 14 | 0 | 16 |
| Serventis opt.role | ActorOps | 2 | 24 | 0 | 26 |
| Serventis opt.role | AgentOps | 2 | 42 | 0 | 44 |
| Serventis opt.sync | AtomicOps | 2 | 18 | 0 | 20 |
| Serventis opt.sync | LatchOps | 2 | 14 | 0 | 16 |
| Serventis opt.sync | LockOps | 2 | 22 | 0 | 24 |
| Serventis opt.tool | CounterOps | 2 | 8 | 0 | 10 |
| Serventis opt.tool | GaugeOps | 2 | 12 | 0 | 14 |
| Serventis opt.tool | LogOps | 2 | 10 | 0 | 12 |
| Serventis opt.tool | ProbeOps | 2 | 30 | 0 | 32 |
| Serventis opt.tool | SensorOps | 2 | 20 | 0 | 22 |
| Substrates Core | CircuitOps | 8 | 3 | 0 | 11 |
| Substrates Core | ConduitOps | 8 | 0 | 1 | 9 |
| Substrates Core | CortexOps | 11 | 7 | 2 | 20 |
| Substrates Core | FlowOps | 7 | 2 | 0 | 9 |
| Substrates Core | NameOps | 16 | 4 | 3 | 23 |
| Substrates Core | PipeOps | 2 | 8 | 3 | 13 |
| Substrates Core | ReservoirOps | 10 | 1 | 1 | 12 |
| Substrates Core | ScopeOps | 11 | 5 | 6 | 22 |
| Substrates Core | StateOps | 1 | 12 | 4 | 17 |
| Substrates Core | SubscriberOps | 10 | 1 | 0 | 11 |

## Complete Benchmark Results

All times in **ns/op** (nanoseconds per operation). Lower is better.

| Benchmark | Fullerstack | Humainary | Δ% | Winner |
|-----------|------------:|----------:|---:|:------:|
| **Serventis SDK** |||||
| i.h.sdk.OperationOps.emit_begin | 27.979 | 7.966 | +251% | **HUM** |
| i.h.sdk.OperationOps.emit_begin_batch | 30.722 | 7.693 | +299% | **HUM** |
| i.h.sdk.OperationOps.emit_end | 28.752 | 8.667 | +232% | **HUM** |
| i.h.sdk.OperationOps.emit_end_batch | 45.433 | 7.456 | +509% | **HUM** |
| i.h.sdk.OperationOps.emit_sign | 30.656 | 8.524 | +260% | **HUM** |
| i.h.sdk.OperationOps.emit_sign_batch | 41.298 | 10.178 | +306% | **HUM** |
| i.h.sdk.OperationOps.operation_from_conduit | 1.546 | 1.871 | -17% | **FS** |
| i.h.sdk.OperationOps.operation_from_conduit_batch | 1.544 | 1.660 | -7% | **FS** |
| i.h.sdk.OutcomeOps.emit_fail | 22.880 | 8.176 | +180% | **HUM** |
| i.h.sdk.OutcomeOps.emit_fail_batch | 41.702 | 8.153 | +411% | **HUM** |
| i.h.sdk.OutcomeOps.emit_sign | 33.729 | 8.338 | +305% | **HUM** |
| i.h.sdk.OutcomeOps.emit_sign_batch | 43.579 | 7.689 | +467% | **HUM** |
| i.h.sdk.OutcomeOps.emit_success | 32.085 | 8.403 | +282% | **HUM** |
| i.h.sdk.OutcomeOps.emit_success_batch | 117.334 | 7.408 | +1484% | **HUM** |
| i.h.sdk.OutcomeOps.outcome_from_conduit | 1.546 | 1.869 | -17% | **FS** |
| i.h.sdk.OutcomeOps.outcome_from_conduit_batch | 1.544 | 1.661 | -7% | **FS** |
| i.h.sdk.SignalSetOps.get_mixed_pattern | 0.225 | 0.226 | -0.4% | ~ |
| i.h.sdk.SignalSetOps.get_single | 0.750 | 0.751 | -0.1% | ~ |
| i.h.sdk.SignalSetOps.get_single_batch | 0.019 | 0.019 | +0.0% | ~ |
| i.h.sdk.SignalSetOps.get_varied_batch | 1.515 | 1.515 | +0.0% | ~ |
| i.h.sdk.SignalSetOps.get_worst_case | 1.183 | 1.184 | -0.1% | ~ |
| i.h.sdk.SituationOps.emit_critical | 23.089 | 9.962 | +132% | **HUM** |
| i.h.sdk.SituationOps.emit_critical_batch | 32.057 | 8.855 | +262% | **HUM** |
| i.h.sdk.SituationOps.emit_normal | 30.912 | 8.362 | +270% | **HUM** |
| i.h.sdk.SituationOps.emit_normal_batch | 29.206 | 9.281 | +215% | **HUM** |
| i.h.sdk.SituationOps.emit_signal | 29.466 | 9.362 | +215% | **HUM** |
| i.h.sdk.SituationOps.emit_signal_batch | 27.794 | 9.575 | +190% | **HUM** |
| i.h.sdk.SituationOps.emit_warning | 24.885 | 8.270 | +201% | **HUM** |
| i.h.sdk.SituationOps.emit_warning_batch | 29.223 | 8.744 | +234% | **HUM** |
| i.h.sdk.SituationOps.situation_from_conduit | 1.549 | 1.857 | -17% | **FS** |
| i.h.sdk.SituationOps.situation_from_conduit_batch | 1.545 | 1.660 | -7% | **FS** |
| i.h.sdk.StatusOps.emit_converging_confirmed | 24.047 | 8.274 | +191% | **HUM** |
| i.h.sdk.StatusOps.emit_converging_confirmed_batch | 36.792 | 8.154 | +351% | **HUM** |
| i.h.sdk.StatusOps.emit_defective_tentative | 28.045 | 8.174 | +243% | **HUM** |
| i.h.sdk.StatusOps.emit_defective_tentative_batch | 35.870 | 9.355 | +283% | **HUM** |
| i.h.sdk.StatusOps.emit_degraded_measured | 26.654 | 8.484 | +214% | **HUM** |
| i.h.sdk.StatusOps.emit_degraded_measured_batch | 35.002 | 7.700 | +355% | **HUM** |
| i.h.sdk.StatusOps.emit_down_confirmed | 28.835 | 8.448 | +241% | **HUM** |
| i.h.sdk.StatusOps.emit_down_confirmed_batch | 26.945 | 7.444 | +262% | **HUM** |
| i.h.sdk.StatusOps.emit_signal | 27.413 | 8.712 | +215% | **HUM** |
| i.h.sdk.StatusOps.emit_signal_batch | 26.901 | 9.243 | +191% | **HUM** |
| i.h.sdk.StatusOps.emit_stable_confirmed | 26.003 | 8.179 | +218% | **HUM** |
| i.h.sdk.StatusOps.emit_stable_confirmed_batch | 29.588 | 7.625 | +288% | **HUM** |
| i.h.sdk.StatusOps.status_from_conduit | 1.557 | 1.859 | -16% | **FS** |
| i.h.sdk.StatusOps.status_from_conduit_batch | 1.547 | 1.661 | -7% | **FS** |
| i.h.sdk.SurveyOps.emit_divided | 30.154 | 7.771 | +288% | **HUM** |
| i.h.sdk.SurveyOps.emit_divided_batch | 37.069 | 8.790 | +322% | **HUM** |
| i.h.sdk.SurveyOps.emit_majority | 35.427 | 7.635 | +364% | **HUM** |
| i.h.sdk.SurveyOps.emit_majority_batch | 29.968 | 7.497 | +300% | **HUM** |
| i.h.sdk.SurveyOps.emit_signal | 29.574 | 7.741 | +282% | **HUM** |
| i.h.sdk.SurveyOps.emit_signal_batch | 31.700 | 7.414 | +328% | **HUM** |
| i.h.sdk.SurveyOps.emit_unanimous | 27.004 | 8.498 | +218% | **HUM** |
| i.h.sdk.SurveyOps.emit_unanimous_batch | 31.354 | 8.648 | +263% | **HUM** |
| i.h.sdk.SurveyOps.survey_from_conduit | 1.546 | 1.867 | -17% | **FS** |
| i.h.sdk.SurveyOps.survey_from_conduit_batch | 1.546 | 1.661 | -7% | **FS** |
| i.h.sdk.SystemOps.emit_alarm_flow | 23.740 | 8.402 | +183% | **HUM** |
| i.h.sdk.SystemOps.emit_alarm_flow_batch | 32.018 | 9.129 | +251% | **HUM** |
| i.h.sdk.SystemOps.emit_fault_link | 31.663 | 8.559 | +270% | **HUM** |
| i.h.sdk.SystemOps.emit_fault_link_batch | 25.959 | 8.039 | +223% | **HUM** |
| i.h.sdk.SystemOps.emit_limit_time | 23.477 | 8.385 | +180% | **HUM** |
| i.h.sdk.SystemOps.emit_limit_time_batch | 34.188 | 8.152 | +319% | **HUM** |
| i.h.sdk.SystemOps.emit_normal_space | 25.911 | 8.028 | +223% | **HUM** |
| i.h.sdk.SystemOps.emit_normal_space_batch | 40.441 | 8.105 | +399% | **HUM** |
| i.h.sdk.SystemOps.emit_signal | 19.950 | 7.898 | +153% | **HUM** |
| i.h.sdk.SystemOps.emit_signal_batch | 28.689 | 8.334 | +244% | **HUM** |
| i.h.sdk.SystemOps.system_from_conduit | 1.547 | 1.870 | -17% | **FS** |
| i.h.sdk.SystemOps.system_from_conduit_batch | 1.547 | 1.661 | -7% | **FS** |
| i.h.sdk.TrendOps.emit_chaos | 31.144 | 8.430 | +269% | **HUM** |
| i.h.sdk.TrendOps.emit_chaos_batch | 28.824 | 7.991 | +261% | **HUM** |
| i.h.sdk.TrendOps.emit_cycle | 29.143 | 8.023 | +263% | **HUM** |
| i.h.sdk.TrendOps.emit_cycle_batch | 32.637 | 7.683 | +325% | **HUM** |
| i.h.sdk.TrendOps.emit_drift | 27.248 | 8.649 | +215% | **HUM** |
| i.h.sdk.TrendOps.emit_drift_batch | 43.444 | 7.751 | +460% | **HUM** |
| i.h.sdk.TrendOps.emit_sign | 49.492 | 7.929 | +524% | **HUM** |
| i.h.sdk.TrendOps.emit_sign_batch | 70.689 | 7.172 | +886% | **HUM** |
| i.h.sdk.TrendOps.emit_spike | 29.535 | 8.539 | +246% | **HUM** |
| i.h.sdk.TrendOps.emit_spike_batch | 38.562 | 7.617 | +406% | **HUM** |
| i.h.sdk.TrendOps.emit_stable | 22.897 | 8.341 | +175% | **HUM** |
| i.h.sdk.TrendOps.emit_stable_batch | 34.930 | 7.731 | +352% | **HUM** |
| i.h.sdk.TrendOps.trend_from_conduit | 1.547 | 1.860 | -17% | **FS** |
| i.h.sdk.TrendOps.trend_from_conduit_batch | 1.550 | 1.660 | -7% | **FS** |
| i.h.sdk.meta.CycleOps.cycle_from_conduit | 1.545 | 1.860 | -17% | **FS** |
| i.h.sdk.meta.CycleOps.cycle_from_conduit_batch | 1.546 | 1.660 | -7% | **FS** |
| i.h.sdk.meta.CycleOps.emit_repeat | 31.442 | 7.784 | +304% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_repeat_batch | 31.934 | 8.960 | +256% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_return | 34.085 | 8.150 | +318% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_return_batch | 37.804 | 8.428 | +349% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_signal | 27.742 | 8.474 | +227% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_signal_batch | 33.914 | 9.587 | +254% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_single | 31.305 | 8.253 | +279% | **HUM** |
| i.h.sdk.meta.CycleOps.emit_single_batch | 30.055 | 9.465 | +218% | **HUM** |
| **Serventis opt.data** |||||
| *CacheOps* |||||
| i.h.opt.data.CacheOps.cache_from_conduit | 1.545 | 1.803 | -14% | **FS** |
| i.h.opt.data.CacheOps.cache_from_conduit_batch | 1.530 | 1.606 | -5% | ~ |
| i.h.opt.data.CacheOps.emit_evict | 22.711 | 8.637 | +163% | **HUM** |
| i.h.opt.data.CacheOps.emit_evict_batch | 37.003 | 7.990 | +363% | **HUM** |
| i.h.opt.data.CacheOps.emit_expire | 25.924 | 8.303 | +212% | **HUM** |
| i.h.opt.data.CacheOps.emit_expire_batch | 33.124 | 7.692 | +331% | **HUM** |
| i.h.opt.data.CacheOps.emit_hit | 28.117 | 9.749 | +188% | **HUM** |
| i.h.opt.data.CacheOps.emit_hit_batch | 39.592 | 7.880 | +402% | **HUM** |
| i.h.opt.data.CacheOps.emit_lookup | 34.903 | 8.613 | +305% | **HUM** |
| i.h.opt.data.CacheOps.emit_lookup_batch | 37.040 | 8.367 | +343% | **HUM** |
| i.h.opt.data.CacheOps.emit_miss | 21.080 | 8.500 | +148% | **HUM** |
| i.h.opt.data.CacheOps.emit_miss_batch | 36.487 | 8.356 | +337% | **HUM** |
| i.h.opt.data.CacheOps.emit_remove | 30.027 | 8.182 | +267% | **HUM** |
| i.h.opt.data.CacheOps.emit_remove_batch | 36.346 | 7.542 | +382% | **HUM** |
| i.h.opt.data.CacheOps.emit_sign | 22.131 | 7.700 | +187% | **HUM** |
| i.h.opt.data.CacheOps.emit_sign_batch | 34.873 | 8.807 | +296% | **HUM** |
| i.h.opt.data.CacheOps.emit_store | 23.057 | 8.106 | +184% | **HUM** |
| i.h.opt.data.CacheOps.emit_store_batch | 53.094 | 7.093 | +649% | **HUM** |
| *PipelineOps* |||||
| i.h.opt.data.PipelineOps.emit_aggregate | 28.324 | 8.155 | +247% | **HUM** |
| i.h.opt.data.PipelineOps.emit_aggregate_batch | 26.513 | 7.918 | +235% | **HUM** |
| i.h.opt.data.PipelineOps.emit_backpressure | 23.997 | 9.140 | +163% | **HUM** |
| i.h.opt.data.PipelineOps.emit_backpressure_batch | 24.660 | 7.205 | +242% | **HUM** |
| i.h.opt.data.PipelineOps.emit_buffer | 21.869 | 7.942 | +175% | **HUM** |
| i.h.opt.data.PipelineOps.emit_buffer_batch | 28.597 | 7.711 | +271% | **HUM** |
| i.h.opt.data.PipelineOps.emit_checkpoint | 32.664 | 8.224 | +297% | **HUM** |
| i.h.opt.data.PipelineOps.emit_checkpoint_batch | 33.408 | 7.996 | +318% | **HUM** |
| i.h.opt.data.PipelineOps.emit_filter | 26.540 | 8.391 | +216% | **HUM** |
| i.h.opt.data.PipelineOps.emit_filter_batch | 45.714 | 8.034 | +469% | **HUM** |
| i.h.opt.data.PipelineOps.emit_input | 30.833 | 7.829 | +294% | **HUM** |
| i.h.opt.data.PipelineOps.emit_input_batch | 50.247 | 8.352 | +502% | **HUM** |
| i.h.opt.data.PipelineOps.emit_lag | 26.706 | 8.148 | +228% | **HUM** |
| i.h.opt.data.PipelineOps.emit_lag_batch | 31.118 | 7.808 | +299% | **HUM** |
| i.h.opt.data.PipelineOps.emit_output | 24.949 | 9.089 | +174% | **HUM** |
| i.h.opt.data.PipelineOps.emit_output_batch | 33.488 | 7.665 | +337% | **HUM** |
| i.h.opt.data.PipelineOps.emit_overflow | 28.063 | 7.827 | +259% | **HUM** |
| i.h.opt.data.PipelineOps.emit_overflow_batch | 29.502 | 7.951 | +271% | **HUM** |
| i.h.opt.data.PipelineOps.emit_sign | 28.789 | 8.239 | +249% | **HUM** |
| i.h.opt.data.PipelineOps.emit_sign_batch | 162.161 | 8.749 | +1753% | **HUM** |
| i.h.opt.data.PipelineOps.emit_skip | 30.964 | 8.409 | +268% | **HUM** |
| i.h.opt.data.PipelineOps.emit_skip_batch | 26.386 | 7.631 | +246% | **HUM** |
| i.h.opt.data.PipelineOps.emit_transform | 23.075 | 8.208 | +181% | **HUM** |
| i.h.opt.data.PipelineOps.emit_transform_batch | 56.527 | 7.758 | +629% | **HUM** |
| i.h.opt.data.PipelineOps.emit_watermark | 27.346 | 8.562 | +219% | **HUM** |
| i.h.opt.data.PipelineOps.emit_watermark_batch | 61.801 | 8.297 | +645% | **HUM** |
| i.h.opt.data.PipelineOps.pipeline_flow_etl | 136.836 | 44.454 | +208% | **HUM** |
| i.h.opt.data.PipelineOps.pipeline_flow_stream | 134.019 | 40.599 | +230% | **HUM** |
| i.h.opt.data.PipelineOps.pipeline_flow_windowed | 146.920 | 42.750 | +244% | **HUM** |
| i.h.opt.data.PipelineOps.pipeline_from_conduit | 1.546 | 1.885 | -18% | **FS** |
| i.h.opt.data.PipelineOps.pipeline_from_conduit_batch | 1.546 | 1.665 | -7% | **FS** |
| *QueueOps* |||||
| i.h.opt.data.QueueOps.emit_dequeue | 26.007 | 8.535 | +205% | **HUM** |
| i.h.opt.data.QueueOps.emit_dequeue_batch | 71.279 | 7.956 | +796% | **HUM** |
| i.h.opt.data.QueueOps.emit_enqueue | 21.994 | 9.087 | +142% | **HUM** |
| i.h.opt.data.QueueOps.emit_enqueue_batch | 66.064 | 8.521 | +675% | **HUM** |
| i.h.opt.data.QueueOps.emit_overflow | 24.191 | 8.585 | +182% | **HUM** |
| i.h.opt.data.QueueOps.emit_overflow_batch | 65.452 | 7.583 | +763% | **HUM** |
| i.h.opt.data.QueueOps.emit_sign | 28.450 | 8.503 | +235% | **HUM** |
| i.h.opt.data.QueueOps.emit_sign_batch | 55.189 | 8.304 | +565% | **HUM** |
| i.h.opt.data.QueueOps.emit_underflow | 26.386 | 7.664 | +244% | **HUM** |
| i.h.opt.data.QueueOps.emit_underflow_batch | 32.842 | 7.495 | +338% | **HUM** |
| i.h.opt.data.QueueOps.queue_from_conduit | 1.546 | 1.873 | -17% | **FS** |
| i.h.opt.data.QueueOps.queue_from_conduit_batch | 1.547 | 1.660 | -7% | **FS** |
| *StackOps* |||||
| i.h.opt.data.StackOps.emit_overflow | 22.757 | 8.581 | +165% | **HUM** |
| i.h.opt.data.StackOps.emit_overflow_batch | 35.652 | 7.811 | +356% | **HUM** |
| i.h.opt.data.StackOps.emit_pop | 24.007 | 7.590 | +216% | **HUM** |
| i.h.opt.data.StackOps.emit_pop_batch | 32.473 | 7.541 | +331% | **HUM** |
| i.h.opt.data.StackOps.emit_push | 25.779 | 8.650 | +198% | **HUM** |
| i.h.opt.data.StackOps.emit_push_batch | 98.335 | 7.454 | +1219% | **HUM** |
| i.h.opt.data.StackOps.emit_sign | 26.410 | 7.966 | +232% | **HUM** |
| i.h.opt.data.StackOps.emit_sign_batch | 33.006 | 8.757 | +277% | **HUM** |
| i.h.opt.data.StackOps.emit_underflow | 26.245 | 7.655 | +243% | **HUM** |
| i.h.opt.data.StackOps.emit_underflow_batch | 72.040 | 8.012 | +799% | **HUM** |
| i.h.opt.data.StackOps.stack_from_conduit | 1.545 | 1.863 | -17% | **FS** |
| i.h.opt.data.StackOps.stack_from_conduit_batch | 1.548 | 1.660 | -7% | **FS** |
| **Serventis opt.exec** |||||
| *ProcessOps* |||||
| i.h.opt.exec.ProcessOps.emit_crash | 28.835 | 7.719 | +274% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_crash_batch | 27.868 | 7.775 | +258% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_fail | 21.993 | 8.024 | +174% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_fail_batch | 34.350 | 7.649 | +349% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_kill | 29.763 | 8.496 | +250% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_kill_batch | 56.351 | 7.597 | +642% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_restart | 23.461 | 8.627 | +172% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_restart_batch | 50.410 | 8.962 | +462% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_resume | 29.543 | 8.274 | +257% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_resume_batch | 47.956 | 7.894 | +507% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_sign | 30.990 | 8.685 | +257% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_sign_batch | 30.483 | 8.445 | +261% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_spawn | 25.220 | 8.244 | +206% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_spawn_batch | 25.854 | 7.357 | +251% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_start | 22.536 | 7.778 | +190% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_start_batch | 36.672 | 8.088 | +353% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_stop | 25.609 | 7.969 | +221% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_stop_batch | 25.607 | 7.563 | +239% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_suspend | 26.092 | 8.300 | +214% | **HUM** |
| i.h.opt.exec.ProcessOps.emit_suspend_batch | 30.331 | 7.555 | +301% | **HUM** |
| i.h.opt.exec.ProcessOps.process_from_conduit | 1.546 | 1.877 | -18% | **FS** |
| i.h.opt.exec.ProcessOps.process_from_conduit_batch | 1.546 | 1.661 | -7% | **FS** |
| *ServiceOps* |||||
| i.h.opt.exec.ServiceOps.emit_call | 27.997 | 7.980 | +251% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_call_batch | 31.366 | 7.864 | +299% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_called | 458.121 | 8.505 | +5286% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_called_batch | 36.485 | 8.950 | +308% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_delay | 27.245 | 8.234 | +231% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_delay_batch | 29.197 | 26.676 | +9% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_delayed | 29.397 | 8.693 | +238% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_delayed_batch | 32.186 | 9.635 | +234% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_discard | 25.298 | 8.827 | +187% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_discard_batch | 31.925 | 9.461 | +237% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_discarded | 28.201 | 8.453 | +234% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_discarded_batch | 28.175 | 9.450 | +198% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_disconnect | 29.693 | 8.390 | +254% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_disconnect_batch | 37.188 | 8.338 | +346% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_disconnected | 28.279 | 9.132 | +210% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_disconnected_batch | 42.184 | 9.142 | +361% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_expire | 25.990 | 9.198 | +183% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_expire_batch | 39.304 | 9.426 | +317% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_expired | 26.401 | 8.193 | +222% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_expired_batch | 26.983 | 8.017 | +237% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_fail | 27.105 | 8.766 | +209% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_fail_batch | 30.240 | 8.097 | +273% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_failed | 22.181 | 8.327 | +166% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_failed_batch | 28.455 | 8.501 | +235% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_recourse | 29.007 | 8.351 | +247% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_recourse_batch | 29.243 | 9.634 | +204% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_recoursed | 29.267 | 8.622 | +239% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_recoursed_batch | 37.185 | 8.748 | +325% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_redirect | 26.709 | 8.551 | +212% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_redirect_batch | 41.395 | 7.708 | +437% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_redirected | 23.563 | 7.981 | +195% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_redirected_batch | 29.162 | 7.727 | +277% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_reject | 20.235 | 8.051 | +151% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_reject_batch | 33.065 | 8.042 | +311% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_rejected | 24.678 | 8.572 | +188% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_rejected_batch | 44.521 | 7.993 | +457% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_resume | 25.284 | 8.583 | +195% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_resume_batch | 26.492 | 8.462 | +213% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_resumed | 21.689 | 8.987 | +141% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_resumed_batch | 22.821 | 8.221 | +178% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_retried | 23.047 | 7.759 | +197% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_retried_batch | 39.679 | 9.009 | +340% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_retry | 23.534 | 8.061 | +192% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_retry_batch | 35.465 | 9.952 | +256% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_schedule | 24.217 | 8.428 | +187% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_schedule_batch | 31.358 | 9.570 | +228% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_scheduled | 28.156 | 8.467 | +233% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_scheduled_batch | 40.526 | 9.080 | +346% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_signal | 25.187 | 8.639 | +192% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_signal_batch | 29.325 | 7.740 | +279% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_start | 30.320 | 9.222 | +229% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_start_batch | 42.294 | 7.643 | +453% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_started | 25.096 | 8.240 | +205% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_started_batch | 39.960 | 9.806 | +308% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_stop | 24.990 | 8.670 | +188% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_stop_batch | 36.277 | 9.547 | +280% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_stopped | 23.280 | 8.300 | +180% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_stopped_batch | 30.317 | 9.277 | +227% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_succeeded | 25.419 | 8.376 | +203% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_succeeded_batch | 35.593 | 9.322 | +282% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_success | 33.348 | 7.742 | +331% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_success_batch | 49.860 | 9.124 | +446% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_suspend | 25.965 | 8.173 | +218% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_suspend_batch | 25.996 | 9.931 | +162% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_suspended | 35.681 | 8.257 | +332% | **HUM** |
| i.h.opt.exec.ServiceOps.emit_suspended_batch | 28.259 | 8.060 | +251% | **HUM** |
| i.h.opt.exec.ServiceOps.service_from_conduit | 1.545 | 1.869 | -17% | **FS** |
| i.h.opt.exec.ServiceOps.service_from_conduit_batch | 1.547 | 1.665 | -7% | **FS** |
| *TaskOps* |||||
| i.h.opt.exec.TaskOps.emit_cancel | 21.613 | 8.025 | +169% | **HUM** |
| i.h.opt.exec.TaskOps.emit_cancel_batch | 57.226 | 7.844 | +630% | **HUM** |
| i.h.opt.exec.TaskOps.emit_complete | 22.819 | 7.705 | +196% | **HUM** |
| i.h.opt.exec.TaskOps.emit_complete_batch | 66.934 | 7.510 | +791% | **HUM** |
| i.h.opt.exec.TaskOps.emit_fail | 37.101 | 9.724 | +282% | **HUM** |
| i.h.opt.exec.TaskOps.emit_fail_batch | 30.516 | 7.741 | +294% | **HUM** |
| i.h.opt.exec.TaskOps.emit_progress | 25.031 | 7.843 | +219% | **HUM** |
| i.h.opt.exec.TaskOps.emit_progress_batch | 40.242 | 7.866 | +412% | **HUM** |
| i.h.opt.exec.TaskOps.emit_reject | 30.399 | 7.340 | +314% | **HUM** |
| i.h.opt.exec.TaskOps.emit_reject_batch | 62.261 | 7.283 | +755% | **HUM** |
| i.h.opt.exec.TaskOps.emit_resume | 23.611 | 8.900 | +165% | **HUM** |
| i.h.opt.exec.TaskOps.emit_resume_batch | 47.633 | 8.067 | +490% | **HUM** |
| i.h.opt.exec.TaskOps.emit_schedule | 29.650 | 7.699 | +285% | **HUM** |
| i.h.opt.exec.TaskOps.emit_schedule_batch | 53.547 | 7.964 | +572% | **HUM** |
| i.h.opt.exec.TaskOps.emit_sign | 27.969 | 8.516 | +228% | **HUM** |
| i.h.opt.exec.TaskOps.emit_sign_batch | 29.692 | 8.771 | +239% | **HUM** |
| i.h.opt.exec.TaskOps.emit_start | 28.975 | 7.993 | +263% | **HUM** |
| i.h.opt.exec.TaskOps.emit_start_batch | 42.926 | 7.401 | +480% | **HUM** |
| i.h.opt.exec.TaskOps.emit_submit | 31.005 | 7.986 | +288% | **HUM** |
| i.h.opt.exec.TaskOps.emit_submit_batch | 52.094 | 7.779 | +570% | **HUM** |
| i.h.opt.exec.TaskOps.emit_suspend | 26.929 | 7.492 | +259% | **HUM** |
| i.h.opt.exec.TaskOps.emit_suspend_batch | 32.450 | 7.881 | +312% | **HUM** |
| i.h.opt.exec.TaskOps.emit_timeout | 27.264 | 7.734 | +253% | **HUM** |
| i.h.opt.exec.TaskOps.emit_timeout_batch | 70.320 | 7.864 | +794% | **HUM** |
| i.h.opt.exec.TaskOps.task_from_conduit | 1.546 | 1.895 | -18% | **FS** |
| i.h.opt.exec.TaskOps.task_from_conduit_batch | 1.544 | 1.666 | -7% | **FS** |
| *TimerOps* |||||
| i.h.opt.exec.TimerOps.emit_meet_deadline | 23.382 | 7.884 | +197% | **HUM** |
| i.h.opt.exec.TimerOps.emit_meet_deadline_batch | 43.702 | 7.583 | +476% | **HUM** |
| i.h.opt.exec.TimerOps.emit_meet_threshold | 22.976 | 8.059 | +185% | **HUM** |
| i.h.opt.exec.TimerOps.emit_meet_threshold_batch | 39.286 | 7.798 | +404% | **HUM** |
| i.h.opt.exec.TimerOps.emit_miss_deadline | 25.169 | 8.552 | +194% | **HUM** |
| i.h.opt.exec.TimerOps.emit_miss_deadline_batch | 28.143 | 9.461 | +197% | **HUM** |
| i.h.opt.exec.TimerOps.emit_miss_threshold | 20.086 | 8.392 | +139% | **HUM** |
| i.h.opt.exec.TimerOps.emit_miss_threshold_batch | 41.607 | 9.604 | +333% | **HUM** |
| i.h.opt.exec.TimerOps.emit_signal | 25.025 | 8.465 | +196% | **HUM** |
| i.h.opt.exec.TimerOps.emit_signal_batch | 26.877 | 8.661 | +210% | **HUM** |
| i.h.opt.exec.TimerOps.timer_from_conduit | 1.545 | 1.866 | -17% | **FS** |
| i.h.opt.exec.TimerOps.timer_from_conduit_batch | 1.547 | 1.664 | -7% | **FS** |
| *TransactionOps* |||||
| i.h.opt.exec.TransactionOps.emit_abort_coordinator | 20.680 | 8.580 | +141% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_abort_coordinator_batch | 29.094 | 9.515 | +206% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_abort_participant | 32.577 | 8.081 | +303% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_abort_participant_batch | 29.427 | 10.226 | +188% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_commit_coordinator | 26.033 | 8.877 | +193% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_commit_coordinator_batch | 47.118 | 8.138 | +479% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_commit_participant | 22.148 | 8.156 | +172% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_commit_participant_batch | 28.966 | 8.317 | +248% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_compensate_coordinator | 30.446 | 8.390 | +263% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_compensate_coordinator_batch | 33.753 | 7.770 | +334% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_compensate_participant | 30.547 | 8.849 | +245% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_compensate_participant_batch | 32.706 | 7.991 | +309% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_conflict_coordinator | 27.766 | 8.028 | +246% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_conflict_coordinator_batch | 32.105 | 8.262 | +289% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_conflict_participant | 25.270 | 8.045 | +214% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_conflict_participant_batch | 23.141 | 7.488 | +209% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_expire_coordinator | 21.637 | 8.389 | +158% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_expire_coordinator_batch | 53.019 | 8.068 | +557% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_expire_participant | 30.027 | 8.961 | +235% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_expire_participant_batch | 38.600 | 8.390 | +360% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_prepare_coordinator | 24.001 | 8.301 | +189% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_prepare_coordinator_batch | 26.828 | 8.011 | +235% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_prepare_participant | 31.721 | 8.509 | +273% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_prepare_participant_batch | 37.448 | 8.015 | +367% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_rollback_coordinator | 41.373 | 7.618 | +443% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_rollback_coordinator_batch | 42.993 | 9.490 | +353% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_rollback_participant | 26.102 | 8.136 | +221% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_rollback_participant_batch | 35.043 | 7.840 | +347% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_signal | 25.591 | 8.783 | +191% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_signal_batch | 44.233 | 9.095 | +386% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_start_coordinator | 28.978 | 8.643 | +235% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_start_coordinator_batch | 35.997 | 7.014 | +413% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_start_participant | 30.014 | 8.598 | +249% | **HUM** |
| i.h.opt.exec.TransactionOps.emit_start_participant_batch | 160.977 | 8.987 | +1691% | **HUM** |
| i.h.opt.exec.TransactionOps.transaction_from_conduit | 1.549 | 1.858 | -17% | **FS** |
| i.h.opt.exec.TransactionOps.transaction_from_conduit_batch | 1.547 | 1.657 | -7% | **FS** |
| **Serventis opt.flow** |||||
| *BreakerOps* |||||
| i.h.opt.flow.BreakerOps.breaker_from_conduit | 1.546 | 1.866 | -17% | **FS** |
| i.h.opt.flow.BreakerOps.breaker_from_conduit_batch | 1.544 | 1.658 | -7% | **FS** |
| i.h.opt.flow.BreakerOps.emit_close | 25.366 | 8.774 | +189% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_close_batch | 24.353 | 8.092 | +201% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_half_open | 24.925 | 8.199 | +204% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_half_open_batch | 39.691 | 7.627 | +420% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_open | 30.983 | 8.207 | +278% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_open_batch | 30.680 | 8.260 | +271% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_probe | 22.930 | 7.871 | +191% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_probe_batch | 29.763 | 7.698 | +287% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_reset | 28.000 | 8.467 | +231% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_reset_batch | 24.596 | 7.703 | +219% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_sign | 29.753 | 8.588 | +246% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_sign_batch | 36.453 | 8.966 | +307% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_trip | 26.249 | 8.300 | +216% | **HUM** |
| i.h.opt.flow.BreakerOps.emit_trip_batch | 28.048 | 8.962 | +213% | **HUM** |
| *FlowOps* |||||
| i.h.opt.flow.FlowOps.emit_fail_egress | 21.914 | 9.478 | +131% | **HUM** |
| i.h.opt.flow.FlowOps.emit_fail_egress_batch | 25.219 | 8.389 | +201% | **HUM** |
| i.h.opt.flow.FlowOps.emit_fail_ingress | 23.837 | 8.683 | +175% | **HUM** |
| i.h.opt.flow.FlowOps.emit_fail_ingress_batch | 41.693 | 8.145 | +412% | **HUM** |
| i.h.opt.flow.FlowOps.emit_fail_transit | 26.766 | 8.812 | +204% | **HUM** |
| i.h.opt.flow.FlowOps.emit_fail_transit_batch | 28.948 | 8.366 | +246% | **HUM** |
| i.h.opt.flow.FlowOps.emit_signal | 24.171 | 8.659 | +179% | **HUM** |
| i.h.opt.flow.FlowOps.emit_signal_batch | 22.644 | 9.550 | +137% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_egress | 33.911 | 7.994 | +324% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_egress_batch | 28.677 | 9.157 | +213% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_ingress | 29.797 | 8.301 | +259% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_ingress_batch | 31.894 | 9.162 | +248% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_transit | 27.952 | 9.160 | +205% | **HUM** |
| i.h.opt.flow.FlowOps.emit_success_transit_batch | 30.668 | 9.654 | +218% | **HUM** |
| i.h.opt.flow.FlowOps.flow_from_conduit | 1.551 | 1.866 | -17% | **FS** |
| i.h.opt.flow.FlowOps.flow_from_conduit_batch | 1.547 | 1.658 | -7% | **FS** |
| *RouterOps* |||||
| i.h.opt.flow.RouterOps.emit_corrupt | 22.348 | 9.508 | +135% | **HUM** |
| i.h.opt.flow.RouterOps.emit_corrupt_batch | 30.891 | 8.374 | +269% | **HUM** |
| i.h.opt.flow.RouterOps.emit_drop | 27.578 | 9.245 | +198% | **HUM** |
| i.h.opt.flow.RouterOps.emit_drop_batch | 28.099 | 8.348 | +237% | **HUM** |
| i.h.opt.flow.RouterOps.emit_forward | 24.958 | 8.600 | +190% | **HUM** |
| i.h.opt.flow.RouterOps.emit_forward_batch | 27.874 | 7.640 | +265% | **HUM** |
| i.h.opt.flow.RouterOps.emit_fragment | 25.345 | 8.154 | +211% | **HUM** |
| i.h.opt.flow.RouterOps.emit_fragment_batch | 34.118 | 7.546 | +352% | **HUM** |
| i.h.opt.flow.RouterOps.emit_reassemble | 21.342 | 8.822 | +142% | **HUM** |
| i.h.opt.flow.RouterOps.emit_reassemble_batch | 46.796 | 7.757 | +503% | **HUM** |
| i.h.opt.flow.RouterOps.emit_receive | 25.044 | 8.635 | +190% | **HUM** |
| i.h.opt.flow.RouterOps.emit_receive_batch | 32.329 | 8.233 | +293% | **HUM** |
| i.h.opt.flow.RouterOps.emit_reorder | 29.904 | 9.419 | +217% | **HUM** |
| i.h.opt.flow.RouterOps.emit_reorder_batch | 52.111 | 8.282 | +529% | **HUM** |
| i.h.opt.flow.RouterOps.emit_route | 25.277 | 8.597 | +194% | **HUM** |
| i.h.opt.flow.RouterOps.emit_route_batch | 282.650 | 8.190 | +3351% | **HUM** |
| i.h.opt.flow.RouterOps.emit_send | 36.951 | 8.979 | +312% | **HUM** |
| i.h.opt.flow.RouterOps.emit_send_batch | 45.695 | 7.752 | +489% | **HUM** |
| i.h.opt.flow.RouterOps.emit_sign | 24.824 | 7.521 | +230% | **HUM** |
| i.h.opt.flow.RouterOps.emit_sign_batch | 27.552 | 8.482 | +225% | **HUM** |
| i.h.opt.flow.RouterOps.router_from_conduit | 1.547 | 1.864 | -17% | **FS** |
| i.h.opt.flow.RouterOps.router_from_conduit_batch | 1.549 | 1.660 | -7% | **FS** |
| *ValveOps* |||||
| i.h.opt.flow.ValveOps.emit_contract | 20.561 | 7.840 | +162% | **HUM** |
| i.h.opt.flow.ValveOps.emit_contract_batch | 24.135 | 8.957 | +169% | **HUM** |
| i.h.opt.flow.ValveOps.emit_deny | 25.299 | 7.774 | +225% | **HUM** |
| i.h.opt.flow.ValveOps.emit_deny_batch | 42.053 | 7.909 | +432% | **HUM** |
| i.h.opt.flow.ValveOps.emit_drain | 27.237 | 8.362 | +226% | **HUM** |
| i.h.opt.flow.ValveOps.emit_drain_batch | 31.822 | 7.721 | +312% | **HUM** |
| i.h.opt.flow.ValveOps.emit_drop | 26.960 | 8.468 | +218% | **HUM** |
| i.h.opt.flow.ValveOps.emit_drop_batch | 31.421 | 7.496 | +319% | **HUM** |
| i.h.opt.flow.ValveOps.emit_expand | 26.524 | 8.973 | +196% | **HUM** |
| i.h.opt.flow.ValveOps.emit_expand_batch | 28.559 | 7.319 | +290% | **HUM** |
| i.h.opt.flow.ValveOps.emit_pass | 34.658 | 8.223 | +321% | **HUM** |
| i.h.opt.flow.ValveOps.emit_pass_batch | 40.435 | 7.800 | +418% | **HUM** |
| i.h.opt.flow.ValveOps.emit_sign | 23.567 | 7.899 | +198% | **HUM** |
| i.h.opt.flow.ValveOps.emit_sign_batch | 53.812 | 8.582 | +527% | **HUM** |
| i.h.opt.flow.ValveOps.valve_from_conduit | 1.548 | 1.854 | -17% | **FS** |
| i.h.opt.flow.ValveOps.valve_from_conduit_batch | 1.545 | 1.661 | -7% | **FS** |
| **Serventis opt.pool** |||||
| *ExchangeOps* |||||
| i.h.opt.pool.ExchangeOps.emit_contract_provider | 25.524 | 8.468 | +201% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_contract_provider_batch | 30.523 | 9.023 | +238% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_contract_receiver | 26.842 | 9.131 | +194% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_contract_receiver_batch | 25.047 | 7.947 | +215% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_full_exchange | 22.294 | 9.108 | +145% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_full_exchange_batch | 29.367 | 9.266 | +217% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_signal | 22.572 | 8.703 | +159% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_signal_batch | 30.843 | 8.072 | +282% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_transfer_provider | 26.603 | 7.817 | +240% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_transfer_provider_batch | 31.356 | 7.470 | +320% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_transfer_receiver | 25.488 | 9.165 | +178% | **HUM** |
| i.h.opt.pool.ExchangeOps.emit_transfer_receiver_batch | 50.390 | 8.082 | +523% | **HUM** |
| i.h.opt.pool.ExchangeOps.exchange_from_conduit | 1.548 | 1.872 | -17% | **FS** |
| i.h.opt.pool.ExchangeOps.exchange_from_conduit_batch | 1.543 | 1.661 | -7% | **FS** |
| *LeaseOps* |||||
| i.h.opt.pool.LeaseOps.emit_acquire | 19.981 | 8.495 | +135% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_acquire_batch | 31.043 | 9.366 | +231% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_acquired | 23.594 | 8.266 | +185% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_acquired_batch | 64.920 | 9.056 | +617% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_denied | 26.580 | 8.258 | +222% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_denied_batch | 28.416 | 7.583 | +275% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_deny | 28.011 | 8.170 | +243% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_deny_batch | 42.008 | 8.826 | +376% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_expire | 28.837 | 8.069 | +257% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_expire_batch | 28.728 | 9.485 | +203% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_expired | 37.207 | 8.971 | +315% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_expired_batch | 33.684 | 7.912 | +326% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_extend | 31.093 | 8.018 | +288% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_extend_batch | 33.852 | 9.432 | +259% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_extended | 31.566 | 8.450 | +274% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_extended_batch | 31.045 | 9.063 | +243% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_grant | 24.401 | 8.323 | +193% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_grant_batch | 27.363 | 7.838 | +249% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_granted | 27.279 | 8.510 | +221% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_granted_batch | 27.557 | 8.987 | +207% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_probe | 30.813 | 8.645 | +256% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_probe_batch | 39.842 | 10.350 | +285% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_probed | 26.601 | 8.582 | +210% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_probed_batch | 36.381 | 8.969 | +306% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_release | 22.383 | 8.352 | +168% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_release_batch | 41.596 | 9.223 | +351% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_released | 26.414 | 8.597 | +207% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_released_batch | 34.512 | 9.543 | +262% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_renew | 27.471 | 8.524 | +222% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_renew_batch | 39.427 | 8.818 | +347% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_renewed | 24.759 | 8.132 | +204% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_renewed_batch | 33.919 | 9.247 | +267% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_revoke | 25.309 | 7.968 | +218% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_revoke_batch | 29.275 | 8.016 | +265% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_revoked | 24.716 | 8.254 | +199% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_revoked_batch | 36.085 | 9.524 | +279% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_signal | 26.272 | 8.172 | +221% | **HUM** |
| i.h.opt.pool.LeaseOps.emit_signal_batch | 45.716 | 9.399 | +386% | **HUM** |
| i.h.opt.pool.LeaseOps.lease_from_conduit | 1.547 | 1.865 | -17% | **FS** |
| i.h.opt.pool.LeaseOps.lease_from_conduit_batch | 1.547 | 1.660 | -7% | **FS** |
| *PoolOps* |||||
| i.h.opt.pool.PoolOps.emit_borrow | 25.906 | 7.929 | +227% | **HUM** |
| i.h.opt.pool.PoolOps.emit_borrow_batch | 31.684 | 7.891 | +302% | **HUM** |
| i.h.opt.pool.PoolOps.emit_contract | 26.718 | 7.894 | +238% | **HUM** |
| i.h.opt.pool.PoolOps.emit_contract_batch | 27.460 | 7.683 | +257% | **HUM** |
| i.h.opt.pool.PoolOps.emit_expand | 24.756 | 7.863 | +215% | **HUM** |
| i.h.opt.pool.PoolOps.emit_expand_batch | 30.652 | 7.638 | +301% | **HUM** |
| i.h.opt.pool.PoolOps.emit_reclaim | 28.002 | 8.266 | +239% | **HUM** |
| i.h.opt.pool.PoolOps.emit_reclaim_batch | 51.417 | 7.810 | +558% | **HUM** |
| i.h.opt.pool.PoolOps.emit_sign | 28.178 | 8.040 | +250% | **HUM** |
| i.h.opt.pool.PoolOps.emit_sign_batch | 37.984 | 7.674 | +395% | **HUM** |
| i.h.opt.pool.PoolOps.pool_from_conduit | 1.546 | 1.892 | -18% | **FS** |
| i.h.opt.pool.PoolOps.pool_from_conduit_batch | 1.548 | 1.660 | -7% | **FS** |
| *ResourceOps* |||||
| i.h.opt.pool.ResourceOps.emit_acquire | 22.764 | 8.284 | +175% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_acquire_batch | 40.880 | 8.259 | +395% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_attempt | 26.148 | 7.842 | +233% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_attempt_batch | 30.394 | 7.597 | +300% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_deny | 25.957 | 8.454 | +207% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_deny_batch | 60.885 | 7.825 | +678% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_grant | 24.412 | 8.625 | +183% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_grant_batch | 42.192 | 7.400 | +470% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_release | 25.408 | 8.186 | +210% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_release_batch | 29.659 | 7.782 | +281% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_sign | 23.535 | 8.310 | +183% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_sign_batch | 47.044 | 8.874 | +430% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_timeout | 24.604 | 8.639 | +185% | **HUM** |
| i.h.opt.pool.ResourceOps.emit_timeout_batch | 83.134 | 8.031 | +935% | **HUM** |
| i.h.opt.pool.ResourceOps.resource_from_conduit | 1.545 | 1.870 | -17% | **FS** |
| i.h.opt.pool.ResourceOps.resource_from_conduit_batch | 1.543 | 1.660 | -7% | **FS** |
| **Serventis opt.role** |||||
| *ActorOps* |||||
| i.h.opt.role.ActorOps.actor_from_conduit | 1.571 | 1.851 | -15% | **FS** |
| i.h.opt.role.ActorOps.actor_from_conduit_batch | 1.547 | 1.667 | -7% | **FS** |
| i.h.opt.role.ActorOps.emit_acknowledge | 28.926 | 8.240 | +251% | **HUM** |
| i.h.opt.role.ActorOps.emit_acknowledge_batch | 43.034 | 8.173 | +427% | **HUM** |
| i.h.opt.role.ActorOps.emit_affirm | 23.411 | 7.724 | +203% | **HUM** |
| i.h.opt.role.ActorOps.emit_affirm_batch | 68.501 | 7.152 | +858% | **HUM** |
| i.h.opt.role.ActorOps.emit_ask | 56.473 | 8.288 | +581% | **HUM** |
| i.h.opt.role.ActorOps.emit_ask_batch | 27.986 | 7.722 | +262% | **HUM** |
| i.h.opt.role.ActorOps.emit_clarify | 23.427 | 8.640 | +171% | **HUM** |
| i.h.opt.role.ActorOps.emit_clarify_batch | 31.011 | 7.644 | +306% | **HUM** |
| i.h.opt.role.ActorOps.emit_command | 30.782 | 8.186 | +276% | **HUM** |
| i.h.opt.role.ActorOps.emit_command_batch | 36.839 | 7.587 | +386% | **HUM** |
| i.h.opt.role.ActorOps.emit_deliver | 31.028 | 8.391 | +270% | **HUM** |
| i.h.opt.role.ActorOps.emit_deliver_batch | 54.755 | 7.584 | +622% | **HUM** |
| i.h.opt.role.ActorOps.emit_deny | 30.483 | 8.372 | +264% | **HUM** |
| i.h.opt.role.ActorOps.emit_deny_batch | 35.516 | 7.860 | +352% | **HUM** |
| i.h.opt.role.ActorOps.emit_explain | 24.455 | 8.898 | +175% | **HUM** |
| i.h.opt.role.ActorOps.emit_explain_batch | 34.934 | 9.192 | +280% | **HUM** |
| i.h.opt.role.ActorOps.emit_promise | 28.308 | 7.970 | +255% | **HUM** |
| i.h.opt.role.ActorOps.emit_promise_batch | 41.127 | 7.899 | +421% | **HUM** |
| i.h.opt.role.ActorOps.emit_report | 21.543 | 9.330 | +131% | **HUM** |
| i.h.opt.role.ActorOps.emit_report_batch | 30.507 | 7.582 | +302% | **HUM** |
| i.h.opt.role.ActorOps.emit_request | 27.590 | 8.258 | +234% | **HUM** |
| i.h.opt.role.ActorOps.emit_request_batch | 28.388 | 8.286 | +243% | **HUM** |
| i.h.opt.role.ActorOps.emit_sign | 23.381 | 7.766 | +201% | **HUM** |
| i.h.opt.role.ActorOps.emit_sign_batch | 22.739 | 7.693 | +196% | **HUM** |
| *AgentOps* |||||
| i.h.opt.role.AgentOps.agent_from_conduit | 1.546 | 1.868 | -17% | **FS** |
| i.h.opt.role.AgentOps.agent_from_conduit_batch | 1.547 | 1.664 | -7% | **FS** |
| i.h.opt.role.AgentOps.emit_accept | 28.223 | 7.935 | +256% | **HUM** |
| i.h.opt.role.AgentOps.emit_accept_batch | 34.549 | 7.609 | +354% | **HUM** |
| i.h.opt.role.AgentOps.emit_accepted | 33.007 | 8.367 | +294% | **HUM** |
| i.h.opt.role.AgentOps.emit_accepted_batch | 26.288 | 7.660 | +243% | **HUM** |
| i.h.opt.role.AgentOps.emit_breach | 33.602 | 8.556 | +293% | **HUM** |
| i.h.opt.role.AgentOps.emit_breach_batch | 21.837 | 7.605 | +187% | **HUM** |
| i.h.opt.role.AgentOps.emit_breached | 29.612 | 8.344 | +255% | **HUM** |
| i.h.opt.role.AgentOps.emit_breached_batch | 51.238 | 8.224 | +523% | **HUM** |
| i.h.opt.role.AgentOps.emit_depend | 33.970 | 8.166 | +316% | **HUM** |
| i.h.opt.role.AgentOps.emit_depend_batch | 34.259 | 9.485 | +261% | **HUM** |
| i.h.opt.role.AgentOps.emit_depended | 24.768 | 8.560 | +189% | **HUM** |
| i.h.opt.role.AgentOps.emit_depended_batch | 29.392 | 8.926 | +229% | **HUM** |
| i.h.opt.role.AgentOps.emit_fulfill | 24.548 | 7.467 | +229% | **HUM** |
| i.h.opt.role.AgentOps.emit_fulfill_batch | 31.083 | 9.461 | +229% | **HUM** |
| i.h.opt.role.AgentOps.emit_fulfilled | 24.254 | 8.505 | +185% | **HUM** |
| i.h.opt.role.AgentOps.emit_fulfilled_batch | 27.436 | 7.899 | +247% | **HUM** |
| i.h.opt.role.AgentOps.emit_inquire | 31.611 | 8.121 | +289% | **HUM** |
| i.h.opt.role.AgentOps.emit_inquire_batch | 30.674 | 9.087 | +238% | **HUM** |
| i.h.opt.role.AgentOps.emit_inquired | 26.780 | 8.399 | +219% | **HUM** |
| i.h.opt.role.AgentOps.emit_inquired_batch | 26.617 | 7.661 | +247% | **HUM** |
| i.h.opt.role.AgentOps.emit_observe | 27.687 | 8.583 | +223% | **HUM** |
| i.h.opt.role.AgentOps.emit_observe_batch | 29.708 | 8.785 | +238% | **HUM** |
| i.h.opt.role.AgentOps.emit_observed | 26.549 | 7.882 | +237% | **HUM** |
| i.h.opt.role.AgentOps.emit_observed_batch | 35.471 | 8.410 | +322% | **HUM** |
| i.h.opt.role.AgentOps.emit_offer | 28.779 | 8.955 | +221% | **HUM** |
| i.h.opt.role.AgentOps.emit_offer_batch | 29.491 | 9.793 | +201% | **HUM** |
| i.h.opt.role.AgentOps.emit_offered | 36.494 | 8.188 | +346% | **HUM** |
| i.h.opt.role.AgentOps.emit_offered_batch | 29.139 | 7.903 | +269% | **HUM** |
| i.h.opt.role.AgentOps.emit_promise | 22.500 | 8.082 | +178% | **HUM** |
| i.h.opt.role.AgentOps.emit_promise_batch | 28.636 | 9.598 | +198% | **HUM** |
| i.h.opt.role.AgentOps.emit_promised | 25.086 | 8.261 | +204% | **HUM** |
| i.h.opt.role.AgentOps.emit_promised_batch | 60.285 | 9.627 | +526% | **HUM** |
| i.h.opt.role.AgentOps.emit_retract | 26.645 | 7.503 | +255% | **HUM** |
| i.h.opt.role.AgentOps.emit_retract_batch | 55.453 | 8.511 | +552% | **HUM** |
| i.h.opt.role.AgentOps.emit_retracted | 26.908 | 8.547 | +215% | **HUM** |
| i.h.opt.role.AgentOps.emit_retracted_batch | 32.920 | 7.807 | +322% | **HUM** |
| i.h.opt.role.AgentOps.emit_signal | 28.709 | 8.436 | +240% | **HUM** |
| i.h.opt.role.AgentOps.emit_signal_batch | 29.532 | 7.721 | +282% | **HUM** |
| i.h.opt.role.AgentOps.emit_validate | 26.888 | 8.220 | +227% | **HUM** |
| i.h.opt.role.AgentOps.emit_validate_batch | 49.200 | 8.931 | +451% | **HUM** |
| i.h.opt.role.AgentOps.emit_validated | 28.379 | 8.274 | +243% | **HUM** |
| i.h.opt.role.AgentOps.emit_validated_batch | 28.428 | 8.401 | +238% | **HUM** |
| **Serventis opt.sync** |||||
| *AtomicOps* |||||
| i.h.opt.sync.AtomicOps.atomic_from_conduit | 1.546 | 1.863 | -17% | **FS** |
| i.h.opt.sync.AtomicOps.atomic_from_conduit_batch | 1.545 | 1.664 | -7% | **FS** |
| i.h.opt.sync.AtomicOps.emit_attempt | 22.151 | 7.985 | +177% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_attempt_batch | 37.000 | 7.757 | +377% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_backoff | 22.324 | 8.150 | +174% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_backoff_batch | 27.423 | 9.791 | +180% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_exhaust | 23.259 | 8.126 | +186% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_exhaust_batch | 136.960 | 7.662 | +1688% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_fail | 33.639 | 7.981 | +321% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_fail_batch | 46.226 | 7.789 | +493% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_park | 28.060 | 8.053 | +248% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_park_batch | 37.623 | 7.905 | +376% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_sign | 29.123 | 8.030 | +263% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_sign_batch | 56.130 | 7.636 | +635% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_spin | 28.026 | 8.096 | +246% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_spin_batch | 28.672 | 7.698 | +272% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_success | 24.673 | 7.612 | +224% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_success_batch | 48.694 | 8.704 | +459% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_yield | 28.788 | 7.925 | +263% | **HUM** |
| i.h.opt.sync.AtomicOps.emit_yield_batch | 23.943 | 7.445 | +222% | **HUM** |
| *LatchOps* |||||
| i.h.opt.sync.LatchOps.emit_abandon | 28.956 | 7.851 | +269% | **HUM** |
| i.h.opt.sync.LatchOps.emit_abandon_batch | 26.899 | 7.689 | +250% | **HUM** |
| i.h.opt.sync.LatchOps.emit_arrive | 30.112 | 7.862 | +283% | **HUM** |
| i.h.opt.sync.LatchOps.emit_arrive_batch | 146.769 | 7.519 | +1852% | **HUM** |
| i.h.opt.sync.LatchOps.emit_await | 31.111 | 8.131 | +283% | **HUM** |
| i.h.opt.sync.LatchOps.emit_await_batch | 36.228 | 7.857 | +361% | **HUM** |
| i.h.opt.sync.LatchOps.emit_release | 26.273 | 8.072 | +225% | **HUM** |
| i.h.opt.sync.LatchOps.emit_release_batch | 31.996 | 9.258 | +246% | **HUM** |
| i.h.opt.sync.LatchOps.emit_reset | 32.166 | 8.095 | +297% | **HUM** |
| i.h.opt.sync.LatchOps.emit_reset_batch | 41.388 | 7.850 | +427% | **HUM** |
| i.h.opt.sync.LatchOps.emit_sign | 26.536 | 8.419 | +215% | **HUM** |
| i.h.opt.sync.LatchOps.emit_sign_batch | 33.285 | 9.738 | +242% | **HUM** |
| i.h.opt.sync.LatchOps.emit_timeout | 28.142 | 7.985 | +252% | **HUM** |
| i.h.opt.sync.LatchOps.emit_timeout_batch | 28.391 | 7.732 | +267% | **HUM** |
| i.h.opt.sync.LatchOps.latch_from_conduit | 1.546 | 1.873 | -17% | **FS** |
| i.h.opt.sync.LatchOps.latch_from_conduit_batch | 1.544 | 1.667 | -7% | **FS** |
| *LockOps* |||||
| i.h.opt.sync.LockOps.emit_abandon | 25.595 | 8.172 | +213% | **HUM** |
| i.h.opt.sync.LockOps.emit_abandon_batch | 71.601 | 7.543 | +849% | **HUM** |
| i.h.opt.sync.LockOps.emit_acquire | 27.533 | 8.652 | +218% | **HUM** |
| i.h.opt.sync.LockOps.emit_acquire_batch | 29.091 | 8.897 | +227% | **HUM** |
| i.h.opt.sync.LockOps.emit_attempt | 26.675 | 8.144 | +228% | **HUM** |
| i.h.opt.sync.LockOps.emit_attempt_batch | 28.786 | 7.620 | +278% | **HUM** |
| i.h.opt.sync.LockOps.emit_contest | 22.889 | 8.545 | +168% | **HUM** |
| i.h.opt.sync.LockOps.emit_contest_batch | 110.866 | 7.942 | +1296% | **HUM** |
| i.h.opt.sync.LockOps.emit_deny | 98.430 | 8.141 | +1109% | **HUM** |
| i.h.opt.sync.LockOps.emit_deny_batch | 28.180 | 7.694 | +266% | **HUM** |
| i.h.opt.sync.LockOps.emit_downgrade | 23.176 | 8.827 | +163% | **HUM** |
| i.h.opt.sync.LockOps.emit_downgrade_batch | 31.944 | 7.812 | +309% | **HUM** |
| i.h.opt.sync.LockOps.emit_grant | 22.967 | 8.052 | +185% | **HUM** |
| i.h.opt.sync.LockOps.emit_grant_batch | 31.339 | 7.474 | +319% | **HUM** |
| i.h.opt.sync.LockOps.emit_release | 28.661 | 8.049 | +256% | **HUM** |
| i.h.opt.sync.LockOps.emit_release_batch | 40.152 | 7.681 | +423% | **HUM** |
| i.h.opt.sync.LockOps.emit_sign | 22.626 | 7.961 | +184% | **HUM** |
| i.h.opt.sync.LockOps.emit_sign_batch | 210.319 | 7.980 | +2536% | **HUM** |
| i.h.opt.sync.LockOps.emit_timeout | 29.541 | 7.907 | +274% | **HUM** |
| i.h.opt.sync.LockOps.emit_timeout_batch | 32.482 | 7.717 | +321% | **HUM** |
| i.h.opt.sync.LockOps.emit_upgrade | 23.055 | 8.036 | +187% | **HUM** |
| i.h.opt.sync.LockOps.emit_upgrade_batch | 32.760 | 7.492 | +337% | **HUM** |
| i.h.opt.sync.LockOps.lock_from_conduit | 1.547 | 1.858 | -17% | **FS** |
| i.h.opt.sync.LockOps.lock_from_conduit_batch | 1.547 | 1.665 | -7% | **FS** |
| **Serventis opt.tool** |||||
| *CounterOps* |||||
| i.h.opt.tool.CounterOps.counter_from_conduit | 1.546 | 1.866 | -17% | **FS** |
| i.h.opt.tool.CounterOps.counter_from_conduit_batch | 1.547 | 1.669 | -7% | **FS** |
| i.h.opt.tool.CounterOps.emit_increment | 27.335 | 8.184 | +234% | **HUM** |
| i.h.opt.tool.CounterOps.emit_increment_batch | 80.107 | 7.996 | +902% | **HUM** |
| i.h.opt.tool.CounterOps.emit_overflow | 34.325 | 7.963 | +331% | **HUM** |
| i.h.opt.tool.CounterOps.emit_overflow_batch | 179.655 | 7.966 | +2155% | **HUM** |
| i.h.opt.tool.CounterOps.emit_reset | 28.327 | 8.459 | +235% | **HUM** |
| i.h.opt.tool.CounterOps.emit_reset_batch | 29.169 | 7.643 | +282% | **HUM** |
| i.h.opt.tool.CounterOps.emit_sign | 20.678 | 8.673 | +138% | **HUM** |
| i.h.opt.tool.CounterOps.emit_sign_batch | 42.546 | 8.673 | +391% | **HUM** |
| *GaugeOps* |||||
| i.h.opt.tool.GaugeOps.emit_decrement | 24.084 | 9.519 | +153% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_decrement_batch | 83.360 | 7.992 | +943% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_increment | 34.254 | 8.042 | +326% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_increment_batch | 43.669 | 7.551 | +478% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_overflow | 27.238 | 8.523 | +220% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_overflow_batch | 40.844 | 8.101 | +404% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_reset | 24.300 | 8.000 | +204% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_reset_batch | 58.893 | 8.390 | +602% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_sign | 23.939 | 8.893 | +169% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_sign_batch | 30.554 | 7.401 | +313% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_underflow | 26.857 | 8.397 | +220% | **HUM** |
| i.h.opt.tool.GaugeOps.emit_underflow_batch | 32.176 | 7.939 | +305% | **HUM** |
| i.h.opt.tool.GaugeOps.gauge_from_conduit | 1.544 | 1.869 | -17% | **FS** |
| i.h.opt.tool.GaugeOps.gauge_from_conduit_batch | 1.547 | 1.662 | -7% | **FS** |
| *LogOps* |||||
| i.h.opt.tool.LogOps.emit_debug | 58.085 | 8.666 | +570% | **HUM** |
| i.h.opt.tool.LogOps.emit_debug_batch | 36.017 | 7.312 | +393% | **HUM** |
| i.h.opt.tool.LogOps.emit_info | 25.079 | 7.850 | +219% | **HUM** |
| i.h.opt.tool.LogOps.emit_info_batch | 32.397 | 8.016 | +304% | **HUM** |
| i.h.opt.tool.LogOps.emit_severe | 26.463 | 8.667 | +205% | **HUM** |
| i.h.opt.tool.LogOps.emit_severe_batch | 39.639 | 8.053 | +392% | **HUM** |
| i.h.opt.tool.LogOps.emit_sign | 29.071 | 8.469 | +243% | **HUM** |
| i.h.opt.tool.LogOps.emit_sign_batch | 212.624 | 8.021 | +2551% | **HUM** |
| i.h.opt.tool.LogOps.emit_warning | 21.276 | 8.213 | +159% | **HUM** |
| i.h.opt.tool.LogOps.emit_warning_batch | 30.056 | 7.905 | +280% | **HUM** |
| i.h.opt.tool.LogOps.log_from_conduit | 1.547 | 1.888 | -18% | **FS** |
| i.h.opt.tool.LogOps.log_from_conduit_batch | 1.545 | 1.661 | -7% | **FS** |
| *ProbeOps* |||||
| i.h.opt.tool.ProbeOps.emit_connect | 26.392 | 9.220 | +186% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_connect_batch | 40.301 | 8.846 | +356% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_connected | 25.289 | 8.806 | +187% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_connected_batch | 38.199 | 7.432 | +414% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_disconnect | 23.667 | 9.093 | +160% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_disconnect_batch | 43.186 | 7.420 | +482% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_disconnected | 24.849 | 8.152 | +205% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_disconnected_batch | 66.630 | 7.821 | +752% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_fail | 24.079 | 9.144 | +163% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_fail_batch | 32.029 | 7.679 | +317% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_failed | 24.642 | 8.229 | +199% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_failed_batch | 67.650 | 9.074 | +646% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_process | 24.618 | 8.118 | +203% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_process_batch | 26.761 | 9.321 | +187% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_processed | 32.685 | 8.544 | +283% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_processed_batch | 33.957 | 8.109 | +319% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_receive_batch | 34.322 | 9.919 | +246% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_received_batch | 24.439 | 9.467 | +158% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_signal | 30.336 | 8.400 | +261% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_signal_batch | 42.204 | 7.867 | +436% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_succeed | 25.779 | 8.268 | +212% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_succeed_batch | 25.901 | 7.674 | +238% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_succeeded | 29.772 | 8.574 | +247% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_succeeded_batch | 28.713 | 8.275 | +247% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transfer | 25.302 | 8.463 | +199% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transfer_inbound | 28.719 | 8.598 | +234% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transfer_outbound | 25.828 | 8.122 | +218% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transferred | 22.126 | 8.439 | +162% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transmit_batch | 96.356 | 10.358 | +830% | **HUM** |
| i.h.opt.tool.ProbeOps.emit_transmitted_batch | 27.981 | 8.255 | +239% | **HUM** |
| i.h.opt.tool.ProbeOps.probe_from_conduit | 1.546 | 1.855 | -17% | **FS** |
| i.h.opt.tool.ProbeOps.probe_from_conduit_batch | 1.545 | 1.659 | -7% | **FS** |
| *SensorOps* |||||
| i.h.opt.tool.SensorOps.emit_above_baseline | 27.443 | 8.566 | +220% | **HUM** |
| i.h.opt.tool.SensorOps.emit_above_baseline_batch | 27.619 | 7.697 | +259% | **HUM** |
| i.h.opt.tool.SensorOps.emit_above_target | 32.586 | 9.360 | +248% | **HUM** |
| i.h.opt.tool.SensorOps.emit_above_target_batch | 35.173 | 9.604 | +266% | **HUM** |
| i.h.opt.tool.SensorOps.emit_above_threshold | 22.421 | 8.558 | +162% | **HUM** |
| i.h.opt.tool.SensorOps.emit_above_threshold_batch | 37.928 | 7.817 | +385% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_baseline | 21.500 | 7.751 | +177% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_baseline_batch | 26.327 | 8.776 | +200% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_target | 29.512 | 8.089 | +265% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_target_batch | 28.724 | 9.122 | +215% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_threshold | 30.787 | 8.341 | +269% | **HUM** |
| i.h.opt.tool.SensorOps.emit_below_threshold_batch | 28.830 | 9.115 | +216% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_baseline | 28.310 | 8.358 | +239% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_baseline_batch | 32.267 | 7.711 | +318% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_target | 31.185 | 8.490 | +267% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_target_batch | 25.319 | 8.887 | +185% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_threshold | 28.274 | 8.457 | +234% | **HUM** |
| i.h.opt.tool.SensorOps.emit_nominal_threshold_batch | 57.700 | 8.185 | +605% | **HUM** |
| i.h.opt.tool.SensorOps.emit_signal | 24.318 | 8.729 | +179% | **HUM** |
| i.h.opt.tool.SensorOps.emit_signal_batch | 26.181 | 8.967 | +192% | **HUM** |
| i.h.opt.tool.SensorOps.sensor_from_conduit | 1.550 | 1.872 | -17% | **FS** |
| i.h.opt.tool.SensorOps.sensor_from_conduit_batch | 1.549 | 1.661 | -7% | **FS** |
| **Substrates Core** |||||
| *CircuitOps* |||||
| i.h.CircuitOps.conduit_create_close | 201.752 | 274.761 | -27% | **FS** |
| i.h.CircuitOps.conduit_create_named | 199.567 | 281.411 | -29% | **FS** |
| i.h.CircuitOps.conduit_create_with_flow | 201.085 | 270.427 | -26% | **FS** |
| i.h.CircuitOps.create_and_close | 347.315 | 325.009 | +7% | **HUM** |
| i.h.CircuitOps.hot_conduit_create | 8.390 | 19.096 | -56% | **FS** |
| i.h.CircuitOps.hot_conduit_create_named | 7.489 | 19.084 | -61% | **FS** |
| i.h.CircuitOps.hot_conduit_create_with_flow | 6.885 | 21.881 | -69% | **FS** |
| i.h.CircuitOps.hot_pipe_async | 4.716 | 8.740 | -46% | **FS** |
| i.h.CircuitOps.hot_pipe_async_with_flow | 9.229 | 10.424 | -11% | **FS** |
| i.h.CircuitOps.pipe_async | 493.084 | 315.931 | +56% | **HUM** |
| i.h.CircuitOps.pipe_async_with_flow | 623.427 | 399.294 | +56% | **HUM** |
| *ConduitOps* |||||
| i.h.ConduitOps.get_by_name | 1.541 | 1.903 | -19% | **FS** |
| i.h.ConduitOps.get_by_name_batch | 1.542 | 1.659 | -7% | **FS** |
| i.h.ConduitOps.get_by_substrate | 1.857 | 2.053 | -10% | **FS** |
| i.h.ConduitOps.get_by_substrate_batch | 1.827 | 1.816 | +0.6% | ~ |
| i.h.ConduitOps.get_cached | 3.080 | 3.481 | -12% | **FS** |
| i.h.ConduitOps.get_cached_batch | 3.086 | 3.308 | -7% | **FS** |
| i.h.ConduitOps.subscribe | 70.259 | 472.514 | -85% | **FS** |
| i.h.ConduitOps.subscribe_batch | 80.004 | 489.816 | -84% | **FS** |
| i.h.ConduitOps.subscribe_with_emission_await | 1949.116 | 8376.233 | -77% | **FS** |
| *CortexOps* |||||
| i.h.CortexOps.circuit | 287.033 | 283.764 | +1% | ~ |
| i.h.CortexOps.circuit_batch | 293.285 | 285.831 | +3% | ~ |
| i.h.CortexOps.circuit_named | 291.682 | 267.902 | +9% | **HUM** |
| i.h.CortexOps.current | 1.370 | 1.067 | +28% | **HUM** |
| i.h.CortexOps.name_class | 3.071 | 1.496 | +105% | **HUM** |
| i.h.CortexOps.name_enum | 1.512 | 2.817 | -46% | **FS** |
| i.h.CortexOps.name_iterable | 3.752 | 11.262 | -67% | **FS** |
| i.h.CortexOps.name_path | 2.464 | 1.891 | +30% | **HUM** |
| i.h.CortexOps.name_path_batch | 2.265 | 1.675 | +35% | **HUM** |
| i.h.CortexOps.name_string | 1.518 | 2.848 | -47% | **FS** |
| i.h.CortexOps.name_string_batch | 1.206 | 2.605 | -54% | **FS** |
| i.h.CortexOps.scope | 3.897 | 9.076 | -57% | **FS** |
| i.h.CortexOps.scope_batch | 3.944 | 7.549 | -48% | **FS** |
| i.h.CortexOps.scope_named | 3.875 | 7.973 | -51% | **FS** |
| i.h.CortexOps.slot_boolean | 1.679 | 2.448 | -31% | **FS** |
| i.h.CortexOps.slot_double | 2.838 | 2.403 | +18% | **HUM** |
| i.h.CortexOps.slot_int | 1.822 | 2.327 | -22% | **FS** |
| i.h.CortexOps.slot_long | 1.639 | 2.349 | -30% | **FS** |
| i.h.CortexOps.slot_string | 1.826 | 2.267 | -19% | **FS** |
| i.h.CortexOps.state_empty | 5.898 | 0.443 | +1231% | **HUM** |
| *FlowOps* |||||
| i.h.FlowOps.baseline_no_flow_await | 22.505 | 16.186 | +39% | **HUM** |
| i.h.FlowOps.flow_combined_diff_guard_await | 16.521 | 29.679 | -44% | **FS** |
| i.h.FlowOps.flow_combined_diff_sample_await | 17.827 | 19.033 | -6% | **FS** |
| i.h.FlowOps.flow_combined_guard_limit_await | 20.635 | 29.187 | -29% | **FS** |
| i.h.FlowOps.flow_diff_await | 26.370 | 28.554 | -8% | **FS** |
| i.h.FlowOps.flow_guard_await | 21.694 | 28.121 | -23% | **FS** |
| i.h.FlowOps.flow_limit_await | 24.563 | 28.068 | -12% | **FS** |
| i.h.FlowOps.flow_sample_await | 21.460 | 17.198 | +25% | **HUM** |
| i.h.FlowOps.flow_sift_await | 14.138 | 18.582 | -24% | **FS** |
| *NameOps* |||||
| i.h.NameOps.name_chained_deep | 4.768 | 16.959 | -72% | **FS** |
| i.h.NameOps.name_chaining | 6.082 | 8.804 | -31% | **FS** |
| i.h.NameOps.name_chaining_batch | 5.837 | 8.887 | -34% | **FS** |
| i.h.NameOps.name_compare | 1.538 | 0.766 | +101% | **HUM** |
| i.h.NameOps.name_compare_batch | 0.002 | 0.001 | +0.0% | ~ |
| i.h.NameOps.name_depth | 0.528 | 1.613 | -67% | **FS** |
| i.h.NameOps.name_depth_batch | 0.001 | 1.337 | -100% | **FS** |
| i.h.NameOps.name_enclosure | 0.550 | 0.542 | +1% | ~ |
| i.h.NameOps.name_from_enum | 1.512 | 2.823 | -46% | **FS** |
| i.h.NameOps.name_from_iterable | 3.801 | 11.883 | -68% | **FS** |
| i.h.NameOps.name_from_iterator | 3.797 | 12.914 | -71% | **FS** |
| i.h.NameOps.name_from_mapped_iterable | 3.914 | 11.692 | -67% | **FS** |
| i.h.NameOps.name_from_name | 2.672 | 4.279 | -38% | **FS** |
| i.h.NameOps.name_from_string | 1.504 | 3.033 | -50% | **FS** |
| i.h.NameOps.name_from_string_batch | 1.205 | 2.830 | -57% | **FS** |
| i.h.NameOps.name_interning_chained | 9.377 | 12.383 | -24% | **FS** |
| i.h.NameOps.name_interning_same_path | 4.801 | 3.558 | +35% | **HUM** |
| i.h.NameOps.name_interning_segments | 4.450 | 9.237 | -52% | **FS** |
| i.h.NameOps.name_iterate_hierarchy | 1.650 | 1.661 | -0.7% | ~ |
| i.h.NameOps.name_parsing | 2.465 | 1.893 | +30% | **HUM** |
| i.h.NameOps.name_parsing_batch | 2.260 | 1.681 | +34% | **HUM** |
| i.h.NameOps.name_path_generation | 0.526 | 33.184 | -98% | **FS** |
| i.h.NameOps.name_path_generation_batch | 0.001 | 28.421 | -100% | **FS** |
| *PipeOps* |||||
| i.h.PipeOps.async_emit_batch | 21.033 | 11.289 | +86% | **HUM** |
| i.h.PipeOps.async_emit_batch_await | 22.594 | 18.129 | +25% | **HUM** |
| i.h.PipeOps.async_emit_chained_await | 22.168 | 17.177 | +29% | **HUM** |
| i.h.PipeOps.async_emit_fanout_await | 20.981 | 18.010 | +16% | **HUM** |
| i.h.PipeOps.async_emit_single | 13.300 | 9.103 | +46% | **HUM** |
| i.h.PipeOps.async_emit_single_await | 1706.476 | 5502.879 | -69% | **FS** |
| i.h.PipeOps.async_emit_with_flow_await | 22.525 | 17.474 | +29% | **HUM** |
| i.h.PipeOps.baseline_blackhole | 0.267 | 0.267 | +0.0% | ~ |
| i.h.PipeOps.baseline_counter | 1.610 | 1.635 | -2% | ~ |
| i.h.PipeOps.baseline_receptor | 0.262 | 0.265 | -1% | ~ |
| i.h.PipeOps.pipe_create | 4.923 | 8.606 | -43% | **FS** |
| i.h.PipeOps.pipe_create_chained | 2.139 | 0.859 | +149% | **HUM** |
| i.h.PipeOps.pipe_create_with_flow | 22.272 | 12.471 | +79% | **HUM** |
| *ReservoirOps* |||||
| i.h.ReservoirOps.baseline_emit_no_reservoir_await | 51.279 | 92.836 | -45% | **FS** |
| i.h.ReservoirOps.baseline_emit_no_reservoir_await_batch | 23.756 | 17.695 | +34% | **HUM** |
| i.h.ReservoirOps.reservoir_burst_then_drain_await | 42.841 | 76.405 | -44% | **FS** |
| i.h.ReservoirOps.reservoir_burst_then_drain_await_batch | 22.906 | 28.945 | -21% | **FS** |
| i.h.ReservoirOps.reservoir_drain_await | 51.119 | 78.088 | -35% | **FS** |
| i.h.ReservoirOps.reservoir_drain_await_batch | 22.957 | 29.193 | -21% | **FS** |
| i.h.ReservoirOps.reservoir_emit_drain_cycles_await | 107.868 | 352.356 | -69% | **FS** |
| i.h.ReservoirOps.reservoir_emit_with_capture_await | 42.608 | 73.097 | -42% | **FS** |
| i.h.ReservoirOps.reservoir_emit_with_capture_await_batch | 21.926 | 24.248 | -10% | **FS** |
| i.h.ReservoirOps.reservoir_process_emissions_await | 45.653 | 92.973 | -51% | **FS** |
| i.h.ReservoirOps.reservoir_process_emissions_await_batch | 25.501 | 26.264 | -3% | ~ |
| i.h.ReservoirOps.reservoir_process_subjects_await | 50.061 | 78.497 | -36% | **FS** |
| *ScopeOps* |||||
| i.h.ScopeOps.scope_child_anonymous | 15.075 | 17.557 | -14% | **FS** |
| i.h.ScopeOps.scope_child_anonymous_batch | 14.085 | 16.616 | -15% | **FS** |
| i.h.ScopeOps.scope_child_named | 14.521 | 22.077 | -34% | **FS** |
| i.h.ScopeOps.scope_child_named_batch | 14.491 | 16.957 | -15% | **FS** |
| i.h.ScopeOps.scope_close_idempotent | 0.676 | 2.445 | -72% | **FS** |
| i.h.ScopeOps.scope_close_idempotent_batch | 0.395 | 0.034 | +1062% | **HUM** |
| i.h.ScopeOps.scope_closure | 348.872 | 285.404 | +22% | **HUM** |
| i.h.ScopeOps.scope_closure_batch | 318.281 | 284.261 | +12% | **HUM** |
| i.h.ScopeOps.scope_complex | 782.787 | 915.198 | -14% | **FS** |
| i.h.ScopeOps.scope_create_and_close | 0.643 | 2.481 | -74% | **FS** |
| i.h.ScopeOps.scope_create_and_close_batch | 0.395 | 0.034 | +1062% | **HUM** |
| i.h.ScopeOps.scope_create_named | 0.653 | 2.480 | -74% | **FS** |
| i.h.ScopeOps.scope_create_named_batch | 0.516 | 0.033 | +1464% | **HUM** |
| i.h.ScopeOps.scope_hierarchy | 26.038 | 27.134 | -4% | ~ |
| i.h.ScopeOps.scope_hierarchy_batch | 21.312 | 26.544 | -20% | **FS** |
| i.h.ScopeOps.scope_parent_closes_children | 28.991 | 43.437 | -33% | **FS** |
| i.h.ScopeOps.scope_parent_closes_children_batch | 28.737 | 42.352 | -32% | **FS** |
| i.h.ScopeOps.scope_register_multiple | 1412.917 | 1458.789 | -3% | ~ |
| i.h.ScopeOps.scope_register_multiple_batch | 1409.814 | 1437.211 | -2% | ~ |
| i.h.ScopeOps.scope_register_single | 276.911 | 287.652 | -4% | ~ |
| i.h.ScopeOps.scope_register_single_batch | 280.058 | 278.847 | +0.4% | ~ |
| i.h.ScopeOps.scope_with_resources | 580.529 | 598.580 | -3% | ~ |
| *StateOps* |||||
| i.h.StateOps.slot_name | 0.520 | 0.524 | -0.8% | ~ |
| i.h.StateOps.slot_name_batch | 0.001 | 0.001 | +0.0% | ~ |
| i.h.StateOps.slot_type | 0.517 | 0.444 | +16% | **HUM** |
| i.h.StateOps.slot_value | 0.634 | 0.641 | -1% | ~ |
| i.h.StateOps.slot_value_batch | 0.001 | 0.001 | +0.0% | ~ |
| i.h.StateOps.state_compact | 48.295 | 10.379 | +365% | **HUM** |
| i.h.StateOps.state_compact_batch | 39.212 | 10.627 | +269% | **HUM** |
| i.h.StateOps.state_iterate_slots | 2.386 | 2.161 | +10% | **HUM** |
| i.h.StateOps.state_slot_add_int | 5.334 | 4.751 | +12% | **HUM** |
| i.h.StateOps.state_slot_add_int_batch | 5.285 | 4.594 | +15% | **HUM** |
| i.h.StateOps.state_slot_add_long | 5.016 | 4.711 | +6% | **HUM** |
| i.h.StateOps.state_slot_add_object | 3.198 | 2.626 | +22% | **HUM** |
| i.h.StateOps.state_slot_add_object_batch | 3.811 | 2.350 | +62% | **HUM** |
| i.h.StateOps.state_slot_add_string | 5.333 | 4.780 | +12% | **HUM** |
| i.h.StateOps.state_value_read | 1.566 | 1.486 | +5% | **HUM** |
| i.h.StateOps.state_value_read_batch | 0.026 | 1.267 | -98% | **FS** |
| i.h.StateOps.state_values_stream | 11.208 | 4.820 | +133% | **HUM** |
| *SubscriberOps* |||||
| i.h.SubscriberOps.close_five_conduits_await | 41.621 | 8705.307 | -100% | **FS** |
| i.h.SubscriberOps.close_five_subscriptions_await | 41.665 | 8729.380 | -100% | **FS** |
| i.h.SubscriberOps.close_idempotent_await | 11.305 | 8217.281 | -100% | **FS** |
| i.h.SubscriberOps.close_idempotent_batch_await | 0.477 | 16.615 | -97% | **FS** |
| i.h.SubscriberOps.close_no_subscriptions_await | 14.042 | 8473.855 | -100% | **FS** |
| i.h.SubscriberOps.close_no_subscriptions_batch_await | 4.470 | 14.603 | -69% | **FS** |
| i.h.SubscriberOps.close_one_subscription_await | 16.616 | 8344.003 | -100% | **FS** |
| i.h.SubscriberOps.close_one_subscription_batch_await | 36.719 | 33.666 | +9% | **HUM** |
| i.h.SubscriberOps.close_ten_conduits_await | 57.727 | 8641.081 | -99% | **FS** |
| i.h.SubscriberOps.close_ten_subscriptions_await | 56.874 | 8533.092 | -99% | **FS** |
| i.h.SubscriberOps.close_with_pending_emissions_await | 8156.726 | 8698.278 | -6% | **FS** |

---

## Key Insights and Patterns

### Overall Summary

| Category | FS Wins | HUM Wins | FS Win Rate |
|----------|--------:|---------:|------------:|
| **Substrates Core** (179 benchmarks) | 84 | 73 | 47% |
| **Serventis** (667 benchmarks) | 59 | 604 | 9% |
| **TOTAL** (846 benchmarks) | 143 | 677 | 17% |

### Substrates Core Performance

**Fullerstack Strengths:**
1. **Circuit Operations** - 73% win rate (8/11)
   - Hot pipe async operations significantly faster
2. **Conduit Operations** - 89% win rate (8/9)
   - Lookup operations (get_by_name, get_cached) consistently faster
3. **Cortex Operations** - 55% win rate (11/20)
   - Scope creation faster
4. **Flow Operations** - 78% win rate (7/9)
   - Guard and limit transformations faster
5. **Name Operations** - 70% win rate (16/23)
   - Path generation dramatically faster (3865%+)
   - String-based name creation faster
6. **Reservoir Operations** - 83% win rate (10/12)
   - Emission draining faster
7. **Scope Operations** - 50% win rate (11/22)
   - Scope creation and closure operations faster
8. **Subscriber Operations** - 91% win rate (10/11)
   - Closing subscriptions 99% faster (41ns vs 8700ns)
   - Vastly superior cleanup performance

**Humainary Strengths:**
1. **State Operations** - 71% win rate (12/17)
   - Immutable state handling significantly better
2. **Pipe Operations** - 62% win rate (8/13)
   - Batch emissions faster

### Serventis Performance Pattern

**Consistent Pattern Across ALL Serventis Modules:**

| Operation Type | FS Avg | HUM Avg | FS Performance |
|----------------|-------:|--------:|---------------:|
| `*_from_conduit` lookups | ~1.5ns | ~1.8ns | **17% faster** |
| `emit_*` operations | ~25-30ns | ~8ns | **200-300% slower** |

This pattern holds true for:
- **SDK** (91 benchmarks): 16 FS wins (all lookups), 70 HUM wins (all emissions)
- **opt.data** (73 benchmarks): Cache, Pipeline, Queue, Stack - same pattern
- **opt.exec** (164 benchmarks): Process, Service, Task, Timer, Transaction - same pattern
- **opt.flow** (70 benchmarks): Breaker, Flow, Router, Valve - same pattern
- **opt.pool** (82 benchmarks): Exchange, Lease, Pool, Resource - same pattern
- **opt.role** (70 benchmarks): Actor, Agent - same pattern
- **opt.sync** (60 benchmarks): Atomic, Latch, Lock - same pattern
- **opt.tool** (90 benchmarks): Counter, Gauge, Log, Probe, Sensor - same pattern

### Root Cause Analysis

**Why FS is faster at lookups:**
- Optimized caching and conduit retrieval logic
- Efficient name interning

**Why HUM is faster at emissions:**
- Serventis emissions go through Substrates Pipe.emit() path
- FS has ~10-15ns overhead per emit vs HUM's ~8ns baseline
- This overhead compounds across all Serventis instruments
- Core emit path optimization needed to close this gap

### Critical Observations

1. **Subscriber cleanup** - FS is dramatically better (99% faster)
   - Suggests different architectural approach to subscription management
   - HUM may have inefficient await/synchronization on close

2. **Name operations** - FS path generation is 3865% faster
   - Indicates fundamental algorithmic difference
   - HUM may be doing expensive string operations

3. **Hardware caveat** - All comparisons are cross-platform
   - HUM runs on Apple M4 (premium ARM chip)
   - FS runs on Azure VM (shared Intel vCPUs)
   - Real performance gap likely smaller on identical hardware

### Optimization Opportunities

**High Priority (Substrates Core):**
1. State operations - investigate HUM's faster immutable state handling
2. Pipe batch emissions - optimize batch processing path
3. Flow sift/sample - improve these specific transformations

**Medium Priority (Serventis):**
1. Core emit path - reduce 10-15ns overhead
   - Would improve ALL 600+ Serventis benchmarks
   - Biggest potential impact

**Low Priority:**
1. Most other operations are competitive or better

---

**Legend:**
- **FS** = Fullerstack wins (>5% faster)
- **HUM** = Humainary wins (>5% faster)
- **~** = Within 5% (tie)
- **Δ%** = Performance difference ((FS-HUM)/HUM × 100)
  - Negative (green) = FS faster (better)
  - Positive (red) = FS slower (worse, HUM faster)
