#!/bin/bash
#
# Benchmark runner for Fullerstack Substrates
#
# Builds the shaded JMH jar and runs benchmarks directly.
#
# Usage:
#   ./scripts/benchmark.sh                    # Run ALL benchmarks
#   ./scripts/benchmark.sh PipeOps            # Run specific benchmark group
#   ./scripts/benchmark.sh "Pipe|Circuit"     # Regex pattern for multiple groups
#   ./scripts/benchmark.sh -l                 # List available benchmarks
#
# Available Benchmark Groups (14):
#   CircuitOps, ConduitOps, CortexOps, CyclicOps, FlowOps, IdOps,
#   NameOps, PipeOps, ReservoirOps, ScopeOps, StateOps, SubjectOps,
#   SubscriberOps, TapOps
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SUBSTRATES_DIR="${PROJECT_ROOT}/fullerstack-substrates"

# Setup Java 26
echo "=== Setting up Java 26 ==="
if [[ -f /usr/local/sdkman/bin/sdkman-init.sh ]]; then
    source /usr/local/sdkman/bin/sdkman-init.sh
    sdk use java 26.ea.35-open
elif [[ -f ~/.sdkman/bin/sdkman-init.sh ]]; then
    source ~/.sdkman/bin/sdkman-init.sh
    sdk use java 26.ea.35-open
else
    echo "ERROR: SDKMAN not found. Please install Java 26."
    exit 1
fi

# Build shaded benchmark jar
echo ""
echo "=== Building benchmark jar ==="
mvn -f "${SUBSTRATES_DIR}/pom.xml" clean install -DskipTests -Deditorconfig.skip=true -q

BENCHMARK_JAR="${SUBSTRATES_DIR}/target/benchmarks.jar"
if [[ ! -f "${BENCHMARK_JAR}" ]]; then
    echo "ERROR: Benchmark jar not found at ${BENCHMARK_JAR}"
    exit 1
fi

# Default JMH options
JMH_OPTS="-f 1 -wi 3 -i 5 -w 2s -r 2s -tu ns -bm avgt"

# Handle arguments
if [[ "$1" == "-l" ]]; then
    echo ""
    echo "=== Available Benchmarks ==="
    java --enable-preview \
        --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
        -XX:-RestrictContended \
        -jar "${BENCHMARK_JAR}" -l
    exit 0
fi

FILTER=""
if [[ -n "$1" ]] && [[ "$1" != -* ]]; then
    FILTER="$1"
    shift
fi

# Override defaults with any remaining args
if [[ $# -gt 0 ]]; then
    JMH_OPTS="$@"
fi

echo ""
echo "=== Running Benchmarks ==="
echo "  Filter: ${FILTER:-ALL}"
echo "  Options: ${JMH_OPTS}"
echo ""

java --enable-preview \
    --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
    -XX:-RestrictContended \
    -jar "${BENCHMARK_JAR}" \
    ${FILTER:+"$FILTER"} \
    ${JMH_OPTS}
