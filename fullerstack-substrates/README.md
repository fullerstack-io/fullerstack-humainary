# Fullerstack Substrates

A **fully compliant implementation** of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) (version 1.0.0-PREVIEW).

## Status

- **Version:** 1.0.0-RC2
- **TCK Compliance:** 387/387 tests passing (100%)
- **Java Version:** 25 (Virtual Threads + Preview Features)

## Quick Start

### Prerequisites

1. **Java 25** (install via SDKMAN):
   ```bash
   sdk install java 25.0.1-open
   sdk use java 25.0.1-open
   ```

2. **Install Humainary Substrates API** (not yet on Maven Central):
   ```bash
   git clone https://github.com/humainary-io/substrates-api-java.git
   cd substrates-api-java
   mvn clean install
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
    <version>1.0.0-RC2</version>
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
messages.subscribe(cortex().subscriber(
    cortex().name("logger"),
    (subject, registrar) -> registrar.register(msg -> System.out.println(msg))
));

// Emit messages
Pipe<String> pipe = messages.get(cortex().name("user-1"));
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
External Emissions → Ingress Queue (JCTools MPSC, wait-free) →
                                                               → Virtual Thread
Cascading Emissions → Transit Queue (Intrusive FIFO) →           → Depth-First Execution
```

**Key Features:**
- **Wait-free producer path** - JCTools MPSC queue for external emissions
- **Intrusive transit queue** - Zero allocation for cascading emissions
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
| `Cell` | `FsCell` | Hierarchical transformation |
| `Name` | `FsName` | Hierarchical dot-notation names |
| `Subject` | `FsSubject` | Contextual entity identity |

## Performance

### Benchmark Highlights

| Benchmark | Fullerstack | Humainary | Notes |
|-----------|-------------|-----------|-------|
| hot_pipe_async | 4.7ns | 8.7ns | 46% faster |
| subject_compare | 1.5ns | 3.6ns | 142% faster |
| name_path_generation | 0.84ns | 33ns | 3865% faster |
| create_await_close | 5.5μs | 175μs | 97% faster |

### Design Target

- 100k+ metrics @ 1Hz
- ~2% CPU usage
- ~200-300MB memory

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) - Core concepts and design
- [Async Architecture](docs/ASYNC-ARCHITECTURE.md) - Async-first design details
- [Circuit Design](docs/CIRCUIT-DESIGN.md) - Dual-queue implementation
- [Developer Guide](docs/DEVELOPER-GUIDE.md) - Best practices and patterns
- [Benchmark Comparison](docs/BENCHMARK-COMPARISON.md) - Full JMH results
- [Serventis Integration](docs/SERVENTIS.md) - Semiotic observability instruments

## Running TCK

```bash
# Build Fullerstack first
cd fullerstack-substrates && mvn clean install -DskipTests

# Run TCK via Humainary's tck.sh
cd ../substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC2 ./tck.sh
```

**Expected:** 387 tests, 0 failures

## Running Benchmarks

```bash
# Build Fullerstack first
cd fullerstack-substrates && mvn clean install -DskipTests

# Run benchmarks via Humainary's jmh.sh
cd ../substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC2 ./jmh.sh
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
