package io.fullerstack.substrates.serventis;

import io.humainary.substrates.api.Substrates.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.humainary.substrates.api.Substrates.cortex;
import static io.humainary.substrates.ext.serventis.ext.Caches.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.humainary.substrates.ext.serventis.ext.Caches;

/**
 * Demonstration of the Caches API (RC6) - Cache operation observability.
 * <p>
 * Caches API tracks cache lifecycle: lookups, hits, misses, storage, eviction.
 * <p>
 * Cache Signs (7):
 * - LOOKUP: Cache lookup attempted
 * - HIT: Value found in cache
 * - MISS: Value not found in cache
 * - STORE: Value stored in cache
 * - EVICT: Value evicted from cache
 * - EXPIRE: Cached value expired
 * - REMOVE: Value explicitly removed
 * <p>
 * Kafka Use Cases:
 * - Metadata cache (topic/partition metadata)
 * - Consumer group coordinator cache
 * - Producer partition cache
 * - Broker connection cache
 */
@DisplayName("Caches API (RC6) - Cache Operation Observability")
class CachesApiDemoTest {

    private Circuit circuit;
    private Conduit<Cache, Sign> caches;

    @BeforeEach
    void setUp() {
        circuit = cortex().circuit(cortex().name("caches-demo"));
        caches = circuit.conduit(
            cortex().name("caches"),
            Caches::composer
        );
    }

    @AfterEach
    void tearDown() {
        if (circuit != null) {
            circuit.close();
        }
    }

    @Test
    @DisplayName("Cache hit: LOOKUP → HIT")
    void cacheHit() {
        Cache metadataCache = caches.percept(cortex().name("topic.metadata"));

        List<Sign> events = new ArrayList<>();
        caches.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        metadataCache.lookup();  // Check cache
        metadataCache.hit();     // Found in cache

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.LOOKUP,
            Sign.HIT
        );
    }

    @Test
    @DisplayName("Cache miss and store: LOOKUP → MISS → STORE")
    void cacheMissAndStore() {
        Cache cache = caches.percept(cortex().name("partition.leader"));

        List<Sign> events = new ArrayList<>();
        caches.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        cache.lookup();  // Check cache
        cache.miss();    // Not found
        cache.store();   // Fetch and store

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.LOOKUP,
            Sign.MISS,
            Sign.STORE
        );
    }

    @Test
    @DisplayName("Cache eviction")
    void cacheEviction() {
        Cache cache = caches.percept(cortex().name("connection.cache"));

        List<Sign> events = new ArrayList<>();
        caches.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        cache.store();  // Add to cache
        cache.evict();  // Evicted due to size limit

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.STORE,
            Sign.EVICT
        );
    }

    @Test
    @DisplayName("Cache expiration")
    void cacheExpiration() {
        Cache cache = caches.percept(cortex().name("session.cache"));

        List<Sign> events = new ArrayList<>();
        caches.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        cache.store();   // Store with TTL
        cache.lookup();  // Later lookup
        cache.expire();  // TTL expired

        circuit.await();

        // ASSERT
        assertThat(events).contains(Sign.EXPIRE);
    }

    @Test
    @DisplayName("Cache invalidation")
    void cacheInvalidation() {
        Cache cache = caches.percept(cortex().name("config.cache"));

        List<Sign> events = new ArrayList<>();
        caches.subscribe(cortex().subscriber(
            cortex().name("receptor"),
            (subject, registrar) -> {
                registrar.register(events::add);
            }
        ));

        // ACT
        cache.store();   // Cache config
        cache.remove();  // Explicitly invalidate

        circuit.await();

        // ASSERT
        assertThat(events).containsExactly(
            Sign.STORE,
            Sign.REMOVE
        );
    }

    @Test
    @DisplayName("All 7 signs available")
    void allSignsAvailable() {
        Cache cache = caches.percept(cortex().name("test-cache"));

        // ACT
        cache.lookup();
        cache.hit();
        cache.miss();
        cache.store();
        cache.evict();
        cache.expire();
        cache.remove();

        circuit.await();

        // ASSERT
        Sign[] allSigns = Sign.values();
        assertThat(allSigns).hasSize(7);
        assertThat(allSigns).contains(
            Sign.LOOKUP,
            Sign.HIT,
            Sign.MISS,
            Sign.STORE,
            Sign.EVICT,
            Sign.EXPIRE,
            Sign.REMOVE
        );
    }
}
