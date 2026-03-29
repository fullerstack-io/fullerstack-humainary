# Claude Code Configuration — Fullerstack Humainary

## Project Overview

Java 26 implementation of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) — an SPI-based runtime for observable, event-driven systems with semiotic signal interpretation. We implement the API; all design credit goes to **William Louth** and the **Humainary** project.

- **Language:** Java 26 (preview features enabled)
- **Build:** Maven 3.9+
- **Package:** `io.fullerstack:fullerstack-substrates:1.0.0-RC3`
- **API:** `io.humainary.substrates` (1.0.0)
- **Tests:** 703 tests passing (255 contract + 448 TCK)
- **License:** Apache 2.0

## Repository Layout

```
fullerstack-humainary/
├── fullerstack-substrates/          # Main Maven project
│   ├── pom.xml                      # Build configuration
│   ├── spi.env                      # SPI env vars for scripts
│   ├── src/
│   │   ├── main/java/io/fullerstack/substrates/   # Implementation (25 classes)
│   │   ├── main/resources/META-INF/services/       # SPI provider registration
│   │   ├── test/java/io/fullerstack/substrates/    # Unit + contract tests (255)
│   │   ├── test/java/io/humainary/substrates/tck/  # Integrated TCK tests (448)
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

The Humainary APIs must be installed locally (not on Maven Central):

```bash
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java/api
mvn clean install -DskipTests
cd ../..

git clone https://github.com/humainary-io/serventis-api-java.git
cd serventis-api-java/api
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

### Run Tests (all: contract + TCK)

```bash
cd fullerstack-substrates
mvn test                            # All 703 tests (255 contract + 448 TCK)
./scripts/tck.sh CircuitTest        # Specific test class
```

### Run JMH Benchmarks

```bash
./scripts/benchmark.sh              # All benchmarks
./scripts/benchmark.sh PipeOps      # Specific group
./scripts/benchmark.sh -l           # List available benchmarks
```

Benchmark groups (14): CircuitOps, ConduitOps, CortexOps, CyclicOps, FlowOps, IdOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubjectOps, SubscriberOps, TapOps.

### Format Code

```bash
./scripts/format.sh                 # Format all Java files
./scripts/format.sh --check         # Check only
```

## Java & JVM Configuration

- Java 26 with `--enable-preview`
- Compiler flags: `--add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED`
- Surefire flags: `-XX:-RestrictContended`, `-XX:+EnableDynamicAgentLoading`, multiple `--add-opens`
- JMH flags: `-XX:+UseCompactObjectHeaders`, `-XX:ParallelGCThreads=2`
- SDKMAN used for Java version management: `sdk use java 26.ea.35-open`

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
| Name | FsName | Hierarchical dot-notation names with caching |
| Subject | FsSubject | Contextual entity identity |
| Scope | FsScope | Resource lifecycle management |
| Subscriber | FsSubscriber | Emission observer |
| Subscription | FsSubscription | Subscriber lifecycle handle |
| Flow | FsFlow | Emission processing pipeline |
| Sift | FsSift | Comparative filtering operations |
| State | FsState | Slot-based state container |
| Slot | FsSlot | Typed state value holder |
| Reservoir | FsReservoir | Buffered emission capture |
| Tap | FsTap | Conduit emission transformation |
| Closure | FsClosure | Block-scoped resource management |
| Current | FsCurrent | Circuit execution context |
| Registrar | FsRegistrar | Pipe registration during subscriber callback |
| Substrate | FsSubstrate | Base substrate implementation |
| Exception | FsException | Provider error handling |

### Infrastructure Classes

| Class | Purpose |
|-------|---------|
| FsCortexProvider | SPI provider entry point |
| IngressQueue | Wait-free MPSC queue for external emissions |
| TransitQueue | Single-threaded FIFO for cascading emissions |
| QChunk | Unified 64-slot chunk with interleaved [receiver, value] layout |

### Key Design Patterns

- **Dual-queue architecture:** IngressQueue (MPSC, wait-free) + TransitQueue (single-threaded cascade)
- **VarHandle semantics:** release/acquire for memory ordering, opaque reads for parked flag
- **@Contended annotation:** False-sharing prevention on hot fields
- **Virtual threads:** Java 26 virtual CPU core pattern
- **Intrusive data structures:** No wrapper objects, cache-friendly layout

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| humainary-substrates-api | 1.0.0 | Substrates API interfaces |
| humainary-serventis-api | 1.0.0 | Serventis semiotic observability API |
| jctools-core | 4.0.5 | High-performance concurrent queues |
| vavr | 0.10.4 | Functional data structures |
| lombok | 1.18.34 | Compile-time code generation |
| slf4j-api | 2.0.16 | Logging facade |
| lmax-disruptor | 4.0.0 | Benchmark comparison baseline |
| jmh-core | 1.37 | JMH benchmarking framework |
| junit-jupiter | 5.11.0 | Testing framework |
| assertj | 3.26.3 | Fluent test assertions |
| mockito | 5.15.2 | Mocking framework |

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
- **Java:** Temurin 26

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
