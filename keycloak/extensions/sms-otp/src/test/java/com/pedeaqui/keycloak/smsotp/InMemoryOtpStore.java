package com.pedeaqui.keycloak.smsotp;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** {@link OtpStore} with per-entry expiry driven by a test clock (same semantics as the Keycloak store). */
final class InMemoryOtpStore implements OtpStore {
    private record Entry(Map<String, String> value, long expiresAt) {
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;

    InMemoryOtpStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean putIfAbsent(String key, long ttlSeconds) {
        if (get(key) != null) {
            return false;
        }
        entries.put(key, new Entry(Map.of(), now() + ttlSeconds));
        return true;
    }

    @Override
    public void put(String key, long ttlSeconds, Map<String, String> value) {
        entries.put(key, new Entry(Map.copyOf(value), now() + ttlSeconds));
    }

    @Override
    public Map<String, String> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.expiresAt() <= now()) {
            entries.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    Map<String, ?> raw() {
        return entries;
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}
