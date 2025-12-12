# Fullerstack Substrates Benchmark Results

Comparison of Fullerstack circuit implementations against Humainary's Alpha SPI reference.

**Test Environment:**
- JDK 25.0.1, OpenJDK 64-Bit Server VM
- Linux 6.8.0-1041-azure (Codespace)
- JMH 1.37, 2 warmup iterations, 3 measurement iterations

**Note:** Disruptor circuit has a bug in the emit wait loop and is excluded from results.

## CortexOps - Circuit-Independent Operations

| Benchmark | Humainary | Fullerstack |
|-----------|-----------|-------------|
| cortex_current | 1.09 ns | 3.31 ns |
| cortex_name_string | 2.85 ns | 3.88 ns |
| cortex_name_string_batch | 2.54 ns | 4.46 ns |
| cortex_name_path | 1.89 ns | 7.09 ns |
| cortex_name_path_batch | 1.69 ns | 7.28 ns |
| cortex_name_iterable | 11.23 ns | 10.79 ns |
| cortex_name_class | 1.48 ns | 10.49 ns |
| cortex_scope | 9.28 ns | 8.82 ns |
| cortex_scope_named | 8.01 ns | 8.28 ns |
| cortex_slot_int | 2.12 ns | 2.81 ns |
| cortex_slot_long | 2.42 ns | 3.01 ns |
| cortex_slot_double | 2.43 ns | 7.42 ns |
| cortex_slot_string | 2.43 ns | 2.95 ns |
| cortex_state_empty | 0.44 ns | 2.36 ns |
| cortex_state_empty_batch | ~0.001 ns | 1.94 ns |

## Pipe Baselines

| Benchmark | Humainary | Fullerstack |
|-----------|-----------|-------------|
| baseline_blackhole | 0.27 ns | 1.14 ns |
| baseline_counter | 1.62 ns | 2.88 ns |
| baseline_receptor | 0.26 ns | 0.58 ns |

## CircuitOps - Circuit Lifecycle (All Implementations)

| Benchmark | Humainary | Baseline | Valve | Ring | Batch |
|-----------|-----------|----------|-------|------|-------|
| create_and_close | 337 ns | 8,570 ns | 5,815 ns | 40,155 ns | 15,354 ns |
| create_await_close | 10,731 ns | 4,330 ns | 6,585 ns | 34,204 ns | 22,491 ns |
| hot_await | N/A | 1.78 ns | 1.62 ns | 2.20 ns | 4.44 ns |
| hot_pipe_create | 8.53 ns | 7.06 ns | 6.51 ns | 6.62 ns | 6.92 ns |
| hot_pipe_create_with_flow | 10.68 ns | 25.73 ns | 26.13 ns | 28.48 ns | 24.18 ns |

## ConduitOps - Conduit Operations (All Implementations)

| Benchmark | Humainary | Baseline | Valve | Ring | Batch |
|-----------|-----------|----------|-------|------|-------|
| conduit_percept | 1.88 ns | 8.53 ns | 8.50 ns | 8.47 ns | 8.74 ns |

## PipeOps - Emit Operations (All Implementations)

| Benchmark | Humainary | Baseline | Valve | Ring | Batch |
|-----------|-----------|----------|-------|------|-------|
| emit_single | 10.65 ns | 31.26 ns | 32.32 ns | 125.52 ns | 21.15 ns |
| emit_single_await | 5,478 ns | 179 ns | 313 ns | 313 ns | 379 ns |
| emit_batch (per op) | 11.84 ns | 37.30 ns | 31.98 ns | 301.64 ns | 29.32 ns |
| emit_batch_await (per op) | 16.87 ns | 116.86 ns | 108.98 ns | 146.71 ns | 44.60 ns |
| emit_with_flow_await (per op) | 21.22 ns | 151.26 ns | 134.04 ns | 186.08 ns | 118.41 ns |
| emit_chained_await (per op) | 16.93 ns | 89.18 ns | 92.34 ns | 157.54 ns | 45.85 ns |

## Summary by Implementation

### Baseline (FsCircuit) - MPSC linked list + virtual thread
- **Best for:** General purpose, simple implementation
- **Hot-path emit:** ~31 ns
- **Create/close:** ~8.6 μs

### Valve (FsValveCircuit) - Object pooling + lazy unpark
- **Best for:** Low-latency scenarios with pooled objects
- **Hot-path emit:** ~32 ns
- **Create/close:** ~5.8 μs (fastest creation)

### Ring (FsRingCircuit) - Zero-allocation ring buffer
- **Best for:** Memory-sensitive applications
- **Hot-path emit:** ~126 ns (slower due to CAS contention)
- **Create/close:** ~40 μs (slowest due to ring buffer pre-allocation)

### Batch (FsBatchCircuit) - Thread-local batching
- **Best for:** High-throughput batch processing
- **Hot-path emit:** ~21 ns (fastest emit)
- **Batch await:** ~45 ns/op (best batch throughput)
- **Create/close:** ~15 μs

## Key Observations

1. **Batch circuit has best emit throughput** - 21ns single emit, 45ns/op batched
2. **Valve has fastest circuit creation** - 5.8μs vs 8.6μs baseline
3. **Ring buffer is memory-efficient but slower** - CAS contention on single-producer
4. **Fullerstack emit_single_await is much faster** - 179-379ns vs Humainary's 5,478ns
5. **Conduit percept lookup is ~4.5x slower** - 8.5ns vs 1.9ns (optimization opportunity)
6. **Name parsing is slower** - Fullerstack uses hierarchical interning
