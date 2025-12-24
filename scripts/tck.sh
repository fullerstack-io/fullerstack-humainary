#!/bin/bash
#
# Central TCK runner for Fullerstack Substrates
#
# Runs all 383 TCK tests against the Fullerstack implementation.
#
# Usage:
#   ./scripts/tck.sh              # Run all TCK tests
#   ./scripts/tck.sh -Dtest=Name  # Run specific test (passed to Maven)
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

# Build Step 1: Install Humainary API
echo "=== Building Humainary API ==="
mvn -f "${HUMAINARY_ROOT}/pom.xml" clean install -DskipTests -q

# Build Step 2: Install Fullerstack
echo "=== Building Fullerstack Substrates ==="
mvn -f "${FULLERSTACK_ROOT}/pom.xml" clean install -DskipTests -q

# Run TCK tests with Fullerstack SPI
echo ""
echo "=== Running TCK Tests against Fullerstack ==="
echo ""

cd "${HUMAINARY_ROOT}/tck"

mvn test \
    -Dtck \
    -Dsubstrates.spi.groupId=io.fullerstack \
    -Dsubstrates.spi.artifactId=fullerstack-substrates \
    -Dsubstrates.spi.version=1.0.0-SNAPSHOT \
    -Dio.humainary.substrates.spi.provider=io.fullerstack.substrates.FsCortexProvider \
    "$@"

