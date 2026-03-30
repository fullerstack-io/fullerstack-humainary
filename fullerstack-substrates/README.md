# Fullerstack Substrates

SPI provider implementation of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) — deterministic signal circulation infrastructure for Java 26.

| | |
|---|---|
| **Version** | 1.0.0-RC4 |
| **API** | Substrates 1.0.0 + Serventis 1.0.0 |
| **Java** | 26 (Virtual Threads + Preview) |
| **Tests** | 722 (274 contract + 448 TCK) |
| **Benchmarks** | 14 JMH groups, 185 benchmarks |

## Prerequisites

1. **Java 26** via [SDKMAN](https://sdkman.io/):
   ```bash
   sdk install java 26.ea.35-open
   sdk use java 26.ea.35-open
   ```

2. **Humainary APIs** (not yet on Maven Central):
   ```bash
   git clone https://github.com/humainary-io/substrates-api-java.git
   cd substrates-api-java/api && mvn clean install -DskipTests && cd ../..

   git clone https://github.com/humainary-io/serventis-api-java.git
   cd serventis-api-java/api && mvn clean install -DskipTests && cd ../..
   ```

## Build & Test

```bash
mvn clean install        # Build + run all 722 tests
mvn test                 # Tests only
```

## Usage

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>1.0.0-RC4</version>
</dependency>
```

The `FsCortexProvider` is discovered automatically via `ServiceLoader`. No configuration needed — just add the dependency and call `Substrates.cortex()`.

```java
import static io.humainary.substrates.api.Substrates.*;

var cortex  = cortex();
var circuit = cortex.circuit(cortex.name("example"));

var conduit = circuit.conduit(
    cortex.name("events"),
    Composer.pipe()
);

conduit.subscribe(circuit.subscriber(
    cortex.name("logger"),
    (subject, registrar) -> registrar.register(System.out::println)
));

conduit.percept(cortex.name("source")).emit("hello");
circuit.await();
circuit.close();
```

## Architecture

Each circuit runs on a single virtual thread with two internal queues:

- **IngressQueue** — wait-free MPSC for external emissions (~13ns per emit)
- **TransitQueue** — single-threaded FIFO for cascading emissions (priority over ingress)

Transit queue priority ensures **causal completion** — all cascading effects resolve atomically before the next external emission is processed. This eliminates race conditions without locks.

See [Circuit Design](docs/CIRCUIT-DESIGN.md) for VarHandle memory ordering, `@Contended` false-sharing prevention, and the QChunk interleaved array layout.

For the conceptual model, read [Kitchen Model](docs/KITCHEN-MODEL.md) — the dual-queue architecture explained as a restaurant kitchen with one chef.

## Benchmarks

```bash
./scripts/benchmark.sh              # All 14 groups
./scripts/benchmark.sh PipeOps      # Specific group
./scripts/benchmark.sh -l           # List available
```

Selected results (ns/op, JDK 26, GitHub Codespaces 2 vCPU):

| Benchmark | ns/op | What it measures |
|-----------|------:|------------------|
| `hot_pipe_async` | 13.7 | Emission through pre-warmed pipe |
| `conduit.get_by_name` | 2.8 | Percept lookup by name |
| `name_from_string` | 2.7 | Name creation + interning |
| `scope_create_and_close` | 0.95 | Scope lifecycle |
| `cyclic_emit` | 3.8 | Cyclic pipe network emission |
| `flow_guard_await` | 35.2 | Emission through guard flow operator |

## Documentation

**Start here**: [Kitchen Model](docs/KITCHEN-MODEL.md) — explains everything as a story.

| Document | What it covers |
|----------|---------------|
| [Kitchen Model](docs/KITCHEN-MODEL.md) | Dual-queue architecture as a restaurant kitchen |
| [Architecture](docs/ARCHITECTURE.md) | Core design, sealed hierarchy, data flow, thread safety |
| [Circuit Design](docs/CIRCUIT-DESIGN.md) | Queue internals, VarHandle, performance optimizations |
| [Async Architecture](docs/ASYNC-ARCHITECTURE.md) | Why everything is async, testing patterns, await() |
| [Developer Guide](docs/DEVELOPER-GUIDE.md) | Usage patterns, best practices, Serventis integration |
| [Use Cases](docs/USE-CASES.md) | Problem domains and when to use Substrates |

### External References

| Resource | Description |
|----------|-------------|
| [Substrates Specification](https://github.com/humainary-io/substrates-api-spec) | Formal spec + design rationale |
| [Substrates API](https://github.com/humainary-io/substrates-api-java) | API interfaces |
| [Serventis API](https://github.com/humainary-io/serventis-api-java) | Semiotic observability instruments |

## License

Apache License 2.0

All API design by **[William Louth](https://humainary.io/)** and the **[Humainary](https://humainary.io/)** project.
