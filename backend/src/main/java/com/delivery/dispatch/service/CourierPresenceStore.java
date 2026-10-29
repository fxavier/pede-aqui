package com.delivery.dispatch.service;

import com.delivery.dispatch.config.CourierLocationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis view of on-duty couriers (LAC-40): a GEO set {@code couriers:geo:<tenantId>} with the latest position per
 * courier (fast candidate search for dispatch, LAC-41) and a presence hash {@code courier:<courierId>:last} that
 * expires after {@code app.courier-location.presence-ttl}. PostgreSQL ({@code courier_last_locations}) stays the source
 * of truth: writes happen after the database commit and every Redis failure is logged (throttled), counted
 * ({@code courier_presence_redis_errors_total}) and ignored. Disabled with {@code app.courier-location.redis-enabled=false}.
 */
@Component
public class CourierPresenceStore {
    private static final Logger log = LoggerFactory.getLogger(CourierPresenceStore.class);
    private static final long WARN_EVERY_MS = 30_000;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final CourierLocationProperties properties;
    private final ObjectProvider<MeterRegistry> meterRegistry;
    private final AtomicLong lastWarn = new AtomicLong();

    public CourierPresenceStore(ObjectProvider<StringRedisTemplate> redisProvider, CourierLocationProperties properties,
                                ObjectProvider<MeterRegistry> meterRegistry) {
        this.redisProvider = redisProvider;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /** Redis key of the tenant's GEO set. */
    public static String geoKey(UUID tenantId) {
        return "couriers:geo:" + tenantId;
    }

    /** Redis key of the courier's presence hash. */
    public static String presenceKey(UUID courierId) {
        return "courier:" + courierId + ":last";
    }

    /** After commit: stores the latest position in the GEO set and refreshes the presence hash TTL. */
    public void recordPosition(UUID tenantId, UUID courierId, double latitude, double longitude, Instant recordedAt) {
        afterCommit(redis -> {
            redis.opsForGeo().add(geoKey(tenantId), new Point(longitude, latitude), courierId.toString());
            String key = presenceKey(courierId);
            redis.opsForHash().putAll(key, Map.of(
                    "tenantId", tenantId.toString(),
                    "lat", Double.toString(latitude),
                    "lng", Double.toString(longitude),
                    "recordedAt", recordedAt.toString()));
            redis.expire(key, properties.getPresenceTtl());
        });
    }

    /** After commit: removes the courier from the GEO set and drops the presence hash (courier went off duty). */
    public void remove(UUID tenantId, UUID courierId) {
        afterCommit(redis -> {
            redis.opsForGeo().remove(geoKey(tenantId), courierId.toString());
            redis.delete(presenceKey(courierId));
        });
    }

    private void afterCommit(java.util.function.Consumer<StringRedisTemplate> action) {
        if (!properties.isRedisEnabled()) {
            return;
        }
        Runnable run = () -> {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return;
            }
            try {
                action.accept(redis);
            } catch (RuntimeException ex) {
                onError(ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    run.run();
                }
            });
        } else {
            run.run();
        }
    }

    private void onError(RuntimeException ex) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry != null) {
            registry.counter("courier.presence.redis.errors").increment();
        }
        long now = System.currentTimeMillis();
        long previous = lastWarn.get();
        if (now - previous >= WARN_EVERY_MS && lastWarn.compareAndSet(previous, now)) {
            log.warn("Courier presence write to Redis failed; continuing with PostgreSQL only: {}", ex.getClass().getSimpleName());
        }
    }
}
