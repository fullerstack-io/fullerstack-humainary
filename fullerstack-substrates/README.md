# Fullerstack Substrates

A **fully compliant implementation** of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) (version 1.0.0-PREVIEW) and [Serventis Extensions](https://github.com/humainary-io/substrates-ext-serventis-java).

This implementation provides the concrete runtime classes that bring the Substrates API to life, enabling event-driven observability systems with deterministic event ordering and semiotic signal interpretation.

## What is This?

**Humainary Substrates API** defines the interfaces and contracts for building observable, event-driven systems. **This project** provides the concrete implementation of those interfaces.

Think of it like:
- **Substrates API** = `java.util.List` interface (what it should do)
- **This Implementation** = `ArrayList` class (how it actually works)

We don't change the API or add features - we implement exactly what the Substrates specification defines, following William Louth's architectural vision.

## TCK Compliance

✅ **381/381 tests passing (100%)**

This implementation passes the complete [Humainary Substrates TCK (Test Compatibility Kit)](https://github.com/humainary-io/substrates-api-java/tree/main/tck), ensuring full specification compliance.

## What We Provide

### Core Runtime Classes

All classes implement the interfaces defined in `io.humainary.substrates.api.Substrates`:

- **SequentialCircuit** - implements `Circuit` interface
  - Sequential event processing using Virtual CPU Core pattern
  - Valve-based queue for deterministic ordering

- **RoutingConduit** - implements `Conduit<P, E>` interface
  - Routes emissions from channels to subscribers
  - Dynamic channel creation and Flow transformations

- **CellNode** - implements `Cell<I, E>` interface
  - Hierarchical computational cells (PREVIEW API)
  - Bidirectional type transformation with ingress/egress composers

- **ContextualSubject** - implements `Subject<S>` interface
  - Subject with parent, state, and type context
  - Supports hierarchical structures

- **CortexRuntimeProvider** - implements SPI for `Cortex` singleton
  - Entry point for the entire API
  - Service Provider Interface registration

### Infrastructure Classes

- **Valve** - Virtual CPU Core pattern (Dual-queue: Ingress + Transit + Virtual Thread)
- **EmissionChannel** - Named emission ports within conduits
- **InternedName** - Dot-notation hierarchical names with identity-based equality
- **LinkedState** - Immutable state management
- **ConcurrentPool** - Thread-safe instance pooling
- **ManagedScope** - Resource lifecycle management

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

The API you use is **100% Humainary Substrates API** - no proprietary extensions:

```java
import io.humainary.substrates.api.Substrates.*;
import static io.humainary.substrates.api.Substrates.cortex;

// Get Cortex singleton (provided by our CortexRuntimeProvider via SPI)
Cortex cortex = cortex();

// Create circuit (our SequentialCircuit implementation)
Circuit circuit = cortex.circuit(cortex.name("example"));

// Create conduit (our RoutingConduit implementation)
Conduit<Pipe<String>, String> conduit = circuit.conduit(
    cortex.name("messages"),
    Composer.pipe()
);

// Subscribe to emissions
conduit.subscribe(
    cortex.subscriber(
        cortex.name("logger"),
        (subject, registrar) ->
            registrar.register(msg -> System.out.println("Got: " + msg))
    )
);

// Emit to channel
Pipe<String> pipe = conduit.get(cortex.name("producer"));
pipe.emit("Hello, Substrates!");

circuit.await();
circuit.close();
```

**Key Point:** Your application code only uses `io.humainary.substrates.api.*` imports - the concrete classes are loaded via Java SPI automatically.

## Documentation

All API documentation is provided by the Humainary project:

### Official Humainary Resources

1. **[Substrates API JavaDoc](https://github.com/humainary-io/substrates-api-java)** - Complete interface specifications
2. **[Observability X Blog Series](https://humainary.io/blog/category/observability-x/)** - Comprehensive guides by William Louth
3. **[Humainary Website](https://humainary.io/)** - Concepts and philosophy

### Implementation-Specific Docs

Our documentation covers implementation details only:

- **[Architecture Guide](docs/ARCHITECTURE.md)** - How our classes implement the interfaces
- **[Async Architecture](docs/ASYNC-ARCHITECTURE.md)** - Valve pattern and queue processing
- **[Developer Guide](docs/DEVELOPER-GUIDE.md)** - Building and testing this implementation

## Building from Source

### Prerequisites

Install the Humainary APIs locally (not yet published to Maven Central):

```bash
# Install Substrates API (includes Serventis extensions in ext/serventis)
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java
mvn clean install
cd ..
```

**Note:** Serventis extensions are included in the substrates-api-java repository under `ext/serventis/`, not a separate repository.

### Build This Implementation

```bash
git clone https://github.com/fullerstack-io/fullerstack-humainary.git
cd fullerstack-humainary/fullerstack-substrates
mvn clean install
```

### Run TCK Tests

```bash
# Run the Humainary TCK against this implementation
cd /path/to/substrates-api-java/tck
mvn test \
  -Dtck \
  -Dtck.spi.groupId=io.fullerstack \
  -Dtck.spi.artifactId=fullerstack-substrates \
  -Dtck.spi.version=1.0.0-SNAPSHOT
```

**Expected:** 381 tests, 0 failures, 0 errors

## Implementation Highlights

### Sequential Processing (Virtual CPU Core)

Our `SequentialCircuit` uses the **Valve pattern**:
- **Dual-queue architecture**: Ingress queue (external emissions) + Transit deque (recursive emissions)
- One virtual thread processes events with depth-first execution
- **Deterministic ordering** - Transit deque has priority for true depth-first processing
- **No locks needed** - single-threaded execution eliminates race conditions

### Routing Architecture

Our `RoutingConduit` manages the emission flow:
- Creates channels on-demand by name
- Routes emissions through Flow transformations
- Delivers to all registered subscribers
- Lazy subscription evaluation (on first emission)

### SPI-Based Loading

The `CortexRuntimeProvider` implements the Service Provider Interface:
- Registered in `META-INF/services/io.humainary.substrates.spi.CortexProvider`
- Loaded automatically by `Substrates.cortex()`
- No application code needs to know about concrete classes

## Requirements

- Java 25 (uses Virtual Threads)
- Maven 3.9+

## License

Apache License 2.0

This implementation uses and complies with the Humainary Substrates API, which is also Apache 2.0 licensed.

## Acknowledgments

This implementation is based entirely on the **Humainary Substrates API** designed by **William Louth**.

We provide the concrete runtime - the API design, architecture, and concepts are all from William Louth and the Humainary project.

**All credit for the Substrates framework goes to:**
- William Louth - API design and architectural vision
- Humainary - Substrates and Serventis specifications

**Learn more:**
- Humainary Website: https://humainary.io/
- Substrates API: https://github.com/humainary-io/substrates-api-java
- Observability X Blog: https://humainary.io/blog/category/observability-x/

## Authors

**Implementation:** Fullerstack (https://fullerstack.io/)
**API & Design:** William Louth - Humainary (https://humainary.io/)
