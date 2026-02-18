# Claude Code Configuration — Fullerstack Humainary

## Project Overview

Java 25 implementation of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) — an SPI-based runtime for observable, event-driven systems with semiotic signal interpretation. We implement the API; all design credit goes to **William Louth** and the **Humainary** project.

- **Language:** Java 25 (preview features enabled)
- **Build:** Maven 3.9+
- **Package:** `io.fullerstack:fullerstack-substrates:1.0.0-RC2`
- **API:** `io.humainary.substrates` (1.0.0-PREVIEW)
- **TCK:** 387 tests passing (100% compliance)
- **License:** Apache 2.0

## Repository Layout

```
fullerstack-humainary/
├── fullerstack-substrates/          # Main Maven project
│   ├── pom.xml                      # Build configuration
│   ├── spi.env                      # SPI env vars for scripts
│   ├── src/
│   │   ├── main/java/io/fullerstack/substrates/   # Implementation (26 classes)
│   │   ├── main/resources/META-INF/services/       # SPI provider registration
│   │   ├── test/java/io/fullerstack/substrates/    # Unit + contract tests
│   │   └── jmh/java/                               # JMH benchmark sources
│   ├── docs/                        # Architecture, guides, benchmarks
│   ├── jmh.sh                       # Direct JMH runner
│   ├── tck.sh                       # Direct TCK runner
│   └── queue-bench.sh               # Isolated queue benchmarks
├── scripts/                         # Top-level wrapper scripts
│   ├── benchmark.sh                 # Benchmark runner (wraps Humainary jmh.sh)
│   ├── tck.sh                       # TCK runner (wraps Humainary tck.sh)
│   └── format.sh                    # Code formatter (javalint)
├── .claude/                         # Claude Code config, hooks, skills, commands
├── .github/workflows/               # CI/CD (publish to GitHub Packages)
├── .editorconfig                    # Formatting rules (Java: 2-space indent, 200 line width)
└── .prettierrc                      # Prettier config for Java (plugin-java)
```

## Build & Test

### Prerequisites

The Humainary API must be installed locally (not on Maven Central):

```bash
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java
mvn clean install -DskipTests
```

### Build

```bash
cd fullerstack-substrates
mvn clean install
```

### Run Tests (unit + contract tests)

```bash
cd fullerstack-substrates
mvn test
```

### Run TCK (Humainary specification compliance)

```bash
./scripts/tck.sh                    # All TCK tests (387 expected)
./scripts/tck.sh CircuitTest        # Specific test class
```

### Run JMH Benchmarks

```bash
./scripts/benchmark.sh              # All benchmarks
./scripts/benchmark.sh PipeOps      # Specific group
./scripts/benchmark.sh -l           # List available benchmarks
```

Benchmark groups: CircuitOps, ConduitOps, CortexOps, FlowOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubscriberOps (Substrates) + 30 Serventis groups.

### Format Code

```bash
./scripts/format.sh                 # Format all Java files
./scripts/format.sh --check         # Check only
```

## Java & JVM Configuration

- Java 25 with `--enable-preview`
- Compiler flags: `--add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED`
- Surefire flags: `-XX:-RestrictContended`, `-XX:+EnableDynamicAgentLoading`, multiple `--add-opens`
- JMH flags: `-XX:+UseCompactObjectHeaders`, `-XX:ParallelGCThreads=2`
- SDKMAN used for Java version management: `sdk use java 25.0.1-open`

## Architecture

### SPI Provider

- Provider class: `io.fullerstack.substrates.FsCortexProvider`
- Registration: `META-INF/services/io.humainary.substrates.spi.CortexProvider`
- Loading: Java `ServiceLoader` auto-discovery

### Core Implementation Classes (package: `io.fullerstack.substrates`)

| Humainary Interface | Fullerstack Impl | Purpose |
|---------------------|------------------|---------|
| Cortex | FsCortex | Entry point — creates Circuits and Scopes |
| Circuit | FsCircuit | Event orchestration with dual-queue processing |
| Conduit | FsConduit | Channel factory and subscriber management |
| Channel | FsChannel | Named emission port |
| Pipe | FsPipe | Async emission carrier |
| Cell | FsCell | Bidirectional hierarchical transformation |
| Name | FsName | Hierarchical dot-notation names with caching |
| Subject | FsSubject | Contextual entity identity |
| Scope | FsScope | Resource lifecycle management |
| Subscriber | FsSubscriber | Emission observer |
| Flow | FsFlow | Emission processing pipeline |
| State | FsState | Slot-based state container |
| Reservoir | FsReservoir | Buffered emission capture |

### Infrastructure Classes

| Class | Purpose |
|-------|---------|
| IngressQueue | Wait-free MPSC queue for external emissions |
| TransitQueue | Single-threaded FIFO for cascading emissions |
| QChunk | Unified 64-slot chunk with interleaved [receiver, value] layout |
| FsCurrent | Circuit execution context with thread-local cache |

### Key Design Patterns

- **Dual-queue architecture:** IngressQueue (MPSC, wait-free) + TransitQueue (single-threaded cascade)
- **VarHandle semantics:** release/acquire for memory ordering, opaque reads for parked flag
- **@Contended annotation:** False-sharing prevention on hot fields
- **Virtual threads:** Java 25 virtual CPU core pattern
- **Intrusive data structures:** No wrapper objects, cache-friendly layout

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| humainary-substrates-api | 1.0.0-PREVIEW | Substrates API interfaces |
| humainary-substrates-ext-serventis | 1.0.0-PREVIEW | Serventis observability extension |
| jctools-core | 4.0.5 | High-performance concurrent queues |
| vavr | 0.10.4 | Functional data structures |
| lombok | 1.18.34 | Compile-time code generation |
| slf4j-api | 2.0.16 | Logging facade |
| lmax-disruptor | 4.0.0 | Benchmark comparison baseline |
| jmh-core | 1.37 | JMH benchmarking framework |
| junit-jupiter | 5.11.0 | Testing framework |
| assertj | 3.26.3 | Fluent test assertions |
| mockito | 5.15.2 | Mocking framework |
| humainary-substrates-tck | 1.0.0-PREVIEW | TCK compliance tests |

## Code Style

- **Indentation:** 2 spaces (all files)
- **Java max line length:** 200 characters
- **Continuation indent:** 2 spaces
- **Formatter:** EditorConfig + javalint (IntelliJ CE formatter)
- **Import order:** `*`, blank, `javax.**`, `java.**`, blank, `$*`
- **Naming:** `Fs` prefix for all implementation classes (FsCortex, FsCircuit, etc.)
- **File size:** Keep under 500 lines

## CI/CD

- **Workflow:** `.github/workflows/publish-package.yml`
- **Trigger:** Push to `main` with changes in `fullerstack-substrates/`
- **Target:** GitHub Packages Maven registry
- **Java:** Temurin 25

## Behavioral Rules

- ALWAYS read a file before editing it
- ALWAYS run tests after making code changes
- ALWAYS verify build succeeds before committing
- NEVER save files to the repository root — use the project directories
- NEVER create documentation files unless explicitly requested
- NEVER commit secrets, credentials, or .env files
- NEVER modify the Humainary API — we implement it, not extend it
- Prefer editing existing files over creating new ones
- Batch parallel tool calls in a single message when independent
