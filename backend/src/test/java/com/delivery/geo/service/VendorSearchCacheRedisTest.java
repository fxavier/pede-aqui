package com.delivery.geo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.common.config.RedisConfig;
import com.delivery.geo.dto.SearchVendorResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * LAC-51 against a real Redis: entries round-trip through the JSON serializer as SearchVendorResponse records,
 * identical queries hit, a different filter misses, and vendorSearch entries carry the 60 s TTL.
 */
@SpringBootTest(classes = {RedisConfig.class, VendorSearchCache.class}, properties = "spring.cache.type=redis")
@ImportAutoConfiguration({RedisAutoConfiguration.class, CacheAutoConfiguration.class})
class VendorSearchCacheRedisTest {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired VendorSearchCache cache;
    @Autowired CacheManager cacheManager;
    @Autowired StringRedisTemplate redis;

    @BeforeAll
    static void requiresDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Skipping Redis cache test: no Docker environment");
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void clear() {
        cacheManager.getCache(VendorSearchCache.CACHE_NAME).clear();
    }

    @Test
    void identicalQueryIsServedFromRedisAndRoundTripsAsRecords() {
        AtomicInteger loads = new AtomicInteger();
        VendorSearchQuery query = query(null);
        SearchVendorResponse row = row();

        List<SearchVendorResponse> first = cache.results(query, () -> { loads.incrementAndGet(); return List.of(row); });
        List<SearchVendorResponse> second = cache.results(query, () -> { loads.incrementAndGet(); return List.of(row); });

        assertThat(loads).hasValue(1);
        assertThat(first).containsExactly(row);
        assertThat(second).containsExactly(row); // deserialized from Redis JSON, not a LinkedHashMap
        assertThat(second.get(0)).isInstanceOf(SearchVendorResponse.class);
    }

    @Test
    void differentMinRatingMisses() {
        AtomicInteger loads = new AtomicInteger();
        cache.results(query(null), () -> { loads.incrementAndGet(); return List.of(row()); });
        cache.results(query(4.0), () -> { loads.incrementAndGet(); return List.of(); });

        assertThat(loads).hasValue(2);
    }

    @Test
    void vendorSearchEntriesExpireAfterSixtySeconds() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        assertThat(((RedisCacheManager) cacheManager).getCacheConfigurations().get(VendorSearchCache.CACHE_NAME)
                .getTtlFunction().getTimeToLive(null, null)).isEqualTo(Duration.ofSeconds(60));

        VendorSearchQuery query = query(null);
        cache.results(query, () -> List.of(row()));
        Long ttl = redis.getExpire(VendorSearchCache.CACHE_NAME + "::" + query.cacheKey());
        assertThat(ttl).isBetween(1L, 60L);
    }

    private static VendorSearchQuery query(Double minRating) {
        return VendorSearchQuery.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                -25.96512, 32.58321, 5000, null, minRating, null, "relevance");
    }

    private static SearchVendorResponse row() {
        return new SearchVendorResponse(UUID.fromString("00000000-0000-0000-0000-0000000000b2"), "Loja", 1.25, 1250,
                true, 4.5, 30, new BigDecimal("75.50"), "DISTANCE_RULE", false, null);
    }
}
