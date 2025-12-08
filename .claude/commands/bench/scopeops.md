---
description: Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run ScopeOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run ScopeOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "ScopeOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| scope_child_anonymous | 3715.96 | X.XX | +X% | ? |
| scope_child_anonymous_batch | 3361.11 | X.XX | +X% | ? |
| scope_child_named | 1466.73 | X.XX | +X% | ? |
| scope_child_named_batch | 1627.99 | X.XX | +X% | ? |
| scope_close_idempotent | 1672.84 | X.XX | +X% | ? |
| scope_close_idempotent_batch | 1619.99 | X.XX | +X% | ? |
| scope_closure | 8568.26 | X.XX | +X% | ? |
| scope_closure_batch | 12864.19 | X.XX | +X% | ? |
| scope_complex | 23799.63 | X.XX | +X% | ? |
| scope_create_and_close | 2009.23 | X.XX | +X% | ? |
| scope_create_and_close_batch | 1832.09 | X.XX | +X% | ? |
| scope_create_named | 5.53 | X.XX | +X% | ? |
| scope_create_named_batch | 5.36 | X.XX | +X% | ? |
| scope_hierarchy | 6142.40 | X.XX | +X% | ? |
| scope_hierarchy_batch | 4959.23 | X.XX | +X% | ? |
| scope_parent_closes_children | 5554.29 | X.XX | +X% | ? |
| scope_parent_closes_children_batch | 6611.37 | X.XX | +X% | ? |
| scope_register_multiple | 37496.73 | X.XX | +X% | ? |
| scope_register_multiple_batch | 38748.93 | X.XX | +X% | ? |
| scope_register_single | 9123.96 | X.XX | +X% | ? |
| scope_register_single_batch | 8922.31 | X.XX | +X% | ? |
| scope_with_resources | 14810.70 | X.XX | +X% | ? |

**Summary:** X/22 Fullerstack wins, X/22 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
