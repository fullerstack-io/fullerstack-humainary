#!/bin/bash
# Run QueueBenchmark - isolated NonBlockingQueue performance tests
# Usage: ./queue-bench.sh [benchmark-pattern] [jmh-args...]
#
# Examples:
#   ./queue-bench.sh                    # Run all queue benchmarks
#   ./queue-bench.sh add_batch          # Run matching benchmarks
#   ./queue-bench.sh -f 3               # Run with 3 forks

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Build first
echo "Building benchmarks..."
mvn clean package -DskipTests -Deditorconfig.skip=true -q

echo ""
echo "=== Running QueueBenchmark ==="
java -server \
  --enable-preview \
  -XX:-RestrictContended \
  --add-opens java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
  -jar target/benchmarks.jar "QueueBenchmark" "$@"
