---
description: Run all Serventis JMH benchmarks comparing Fullerstack vs Humainary
---

**IMPORTANT: ALWAYS run the bash command with `run_in_background: true` to prevent interruption.**

Run all Serventis instrument JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Run the benchmark script with Serventis pattern:

```bash
/workspaces/fullerstack-humainary/scripts/benchmark.sh "CacheOps|CounterOps|GaugeOps|ProbeOps|ServiceOps|QueueOps|MonitorOps|ResourceOps|ReporterOps|AgentOps|ActorOps|RouterOps|PipelineOps"
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

Get Humainary baselines from: /workspaces/fullerstack-humainary/substrates-api-java/BENCHMARKS.md

Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time (faster).
