#!/bin/bash
# Run TCK + contract tests against Fullerstack SPI
#
# The upstream TCK source files were integrated directly into this repository
# when the TCK module was removed in API 1.0.0. All 448 TCK tests now run
# alongside our 255 contract tests (703 total).
#
# Usage: ./tck.sh [test-pattern]

set -e

echo "=== Fullerstack Test Runner ==="
echo ""
echo "Running 255 contract tests + 448 integrated TCK tests (703 total)"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mvn test -Deditorconfig.skip=true "$@"
