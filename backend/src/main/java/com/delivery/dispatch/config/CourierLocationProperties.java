package com.delivery.dispatch.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.courier-location.*} (LAC-40): GPS consent version, ingestion limits, Redis presence and route
 * history retention. The presence sweeper (LAC-42) reads {@code app.courier-presence.*} itself.
 */
@Component
@ConfigurationProperties(prefix = "app.courier-location")
public class CourierLocationProperties {
    /** Version of the consent text couriers must have accepted; bump it to require a new consent. */
    private String consentVersion = "2026-10-v1";
    /** Maximum points per POST /couriers/me/locations batch. */
    private int maxBatchSize = 50;
    /** Points less accurate than this (metres) are dropped. */
    private double maxAccuracyMeters = 100;
    /** Implausible movement between consecutive points (km/h) is dropped. */
    private double maxSpeedKmh = 120;
    /** Points recorded longer ago than this are dropped (buffered offline points). */
    private Duration maxPointAge = Duration.ofMinutes(5);
    /** Points recorded further in the future than this (client clock skew) are dropped. */
    private Duration maxClockSkew = Duration.ofMinutes(5);
    /** Write latest positions / presence to Redis (GEO set + TTL hash). Off in the test suite. */
    private boolean redisEnabled = true;
    /** TTL of the Redis presence hash courier:{id}:last. */
    private Duration presenceTtl = Duration.ofMinutes(2);
    /** Route history retention (by received_at). */
    private Duration historyRetention = Duration.ofDays(30);
    /** Rows deleted per retention batch, and the maximum batches per run. */
    private int retentionBatchSize = 5000;
    private int retentionMaxBatches = 200;

    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }
    public double getMaxAccuracyMeters() { return maxAccuracyMeters; }
    public void setMaxAccuracyMeters(double maxAccuracyMeters) { this.maxAccuracyMeters = maxAccuracyMeters; }
    public double getMaxSpeedKmh() { return maxSpeedKmh; }
    public void setMaxSpeedKmh(double maxSpeedKmh) { this.maxSpeedKmh = maxSpeedKmh; }
    public Duration getMaxPointAge() { return maxPointAge; }
    public void setMaxPointAge(Duration maxPointAge) { this.maxPointAge = maxPointAge; }
    public Duration getMaxClockSkew() { return maxClockSkew; }
    public void setMaxClockSkew(Duration maxClockSkew) { this.maxClockSkew = maxClockSkew; }
    public boolean isRedisEnabled() { return redisEnabled; }
    public void setRedisEnabled(boolean redisEnabled) { this.redisEnabled = redisEnabled; }
    public Duration getPresenceTtl() { return presenceTtl; }
    public void setPresenceTtl(Duration presenceTtl) { this.presenceTtl = presenceTtl; }
    public Duration getHistoryRetention() { return historyRetention; }
    public void setHistoryRetention(Duration historyRetention) { this.historyRetention = historyRetention; }
    public int getRetentionBatchSize() { return retentionBatchSize; }
    public void setRetentionBatchSize(int retentionBatchSize) { this.retentionBatchSize = retentionBatchSize; }
    public int getRetentionMaxBatches() { return retentionMaxBatches; }
    public void setRetentionMaxBatches(int retentionMaxBatches) { this.retentionMaxBatches = retentionMaxBatches; }
}
