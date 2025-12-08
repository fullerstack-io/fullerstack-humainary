---
description: Run SubscriberOps JMH benchmarks comparing Fullerstack vs Humainary
---

Run SubscriberOps JMH benchmarks comparing Fullerstack vs Humainary implementation.

## Steps

1. Build everything and run SubscriberOps benchmarks:
```bash
source /usr/local/sdkman/bin/sdkman-init.sh && sdk use java 25.0.1-open && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/fullerstack-substrates/pom.xml clean install -DskipTests -q && \
mvn -f /workspaces/fullerstack-humainary/substrates-api-java/jmh/pom.xml clean package -DskipTests -q \
  -Dsubstrates.spi.groupId=io.fullerstack \
  -Dsubstrates.spi.artifactId=fullerstack-substrates \
  -Dsubstrates.spi.version=1.0.0-SNAPSHOT && \
java --enable-preview -jar /workspaces/fullerstack-humainary/substrates-api-java/jmh/target/humainary-substrates-jmh-1.0.0-PREVIEW-jar-with-dependencies.jar \
  -f 1 -wi 2 -i 3 -t 1 "SubscriberOps" 2>&1
```

2. Present results in this side-by-side comparison table:

| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |
|-----------|----------------|------------------|------|--------|
| close_five_conduits_await | 8696.40 | X.XX | +X% | ? |
| close_five_subscriptions_await | 8630.90 | X.XX | +X% | ? |
| close_idempotent_await | 8438.48 | X.XX | +X% | ? |
| close_idempotent_batch_await | 17.23 | X.XX | +X% | ? |
| close_no_subscriptions_await | 8450.16 | X.XX | +X% | ? |
| close_no_subscriptions_batch_await | 14.24 | X.XX | +X% | ? |
| close_one_subscription_await | 8437.66 | X.XX | +X% | ? |
| close_one_subscription_batch_await | 34.88 | X.XX | +X% | ? |
| close_ten_conduits_await | 8514.56 | X.XX | +X% | ? |
| close_ten_subscriptions_await | 8726.96 | X.XX | +X% | ? |
| close_with_pending_emissions_await | 8713.31 | X.XX | +X% | ? |

**Summary:** X/11 Fullerstack wins, X/11 Humainary wins

Replace X.XX with actual Fullerstack results. Calculate Diff as ((Fullerstack - Humainary) / Humainary * 100). Winner is whichever has lower time (faster).
