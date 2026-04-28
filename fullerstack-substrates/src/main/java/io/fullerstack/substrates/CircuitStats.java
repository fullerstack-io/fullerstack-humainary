package io.fullerstack.substrates;

/// **Fullerstack-internal diagnostic.** Not part of the Substrates API,
/// not part of any stability contract. The shape and contents may change
/// between releases without notice.
///
/// Snapshot of internal queue and dispatch counters for a circuit. Intended
/// for use by Fullerstack's own test harness, JMH benchmarks, and ad-hoc
/// tuning work — not for application observability. Application code should
/// use the spec-level mechanisms (subscribers, fibers, reservoirs) to capture
/// signal-level information; the engine's internal counters are not stable
/// surface area.
///
/// Counters are best-effort: writes are issued on the worker thread; reads
/// from any thread observe values published via volatile semantics. Numbers
/// may lag the most recent worker activity by one fence interval.
///
/// @param ingressDrainBatchCount  number of times the worker drained an ingress batch
/// @param transitDrainCount       number of times the transit ring was drained
/// @param transitEnqueueCount     number of times an entry was enqueued onto transit
/// @param transitEntriesProcessed number of entries actually consumed by drain calls
/// @param transitGrowCount        number of times the transit ring doubled
/// @param transitCurrentSize      live entries in the transit ring at snapshot time
/// @param transitCurrentCapacity  current backing capacity of the transit ring (== high-water in 2x steps)
/// @param rebuildCount            number of channel rebuilds (lazy subscriber graph rebuilds)
public record CircuitStats (
  long ingressDrainBatchCount,
  long transitDrainCount,
  long transitEnqueueCount,
  long transitEntriesProcessed,
  long transitGrowCount,
  int  transitCurrentSize,
  int  transitCurrentCapacity,
  long rebuildCount
) {}
