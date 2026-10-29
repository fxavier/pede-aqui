package com.delivery.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** LAC-11 limiter semantics without Redis: local buckets and the Redis-down fallback. */
class RateLimiterTest {

    @Test
    void localBucketAllowsCapacityThenRejectsWithRetryAfter() {
        RateLimitPolicy.Rule rule = rule(3, Duration.ofMinutes(1));
        RateLimiter limiter = new RateLimiter(new RateLimitProperties(), null, new SimpleMeterRegistry());

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume(rule, "k").allowed()).isTrue();
        }
        RateLimiter.Decision rejected = limiter.tryConsume(rule, "k");

        assertThat(rejected.allowed()).isFalse();
        // Greedy refill: one token every 20 s.
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 20L);
        assertThat(limiter.tryConsume(rule, "other-key").allowed()).isTrue();
        assertThat(limiter.isDistributed()).isFalse();
    }

    @Test
    void unreachableRedisFallsBackToLocalBucketsAndCountsTheError() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRedisConnectTimeout(Duration.ofMillis(300));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimiter limiter = new RateLimiter(properties, RedisURI.create("127.0.0.1", closedPort), registry);
        RateLimitPolicy.Rule rule = rule(2, Duration.ofMinutes(1));
        try {
            assertThat(limiter.tryConsume(rule, "k").allowed()).isTrue();
            assertThat(limiter.tryConsume(rule, "k").allowed()).isTrue();
            // Not fail-open: the local bucket still enforces the limit.
            assertThat(limiter.tryConsume(rule, "k").allowed()).isFalse();
            assertThat(registry.get("rate.limit.backend.errors").counter().count()).isGreaterThanOrEqualTo(1.0);
        } finally {
            limiter.destroy();
        }
    }

    static RateLimitPolicy.Rule rule(long capacity, Duration period) {
        RateLimitProperties.Group group = new RateLimitProperties.Group();
        group.setPaths(List.of("/x"));
        group.setCapacity(capacity);
        group.setPeriod(period);
        return new RateLimitPolicy(Map.of("g", group)).resolve("GET", "/x").orElseThrow().rule();
    }
}
