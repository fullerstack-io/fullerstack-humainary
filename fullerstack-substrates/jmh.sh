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

# Disable Fullerstack JMH tests to avoid conflicts with Humainary JMH benchmarks
# The benchmarks are defined in Humainary's jmh module, not Fullerstack's
if [ -d "src/jmh" ]; then
  mv src/jmh src/jmh.disabled
  echo "Disabled Fullerstack JMH tests (renamed src/jmh -> src/jmh.disabled)"
fi

# Cleanup function to restore JMH folder on exit
cleanup() {
  cd "$SCRIPT_DIR"
  if [ -d "src/jmh.disabled" ]; then
    mv src/jmh.disabled src/jmh
    echo "Restored Fullerstack JMH tests (renamed src/jmh.disabled -> src/jmh)"
  fi
}
trap cleanup EXIT

# Extract version from pom.xml
SPI_VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')

# Build first
echo "Building Fullerstack $SPI_VERSION..."
mvn clean install -DskipTests -Deditorconfig.skip=true -q

# Build and run via Humainary's infrastructure
cd ../substrates-api-java

echo ""
echo "=== Running tests ==="
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION="$SPI_VERSION" \
  ./mvnw clean install -U -Dsubstrates.spi.groupId=io.fullerstack -Dsubstrates.spi.artifactId=fullerstack-substrates -Dsubstrates.spi.version="$SPI_VERSION"

echo ""
echo "=== Building JMH benchmarks ==="
./mvnw clean package -Pjmh -Dsubstrates.spi.groupId=io.fullerstack -Dsubstrates.spi.artifactId=fullerstack-substrates -Dsubstrates.spi.version="$SPI_VERSION"

echo ""
echo "=== Running JMH benchmarks ==="

# Diagnostic flags (pass --diag to enable compilation + GC logging)
DIAG_FLAGS=""
JMH_ARGS=()
for arg in "$@"; do
  if [ "$arg" = "--diag" ]; then
    DIAG_FLAGS="-XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=hotspot_compilation.log -Xlog:gc*,safepoint:file=gc.log:time,uptime,level,tags"
    echo "  Diagnostics enabled: compilation log + GC/safepoint log"
  else
    JMH_ARGS+=("$arg")
  fi
done

java -server \
  --enable-preview \
  -XX:-RestrictContended \
  -XX:+UseCompactObjectHeaders \
  -XX:ParallelGCThreads=2 \
  $DIAG_FLAGS \
  --add-opens java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
  -jar jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar "${JMH_ARGS[@]}"
