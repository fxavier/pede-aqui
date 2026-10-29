package com.pedeaqui.keycloak.smsotp;

import java.util.Map;

/**
 * Expiring key/value store for OTP counters, cooldowns and locks. Shared by every Keycloak node in production
 * ({@link KeycloakOtpStore} over the single-use-object cache); in-memory in unit tests. Keys never contain raw
 * phone numbers or IPs — {@link PhoneOtpEngine} HMACs them first.
 */
public interface OtpStore {
    /** Stores an empty marker under {@code key} if absent; true when this call created it (atomic). */
    boolean putIfAbsent(String key, long ttlSeconds);

    /** Stores (replacing) {@code value} under {@code key} for {@code ttlSeconds}. */
    void put(String key, long ttlSeconds, Map<String, String> value);

    /** The live value under {@code key}, or null. */
    Map<String, String> get(String key);

    /** Removes {@code key} if present. */
    void remove(String key);
}
