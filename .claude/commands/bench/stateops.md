---
description: Run StateOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run StateOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run StateOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/stateops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/stateops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

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
  "StateOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| slot_name | 0.52 | X | X% | ? |
| slot_name_batch | ~0 | X | X% | ? |
| slot_type | 0.44 | X | X% | ? |
| slot_value | 0.66 | X | X% | ? |
| slot_value_batch | ~0 | X | X% | ? |
| state_compact | 10.3 | X | X% | ? |
| state_compact_batch | 10.7 | X | X% | ? |
| state_iterate_slots | 2.16 | X | X% | ? |
| state_slot_add_int | 4.76 | X | X% | ? |
| state_slot_add_int_batch | 4.89 | X | X% | ? |
| state_slot_add_long | 4.75 | X | X% | ? |
| state_slot_add_object | 2.56 | X | X% | ? |
| state_slot_add_object_batch | 2.43 | X | X% | ? |
| state_slot_add_string | 4.73 | X | X% | ? |
| state_value_read | 1.49 | X | X% | ? |
| state_value_read_batch | 1.27 | X | X% | ? |
| state_values_stream | 4.98 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/17 Fullerstack wins, X/17 Humainary wins
