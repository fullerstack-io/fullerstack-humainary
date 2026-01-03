#!/bin/bash
# Run TCK tests against Fullerstack SPI
# Usage: ./tck.sh [test-pattern]
#
# Examples:
#   ./tck.sh                    # Run all TCK tests
#   ./tck.sh CircuitTest        # Run specific test class

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Extract version from pom.xml
SPI_VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

# Build first
echo "Building Fullerstack $SPI_VERSION..."
mvn clean install -DskipTests -Deditorconfig.skip=true -q

# Run TCK via Humainary's tck.sh
cd ../substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION="$SPI_VERSION" ./tck.sh "$@"
