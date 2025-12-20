#!/bin/bash
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/substrates-all-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/substrates-all-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

echo "Building projects..."
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT

echo "Running benchmarks with circuit type: ${CIRCUIT_TYPE}"
echo "Results will be saved to: ${JSON_FILE}"

java --enable-preview \
  -Dfullerstack.circuit.type=${CIRCUIT_TYPE} \
  -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff "${JSON_FILE}" \
  "io.humainary.substrates.jmh.*" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "=========================================="
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
echo "Circuit Type: ${CIRCUIT_TYPE}"
echo "=========================================="
