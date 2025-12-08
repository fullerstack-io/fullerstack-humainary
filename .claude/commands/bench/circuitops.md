---
description: Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run CircuitOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run CircuitOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "CircuitOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| hot_pipe_async | 4.32 | X.XX | +X% | ? |
| hot_await_queue_drain | 5.67 | X.XX | +X% | ? |
| hot_conduit_create | 18.48 | X.XX | +X% | ? |
| hot_conduit_create_named | 18.77 | X.XX | +X% | ? |
| hot_conduit_create_with_flow | 19.74 | X.XX | +X% | ? |
| hot_pipe_async_with_flow | 34.16 | X.XX | +X% | ? |
| conduit_create_close | 8918 | X.XX | +X% | ? |
| conduit_create_named | 8662 | X.XX | +X% | ? |
| conduit_create_with_flow | 9078 | X.XX | +X% | ? |
| create_and_close | 7099 | X.XX | +X% | ? |
| create_await_close | 7832 | X.XX | +X% | ? |
| pipe_async | 6832 | X.XX | +X% | ? |
| pipe_async_with_flow | 7148 | X.XX | +X% | ? |

**Summary:** X/13 Fullerstack wins, X/13 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
