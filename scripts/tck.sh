#!/bin/bash
#
# TCK runner for Fullerstack Substrates
#
# Runs the integrated TCK tests (copied from upstream before removal in 1.0.0)
# plus Fullerstack's own contract tests.
#
# Usage:
#   ./scripts/tck.sh              # Run all tests (703 expected)
#   ./scripts/tck.sh CircuitTest  # Run specific test class
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FULLERSTACK_ROOT="${PROJECT_ROOT}/fullerstack-substrates"

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

echo ""
echo "=== Fullerstack Test Runner ==="
echo ""
echo "Running 255 contract tests + 448 integrated TCK tests (703 total)"
echo ""

# Build test filter if argument provided
TEST_FILTER=""
if [[ -n "$1" ]]; then
    TEST_FILTER="-Dtest=$1"
    echo "Running specific test: $1"
fi

cd "${FULLERSTACK_ROOT}"
mvn clean test -Deditorconfig.skip=true ${TEST_FILTER}

echo ""
echo "=========================================="
echo "Tests Complete!"
echo "=========================================="
