---
description: Run ConduitOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run ConduitOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run ConduitOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "ConduitOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| get_by_name | 1.23 | X.XX | +X% | ? |
| get_by_name_batch | 1.04 | X.XX | +X% | ? |
| get_by_substrate | 1.83 | X.XX | +X% | ? |
| get_by_substrate_batch | 1.50 | X.XX | +X% | ? |
| get_cached | 2.02 | X.XX | +X% | ? |
| get_cached_batch | 1.86 | X.XX | +X% | ? |
| subscribe | 126.42 | X.XX | +X% | ? |
| subscribe_batch | 129.72 | X.XX | +X% | ? |
| subscribe_with_emission_await | 11688.86 | X.XX | +X% | ? |

**Summary:** X/9 Fullerstack wins, X/9 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
