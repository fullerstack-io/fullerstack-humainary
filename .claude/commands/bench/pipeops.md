---
description: Run PipeOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run PipeOps JMH benchmarks using Humainary's official jmh.sh with Fullerstack as the SPI provider.

## Instructions

**Run with `run_in_background: true`. Do NOT block waiting - just report the task ID.**

```bash
cd /workspaces/fullerstack-humainary/fullerstack-substrates && \
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn clean install -DskipTests -q && \
cd /workspaces/fullerstack-humainary/substrates-api-java && \
SPI_GROUP=io.fullerstack SPI_ARTIFACT=fullerstack-substrates SPI_VERSION=1.0.0-RC1 \
./jmh.sh PipeOps
```

When complete, present comparison table using Humainary baselines from BENCHMARKS.md.
Diff = ((Fullerstack - Humainary) / Humainary * 100). Winner = lower time.
