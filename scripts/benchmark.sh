#!/bin/bash
#
# Benchmark runner for Fullerstack Substrates
#
# This script wraps Humainary's jmh.sh with Fullerstack as the SPI provider.
# Results are saved to benchmark-results/ directory.
#
# Usage:
#   ./scripts/benchmark.sh                    # Run ALL Substrates benchmarks
#   ./scripts/benchmark.sh PipeOps            # Run specific benchmark group
#   ./scripts/benchmark.sh "Pipe|Circuit"     # Regex pattern for multiple groups
#   ./scripts/benchmark.sh -l                 # List available benchmarks
#
# JMH Options (passed through to jmh.sh):
#   ./scripts/benchmark.sh -wi 5 -i 10 -f 2   # Custom warmup/iterations/forks
#
# Available Benchmark Groups:
#   Substrates: CircuitOps, ConduitOps, CortexOps, FlowOps, NameOps,
#               PipeOps, ReservoirOps, ScopeOps, StateOps, SubscriberOps
#   Serventis:  CacheOps, CounterOps, GaugeOps, ProbeOps, etc.
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
HUMAINARY_ROOT="${PROJECT_ROOT}/substrates-api-java"
FULLERSTACK_ROOT="${PROJECT_ROOT}/fullerstack-substrates"
RESULTS_DIR="${PROJECT_ROOT}/benchmark-results"

# Setup Java 25
echo "=== Setting up Java 25 ==="
if [[ -f /usr/local/sdkman/bin/sdkman-init.sh ]]; then
    source /usr/local/sdkman/bin/sdkman-init.sh
    sdk use java 25.0.1-open
elif [[ -f ~/.sdkman/bin/sdkman-init.sh ]]; then
    source ~/.sdkman/bin/sdkman-init.sh
    sdk use java 25.0.1-open
else
    echo "ERROR: SDKMAN not found. Please install Java 25."
    exit 1
fi

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Build Fullerstack first (must be installed to local repo)
echo ""
echo "=== Building Fullerstack Substrates ==="
mvn -f "${FULLERSTACK_ROOT}/pom.xml" clean install -DskipTests -q

# Generate output filenames
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
if [[ -z "$1" ]] || [[ "$1" == -* ]]; then
    PATTERN_NAME="all"
else
    PATTERN_NAME=$(echo "$1" | tr '|' '-' | tr '[:upper:]' '[:lower:]')
fi
JSON_FILE="${RESULTS_DIR}/${PATTERN_NAME}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/${PATTERN_NAME}-${TIMESTAMP}.txt"

echo ""
echo "=== Configuration ==="
echo "  Results JSON: ${JSON_FILE}"
echo "  Results Text: ${TXT_FILE}"
echo ""

# Run benchmarks using Humainary's jmh.sh with Fullerstack SPI
echo "=== Running Benchmarks via Humainary jmh.sh ==="
cd "${HUMAINARY_ROOT}"
SPI_GROUP=io.fullerstack \
SPI_ARTIFACT=fullerstack-substrates \
SPI_VERSION=1.0.0-RC1 \
./jmh.sh -rf json -rff "${JSON_FILE}" "$@" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "=========================================="
echo "Benchmark Complete!"
echo "  JSON Results: ${JSON_FILE}"
echo "  Text Results: ${TXT_FILE}"
echo "=========================================="

# Generate comparison table if script exists
COMPARISON_SCRIPT="${SCRIPT_DIR}/generate-comparison.py"
if [[ -f "${COMPARISON_SCRIPT}" ]] && [[ -f "${JSON_FILE}" ]]; then
    echo ""
    echo "=== Generating Comparison Report ==="
    python3 "${COMPARISON_SCRIPT}" "${JSON_FILE}" --print-table --update-md
    echo ""
    echo "Comparison saved to: ${PROJECT_ROOT}/fullerstack-substrates/docs/BENCHMARK-COMPARISON.md"
fi
