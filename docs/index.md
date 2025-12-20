# Fullerstack Substrates - Documentation Index

**Version:** 1.0.0-SNAPSHOT
**API Version:** Humainary Substrates 1.0.0-PREVIEW
**Java Version:** 25 (Preview Features Required)
**Last Updated:** 2025-12-15
**TCK Compliance:** 381/381 tests (100%)

---

## Overview

Fullerstack Substrates is a **fully compliant implementation** of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java), an event-driven observability framework designed by William Louth. This library provides the concrete runtime implementation of the Substrates specification.

**Key Concept:** Think of it like `java.util.List` (interface from Humainary) vs `ArrayList` (implementation from Fullerstack).

---

## Quick Start

### Prerequisites

```bash
# Install Java 25 via SDKMAN
sdk install java 25.0.1-open
sdk use java 25.0.1-open

# Install Humainary Substrates API (not yet on Maven Central)
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java && mvn clean install
```

### Basic Usage

```java
import static io.humainary.substrates.api.Substrates.*;

// Create a circuit (event orchestration hub)
try (Circuit circuit = cortex().circuit(cortex().name("my-app"))) {

    // Create a conduit with pipe composer
    Conduit<Pipe<String>, String> messages = circuit.conduit(
        cortex().name("messages"),
        Composer.pipe()
    );

    // Subscribe to emissions
    messages.subscribe((subject, registrar) ->
        registrar.register(msg -> System.out.println("Received: " + msg))
    );

    // Emit through a pipe
    Pipe<String> producer = messages.get(cortex().name("producer-1"));
    producer.emit("Hello, Substrates!");

    circuit.await(); // Wait for async processing
}
```

---

## Documentation Index

### Core Documentation

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.md](../fullerstack-substrates/docs/ARCHITECTURE.md) | Core architecture, sealed hierarchy, data flow |
| [ASYNC-ARCHITECTURE.md](../fullerstack-substrates/docs/ASYNC-ARCHITECTURE.md) | Virtual CPU Core pattern, Valve design |
| [DEVELOPER-GUIDE.md](../fullerstack-substrates/docs/DEVELOPER-GUIDE.md) | Best practices, performance tips, testing |
| [CIRCUIT-PERFORMANCE-PLAN.md](../fullerstack-substrates/docs/CIRCUIT-PERFORMANCE-PLAN.md) | **Active** - Performance optimization plan |

### Examples

| Example | Description |
|---------|-------------|
| [01-HelloSubstrates](../fullerstack-substrates/docs/examples/01-HelloSubstrates.md) | Basic circuit and conduit usage |
| [02-Transformations](../fullerstack-substrates/docs/examples/02-Transformations.md) | Flow transformations (sift, limit, skip) |
| [03-MultipleSubscribers](../fullerstack-substrates/docs/examples/03-MultipleSubscribers.md) | Fan-out patterns |
| [04-ResourceManagement](../fullerstack-substrates/docs/examples/04-ResourceManagement.md) | Scope and lifecycle management |
| [05-SemioticObservability](../fullerstack-substrates/docs/examples/05-SemioticObservability.md) | Serventis instruments |

### Benchmarks & Performance

| Document | Description |
|----------|-------------|
| [CIRCUIT-PERFORMANCE-PLAN.md](../fullerstack-substrates/docs/CIRCUIT-PERFORMANCE-PLAN.md) | Performance improvement plan |
| [BENCHMARK-RESULTS-2025-12-13.md](../fullerstack-substrates/docs/BENCHMARK-RESULTS-2025-12-13.md) | Latest benchmark results |
| [Humainary BENCHMARKS.md](../substrates-api-java/BENCHMARKS.md) | Reference implementation benchmarks |

---

## Source Code Structure

```
fullerstack-substrates/src/main/java/io/fullerstack/substrates/
├── FsCortexProvider.java     # SPI entry point (loaded via ServiceLoader)
├── FsCortex.java             # Cortex implementation (factory)
├── FsCircuit.java            # Base circuit (ring buffer)
├── FsConduit.java            # Conduit implementation (channel routing)
├── FsChannel.java            # Channel implementation
├── FsCircuitPipe.java        # Unified pipe implementation
├── FsFlow.java               # Flow transformations (sift, limit, skip)
├── FsCell.java               # Hierarchical computation cells
├── FsName.java               # Interned hierarchical names
├── FsState.java              # Immutable state container
├── FsSubject.java            # Contextual subject with parent/state/type
├── FsScope.java              # Resource lifecycle management
├── FsReservoir.java          # Subscriber management
├── FsSift.java               # Filter transformations
├── FsSlot.java               # Typed state slots (record)
│
├── valve/
│   └── FsValveCircuit.java   # MPSC linked list circuit (fastest)
├── optimized/
│   └── FsOptimizedCircuit.java # Ring buffer with cache-line padding
├── ring/
│   └── FsRingCircuit.java    # Current default circuit
├── batch/
│   └── FsBatchCircuit.java   # Batch processing circuit
└── disruptor/
    └── FsDisruptorCircuit.java # LMAX Disruptor integration
```

### Source Metrics

| Category | Files | Lines | Description |
|----------|-------|-------|-------------|
| Main Sources | 28 | ~5,500 | Core implementation |
| Test Sources | 8 | ~400 | Contract tests |
| JMH Benchmarks | 43 | ~3,000 | Performance benchmarks |
| **Total** | **79** | **~8,900** | |

---

## Circuit Implementations

| Circuit | Emit Latency | Await Consistency | Use Case |
|---------|--------------|-------------------|----------|
| **FsValveCircuit** | 27.6 ns (fastest) | High variance | General purpose |
| **FsOptimizedCircuit** | 43.9 ns | Most consistent | Latency-sensitive |
| **FsRingCircuit** | 94.4 ns (default) | Good | Current default |
| **FsBatchCircuit** | — | — | High-throughput |
| **FsDisruptorCircuit** | — | — | LMAX Disruptor |

Select via system property:
```bash
java -Dfullerstack.circuit.type=valve -jar app.jar
# Options: valve, optimized, ring, base
```

---

## Critical Issues

### Performance Optimization (Active)

**Status:** Under Investigation
**Document:** [CIRCUIT-PERFORMANCE-PLAN.md](../fullerstack-substrates/docs/CIRCUIT-PERFORMANCE-PLAN.md)

| Metric | Previous Best | Current | Target (Humainary) |
|--------|---------------|---------|-------------------|
| Single Emit | ~16 ns | 27.6 ns | 10.6 ns |
| Batch Emit | ~20 ns | 29.1 ns | 11.8 ns |

A high-performance circuit implementation was accidentally lost. Recovery efforts are ongoing with guidance from William Louth.

---

## Build Commands

```bash
# Ensure Java 25 active
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-open

# Build
cd fullerstack-substrates
mvn clean install

# Run tests
mvn test

# Format code
mvn spotless:apply

# Run benchmarks
mvn clean package
java --enable-preview -jar target/benchmarks.jar

# Run specific benchmark
java --enable-preview -jar target/benchmarks.jar "OptimizedCircuitBenchmark"

# Run TCK
cd ../substrates-api-java/tck
mvn test -Dtck \
  -Dtck.spi.groupId=io.fullerstack \
  -Dtck.spi.artifactId=fullerstack-substrates \
  -Dtck.spi.version=1.0.0-SNAPSHOT
```

---

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Humainary Substrates API | 1.0.0-PREVIEW | Core API specification |
| Humainary Serventis | 1.0.0-PREVIEW | 24 observability instruments |
| Lombok | 1.18.34 | Boilerplate reduction |
| Vavr | 0.10.4 | Functional data structures |
| SLF4J | 2.0.16 | Logging facade |
| LMAX Disruptor | 4.0.0 | High-performance ring buffer |
| JMH | 1.37 | Microbenchmarking |
| JUnit 5 | 5.11.0 | Testing framework |

---

## Architecture Principles

1. **API Purity** - Application code imports only `io.humainary.substrates.api.*`
2. **SPI Loading** - `FsCortexProvider` loaded via `ServiceLoader`
3. **Async Processing** - Always use `circuit.await()` in tests
4. **Resource Cleanup** - Use try-with-resources for Circuits and Scopes
5. **Caching** - Cache Pipes for repeated emissions
6. **Thread Safety** - Don't block in subscriber callbacks

---

## External Resources

- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) - Official API specification
- [Observability X Blog](https://humainary.io/blog/category/observability-x/) - William Louth's architectural vision
- [Serventis SIGNS.md](../substrates-api-java/ext/serventis/SIGNS.md) - Semiotic observability concepts

---

## Project Status

| Aspect | Status |
|--------|--------|
| API Compliance | 100% (381/381 TCK tests) |
| Performance | Under optimization |
| Documentation | Complete |
| Examples | 5 tutorials |
| Benchmarks | Comprehensive |

---

*Generated by Document Project Workflow - 2025-12-15*
