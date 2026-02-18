#!/bin/bash
#
# TCK runner for Fullerstack Substrates
#
# This script wraps Humainary's tck.sh with Fullerstack as the SPI provider.
# Runs all 383 TCK tests to verify API compliance.
#
# Usage:
#   ./scripts/tck.sh              # Run all TCK tests
#   ./scripts/tck.sh CircuitTest  # Run specific test class
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

# Extract version from pom.xml
SPI_VERSION=$(grep -m1 '<version>' "${FULLERSTACK_ROOT}/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
echo "Using Fullerstack version: ${SPI_VERSION}"

# Build Fullerstack first (must be installed to local repo)
echo "=== Building Fullerstack Substrates ==="
mvn -f "${FULLERSTACK_ROOT}/pom.xml" clean install -DskipTests -Deditorconfig.skip=true -q

# Run TCK using Humainary's tck.sh with Fullerstack SPI
echo ""
echo "=== Running TCK Tests via Humainary tck.sh ==="
echo ""

cd "${HUMAINARY_ROOT}"

# Build test filter if argument provided
TEST_FILTER=""
FAIL_IF_NO_TESTS=""
if [[ -n "$1" ]]; then
    TEST_FILTER="-Dtest=$1"
    FAIL_IF_NO_TESTS="-Dsurefire.failIfNoSpecifiedTests=false"
    echo "Running specific test: $1"
fi

# Run TCK with Fullerstack SPI
./mvnw clean install -U -Dguice_custom_class_loading=CHILD -Dtck \
    -Dsubstrates.spi.groupId=io.fullerstack \
    -Dsubstrates.spi.artifactId=fullerstack-substrates \
    -Dsubstrates.spi.version="${SPI_VERSION}" \
    ${TEST_FILTER} ${FAIL_IF_NO_TESTS}

echo ""
echo "=========================================="
echo "TCK Complete!"
echo "=========================================="
