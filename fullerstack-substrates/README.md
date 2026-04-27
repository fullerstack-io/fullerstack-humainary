# Fullerstack Substrates

SPI provider implementation of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) — deterministic signal circulation infrastructure for Java 26.

| | |
|---|---|
| **Version** | 1.0.0-RC6 |
| **API** | Substrates 2.3.0 + Serventis 2.3.0 |
| **Java** | 26 (Virtual Threads + Preview) |
| **Tests** | 477 (75 contract + 363 TCK + 39 Serventis) |
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
mvn clean install        # Build + run all 477 tests
mvn test                 # Tests only
```

## Usage

The artifact is published to [GitHub Packages](https://github.com/fullerstack-io/fullerstack-humainary/packages). GitHub Packages requires authentication even for public packages, so consumers need both a repository declaration **and** credentials.

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
    <version>1.0.0-RC6</version>
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

The token must be a [personal access token](https://github.com/settings/tokens) with the `read:packages` scope. Export it as `GITHUB_TOKEN` in your shell (or paste it directly into `settings.xml`, but environment variable is safer).

**3. The provider loads automatically.** `FsCortexProvider` is discovered via `ServiceLoader` — no configuration code needed. Just call `Substrates.cortex()`:

```java
import static io.humainary.substrates.api.Substrates.*;

var cortex  = cortex();
var circuit = cortex.circuit(cortex.name("example"));

var conduit = circuit.conduit(cortex.name("events"), String.class);

conduit.subscribe(circuit.subscriber(
    cortex.name("logger"),
    (subject, registrar) -> registrar.register(System.out::println)
));

conduit.get(cortex.name("source")).emit("hello");
circuit.await();
circuit.close();
```

## Architecture

Each circuit runs on a single virtual thread with two internal queues:

- **IngressQueue** — wait-free MPSC for external emissions, backed by a 128-slot `QChunk` (~13ns per emit)
- **TransitQueueRing** — single-threaded power-of-2 ring for cascading emissions (priority over ingress)

Transit queue priority ensures **causal completion** — all cascading effects resolve atomically before the next external emission is processed. This eliminates race conditions without locks.

See [Circuit Design](docs/CIRCUIT-DESIGN.md) for VarHandle memory ordering, `@Contended` false-sharing prevention, and the QChunk interleaved array layout.

For the conceptual model, read [Kitchen Model](docs/KITCHEN-MODEL.md) — the dual-queue architecture explained as a restaurant kitchen with one chef.

## Benchmarks

```bash
./scripts/benchmark.sh              # All 14 groups
./scripts/benchmark.sh PipeOps      # Specific group
./scripts/benchmark.sh -l           # List available
```

Selected results (ns/op, JDK 26, GitHub Codespaces 2 vCPU). Cross-platform numbers in [Benchmark Comparison](docs/BENCHMARK-COMPARISON.md) are due for re-measurement on a quiet host; the figure below is from a recent run with a 10-iteration warmup:

| Benchmark | ns/op | What it measures |
|-----------|------:|------------------|
| `cyclic_emit_deep_await_batch` | ~12.9 | Per-cycle cost of a deep cascade through cyclic pipe networks |

## Documentation

**Start here**: [Kitchen Model](docs/KITCHEN-MODEL.md) — explains everything as a story.

| Document | What it covers |
|----------|---------------|
| [Kitchen Model](docs/KITCHEN-MODEL.md) | Dual-queue architecture as a restaurant kitchen |
| [Architecture](docs/ARCHITECTURE.md) | Core design, sealed hierarchy, data flow, thread safety |
| [Circuit Design](docs/CIRCUIT-DESIGN.md) | Queue internals, VarHandle, performance optimizations |
| [Async Architecture](docs/ASYNC-ARCHITECTURE.md) | Why everything is async, testing patterns, await() |
| [Developer Guide](docs/DEVELOPER-GUIDE.md) | Usage patterns, best practices, Serventis integration |
| [Benchmark Comparison](docs/BENCHMARK-COMPARISON.md) | Cross-platform JMH results vs Humainary baseline |
| [Examples](docs/examples/README.md) | Hands-on producer/consumer code samples |

### External References

| Resource | Description |
|----------|-------------|
| [Substrates Specification](https://github.com/humainary-io/substrates-api-spec) | Formal spec + design rationale |
| [Substrates API](https://github.com/humainary-io/substrates-api-java) | API interfaces |
| [Serventis API](https://github.com/humainary-io/serventis-api-java) | Semiotic observability instruments |

## License

Apache License 2.0

All API design by **[William Louth](https://humainary.io/)** and the **[Humainary](https://humainary.io/)** project.
