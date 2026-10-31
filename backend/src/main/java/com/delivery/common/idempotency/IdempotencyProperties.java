package com.delivery.common.idempotency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code app.idempotency.*} (LAC-24): covered endpoints, retention and size bounds of the generic Idempotency-Key layer. */
@Component
@ConfigurationProperties(prefix = "app.idempotency")
public class IdempotencyProperties {
    private boolean enabled = true;
    /** {@code "METHOD /ant/path/pattern"} entries; only these honour the Idempotency-Key header. */
    private List<String> endpoints = new ArrayList<>();
    /** How long a key is remembered (and its response replayed). */
    private Duration ttl = Duration.ofHours(24);
    /** An IN_PROGRESS record older than this is considered abandoned (crashed instance) and may be taken over. */
    private Duration inProgressLease = Duration.ofMinutes(2);
    /** Requests with a larger body bypass the layer (money endpoints have tiny bodies). */
    private int maxRequestBytes = 65_536;
    /** 2xx responses larger than this are not stored (the key is released). */
    private int maxResponseBytes = 65_536;
    /** Rows deleted per cleanup batch / batches per run. */
    private int cleanupBatchSize = 5_000;
    private int cleanupMaxBatches = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getInProgressLease() { return inProgressLease; }
    public void setInProgressLease(Duration inProgressLease) { this.inProgressLease = inProgressLease; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public void setMaxRequestBytes(int maxRequestBytes) { this.maxRequestBytes = maxRequestBytes; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    public int getCleanupMaxBatches() { return cleanupMaxBatches; }
    public void setCleanupMaxBatches(int cleanupMaxBatches) { this.cleanupMaxBatches = cleanupMaxBatches; }
}
