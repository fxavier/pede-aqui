package com.delivery.auth.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the customer's AppUserProfile in the resolved tenant, idempotently (ADR-0001 §2, AS-03).
 * Called at self-registration and, as a safety net, on every authenticated customer request. Concurrent
 * first requests are serialised by UNIQUE (tenant_id, keycloak_user_id): insert-or-select via
 * ON CONFLICT DO NOTHING, so the loser re-reads the winner's row instead of failing.
 */
@Service
public class CustomerProfileProvisioningService {
    private static final int EMAIL_MAX = 200;
    private static final int DISPLAY_NAME_MAX = 160;

    private final JdbcTemplate jdbc;

    public CustomerProfileProvisioningService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the profile id of the customer in the tenant, creating it (role CUSTOMER) when missing. */
    @Transactional
    public UUID ensureCustomerProfile(UUID tenantId, String keycloakUserId, String email, String displayName) {
        Optional<UUID> existing = findProfileId(tenantId, keycloakUserId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String safeEmail = truncate(firstNonBlank(email, keycloakUserId), EMAIL_MAX);
        String safeName = truncate(firstNonBlank(displayName, email, keycloakUserId), DISPLAY_NAME_MAX);
        Timestamp now = Timestamp.from(Instant.now());
        UUID candidateId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status,
                                               preferred_language, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'pt', ?, ?)
                ON CONFLICT (tenant_id, keycloak_user_id) DO NOTHING""",
                candidateId, tenantId, keycloakUserId, safeEmail, safeName, now, now);
        if (inserted == 1) {
            jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'CUSTOMER') ON CONFLICT DO NOTHING",
                    candidateId);
            return candidateId;
        }
        // Lost the race: the concurrent winner has committed (the unique index made us wait for it).
        return findProfileId(tenantId, keycloakUserId)
                .orElseThrow(() -> new IllegalStateException("Customer profile vanished after a concurrent insert"));
    }

    private Optional<UUID> findProfileId(UUID tenantId, String keycloakUserId) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM app_user_profiles WHERE tenant_id = ? AND keycloak_user_id = ?",
                UUID.class, tenantId, keycloakUserId);
        return ids.stream().findFirst();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
