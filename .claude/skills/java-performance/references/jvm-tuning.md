# JVM Tuning Reference

## Table of Contents
- [GC Selection](#gc-selection)
- [Memory Configuration](#memory-configuration)
- [JIT Compiler](#jit-compiler)
- [Profiling Tools](#profiling-tools)
- [Virtual Threads](#virtual-threads)

## GC Selection

### Collector Comparison

| Collector | Latency | Throughput | Use Case |
|-----------|---------|------------|----------|
| G1GC | Medium | Good | Default, balanced |
| ZGC | Ultra-low | Good | <1ms pause requirement |
| Shenandoah | Ultra-low | Good | Low pause, RedHat |
| Parallel GC | High | Best | Batch processing |
| Serial GC | Variable | Poor | Single-core, small heaps |

### Recommended Flags

```bash
# G1GC (default in JDK 9+)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m

# ZGC (JDK 15+ production ready)
-XX:+UseZGC
-XX:+ZGenerational          # JDK 21+, better throughput

# Shenandoah
-XX:+UseShenandoahGC
-XX:ShenandoahGCHeuristics=adaptive
```

## Memory Configuration

### Heap Sizing
```bash
-Xms4g -Xmx4g              # Fixed heap (recommended for prod)
-XX:NewRatio=2             # Old:Young = 2:1
-XX:SurvivorRatio=8        # Eden:Survivor = 8:1
```

### Off-Heap Memory
```bash
-XX:MaxDirectMemorySize=2g  # NIO buffers
-XX:MaxMetaspaceSize=512m   # Class metadata
-XX:ReservedCodeCacheSize=256m  # JIT compiled code
```

### Container-Aware (Docker/K8s)
```bash
-XX:+UseContainerSupport   # Auto-detect container limits (default)
-XX:MaxRAMPercentage=75.0  # Use 75% of container memory
```

## JIT Compiler

### Tiered Compilation
```bash
-XX:+TieredCompilation     # Default, C1 → C2 progression
-XX:TieredStopAtLevel=1    # C1 only (faster startup)
-XX:TieredStopAtLevel=4    # Full optimization (default)
```

### Inlining
```bash
-XX:MaxInlineSize=35       # Max bytecode size for inlining
-XX:FreqInlineSize=325     # Max for hot methods
-XX:InlineSmallCode=2000   # Max native code size to inline
```

### Diagnostics
```bash
-XX:+PrintCompilation      # Log JIT compilations
-XX:+UnlockDiagnosticVMOptions
-XX:+PrintInlining         # Log inlining decisions
-XX:+PrintAssembly         # Requires hsdis plugin
```

## Profiling Tools

### Flight Recorder (JFR)
```bash
# Start recording
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
     -jar app.jar

# Analyze
jfr print --events jdk.ExecutionSample profile.jfr
```

### async-profiler
```bash
# CPU profiling
./profiler.sh -d 30 -f profile.html <pid>

# Allocation profiling
./profiler.sh -e alloc -d 30 -f alloc.html <pid>

# Lock contention
./profiler.sh -e lock -d 30 -f locks.html <pid>
```

### JMH Profilers
```bash
java -jar benchmarks.jar -prof gc           # GC stats
java -jar benchmarks.jar -prof jfr          # Flight Recorder
java -jar benchmarks.jar -prof async        # async-profiler
java -jar benchmarks.jar -prof perfasm      # Assembly (Linux)
java -jar benchmarks.jar -prof stack        # Stack sampling
```

## Virtual Threads

### Configuration (JDK 21+)
```bash
# Virtual thread scheduler pool size
-Djdk.virtualThreadScheduler.parallelism=8

# Max pool size for carrier threads
-Djdk.virtualThreadScheduler.maxPoolSize=256
```

### Best Practices
```java
// DO: Use virtual threads for I/O-bound work
Thread.startVirtualThread(() -> {
  blockingHttpCall();  // OK to block
});

// DON'T: Use for CPU-bound work
ExecutorService cpu = Executors.newFixedThreadPool(
  Runtime.getRuntime().availableProcessors()
);

// DON'T: Hold locks across blocking calls
synchronized (lock) {
  blockingCall();  // Pins carrier thread!
}

// DO: Use ReentrantLock instead
lock.lock();
try {
  blockingCall();  // Virtual thread can unmount
} finally {
  lock.unlock();
}
```

### Pinning Detection
```bash
-Djdk.tracePinnedThreads=full  # Log when virtual thread pins
```
