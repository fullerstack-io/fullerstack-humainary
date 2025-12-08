---
description: Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run ReservoirOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run ReservoirOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "ReservoirOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| baseline_emit_no_reservoir_await | 96.22 | X.XX | +X% | ? |
| baseline_emit_no_reservoir_await_batch | 18.46 | X.XX | +X% | ? |
| reservoir_burst_then_drain_await | 90.19 | X.XX | +X% | ? |
| reservoir_burst_then_drain_await_batch | 28.81 | X.XX | +X% | ? |
| reservoir_drain_await | 93.80 | X.XX | +X% | ? |
| reservoir_drain_await_batch | 28.34 | X.XX | +X% | ? |
| reservoir_emit_drain_cycles_await | 328.06 | X.XX | +X% | ? |
| reservoir_emit_with_capture_await | 79.97 | X.XX | +X% | ? |
| reservoir_emit_with_capture_await_batch | 23.86 | X.XX | +X% | ? |
| reservoir_process_emissions_await | 89.08 | X.XX | +X% | ? |
| reservoir_process_emissions_await_batch | 26.14 | X.XX | +X% | ? |
| reservoir_process_subjects_await | 97.45 | X.XX | +X% | ? |

**Summary:** X/12 Fullerstack wins, X/12 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
