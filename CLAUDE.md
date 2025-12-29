# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository provides a **fully compliant implementation** of the Humainary Substrates API (version 1.0.0-PREVIEW), an event-driven observability framework designed by William Louth. The API design, architecture, and concepts are from Humainary - this repository provides the concrete runtime implementation.

**Key Point:** We implement exactly what the Substrates specification defines. We don't change the API or add proprietary extensions.

## Build Commands

### Prerequisites

#### Java 25 Requirement
Both the Humainary Substrates API and this implementation **require Java 25** with preview features enabled.

**Install Java 25 using SDKMAN:**
```bash
# Install SDKMAN if not already installed
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh

# Install Java 25
sdk install java 25.0.1-open
sdk use java 25.0.1-open

# Verify installation
java -version  # Should show: openjdk version "25.0.1"
```

**Alternative: Check SDKMAN installation**
```bash
# If SDKMAN is already installed at /usr/local/sdkman
source /usr/local/sdkman/bin/sdkman-init.sh
sdk install java 25.0.1-open
```

#### Install Humainary Substrates API
The Humainary Substrates API must be installed locally first (not yet published to Maven Central):

```bash
# Ensure Java 25 is active
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-open

# Clone and install the API dependency
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java
mvn clean install
cd ..
```

### Common Commands

**Important:** Ensure Java 25 is active before running any Maven commands:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-open
```

**Build the project:**
```bash
cd fullerstack-substrates
mvn clean install
```

**Run tests:**
```bash
mvn test
```

**Run a single test:**
```bash
mvn test -Dtest=ClassName
mvn test -Dtest=ClassName#methodName
```

**Format code (Spotless with Google Java Format - AOSP style):**
```bash
mvn spotless:apply
```

**Check code formatting:**
```bash
mvn spotless:check
```

**Run benchmarks (using Humainary's jmh.sh):**
```bash
# Build Fullerstack first
cd fullerstack-substrates && mvn clean install -DskipTests -q

# Run benchmarks via Humainary's jmh.sh with Fullerstack SPI
cd substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC1 ./jmh.sh

# Or run specific benchmark group
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC1 ./jmh.sh PipeOps
```

**Run TCK (using Humainary's tck.sh):**
```bash
# Build Fullerstack first
cd fullerstack-substrates && mvn clean install -DskipTests -q

# Run TCK via Humainary's tck.sh with Fullerstack SPI
cd substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC1 ./tck.sh
```

## Architecture Overview

### Core Concepts

**Substrates API = Interface** (from Humainary)
**Fullerstack Implementation = Concrete Runtime** (this repository)

Think of it like `java.util.List` (interface) vs `ArrayList` (implementation).

### Key Components

1. **Cortex** - Entry point accessed via `Substrates.cortex()` (SPI-loaded singleton)
2. **Circuit** - Event orchestration hub with Virtual CPU Core pattern (sequential processing)
3. **Conduit** - Container that creates Channels and routes emissions to subscribers
4. **Channel** - Named emission port within a Conduit
5. **Pipe** - Producer/consumer interface for emitting values
6. **Cell** - Hierarchical computational cell with type transformation (I → E)
7. **Valve** - Dual-queue architecture (Ingress + Transit) + Virtual Thread per Circuit

### Circuit Architecture

Fullerstack uses a single consolidated circuit implementation (`FsCircuit`) with an intrusive MPSC linked list queue:

**Key design:**
- External emissions: create job, submit to MPSC queue via `getAndSet`
- Cascade emissions (on circuit thread): direct call, no job allocation
- Park/unpark for efficient thread synchronization

```
External Emissions → MPSC Queue (intrusive linked list) →
                                                          → Virtual Thread
Cascade Emissions → Direct call (no job allocation) →       → Sequential Execution
```

**Key guarantees:**
- Single virtual thread per Circuit eliminates race conditions
- Wait-free producer path via atomic `getAndSet`
- Park/unpark synchronization (no busy-spinning)
- Cascade optimization: same-thread emissions bypass queue entirely
- Passes all 383 TCK tests

### Package Structure

```
io.fullerstack.substrates/
├── capture/         - Emission capture with Subject context
├── cell/            - CellNode implementation (hierarchical transformation)
├── channel/         - EmissionChannel implementation
├── circuit/         - SequentialCircuit and CortexRuntimeProvider (SPI)
├── closure/         - Lazy-loading Subject-based closures
├── conduit/         - RoutingConduit implementation
├── current/         - ThreadLocal context tracking
├── flow/            - Transformation pipeline with fusion optimization
├── id/              - UUID-based unique identifiers
├── lookup/          - Dual-key caching for percept lookup
├── name/            - InternedName (hierarchical dot-notation)
├── pipe/            - ProducerPipe implementation
├── reservoir/       - Internal subscriber management
├── scope/           - ManagedScope for resource lifecycle
├── sift/            - Filter transformations
├── slot/            - LinkedState (immutable state management)
├── state/           - StateBuilder and State implementations
├── subject/         - ContextualSubject with parent/state/type
├── subscriber/      - Subscriber and Registrar implementations
├── subscription/    - Subscription lifecycle management
└── valve/           - Valve (Virtual CPU Core pattern)
```

### Important Implementation Details

**Sealed Hierarchy:** The Substrates API uses sealed interfaces (Java JEP 409) to control which classes can implement them. All our implementations extend the non-sealed extension points (`Circuit`, `Conduit`, `Cell`, `Channel`, `Pipe`, `Sink`).

**Pipeline Fusion:** Flow transformations automatically fuse adjacent operations:
- `skip(n1).skip(n2)` → `skip(n1+n2)` (sum)
- `limit(n1).limit(n2)` → `limit(min(n1,n2))` (minimum)

**Event-Driven Await:** Circuits use wait/notify for zero-latency synchronization instead of polling with Thread.sleep().

**SPI Loading:** The `CortexRuntimeProvider` is loaded via Java ServiceLoader from `META-INF/services/io.humainary.substrates.spi.CortexProvider`.

## Development Guidelines

### Code Style

- **Java 25 required** (uses Virtual Threads and preview features)
- **AOSP Google Java Format** (2-space indentation via Spotless)
- Always run `mvn spotless:apply` before committing
- Use Lombok for boilerplate reduction (`@Builder`, `@Getter`, etc.)

### Testing Requirements

**Testing Async Processing:**
```java
// CRITICAL: Circuit processing is asynchronous
pipe.emit(value);  // Returns immediately
circuit.await();   // MUST await processing in tests
// or Thread.sleep(100);
```

**Use CopyOnWriteArrayList for test collectors:**
```java
List<String> received = new CopyOnWriteArrayList<>();  // Thread-safe
conduit.subscribe((subject, registrar) -> registrar.register(received::add));
```

### Common Patterns

**1. Creating a Circuit and Conduit:**
```java
import static io.humainary.substrates.api.Substrates.*;

Circuit circuit = cortex().circuit(cortex().name("kafka"));
Conduit<Pipe<String>, String> conduit = circuit.conduit(
    cortex().name("messages"),
    Composer.pipe()
);
```

**2. Emitting to a Pipe:**
```java
Pipe<String> pipe = conduit.get(cortex().name("producer-1"));
pipe.emit("Hello, Substrates!");
```

**3. Subscribing to emissions:**
```java
conduit.subscribe(cortex().subscriber(
    cortex().name("logger"),
    (subject, registrar) -> registrar.register(msg -> System.out.println(msg))
));
```

**4. Resource cleanup:**
```java
try (Circuit circuit = cortex().circuit(cortex().name("test"))) {
    // Use circuit
}  // Auto-closes

// Or with Scope for grouped cleanup
Scope scope = cortex().scope(cortex().name("session"));
Circuit circuit = scope.register(cortex().circuit(cortex().name("kafka")));
scope.close();  // Closes all registered resources
```

### Serventis Integration (PREVIEW)

Serventis extends Substrates with semantic signaling instruments organized by **semiotic ascent**:

**Layered Architecture:** Raw Signs → Systems → Statuses → Situations → Actions

**Module Structure:**
- **sdk/** (Universal): Systems, Statuses, Situations, Outcomes, Operations, Trends, Surveys
- **tool/** (Measurement): Counters, Gauges, Probes, Sensors, Logs
- **data/** (Data Structures): Queues, Stacks, Caches, Pipelines
- **flow/** (Flow Control): Valves, Breakers, Routers, Flows
- **sync/** (Synchronization): Locks, Atomics, Latches
- **pool/** (Resources): Resources, Pools, Leases, Exchanges
- **exec/** (Execution): Tasks, Services, Processes, Timers, Transactions
- **role/** (Coordination): Agents, Actors

**Example:**
```java
import io.humainary.serventis.sdk.*;
import io.humainary.serventis.opt.tool.*;

// Raw Signs: Counter for request counting
var counters = circuit.conduit(cortex().name("requests"), Counters::composer);
counters.get(cortex().name("http.requests")).increment();

// Behavioral Condition: Status assessment
var statuses = circuit.conduit(cortex().name("health"), Statuses::composer);
statuses.get(cortex().name("api")).degraded(Statuses.Confidence.MEASURED);
```

**Key insight:** The same signal means different things based on Subject context (semiotic observability). See [SERVENTIS.md](fullerstack-substrates/docs/SERVENTIS.md) for full documentation.

## TCK Compliance

This implementation passes **383/383 tests** from the Humainary Substrates TCK (Test Compatibility Kit), ensuring 100% API compliance.

The TCK tests are maintained by Humainary and verify that implementations correctly handle:
- Circuit event ordering and processing
- Conduit channel creation and routing
- Flow transformations (sift, limit, sample, skip, etc.)
- Cell hierarchical computation
- State and Slot immutability
- Resource lifecycle management
- Serventis instrument APIs

## Performance Characteristics

**Design Target:** 100k+ metrics @ 1Hz with ~2% CPU usage

**Per-Operation Costs:**
- Component lookup: ~5-10ns (ConcurrentHashMap)
- Pipe emission: ~100-300ns (with transformations)
- Subscriber notification: ~20-50ns per subscriber

**Thread Safety:**
- ConcurrentHashMap for all component caches
- CopyOnWriteArrayList for subscriber lists (read-heavy workload)
- BlockingQueue for Circuit event processing
- Immutable Name, State, and Signal types

## Key Documentation

- **README.md** - Project overview and quick start
- **fullerstack-substrates/docs/ARCHITECTURE.md** - Detailed architecture (sealed hierarchy, circuit types, data flow)
- **fullerstack-substrates/docs/ASYNC-ARCHITECTURE.md** - Virtual CPU Core and event-driven synchronization
- **fullerstack-substrates/docs/DEVELOPER-GUIDE.md** - Best practices, performance tips, testing strategies
- **fullerstack-substrates/docs/USE-CASES.md** - Problem domains where Substrates excels
- **fullerstack-substrates/docs/SERVENTIS.md** - Semiotic observability and Serventis instruments

**External Resources:**
- [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) - Official API specification
- [Observability X Blog](https://humainary.io/blog/category/observability-x/) - William Louth's architectural vision
- [Serventis: Big Things Have Small Beginnings](https://humainary.io/blog/serventis-big-things-have-small-beginnings/) - Semiotic ascent philosophy

## Critical Reminders

1. **API purity:** Application code should ONLY import `io.humainary.substrates.api.*` - never our concrete classes directly (SPI handles loading)
2. **Async testing:** Always use `circuit.await()` or `Thread.sleep()` in tests to wait for async processing
3. **Resource cleanup:** Always close Circuits, Conduits, and Scopes (use try-with-resources)
4. **Caching:** Cache Pipes for repeated emissions - don't call `conduit.get(name)` in loops
5. **Thread safety:** Don't block in subscriber callbacks - use virtual threads for slow operations
6. **Code formatting:** Run `mvn spotless:apply` before committing

## Philosophy

**Build it simple, build it correct, then optimize hot paths identified by profiling.**

We don't add features or change the API - we implement exactly what the Substrates specification defines, following William Louth's architectural vision for semiotic observability.
- Always show full jmh benchmark results with a side by side comparisson with humainary results from substrates-api-java/BENCHMARKS.md in a single table