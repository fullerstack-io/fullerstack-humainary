---
description: Run all Serventis JMH benchmarks comparing Fullerstack vs Humainary
---

Run all Serventis instrument JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run Serventis benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 \
  "CacheOps|CounterOps|GaugeOps|ProbeOps|ServiceOps|QueueOps|MonitorOps|ResourceOps|ReporterOps|AgentOps|ActorOps|RouterOps|PipelineOps" 2>&1
```

2. Present results grouped by OODA phase:

### OBSERVE Phase (Probes, Services, Queues, Gauges, Counters, Caches)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| ... | X.XX | X.XX | +X% | ? |

### ORIENT Phase (Monitors, Resources)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| ... | X.XX | X.XX | +X% | ? |

### DECIDE Phase (Reporters)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| ... | X.XX | X.XX | +X% | ? |

### ACT Phase (Agents, Actors, Routers)
| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| ... | X.XX | X.XX | +X% | ? |

### Summary
| Phase | Total | Fullerstack Wins | Humainary Wins |
|-------|-------|-----------------|----------------|
| OBSERVE | X | X | X |
| ORIENT | X | X | X |
| DECIDE | X | X | X |
| ACT | X | X | X |
| **Total** | **X** | **X** | **X** |

Get Humainary baselines from: /workspaces/fullerstack-humainary/substrates-api-java/BENCHMARKS.md
