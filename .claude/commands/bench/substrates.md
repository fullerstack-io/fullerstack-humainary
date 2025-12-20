---
description: Run ALL Substrates JMH benchmarks (10 groups) comparing Fullerstack vs Humainary
---

**IMPORTANT: This benchmark takes 12-15 minutes. ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run all 10 Substrates JMH benchmark groups comparing Fullerstack vs Humainary implementation.

**Groups:** CircuitOps, ConduitOps, CortexOps, FlowOps, NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubscriberOps

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental
- **Available Circuits:**
  - `experimental` - Best performance, JIT-stable design (default)
  - `base` - Original FsCircuit implementation
  - `valve` - Dual-queue architecture with Virtual Thread
  - `ring` - Ring buffer based
  - `optimized` - Sequence-based with padding
  - `turbo` - MPSC linked queue
  - `inline` - Ring + PUBLISHED counter
  - `ultra` - 4096 ring buffer

## Steps

1. Build and run ALL Substrates benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/substrates-all-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/substrates-all-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

echo "Building projects..."
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT

echo "Running benchmarks with circuit type: ${CIRCUIT_TYPE}"
echo "Results will be saved to: ${JSON_FILE}"

java --enable-preview \
  -Dfullerstack.circuit.type=${CIRCUIT_TYPE} \
  -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff "${JSON_FILE}" \
  "io.humainary.substrates.jmh.*" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "=========================================="
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
echo "Circuit Type: ${CIRCUIT_TYPE}"
echo "=========================================="
```

2. Parse and extract results:

```bash
# Extract benchmark scores from JSON
cat "${JSON_FILE}" | grep -E '"benchmark"|"score" :' | paste - - | \
  sed 's/.*jmh\.\([^"]*\)".*"score" : \([0-9.]*\).*/\1 \2/' | sort
```

3. Present ALL results in a single comparison table with Humainary baselines.

**Key Humainary Baselines (from BENCHMARKS.md):**
- CircuitOps: hot_pipe_async=8.5ns, hot_conduit_create=19.1ns
- ConduitOps: get_by_name=1.9ns, subscribe=436.6ns
- CortexOps: circuit=279.2ns, current=1.1ns
- FlowOps: baseline_no_flow_await=17.8ns
- NameOps: name_from_string=3.0ns, name_compare=33.1ns
- PipeOps: async_emit_single=10.6ns
- ReservoirOps: baseline_emit=96.2ns
- ScopeOps: scope_create_and_close=2.4ns
- StateOps: slot_value=0.66ns
- SubscriberOps: close_no_subscriptions_await=8,450ns

**Circuit Type:** ${CIRCUIT_TYPE}

## Finding Results

All benchmark results are saved to `/workspaces/fullerstack-humainary/benchmark-results/` with naming pattern:
- `{group}-{circuit_type}-{timestamp}.json` - Machine-readable JSON
- `{group}-{circuit_type}-{timestamp}.txt` - Human-readable text output

List recent results:
```bash
ls -lt /workspaces/fullerstack-humainary/benchmark-results/ | head -20
```
