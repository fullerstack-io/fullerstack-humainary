#!/bin/bash
# Format Java code using javalint (IntelliJ CE formatter with EditorConfig support)
# Usage: ./scripts/format.sh [pattern]
# Examples:
#   ./scripts/format.sh                    # Format all Java files in src/
#   ./scripts/format.sh src/main/**/*.java # Format only main source files
#   ./scripts/format.sh --check            # Check without formatting

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
JAVALINT_JAR="$ROOT_DIR/tools/javalint.jar"

# Ensure Java is available
if command -v sdk &> /dev/null; then
  source /usr/local/sdkman/bin/sdkman-init.sh 2>/dev/null || true
  sdk use java 25.0.1-open > /dev/null 2>&1 || true
fi

cd "$ROOT_DIR/fullerstack-substrates"

# Parse arguments
if [[ "$1" == "--check" ]]; then
  echo "Checking formatting (no changes)..."
  java -cp "$JAVALINT_JAR" io.github.kamilperczynski.javalint.cli.MainKt "${@:2:-src/**/*.java}"
elif [[ -n "$1" ]]; then
  echo "Formatting: $1"
  java -cp "$JAVALINT_JAR" io.github.kamilperczynski.javalint.cli.MainKt -F "$@"
else
  echo "Formatting all Java files in src/..."
  java -cp "$JAVALINT_JAR" io.github.kamilperczynski.javalint.cli.MainKt -F "src/**/*.java"
fi

echo "Done!"
