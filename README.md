# Fullerstack Humainary

**Implementations of [Humainary](https://humainary.io/) frameworks and APIs.**

This repository provides concrete runtime implementations of Humainary's observable, event-driven system specifications, bringing William Louth's architectural vision to life with robust Java implementations.

## Projects

### [Fullerstack Substrates](fullerstack-substrates/)

A **fully compliant implementation** of the [Humainary Substrates API](https://github.com/humainary-io/substrates-api-java) (version 1.0.0-PREVIEW).

**Status:** Production-ready
**TCK Compliance:** ✅ 387/387 tests passing (100%)
**Java Version:** 25 (Virtual Threads)

Substrates provides the infrastructure for building observable, event-driven systems with deterministic event ordering and semiotic signal interpretation.

**Key Features:**
- **Sequential Processing** - Virtual CPU Core pattern with dual-queue architecture
- **Dynamic Routing** - On-demand channel creation and flow transformations
- **Hierarchical Computation** - Cell-based bidirectional type transformation
- **SPI-Based Loading** - Automatic provider discovery via ServiceLoader
- **Full TCK Compliance** - Passes all 383 Humainary specification tests

**Quick Start:**
```xml
<dependency>
    <groupId>io.fullerstack</groupId>
    <artifactId>fullerstack-substrates</artifactId>
    <version>1.0.0-RC2</version>
</dependency>
```

```java
import static io.humainary.substrates.api.Substrates.*;

// Get Cortex singleton (our implementation loaded via SPI)
Cortex cortex = cortex();

// Create circuit with sequential processing
Circuit circuit = cortex.circuit(cortex.name("example"));

// Your code uses 100% Humainary Substrates API
// Our implementation handles the runtime
```

**Documentation:**
- [Implementation Architecture](fullerstack-substrates/docs/ARCHITECTURE.md)
- [Async Processing Architecture](fullerstack-substrates/docs/ASYNC-ARCHITECTURE.md)
- [Developer Guide](fullerstack-substrates/docs/DEVELOPER-GUIDE.md)

## Philosophy

We provide the **concrete runtime** - the API design, architecture, and concepts are all from **William Louth** and the **Humainary project**.

Think of it like:
- **Humainary Substrates API** = `java.util.List` interface (what it should do)
- **Fullerstack Implementation** = `ArrayList` class (how it actually works)

We don't change the API or add features - we implement exactly what the Substrates specification defines.

## Building from Source

### Prerequisites

Install the Humainary APIs locally (not yet published to Maven Central):

```bash
# Install Substrates API (includes Serventis extensions)
git clone https://github.com/humainary-io/substrates-api-java.git
cd substrates-api-java
mvn clean install
```

### Build This Implementation

```bash
git clone https://github.com/fullerstack-io/fullerstack-humainary.git
cd fullerstack-humainary/fullerstack-substrates
mvn clean install
```

### Run TCK Tests

```bash
# Run the Humainary TCK against our implementation
cd /path/to/substrates-api-java/tck
mvn test \
  -Dtck \
  -Dtck.spi.groupId=io.fullerstack \
  -Dtck.spi.artifactId=fullerstack-substrates \
  -Dtck.spi.version=1.0.0-RC2
```

**Expected:** 387 tests, 0 failures, 0 errors

## Requirements

- **Java 25** (uses Virtual Threads)
- **Maven 3.9+**

## License

Apache License 2.0

These implementations use and comply with the Humainary Substrates API, which is also Apache 2.0 licensed.

## Acknowledgments

All implementations in this repository are based entirely on APIs designed by **William Louth**.

We provide the concrete runtime - the API design, architecture, and concepts are all from William Louth and the Humainary project.

**All credit for the Substrates framework goes to:**
- **William Louth** - API design and architectural vision
- **Humainary** - Substrates and Serventis specifications

**Learn more:**
- Humainary Website: https://humainary.io/
- Substrates API: https://github.com/humainary-io/substrates-api-java
- Observability X Blog: https://humainary.io/blog/category/observability-x/

## Authors

**Implementation:** [Fullerstack](https://fullerstack.io/)
**API & Design:** [William Louth](https://humainary.io/) - Humainary
