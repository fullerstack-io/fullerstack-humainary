#!/bin/bash
#
# TCK runner for Fullerstack Substrates
#
# This script wraps Humainary's tck.sh with Fullerstack as the SPI provider.
# Runs all 383 TCK tests to verify API compliance.
#
# Usage:
#   ./scripts/tck.sh              # Run all TCK tests
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
HUMAINARY_ROOT="${PROJECT_ROOT}/substrates-api-java"
FULLERSTACK_ROOT="${PROJECT_ROOT}/fullerstack-substrates"

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

echo ""
echo "=== Fullerstack TCK Test Runner ==="
echo ""

# Build Fullerstack first (must be installed to local repo)
echo "=== Building Fullerstack Substrates ==="
mvn -f "${FULLERSTACK_ROOT}/pom.xml" clean install -DskipTests -q

# Run TCK using Humainary's tck.sh with Fullerstack SPI
echo ""
echo "=== Running TCK Tests via Humainary tck.sh ==="
echo ""

cd "${HUMAINARY_ROOT}"
SPI_GROUP=io.fullerstack \
SPI_ARTIFACT=fullerstack-substrates \
SPI_VERSION=1.0.0-RC1 \
./tck.sh

echo ""
echo "=========================================="
echo "TCK Complete!"
echo "=========================================="
