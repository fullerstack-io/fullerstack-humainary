---
description: Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run ScopeOps benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/scopeops-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/scopeops-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

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
  "ScopeOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present comparison table using Humainary baselines from BENCHMARKS.md:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| scope_child_anonymous | 18.2 | X | X% | ? |
| scope_child_anonymous_batch | 17.7 | X | X% | ? |
| scope_child_named | 17.1 | X | X% | ? |
| scope_child_named_batch | 19.4 | X | X% | ? |
| scope_close_idempotent | 2.39 | X | X% | ? |
| scope_close_idempotent_batch | 0.03 | X | X% | ? |
| scope_closure | 286.1 | X | X% | ? |
| scope_closure_batch | 307.4 | X | X% | ? |
| scope_complex | 917.0 | X | X% | ? |
| scope_create_and_close | 2.43 | X | X% | ? |
| scope_create_and_close_batch | 0.03 | X | X% | ? |
| scope_create_named | 2.43 | X | X% | ? |
| scope_create_named_batch | 0.03 | X | X% | ? |
| scope_hierarchy | 27.3 | X | X% | ? |
| scope_hierarchy_batch | 26.6 | X | X% | ? |
| scope_parent_closes_children | 43.5 | X | X% | ? |
| scope_parent_closes_children_batch | 42.3 | X | X% | ? |
| scope_register_multiple | 1,450 | X | X% | ? |
| scope_register_multiple_batch | 1,398 | X | X% | ? |
| scope_register_single | 287.1 | X | X% | ? |
| scope_register_single_batch | 283.1 | X | X% | ? |
| scope_with_resources | 581.9 | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}
**Summary:** X/22 Fullerstack wins, X/22 Humainary wins
