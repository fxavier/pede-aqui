package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.dispatch.config.CourierLocationProperties;
import com.delivery.dispatch.service.CourierPresenceStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * LAC-40 against a real Redis: a recorded position lands in the tenant's GEO set (searchable by radius) and in a
 * presence hash with the configured TTL; removal drops both. Outside a transaction the writes are immediate.
 */
@SpringBootTest(classes = {CourierPresenceStore.class, CourierLocationProperties.class},
        properties = {"app.courier-location.redis-enabled=true", "app.courier-location.presence-ttl=PT2M"})
@ImportAutoConfiguration({RedisAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class})
class CourierPresenceStoreRedisTest {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Autowired CourierPresenceStore store;
    @Autowired StringRedisTemplate redis;

    @BeforeAll
    static void requiresDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Skipping Redis presence test: no Docker environment");
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

    @Test
    void recordedPositionIsInTheGeoSetAndThePresenceHashExpires() {
        UUID tenant = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        UUID farAway = UUID.randomUUID();

        store.recordPosition(tenant, courier, -25.9692, 32.5732, Instant.parse("2026-10-29T10:00:00Z"));
        store.recordPosition(tenant, farAway, -19.8436, 34.8389, Instant.parse("2026-10-29T10:00:00Z")); // Beira

        var near = redis.opsForGeo().radius(CourierPresenceStore.geoKey(tenant),
                new org.springframework.data.geo.Circle(new Point(32.58, -25.97), new Distance(5, Metrics.KILOMETERS)));
        assertThat(near.getContent()).extracting(r -> r.getContent().getName()).containsExactly(courier.toString());

        String key = CourierPresenceStore.presenceKey(courier);
        assertThat(redis.opsForHash().get(key, "tenantId")).isEqualTo(tenant.toString());
        assertThat(redis.opsForHash().get(key, "recordedAt")).isEqualTo("2026-10-29T10:00:00Z");
        Long ttl = redis.getExpire(key);
        assertThat(ttl).isBetween(Duration.ofSeconds(100).toSeconds(), Duration.ofMinutes(2).toSeconds());

        store.remove(tenant, courier);
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(redis.opsForGeo().position(CourierPresenceStore.geoKey(tenant), courier.toString()))
                .isEqualTo(java.util.Arrays.asList((Point) null));
        assertThat(redis.opsForGeo().position(CourierPresenceStore.geoKey(tenant), farAway.toString())).hasSize(1).doesNotContainNull();
    }
}
