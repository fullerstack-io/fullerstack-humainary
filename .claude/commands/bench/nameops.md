---
description: Run NameOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run NameOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run NameOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "NameOps" 2>&1
```

2. Present results in this side-by-side comparison table (Humainary baseline from BENCHMARKS.md):

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| name_chained_deep | 16.9 | X.XX | +X% | ? |
| name_chaining | 8.6 | X.XX | +X% | ? |
| name_chaining_batch | 8.6 | X.XX | +X% | ? |
| name_compare | 33.0 | X.XX | +X% | ? |
| name_compare_batch | 33.0 | X.XX | +X% | ? |
| name_depth | 1.7 | X.XX | +X% | ? |
| name_depth_batch | 1.7 | X.XX | +X% | ? |
| name_enclosure | 0.6 | X.XX | +X% | ? |
| name_from_enum | - | X.XX | - | ? |
| name_from_iterable | 12.0 | X.XX | +X% | ? |
| name_from_iterator | 12.0 | X.XX | +X% | ? |
| name_from_mapped_iterable | 12.0 | X.XX | +X% | ? |
| name_from_name | 4.2 | X.XX | +X% | ? |
| name_from_string | 3.0 | X.XX | +X% | ? |
| name_from_string_batch | 3.0 | X.XX | +X% | ? |
| name_interning_chained | 12.4 | X.XX | +X% | ? |
| name_interning_same_path | 3.5 | X.XX | +X% | ? |
| name_interning_segments | 10.1 | X.XX | +X% | ? |
| name_iterate_hierarchy | 1.8 | X.XX | +X% | ? |
| name_parsing | 1.9 | X.XX | +X% | ? |
| name_parsing_batch | 1.7 | X.XX | +X% | ? |
| name_path_generation | 31.3 | X.XX | +X% | ? |
| name_path_generation_batch | 30.0 | X.XX | +X% | ? |

**Summary:** X/23 Fullerstack wins, X/23 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
