---
description: Run NameOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run NameOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run NameOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/nameops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/nameops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

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
  "NameOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| name_chained_deep | 16.9 | X | X% | ? |
| name_chaining | 8.56 | X | X% | ? |
| name_chaining_batch | 9.04 | X | X% | ? |
| name_compare | 33.1 | X | X% | ? |
| name_compare_batch | 32.0 | X | X% | ? |
| name_depth | 1.70 | X | X% | ? |
| name_depth_batch | 1.40 | X | X% | ? |
| name_enclosure | 0.59 | X | X% | ? |
| name_from_enum | 2.83 | X | X% | ? |
| name_from_iterable | 12.0 | X | X% | ? |
| name_from_iterator | 12.9 | X | X% | ? |
| name_from_mapped_iterable | 11.7 | X | X% | ? |
| name_from_name | 4.22 | X | X% | ? |
| name_from_string | 3.05 | X | X% | ? |
| name_from_string_batch | 2.80 | X | X% | ? |
| name_interning_chained | 12.4 | X | X% | ? |
| name_interning_same_path | 3.54 | X | X% | ? |
| name_interning_segments | 10.1 | X | X% | ? |
| name_iterate_hierarchy | 1.80 | X | X% | ? |
| name_parsing | 1.89 | X | X% | ? |
| name_parsing_batch | 1.68 | X | X% | ? |
| name_path_generation | 31.3 | X | X% | ? |
| name_path_generation_batch | 30.0 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/23 Fullerstack wins, X/23 Humainary wins
