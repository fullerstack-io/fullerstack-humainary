# Fullerstack Substrates

A Java 26 implementation of the [Humainary Substrates](https://github.com/humainary-io/substrates-api-java) deterministic signal circulation infrastructure.

Substrates is a runtime for observable, event-driven systems where every emission is processed in strict enqueue order on a single virtual thread per circuit. This implementation provides the SPI provider — your code uses 100% Humainary API, our code handles the execution.

| | |
|---|---|
| **API** | [Humainary Substrates 2.4.0](https://github.com/humainary-io/substrates-api-java) |
| **Spec** | [Substrates API Specification](https://github.com/humainary-io/substrates-api-spec) |
| **Serventis** | [Humainary Serventis 2.4.0](https://github.com/humainary-io/serventis-api-java) (semiotic observability) |
| **Implementation** | `io.fullerstack:fullerstack-substrates:2.4.0-RC1` |
| **Java** | 26 (Virtual Threads + Preview Features) |
| **Tests** | 494 passing (80 contract + 375 TCK + 39 Serventis) |
| **License** | Apache 2.0 |

## Quick Start

The artifact is published to [GitHub Packages](https://github.com/fullerstack-io/fullerstack-humainary/packages). GitHub Packages requires authentication for all downloads — see [Consuming the artifact](#consuming-the-artifact) below for repository setup and credentials.

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>2.4.0-RC1</version>
</dependency>
```

```java
import static io.humainary.substrates.api.Substrates.*;

// Cortex is the entry point — our provider loaded via SPI
var cortex  = cortex();
var circuit = cortex.circuit(cortex.name("example"));

// Create a typed conduit and subscribe
var conduit = circuit.conduit(cortex.name("events"), String.class);
conduit.subscribe(circuit.subscriber(
    cortex.name("logger"),
    (subject, registrar) -> registrar.register(System.out::println)
));

// Emit — async, deterministic, ~12 ns
conduit.get(cortex.name("source")).emit("hello");
circuit.await();
circuit.close();
```

## Consuming the artifact

The artifact lives in [GitHub Packages](https://github.com/fullerstack-io/fullerstack-humainary/packages). GitHub Packages requires authentication even for public packages, so you need both a repository declaration **and** credentials.

**1. Add the repository and dependency to your `pom.xml`:**

```xml
<repositories>
  <repository>
    <id>github-fullerstack</id>
    <url>https://maven.pkg.github.com/fullerstack-io/fullerstack-humainary</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>2.4.0-RC1</version>
  </dependency>
</dependencies>
```

**2. Configure credentials in `~/.m2/settings.xml`:**

```xml
<settings>
  <servers>
    <server>
      <id>github-fullerstack</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Generate a [personal access token](https://github.com/settings/tokens) with the `read:packages` scope, export it as `GITHUB_TOKEN` in your shell, and Maven will authenticate automatically. You can also paste the token directly into `settings.xml`, but the environment-variable form is safer.

The `FsCortexProvider` is then discovered automatically via Java `ServiceLoader` — no configuration code needed in your app.

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
mvn clean install        # Build + 494 tests
```

### Benchmarks

```bash
./scripts/benchmark.sh              # All groups (14 Substrates + 36 Serventis instruments)
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

26 classes in `io.fullerstack.substrates`:

| API Interface | Implementation | Purpose |
|--------------|---------------|---------|
| Cortex | FsCortex | Entry point — circuits, scopes, names, flows, fibers |
| Circuit | FsCircuit | Dual-queue sequential execution engine + `pulse()` diagnostic (2.4) |
| Conduit | FsConduit | Channel factory + subscriber management; `Pool<Pipe<E>>` |
| Pipe | FsPipe | Async emission carrier |
| Pool | FsDerivedPool | Derived pool — `pool(Function)` / `pool(Flow)` / `pool(Fiber)` with three-state lazy storage |
| Flow | FsFlow | Type-changing composition: `map` / `fiber` / `flow` / `pipe` |
| Fiber | FsFiber | Per-emission operators (~35: `guard`, `diff`, `limit`, `peek`, `replace`, `chance`, `change`, `deadband`, `delay`, `edge`, `every`, `hysteresis`, `inhibit`, `pulse`, `rolling`, `steady`, `tumble`, ...) |
| Name | FsName | Hierarchical dot-notation names with interning |
| Subject | FsSubject | Identity (Id + Name + State + Type) |
| Scope | FsScope | Structured resource lifecycle (RAII) |
| Subscriber | FsSubscriber | Emission observer with lazy callback |
| Subscription | FsSubscription | Subscriber lifecycle handle (with `onClose` overload, 2.4) |
| Tap | FsTap | Source emission transformation; `tap(Function|Flow|Fiber)` |
| Reservoir | FsReservoir | Buffered emission capture |
| Closure | FsClosure | Block-scoped resource management |
| Current | FsCurrent | Execution context identity (per circuit, 2.4) |
| State | FsState | Slot-based state container |
| Slot | FsSlot | Typed state value holder |
| Registrar | FsRegistrar | Pipe registration during subscriber callback |
| (internal) | FsChannel | Per-name dispatch — split: `dispatch` (receptors) vs `cascadeDispatch` (receptors + STEM) |
| (internal) | FsHub | Subscriber list + version counter (per-conduit) |
| (internal) | FsOperators | Shared operator implementations (Guard, Diff, Limit, Peek, ... — used by FsFiber and FsFlow) |
| (Fault) | — | `Substrates.Fault` is `final` in 2.4; we throw it directly |

Infrastructure: `IngressQueue` (wait-free MPSC), `TransitQueueRing` (single-threaded power-of-2 ring; cascade priority), `QChunk` (128-slot interleaved `[receiver, value]` array), `FsCortexProvider` (SPI entry point).

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
| [serventis-api-java](https://github.com/humainary-io/serventis-api-java) | Serventis semiotic observability (~36 instrument types) |

All API design, architecture, and concepts by **[William Louth](https://humainary.io/)** and the **Humainary** project. We implement the specification — we don't extend it.

## License

Apache License 2.0
