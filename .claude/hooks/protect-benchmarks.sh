#!/bin/bash
# Protect running JMH benchmarks from mvn clean/install

input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command' 2>/dev/null)

# Check if this is an mvn clean or mvn install command
if echo "$command" | grep -qE "mvn\s+(clean|install)"; then
  # Check if JMH java process is running (specifically the jar)
  if pgrep -f "java.*substrates-jmh.*jar-with-dependencies" > /dev/null 2>&1; then
    echo "BLOCKED: JMH benchmark is running. Cannot run mvn clean/install." >&2
    pgrep -f -a "java.*substrates-jmh" >&2
    exit 2
  fi
fi

exit 0
