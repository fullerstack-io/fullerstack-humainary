---
description: Run FlowOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run FlowOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run FlowOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "FlowOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| baseline_no_flow_await | 17.81 | X.XX | +X% | ? |
| flow_combined_diff_guard_await | 29.96 | X.XX | +X% | ? |
| flow_combined_diff_sample_await | 19.35 | X.XX | +X% | ? |
| flow_combined_guard_limit_await | 28.34 | X.XX | +X% | ? |
| flow_diff_await | 30.04 | X.XX | +X% | ? |
| flow_guard_await | 30.37 | X.XX | +X% | ? |
| flow_limit_await | 28.67 | X.XX | +X% | ? |
| flow_sample_await | 17.25 | X.XX | +X% | ? |
| flow_sift_await | 18.68 | X.XX | +X% | ? |

**Summary:** X/9 Fullerstack wins, X/9 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
