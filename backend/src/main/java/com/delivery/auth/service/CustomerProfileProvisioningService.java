package com.delivery.auth.service;

import com.delivery.auth.validation.PhoneNumberE164;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the customer's AppUserProfile in the resolved tenant, idempotently (ADR-0001 §2, AS-03).
 * Called at self-registration and, as a safety net, on every authenticated customer request. Concurrent
 * first requests are serialised by UNIQUE (tenant_id, keycloak_user_id): insert-or-select via
 * ON CONFLICT DO NOTHING, so the loser re-reads the winner's row instead of failing. Also mirrors the verified
 * phone of the Keycloak phone login (claims phone_number / phone_number_verified, LAC-18) onto the profile.
 */
@Service
public class CustomerProfileProvisioningService {
    private static final Logger log = LoggerFactory.getLogger(CustomerProfileProvisioningService.class);
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

    /** Outcome of {@link #syncVerifiedPhone}. */
    public enum PhoneSync {
        UPDATED, UNCHANGED, NOT_VERIFIED, INVALID, TAKEN_IN_TENANT, NO_PROFILE
    }

    /**
     * Stores the token's verified phone (E.164) on the profile (LAC-18). Only a claim with
     * {@code phone_number_verified=true} and a valid E.164 number is accepted; an unchanged number costs one SELECT.
     * A number already verified on another profile of the tenant is left alone (WARN without PII) instead of
     * failing the request. Deliberately not {@code @Transactional}: each statement auto-commits, so a unique-index
     * race can be caught without poisoning a surrounding transaction.
     */
    public PhoneSync syncVerifiedPhone(UUID tenantId, String keycloakUserId, String phoneClaim, Boolean phoneVerifiedClaim) {
        if (!Boolean.TRUE.equals(phoneVerifiedClaim)) {
            return PhoneSync.NOT_VERIFIED;
        }
        if (!PhoneNumberE164.isValid(phoneClaim)) {
            log.warn("Ignoring invalid phone_number claim on a customer token (not E.164)");
            return PhoneSync.INVALID;
        }
        List<String> current = jdbc.queryForList(
                "SELECT phone_e164 FROM app_user_profiles WHERE tenant_id = ? AND keycloak_user_id = ? AND status <> 'DELETED'",
                String.class, tenantId, keycloakUserId);
        if (current.isEmpty()) {
            return PhoneSync.NO_PROFILE;
        }
        if (Objects.equals(current.get(0), phoneClaim)) {
            return PhoneSync.UNCHANGED;
        }
        Timestamp now = Timestamp.from(Instant.now());
        try {
            int updated = jdbc.update("""
                    UPDATE app_user_profiles p
                       SET phone_e164 = ?, phone_verified_at = ?, updated_at = ?
                     WHERE p.tenant_id = ? AND p.keycloak_user_id = ?
                       AND p.status <> 'DELETED'
                       AND p.phone_e164 IS DISTINCT FROM ?
                       AND NOT EXISTS (SELECT 1 FROM app_user_profiles o
                                        WHERE o.tenant_id = p.tenant_id AND o.phone_e164 = ? AND o.id <> p.id)""",
                    phoneClaim, now, now, tenantId, keycloakUserId, phoneClaim, phoneClaim);
            if (updated == 1) {
                return PhoneSync.UPDATED;
            }
        } catch (DataIntegrityViolationException raceLost) {
            // Concurrent claim of the same number by another profile (uq_app_user_profiles_tenant_phone_e164).
        }
        log.warn("Verified phone {} already belongs to another customer profile of the tenant; not linked",
                PhoneNumberE164.mask(phoneClaim));
        return PhoneSync.TAKEN_IN_TENANT;
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
