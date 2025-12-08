---
description: Run PipeOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run PipeOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run PipeOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "PipeOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| async_emit_batch | 11.84 | X.XX | +X% | ? |
| async_emit_batch_await | 16.87 | X.XX | +X% | ? |
| async_emit_chained_await | 16.93 | X.XX | +X% | ? |
| async_emit_fanout_await | 18.72 | X.XX | +X% | ? |
| async_emit_single | 10.65 | X.XX | +X% | ? |
| async_emit_single_await | 5477.58 | X.XX | +X% | ? |
| async_emit_with_flow_await | 21.22 | X.XX | +X% | ? |
| baseline_blackhole | 0.27 | X.XX | +X% | ? |
| baseline_counter | 1.62 | X.XX | +X% | ? |
| baseline_receptor | 0.26 | X.XX | +X% | ? |
| pipe_create | 8.75 | X.XX | +X% | ? |
| pipe_create_chained | 0.86 | X.XX | +X% | ? |
| pipe_create_with_flow | 13.23 | X.XX | +X% | ? |

**Summary:** X/13 Fullerstack wins, X/13 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
