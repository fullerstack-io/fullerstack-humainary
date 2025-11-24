#!/bin/bash
# Run JMH Benchmarks for Substrates Implementation
# Usage: ./run-benchmarks.sh [benchmark-pattern] [options]
#
# Examples:
#   ./run-benchmarks.sh                                    # Run all benchmarks
#   ./run-benchmarks.sh PipeOps                            # Run all PipeOps benchmarks
#   ./run-benchmarks.sh "PipeOps.emit_to_async_pipe"      # Run specific benchmark
#   ./run-benchmarks.sh CircuitOps -wi 5 -i 10            # Custom warmup/iterations
#   ./run-benchmarks.sh PipeOps -prof perfnorm            # With profiler

# Ensure benchmarks.jar exists
if [ ! -f target/benchmarks.jar ]; then
    echo "Building benchmarks.jar..."
    mvn clean package -DskipTests -q
fi

# Extract benchmark pattern (first argument)
PATTERN=${1:-""}

# Shift to get remaining arguments (JMH options)
if [ -n "$1" ]; then
    shift
fi

# Run benchmarks
if [ -z "$PATTERN" ]; then
    echo "Running ALL benchmarks..."
    java -jar target/benchmarks.jar "$@"
else
    echo "Running benchmarks matching: $PATTERN"
    java -jar target/benchmarks.jar "$PATTERN" "$@"
fi
