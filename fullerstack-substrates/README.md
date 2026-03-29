# Fullerstack Substrates

A **fully compliant implementation** of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) (version 1.0.0).

## Status

- **Version:** 1.0.0-RC3
- **Java Version:** 26 (Virtual Threads + Preview Features)
- **Substrates API:** 1.0.0

## Quick Start

### Prerequisites

1. **Java 26** (install via SDKMAN):
   ```bash
   sdk install java 26.ea.35-open
   sdk use java 26.ea.35-open
   ```

2. **Install Humainary APIs** (not yet on Maven Central):
   ```bash
   # Substrates API
   git clone https://github.com/humainary-io/substrates-api-java.git
   cd substrates-api-java/api
   mvn clean install -DskipTests
   cd ../..

   # Serventis API
   git clone https://github.com/humainary-io/serventis-api-java.git
   cd serventis-api-java/api
   mvn clean install -DskipTests
   ```

### Build

```bash
cd fullerstack-substrates
mvn clean install
```

### Usage

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>1.0.0-RC3</version>
</dependency>
```

```java
import static io.humainary.substrates.api.Substrates.*;

// Create circuit with sequential processing
Circuit circuit = cortex().circuit(cortex().name("example"));

// Create conduit for typed emissions
Conduit<Pipe<String>, String> messages = circuit.conduit(
    cortex().name("messages"),
    Composer.pipe()
);

// Subscribe to emissions
messages.subscribe(circuit.subscriber(
    cortex().name("logger"),
    (subject, registrar) -> registrar.register(msg -> System.out.println(msg))
));

// Emit messages
Pipe<String> pipe = messages.percept(cortex().name("user-1"));
pipe.emit("Hello, Substrates!");

// Wait for async processing
circuit.await();

// Cleanup
circuit.close();
```

## Architecture

### Virtual CPU Core Pattern

Each Circuit uses a single virtual thread with dual-queue architecture:

```
External Emissions → Ingress Queue (IngressQueue, wait-free) →
                                                               → Virtual Thread
Cascading Emissions → Transit Queue (Intrusive FIFO) →           → Depth-First Execution
```

**Key Features:**
- **Wait-free producer path** - Custom IngressQueue for external emissions
- **TransitQueue** - Separate class for cascading emissions
- **VarHandle optimization** - Opaque reads for parked flag (cheaper than volatile)
- **Eager thread start** - Thread created immediately on circuit construction
- **Depth-first execution** - Transit queue has priority for causality preservation

### Implementation Classes

| API Interface | Implementation | Description |
|---------------|----------------|-------------|
| `Cortex` | `FsCortex` | Entry point, creates Circuits and Scopes |
| `Circuit` | `FsCircuit` | Event orchestration with dual-queue processing |
| `Conduit` | `FsConduit` | Creates Channels, manages subscribers |
| `Channel` | `FsChannel` | Named emission port |
| `Pipe` | `FsPipe` | Async emission carrier |
| `Name` | `FsName` | Hierarchical dot-notation names |
| `Subject` | `FsSubject` | Contextual entity identity |
| `Scope` | `FsScope` | Resource lifecycle management |
| `Subscriber` | `FsSubscriber` | Emission observer |
| `Tap` | `FsTap` | Conduit emission transformation |
| `Reservoir` | `FsReservoir` | Buffered emission capture |
| `Current` | `FsCurrent` | Circuit execution context |
| `State` | `FsState` | Slot-based state container |
| `Slot` | `FsSlot` | Typed state value holder |
| `Flow` | `FsFlow` | Emission processing pipeline |

Internal support classes: `IngressQueue` (wait-free external queue), `TransitQueue` (intrusive cascade FIFO), `QChunk` (unrolled linked list with cache-line isolation).

## Performance

Benchmarks are run using JMH (Java Microbenchmark Harness) in average time mode (ns/op).
See [Benchmark Comparison](docs/BENCHMARK-COMPARISON.md) for full results across all 14 benchmark groups.

### Selected Results (Fullerstack ns/op)

| Benchmark | ns/op | Group |
|-----------|------:|-------|
| hot_pipe_async | 12.2 | CircuitOps |
| hot_conduit_create | 20.1 | CircuitOps |
| async_emit_single | 11.0 | PipeOps |
| name_from_string | 2.2 | NameOps |
| scope_create_and_close | 0.7 | ScopeOps |
| subject_compare | 2.9 | SubjectOps |
| baseline_no_flow_await | 18.6 | FlowOps |
| cyclic_emit | 2.6 | CyclicOps |

> Hardware: AMD EPYC 7763 (2 vCPU), 8 GB, JDK 25.0.1 (GitHub Codespaces).
> See the comparison doc for Humainary baselines collected on Apple M4.

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) - Core concepts and design
- [Async Architecture](docs/ASYNC-ARCHITECTURE.md) - Async-first design details
- [Circuit Design](docs/CIRCUIT-DESIGN.md) - Dual-queue implementation
- [Developer Guide](docs/DEVELOPER-GUIDE.md) - Best practices and patterns
- [Benchmark Comparison](docs/BENCHMARK-COMPARISON.md) - Full JMH results
- [Serventis Integration](docs/SERVENTIS.md) - Semiotic observability instruments
- [Kitchen Model](docs/KITCHEN-MODEL.md) - Kitchen analogy for Substrates concepts
- [Use Cases](docs/USE-CASES.md) - Practical application scenarios

## Running Tests

```bash
cd fullerstack-substrates && mvn test
```

> **703 tests** (255 contract + 448 TCK). The upstream TCK source files were integrated directly
> into this repository when the TCK module was removed in API 1.0.0.

## Running Benchmarks

```bash
./scripts/benchmark.sh              # Run all 14 benchmark groups
./scripts/benchmark.sh PipeOps      # Run a specific group
./scripts/benchmark.sh -l           # List available benchmarks
```

## License

Apache License 2.0

## Acknowledgments

This implementation is based entirely on the Humainary Substrates API designed by **William Louth**.

- **William Louth** - API design and architectural vision
- **Humainary** - Substrates and Serventis specifications
- [Humainary Website](https://humainary.io/)
- [Substrates API](https://github.com/humainary-io/substrates-api-java)
- [Observability X Blog](https://humainary.io/blog/category/observability-x/)
