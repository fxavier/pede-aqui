package com.delivery.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * LAC-11 acceptance: two application contexts (two "instances", each with its own RateLimiter and Redis connection)
 * pointed at the same Redis share one bucket per key, and bucket keys carry a TTL so Redis does not grow unbounded.
 */
class RateLimiterRedisTest {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static ConfigurableApplicationContext instanceA;
    private static ConfigurableApplicationContext instanceB;

    /** Just the rate-limit wiring plus Spring Boot's Redis connection details. */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(RedisAutoConfiguration.class)
    @Import(RateLimitConfig.class)
    static class LimiterInstance {
    }

    @BeforeAll
    static void startInstances() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Skipping Redis rate-limit test: no Docker environment");
        REDIS.start();
        instanceA = start();
        instanceB = start();
    }

    @AfterAll
    static void stopInstances() {
        if (instanceA != null) {
            instanceA.close();
        }
        if (instanceB != null) {
            instanceB.close();
        }
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    private static ConfigurableApplicationContext start() {
        // Command-line args outrank the test application.yml, which forces backend=local for the rest of the suite.
        return new SpringApplicationBuilder(LimiterInstance.class)
                .web(WebApplicationType.NONE)
                .run("--app.rate-limit.backend=redis",
                        // Generous timeouts: a cold JVM/Netty on a busy CI box must not flip the test to local buckets.
                        "--app.rate-limit.redis-timeout=5s",
                        "--app.rate-limit.redis-connect-timeout=10s",
                        "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                        "--spring.main.banner-mode=off");
    }

    @Test
    void twoInstancesShareTheSameBucket() {
        RateLimiter limiterA = instanceA.getBean(RateLimiter.class);
        RateLimiter limiterB = instanceB.getBean(RateLimiter.class);
        assertThat(limiterA).isNotSameAs(limiterB);
        assertThat(limiterA.isDistributed()).isTrue();
        assertThat(limiterB.isDistributed()).isTrue();

        // Shipped registration group: 5 per 15 min.
        RateLimitPolicy.Rule rule = instanceA.getBean(RateLimitPolicy.class)
                .resolve("POST", "/api/v1/register").orElseThrow().rule();
        assertThat(rule.capacity()).isEqualTo(5);
        String key = RateLimitInterceptor.bucketKey("rl", rule.group(), "POST", "/api/v1/register", "ip:203.0.113.9");

        for (int i = 0; i < 3; i++) {
            assertThat(limiterA.tryConsume(rule, key).allowed()).isTrue();
        }
        assertThat(limiterB.tryConsume(rule, key).allowed()).isTrue();
        assertThat(limiterB.tryConsume(rule, key).allowed()).isTrue();

        // 5 tokens spent across the two instances: both now reject, with a Retry-After hint.
        RateLimiter.Decision onA = limiterA.tryConsume(rule, key);
        RateLimiter.Decision onB = limiterB.tryConsume(rule, key);
        assertThat(onA.allowed()).isFalse();
        assertThat(onB.allowed()).isFalse();
        assertThat(onB.retryAfterSeconds()).isBetween(1L, 180L);

        // A different client keeps its own bucket.
        String otherKey = RateLimitInterceptor.bucketKey("rl", rule.group(), "POST", "/api/v1/register", "ip:198.51.100.1");
        assertThat(limiterB.tryConsume(rule, otherKey).allowed()).isTrue();
    }

    @Test
    void bucketKeysLiveInRedisWithAnExpiry() {
        RateLimiter limiter = instanceA.getBean(RateLimiter.class);
        RateLimitPolicy.Rule rule = instanceA.getBean(RateLimitPolicy.class)
                .resolve("POST", "/api/v1/checkout").orElseThrow().rule();
        String key = RateLimitInterceptor.bucketKey("rl", rule.group(), "POST", "/api/v1/checkout", "sub:ttl-user");

        assertThat(limiter.tryConsume(rule, key).allowed()).isTrue();

        StringRedisTemplate redis = instanceA.getBean(StringRedisTemplate.class);
        Set<String> keys = redis.keys("rl:checkout:*");
        assertThat(keys).contains(key);
        assertThat(redis.getExpire(key)).isPositive();
    }
}
