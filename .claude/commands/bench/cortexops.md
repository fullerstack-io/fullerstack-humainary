---
description: Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run CortexOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run CortexOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "CortexOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| circuit | 279.17 | X.XX | +X% | ? |
| circuit_batch | 280.10 | X.XX | +X% | ? |
| circuit_named | 288.31 | X.XX | +X% | ? |
| current | 1.09 | X.XX | +X% | ? |
| name_class | 1.48 | X.XX | +X% | ? |
| name_enum | 2.81 | X.XX | +X% | ? |
| name_iterable | 11.23 | X.XX | +X% | ? |
| name_path | 1.89 | X.XX | +X% | ? |
| name_path_batch | 1.69 | X.XX | +X% | ? |
| name_string | 2.85 | X.XX | +X% | ? |
| name_string_batch | 2.54 | X.XX | +X% | ? |
| scope | 9.28 | X.XX | +X% | ? |
| scope_batch | 7.58 | X.XX | +X% | ? |
| scope_named | 8.01 | X.XX | +X% | ? |
| slot_boolean | 2.41 | X.XX | +X% | ? |
| slot_double | 2.43 | X.XX | +X% | ? |
| slot_int | 2.12 | X.XX | +X% | ? |
| slot_long | 2.42 | X.XX | +X% | ? |
| slot_string | 2.43 | X.XX | +X% | ? |
| state_empty | 0.44 | X.XX | +X% | ? |
| state_empty_batch | 0.001 | X.XX | +X% | ? |

**Summary:** X/21 Fullerstack wins, X/21 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
