# JMH Benchmarking Patterns

## Table of Contents
- [Setup](#setup)
- [Benchmark Modes](#benchmark-modes)
- [State Management](#state-management)
- [Common Pitfalls](#common-pitfalls)
- [Interpreting Results](#interpreting-results)

## Setup

### Maven Dependencies
```xml
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-core</artifactId>
  <version>1.37</version>
</dependency>
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-generator-annprocess</artifactId>
  <version>1.37</version>
  <scope>provided</scope>
</dependency>
```

### Basic Benchmark
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class MyBenchmark {

  @Benchmark
  public int baseline() {
    return 42;
  }
}
```

## Benchmark Modes

| Mode | Measures | Use When |
|------|----------|----------|
| `AverageTime` | Mean time per operation | Default choice for latency |
| `Throughput` | Operations per second | Measuring capacity |
| `SampleTime` | Latency distribution (percentiles) | Understanding tail latency |
| `SingleShotTime` | Cold start time | Startup performance |

## State Management

### Scope Levels
```java
@State(Scope.Benchmark)  // Shared across all threads
@State(Scope.Thread)     // One per thread (no contention)
@State(Scope.Group)      // Shared within @Group
```

### Setup/Teardown
```java
@Setup(Level.Trial)      // Once per fork
@Setup(Level.Iteration)  // Each measurement iteration
@Setup(Level.Invocation) // Each @Benchmark call (AVOID - adds overhead)

@TearDown(Level.Trial)   // Cleanup resources
```

## Common Pitfalls

### Dead Code Elimination
```java
// BAD: JIT may eliminate unused computation
@Benchmark
public void bad() {
  Math.sin(x);  // Result unused - may be optimized away
}

// GOOD: Return result or use Blackhole
@Benchmark
public double good() {
  return Math.sin(x);
}

@Benchmark
public void goodBlackhole(Blackhole bh) {
  bh.consume(Math.sin(x));
}
```

### Constant Folding
```java
// BAD: JIT computes at compile time
private static final double X = 1.0;
@Benchmark
public double bad() {
  return Math.sin(X);  // Constant - computed once
}

// GOOD: Use @State field
@State(Scope.Thread)
public static class MyState {
  double x = 1.0;
}
@Benchmark
public double good(MyState state) {
  return Math.sin(state.x);
}
```

### Loop Optimization
```java
// BAD: JIT may vectorize or unroll unexpectedly
@Benchmark
public int bad() {
  int sum = 0;
  for (int i = 0; i < 1000; i++) {
    sum += array[i];
  }
  return sum;
}

// GOOD: Use @OperationsPerInvocation
@Benchmark
@OperationsPerInvocation(1000)
public int good() { /* same loop */ }
```

## Interpreting Results

### Understanding Error Margins
```
Benchmark          Mode  Cnt   Score    Error  Units
emit_increment     avgt    5  26.941 ± 9.927  ns/op
```
- **Score**: Mean of measurements
- **Error**: 99.9% confidence interval
- **High error** (>50% of score): Results unreliable, increase iterations

### Comparing Implementations
```java
// Run both in same JMH run for fair comparison
@Benchmark public void implA() { ... }
@Benchmark public void implB() { ... }
```

### Profilers
```bash
java -jar benchmarks.jar -prof gc         # GC overhead
java -jar benchmarks.jar -prof perfasm    # Assembly (Linux)
java -jar benchmarks.jar -prof jfr        # Flight Recorder
java -jar benchmarks.jar -prof async      # async-profiler
```
