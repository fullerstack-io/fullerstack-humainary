#!/bin/bash
# Run JMH benchmarks against Fullerstack SPI
# Usage: ./jmh.sh [benchmark-pattern] [jmh-args...]
#
# Examples:
#   ./jmh.sh                           # Run all benchmarks
#   ./jmh.sh PipeOps                   # Run PipeOps benchmarks
#   ./jmh.sh "emit.*batch" -f 3        # Run matching benchmarks with 3 forks

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Extract version from pom.xml
SPI_VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

# Build first
echo "Building Fullerstack $SPI_VERSION..."
mvn clean install -DskipTests -Deditorconfig.skip=true -q

# Run benchmarks via Humainary's jmh.sh
cd ../substrates-api-java
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION="$SPI_VERSION" ./jmh.sh "$@"
