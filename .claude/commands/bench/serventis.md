---
description: Run all Serventis JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run all Serventis instrument JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Configuration

- **Results Directory:** `/workspaces/fullerstack-humainary/benchmark-results/`
- **Circuit Type:** Set via `-Dfullerstack.circuit.type=` (experimental, base, valve, ring, optimized, turbo, inline, ultra)
- **Default Circuit:** experimental

## Steps

1. Build and run Serventis benchmarks with results saved to file:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open

CIRCUIT_TYPE="${CIRCUIT_TYPE:-experimental}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RESULTS_DIR="/workspaces/fullerstack-humainary/benchmark-results"
mkdir -p "${RESULTS_DIR}"
JSON_FILE="${RESULTS_DIR}/serventis-${CIRCUIT_TYPE}-${TIMESTAMP}.json"
TXT_FILE="${RESULTS_DIR}/serventis-${CIRCUIT_TYPE}-${TIMESTAMP}.txt"

mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT

java --enable-preview \
  -Dfullerstack.circuit.type=${CIRCUIT_TYPE} \
  -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  -rf json -rff "${JSON_FILE}" \
  "CacheOps|CounterOps|GaugeOps|ProbeOps|ServiceOps|QueueOps|MonitorOps|ResourceOps|ReporterOps|AgentOps|ActorOps|RouterOps|PipelineOps" 2>&1 | tee "${TXT_FILE}"

echo ""
echo "Results saved to:"
echo "  JSON: ${JSON_FILE}"
echo "  Text: ${TXT_FILE}"
```

2. Present results grouped by OODA phase:

### OBSERVE Phase (Probes, Services, Queues, Gauges, Counters, Caches)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| ... | X | X | X% | ? |

### ORIENT Phase (Monitors, Resources)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| ... | X | X | X% | ? |

### DECIDE Phase (Reporters)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| ... | X | X | X% | ? |

### ACT Phase (Agents, Actors, Routers)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|---------------:|----------------:|-----:|:------:|
| ... | X | X | X% | ? |

**Circuit Type:** ${CIRCUIT_TYPE}

Get Humainary baselines from: /workspaces/fullerstack-humainary/substrates-api-java/BENCHMARKS.md
