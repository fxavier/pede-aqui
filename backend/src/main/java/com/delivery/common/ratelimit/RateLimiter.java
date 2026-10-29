package com.delivery.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

/**
 * Token-bucket rate limiter (LAC-11). With a Redis URI the buckets live in Redis through Bucket4j's Lettuce
 * compare-and-swap proxy, so every instance shares them; without one (backend=local) they are in-memory.
 *
 * <p>Redis outage policy: <b>fall back to a per-instance local bucket</b> (not fail-open). The limited routes
 * include brute-force targets (delivery PIN, registration); failing open would remove the protection exactly when an
 * attacker can degrade Redis. The cost is that, while Redis is down, the effective limit is the configured one
 * times the number of instances. Each Redis failure increments {@code rate_limit_backend_errors_total} and logs a
 * throttled WARN; the connection is retried lazily (at most every {@code reconnect-backoff}).
 */
public class RateLimiter implements InitializingBean, DisposableBean {

    /** Outcome of one consumption attempt. */
    public record Decision(boolean allowed, long remainingTokens, long retryAfterSeconds) {
    }

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final RateLimitProperties properties;
    @Nullable private final RedisClient redisClient;
    private final Counter backendErrors;
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    @Nullable private volatile StatefulRedisConnection<String, byte[]> connection;
    @Nullable private volatile ProxyManager<String> proxyManager;
    private volatile long nextConnectAttemptNanos;
    private volatile long nextWarnNanos;

    /**
     * @param redisUri where the shared buckets live; null = local buckets only.
     */
    public RateLimiter(RateLimitProperties properties, @Nullable RedisURI redisUri, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.backendErrors = Counter.builder("rate.limit.backend.errors")
                .description("Rate-limit checks that could not reach Redis and used a local bucket instead")
                .register(meterRegistry);
        if (redisUri == null) {
            this.redisClient = null;
            return;
        }
        redisUri.setTimeout(properties.getRedisTimeout());
        RedisClient client = RedisClient.create(redisUri);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(properties.getRedisConnectTimeout()).build())
                // While disconnected, fail immediately (→ local fallback) instead of queueing until the timeout.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
        this.redisClient = client;
    }

    /**
     * Connects at startup (non-fatal) so the first limited request does not pay connection set-up; when Redis is
     * not reachable yet the limiter starts on local buckets and retries lazily.
     */
    @Override
    public void afterPropertiesSet() {
        redisProxy();
    }

    /** True when buckets are shared through Redis (whether or not Redis is currently reachable). */
    public boolean isDistributed() {
        return redisClient != null;
    }

    /** Takes one token from the bucket {@code key} configured by {@code rule}. */
    public Decision tryConsume(RateLimitPolicy.Rule rule, String key) {
        ProxyManager<String> redis = redisProxy();
        if (redis != null) {
            try {
                return decision(redis.builder().build(key, rule::bucket).tryConsumeAndReturnRemaining(1));
            } catch (RuntimeException e) {
                onBackendError(e);
            }
        }
        return decision(localBucket(rule, key).tryConsumeAndReturnRemaining(1));
    }

    private Bucket localBucket(RateLimitPolicy.Rule rule, String key) {
        if (localBuckets.size() >= properties.getLocalMaxKeys()) {
            // Crude bound against key-space flooding (e.g. rotating IPs): forget every local bucket at once.
            localBuckets.clear();
        }
        return localBuckets.computeIfAbsent(key, ignored -> Bucket.builder()
                .addLimit(rule.bucket().getBandwidths()[0])
                .build());
    }

    @Nullable
    private ProxyManager<String> redisProxy() {
        if (redisClient == null) {
            return null;
        }
        ProxyManager<String> current = proxyManager;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (proxyManager != null) {
                return proxyManager;
            }
            if (System.nanoTime() - nextConnectAttemptNanos < 0) {
                return null;
            }
            try {
                StatefulRedisConnection<String, byte[]> conn =
                        redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                connection = conn;
                proxyManager = Bucket4jLettuce.casBasedBuilder(conn)
                        .requestTimeout(properties.getRedisTimeout())
                        // Keys disappear once the bucket would be full again: no unbounded growth in Redis.
                        .expirationAfterWrite(
                                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(5)))
                        .build();
                log.info("Rate limiter connected to Redis; buckets are shared across instances");
                return proxyManager;
            } catch (RuntimeException e) {
                nextConnectAttemptNanos = System.nanoTime() + properties.getReconnectBackoff().toNanos();
                onBackendError(e);
                return null;
            }
        }
    }

    private void onBackendError(RuntimeException e) {
        backendErrors.increment();
        long now = System.nanoTime();
        if (now - nextWarnNanos >= 0) {
            nextWarnNanos = now + WARN_INTERVAL_NANOS;
            log.warn("Rate limiter cannot use Redis ({}); falling back to per-instance buckets", e.toString());
        }
    }

    private static Decision decision(ConsumptionProbe probe) {
        long retryAfter = probe.isConsumed()
                ? 0
                : Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        return new Decision(probe.isConsumed(), probe.getRemainingTokens(), retryAfter);
    }

    @Override
    public void destroy() {
        StatefulRedisConnection<String, byte[]> conn = connection;
        if (conn != null) {
            conn.close();
        }
        if (redisClient != null) {
            redisClient.shutdown(Duration.ZERO, Duration.ofSeconds(2));
        }
    }
}
