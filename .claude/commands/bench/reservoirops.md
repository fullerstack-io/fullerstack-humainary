---
description: Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run ReservoirOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/reservoirops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/reservoirops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT

java --enable-preview \
  -Dfullerstack.circuit.type=${CIRCUIT_TYPE} \
  -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff "${JSON_FILE}" \
  "ReservoirOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| baseline_emit_no_reservoir_await | 96.2 | X | X% | ? |
| baseline_emit_no_reservoir_await_batch | 18.5 | X | X% | ? |
| reservoir_burst_then_drain_await | 90.2 | X | X% | ? |
| reservoir_burst_then_drain_await_batch | 28.8 | X | X% | ? |
| reservoir_drain_await | 93.8 | X | X% | ? |
| reservoir_drain_await_batch | 28.3 | X | X% | ? |
| reservoir_emit_drain_cycles_await | 328.1 | X | X% | ? |
| reservoir_emit_with_capture_await | 80.0 | X | X% | ? |
| reservoir_emit_with_capture_await_batch | 23.9 | X | X% | ? |
| reservoir_process_emissions_await | 89.1 | X | X% | ? |
| reservoir_process_emissions_await_batch | 26.1 | X | X% | ? |
| reservoir_process_subjects_await | 97.5 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/12 Fullerstack wins, X/12 Humainary wins
