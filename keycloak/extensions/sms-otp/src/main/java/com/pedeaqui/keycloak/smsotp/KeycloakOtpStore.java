package com.pedeaqui.keycloak.smsotp;

import java.util.Map;
import org.keycloak.models.SingleUseObjectProvider;

/**
 * {@link OtpStore} over Keycloak's single-use-object store (the replicated cache that also holds action-token
 * markers), so counters, cooldowns and locks are shared by every Keycloak node and expire by themselves.
 */
public class KeycloakOtpStore implements OtpStore {
    private final SingleUseObjectProvider objects;

    public KeycloakOtpStore(SingleUseObjectProvider objects) {
        this.objects = objects;
    }

    @Override
    public boolean putIfAbsent(String key, long ttlSeconds) {
        return objects.putIfAbsent(key, ttlSeconds);
    }

    @Override
    public void put(String key, long ttlSeconds, Map<String, String> value) {
        objects.put(key, ttlSeconds, value);
    }

    @Override
    public Map<String, String> get(String key) {
        return objects.get(key);
    }

    @Override
    public void remove(String key) {
        objects.remove(key);
    }
}
