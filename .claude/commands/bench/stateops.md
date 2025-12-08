---
description: Run StateOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run StateOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run StateOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "StateOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| slot_name | 0.82 | X.XX | +X% | ? |
| slot_name_batch | 8.33 | X.XX | +X% | ? |
| slot_type | 0.87 | X.XX | +X% | ? |
| slot_value | 1.00 | X.XX | +X% | ? |
| slot_value_batch | 9.88 | X.XX | +X% | ? |
| state_compact | 91.92 | X.XX | +X% | ? |
| state_compact_batch | 96.29 | X.XX | +X% | ? |
| state_iterate_slots | 20.84 | X.XX | +X% | ? |
| state_slot_add_int | 23.91 | X.XX | +X% | ? |
| state_slot_add_int_batch | 20.63 | X.XX | +X% | ? |
| state_slot_add_long | 23.40 | X.XX | +X% | ? |
| state_slot_add_object | 18.88 | X.XX | +X% | ? |
| state_slot_add_object_batch | 18.14 | X.XX | +X% | ? |
| state_slot_add_string | 23.30 | X.XX | +X% | ? |
| state_value_read | 9.42 | X.XX | +X% | ? |
| state_value_read_batch | 4.80 | X.XX | +X% | ? |
| state_values_stream | 36.16 | X.XX | +X% | ? |

**Summary:** X/17 Fullerstack wins, X/17 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
