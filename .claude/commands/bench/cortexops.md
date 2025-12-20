---
description: Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run CortexOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/cortexops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/cortexops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

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
  "CortexOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| circuit | 279.2 | X | X% | ? |
| circuit_batch | 280.1 | X | X% | ? |
| circuit_named | 288.3 | X | X% | ? |
| current | 1.09 | X | X% | ? |
| name_class | 1.48 | X | X% | ? |
| name_enum | 2.81 | X | X% | ? |
| name_iterable | 11.2 | X | X% | ? |
| name_path | 1.89 | X | X% | ? |
| name_path_batch | 1.69 | X | X% | ? |
| name_string | 2.85 | X | X% | ? |
| name_string_batch | 2.54 | X | X% | ? |
| scope | 9.28 | X | X% | ? |
| scope_batch | 7.58 | X | X% | ? |
| scope_named | 8.01 | X | X% | ? |
| slot_boolean | 2.41 | X | X% | ? |
| slot_double | 2.43 | X | X% | ? |
| slot_int | 2.12 | X | X% | ? |
| slot_long | 2.42 | X | X% | ? |
| slot_string | 2.43 | X | X% | ? |
| state_empty | 0.44 | X | X% | ? |
| state_empty_batch | ~0 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/21 Fullerstack wins, X/21 Humainary wins
