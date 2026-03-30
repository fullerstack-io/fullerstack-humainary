# Fullerstack Substrates

A Java 26 implementation of the [Humainary Substrates](https://github.com/humainary-io/substrates-api-java) deterministic signal circulation infrastructure.

Substrates is a runtime for observable, event-driven systems where every emission is processed in strict enqueue order on a single virtual thread per circuit. This implementation provides the SPI provider — your code uses 100% Humainary API, our code handles the execution.

| | |
|---|---|
| **API** | [Humainary Substrates 1.0.0](https://github.com/humainary-io/substrates-api-java) |
| **Spec** | [Substrates API Specification](https://github.com/humainary-io/substrates-api-spec) |
| **Serventis** | [Humainary Serventis 1.0.0](https://github.com/humainary-io/serventis-api-java) (semiotic observability) |
| **Implementation** | `io.fullerstack:fullerstack-substrates:1.0.0-RC4` |
| **Java** | 26 (Virtual Threads + Preview Features) |
| **Tests** | 703 passing (255 contract + 448 TCK) |
| **License** | Apache 2.0 |

## Quick Start

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>1.0.0-RC4</version>
</dependency>
```

```java
import static io.humainary.substrates.api.Substrates.*;

// Cortex is the entry point — our provider loaded via SPI
var cortex  = cortex();
var circuit = cortex.circuit(cortex.name("example"));

// Create a conduit and subscribe
var conduit = circuit.conduit(cortex.name("events"), Composer.pipe());
conduit.subscribe(circuit.subscriber(
    cortex.name("logger"),
    (subject, registrar) -> registrar.register(System.out::println)
));

// Emit — async, deterministic, ~13ns
conduit.percept(cortex.name("source")).emit("hello");
circuit.await();
circuit.close();
```

## Building from Source

### Prerequisites

**Java 26** ([SDKMAN](https://sdkman.io/)):

```bash
sdk install java 26.ea.35-open
sdk use java 26.ea.35-open
```

**Humainary APIs** (not yet on Maven Central):

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
cd ../..
```

### Build & Test

```bash
git clone https://github.com/fullerstack-io/fullerstack-humainary.git
cd fullerstack-humainary/fullerstack-substrates
mvn clean install        # Build + 703 tests
```

### Benchmarks

```bash
./scripts/benchmark.sh              # All 14 JMH groups
./scripts/benchmark.sh PipeOps      # Specific group
./scripts/benchmark.sh -l           # List available
```

## Architecture

Each circuit runs on a single virtual thread with two internal queues:

```
External threads ──emit──→ Ingress Queue (MPSC, wait-free)
                                         ↓
                              Circuit Thread (virtual)
                                         ↓
Cascading emissions ─────→ Transit Queue (priority) ──→ Subscribers
```

**Deterministic ordering**: emissions processed in strict enqueue order. Transit queue has priority for causal completion — all cascading effects resolve before the next external emission.

See the [Specification](https://github.com/humainary-io/substrates-api-spec) for the formal model, and [Implementation Architecture](fullerstack-substrates/docs/ARCHITECTURE.md) for our design decisions.

### Implementation

25 classes in `io.fullerstack.substrates`:

| API Interface | Implementation | Purpose |
|--------------|---------------|---------|
| Cortex | FsCortex | Entry point — circuits, scopes, names |
| Circuit | FsCircuit | Dual-queue sequential execution engine |
| Conduit | FsConduit | Channel factory + subscriber management |
| Channel | FsChannel | Named emission port |
| Pipe | FsPipe | Async emission carrier |
| Flow | FsFlow | Processing pipeline (diff, guard, limit, sample, sift) |
| Name | FsName | Hierarchical dot-notation names with interning |
| Subject | FsSubject | Identity (Id + Name + State + Type) |
| Scope | FsScope | Structured resource lifecycle (RAII) |
| Subscriber | FsSubscriber | Emission observer with lazy callback |
| Subscription | FsSubscription | Subscriber lifecycle handle |
| Tap | FsTap | Conduit emission transformation |
| Reservoir | FsReservoir | Buffered emission capture |
| Closure | FsClosure | Block-scoped resource management |
| Current | FsCurrent | Circuit execution context |
| State | FsState | Slot-based state container |
| Slot | FsSlot | Typed state value holder |
| Sift | FsSift | Comparison-based filtering |
| Registrar | FsRegistrar | Pipe registration during subscriber callback |
| Substrate | FsSubstrate | Base substrate implementation |
| Exception | FsException | Provider error handling |

Infrastructure: `IngressQueue` (wait-free MPSC), `TransitQueue` (single-threaded cascade FIFO), `QChunk` (64-slot interleaved array), `FsCortexProvider` (SPI entry point).

## Documentation

| Document | Audience | Description |
|----------|----------|-------------|
| [Kitchen Model](fullerstack-substrates/docs/KITCHEN-MODEL.md) | Everyone | Dual-queue architecture explained as a restaurant kitchen |
| [Architecture](fullerstack-substrates/docs/ARCHITECTURE.md) | Engineers | Core design, sealed hierarchy, data flow |
| [Circuit Design](fullerstack-substrates/docs/CIRCUIT-DESIGN.md) | Implementers | Queue internals, VarHandle, performance |
| [Async Architecture](fullerstack-substrates/docs/ASYNC-ARCHITECTURE.md) | Test writers | Async-first design, testing patterns, await() |
| [Developer Guide](fullerstack-substrates/docs/DEVELOPER-GUIDE.md) | Users | Patterns, best practices, Serventis integration |

**Start here**: [Kitchen Model](fullerstack-substrates/docs/KITCHEN-MODEL.md) — explains the dual-queue architecture as a story. Then read [Architecture](fullerstack-substrates/docs/ARCHITECTURE.md) for the technical details.

## Humainary Ecosystem

| Repository | Description |
|-----------|-------------|
| [substrates-api-java](https://github.com/humainary-io/substrates-api-java) | Substrates API interfaces |
| [substrates-api-spec](https://github.com/humainary-io/substrates-api-spec) | Formal specification + design rationale |
| [serventis-api-java](https://github.com/humainary-io/serventis-api-java) | Serventis semiotic observability (33 instrument types) |

All API design, architecture, and concepts by **[William Louth](https://humainary.io/)** and the **Humainary** project. We implement the specification — we don't extend it.

## License

Apache License 2.0
