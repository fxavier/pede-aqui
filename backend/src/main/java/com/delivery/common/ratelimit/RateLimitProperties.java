package com.delivery.common.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit settings bound from {@code app.rate-limit.*} (LAC-11). Each entry of {@code groups} is a route group
 * with its own token bucket (capacity tokens, refilled greedily over {@code period}); the defaults live in
 * application.yml. A map (not a list) so a single value can be overridden per environment, e.g.
 * {@code APP_RATE_LIMIT_GROUPS_CHECKOUT_CAPACITY=20}.
 */
@ConfigurationProperties("app.rate-limit")
public class RateLimitProperties {

    /** Where the buckets live. */
    public enum Backend {
        /** Shared across instances through Redis (Bucket4j + Lettuce); local buckets only while Redis is down. */
        REDIS,
        /** Per-instance in-memory buckets (tests, single-node dev without Redis). */
        LOCAL
    }

    private boolean enabled = true;
    private Backend backend = Backend.REDIS;
    /** Command and Bucket4j request timeout for Redis calls; keep it small, it is on every limited request. */
    private Duration redisTimeout = Duration.ofMillis(500);
    private Duration redisConnectTimeout = Duration.ofSeconds(2);
    /** Minimum wait between two connection attempts while Redis is unreachable. */
    private Duration reconnectBackoff = Duration.ofSeconds(5);
    private String keyPrefix = "rl";
    /** Upper bound on in-memory buckets (local backend / Redis fallback); the map is reset when exceeded. */
    private int localMaxKeys = 100_000;
    /**
     * Reverse proxies / load balancers (IP or CIDR) allowed to set X-Forwarded-For. Empty (default): the header is
     * ignored and the client IP is always {@code request.getRemoteAddr()}.
     */
    private List<String> trustedProxies = new ArrayList<>();
    private Map<String, Group> groups = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public Duration getRedisTimeout() {
        return redisTimeout;
    }

    public void setRedisTimeout(Duration redisTimeout) {
        this.redisTimeout = redisTimeout;
    }

    public Duration getRedisConnectTimeout() {
        return redisConnectTimeout;
    }

    public void setRedisConnectTimeout(Duration redisConnectTimeout) {
        this.redisConnectTimeout = redisConnectTimeout;
    }

    public Duration getReconnectBackoff() {
        return reconnectBackoff;
    }

    public void setReconnectBackoff(Duration reconnectBackoff) {
        this.reconnectBackoff = reconnectBackoff;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getLocalMaxKeys() {
        return localMaxKeys;
    }

    public void setLocalMaxKeys(int localMaxKeys) {
        this.localMaxKeys = localMaxKeys;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public Map<String, Group> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, Group> groups) {
        this.groups = groups;
    }

    /** One route group: which requests it covers and its bucket size. */
    public static class Group {
        /** Spring path patterns (PathPattern syntax, relative to the context path), e.g. /api/v1/deliveries/{@literal *}/complete. */
        private List<String> paths = new ArrayList<>();
        /** HTTP methods covered; empty = every method. */
        private List<String> methods = new ArrayList<>();
        private long capacity;
        private Duration period = Duration.ofMinutes(1);

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }
    }
}
