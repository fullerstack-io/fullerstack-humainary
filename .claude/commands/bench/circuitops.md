---
description: Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run CircuitOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

# Set circuit type (default: experimental)
CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/circuitops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/circuitops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

# Build
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT

# Run benchmarks - save both JSON and text output
java --enable-preview \
  -Dfullerstack.circuit.type=${CIRCUIT_TYPE} \
  -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff "${JSON_FILE}" \
  "CircuitOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Parse results and compare with Humainary baseline:

```bash
# Extract scores from JSON
cat "${JSON_FILE}" | grep -E '"benchmark"|"score" :' | paste - - | \
  sed 's/.*jmh\.\([^"]*\)".*"score" : \([0-9.]*\).*/\1 \2/'
```

3. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| conduit_create_close | 281.7 | X | X% | ? |
| conduit_create_named | 282.0 | X | X% | ? |
| conduit_create_with_flow | 280.1 | X | X% | ? |
| create_and_close | 337.3 | X | X% | ? |
| create_await_close | 10,731 | X | X% | ? |
| hot_await_queue_drain | 5,799 | X | X% | ? |
| hot_conduit_create | 19.1 | X | X% | ? |
| hot_conduit_create_named | 19.1 | X | X% | ? |
| hot_conduit_create_with_flow | 21.9 | X | X% | ? |
| hot_pipe_async | 8.5 | X | X% | ? |
| hot_pipe_async_with_flow | 10.7 | X | X% | ? |
| pipe_async | 309.1 | X | X% | ? |
| pipe_async_with_flow | 320.4 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/13 Fullerstack wins, X/13 Humainary wins

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
