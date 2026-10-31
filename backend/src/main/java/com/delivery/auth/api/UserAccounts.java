package com.delivery.auth.api;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.MarketplaceRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity facade of the auth module (ADR-0002 C8/C9): the Keycloak user and app-user-profile operations that tenant
 * onboarding and subscription provisioning need, so those flows live in the tenant module without reaching into the
 * auth repositories, entities or Keycloak client. Thin pass-through: exceptions of the underlying calls (for example
 * {@code email_exists}) propagate unchanged and no transaction is opened here (callers own it).
 */
@Service
public class UserAccounts {
    /** Profile status written by {@link #pseudonymise} (W7-C); such a profile is never re-provisioned or re-linked. */
    public static final String STATUS_DELETED = "DELETED";
    /** Display name that replaces the customer's name on pseudonymisation. */
    public static final String PSEUDONYMISED_DISPLAY_NAME = "Cliente removido";

    private final AppUserProfileRepository profileRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbc;

    public UserAccounts(AppUserProfileRepository profileRepository, KeycloakAdminService keycloakAdminService, JdbcTemplate jdbc) {
        this.profileRepository = profileRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.jdbc = jdbc;
    }

    /**
     * The profile as its owner sees it (self-service settings and personal-data export, W7-C). Contains personal data:
     * only ever return it to the subject itself.
     */
    public record SelfProfile(UUID profileId, UUID tenantId, String keycloakUserId, String email, String displayName,
                              String phone, String phoneE164, Instant phoneVerifiedAt, String fullName, String nif,
                              LocalDate dateOfBirth, String address, String preferredLanguage, String status,
                              Instant createdAt, Instant updatedAt) {}

    /** The caller's own profile in the tenant, if it exists. */
    @Transactional(readOnly = true)
    public Optional<SelfProfile> findSelf(UUID tenantId, String keycloakUserId) {
        return profileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId).map(UserAccounts::toSelf);
    }

    /**
     * Applies a self-service change (display name and/or preferred locale; null = unchanged) and mirrors it to the
     * Keycloak user (first/last name, {@code locale} attribute) inside the same transaction: a Keycloak failure rolls
     * the profile change back. E-mail and phone are not changed here (Keycloak flows own them).
     */
    @Transactional
    public SelfProfile updateSelf(UUID tenantId, UUID profileId, String displayName, String preferredLocale) {
        AppUserProfile profile = profileRepository.findByIdAndTenantId(profileId, tenantId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        if (displayName == null && preferredLocale == null) {
            return toSelf(profile);
        }
        profile.updateSelfService(displayName, preferredLocale);
        AppUserProfile saved = profileRepository.saveAndFlush(profile);
        String[] names = displayName == null ? null : splitName(displayName);
        keycloakAdminService.updateUserProfile(saved.getKeycloakUserId(), names == null ? null : names[0],
                names == null ? null : names[1], preferredLocale);
        return toSelf(saved);
    }

    /**
     * Pseudonymises the profile (account deletion, W7-C) in the caller's erasure transaction: name -> "Cliente removido",
     * e-mail -> {@code deleted-<profileId>@deleted.invalid}, both phones, full name, NIF, birth date, free-text address and
     * avatar -> NULL, status -> DELETED. The Keycloak subject is kept: it is a random id whose Keycloak user is deleted,
     * and keeping it stops the request filter from provisioning a fresh profile for a still-valid token. Returns false
     * when the profile does not exist in the tenant. Idempotent.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean pseudonymise(UUID tenantId, UUID profileId) {
        return jdbc.update("""
                UPDATE app_user_profiles
                   SET email = ?, display_name = ?, phone = NULL, phone_e164 = NULL, phone_verified_at = NULL,
                       full_name = NULL, nif = NULL, date_of_birth = NULL, address = NULL, avatar_storage_key = NULL,
                       status = ?, updated_at = ?
                 WHERE id = ? AND tenant_id = ?""",
                pseudonymisedEmail(profileId), PSEUDONYMISED_DISPLAY_NAME, STATUS_DELETED, Timestamp.from(Instant.now()),
                profileId, tenantId) == 1;
    }

    /** The e-mail placeholder of a pseudonymised profile (RFC 2606 {@code .invalid}: never deliverable). */
    public static String pseudonymisedEmail(UUID profileId) {
        return "deleted-" + profileId + "@deleted.invalid";
    }

    /** Disables the Keycloak user; false when it no longer exists. Failures throw {@code keycloak_error}. */
    public boolean disableKeycloakUser(String keycloakUserId) {
        return keycloakAdminService.disableUser(keycloakUserId);
    }

    /** Deletes the Keycloak user, reporting failures (404 = already deleted). */
    public void deleteKeycloakUserStrict(String keycloakUserId) {
        keycloakAdminService.deleteUserStrict(keycloakUserId);
    }

    /** Splits a display name into Keycloak first name (first word) and last name (the rest, may be empty). */
    public static String[] splitName(String displayName) {
        String trimmed = displayName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[] {trimmed, ""} : new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private static SelfProfile toSelf(AppUserProfile p) {
        return new SelfProfile(p.getId(), p.getTenantId(), p.getKeycloakUserId(), p.getEmail(), p.getDisplayName(), p.getPhone(),
                p.getPhoneE164(), p.getPhoneVerifiedAt(), p.getFullName(), p.getNif(), p.getDateOfBirth(), p.getAddress(),
                p.getPreferredLanguage(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt());
    }

    /** Summary of an app user profile (no roles, no PII beyond what provisioning already returns). */
    public record UserAccount(UUID profileId, UUID tenantId, String email, String displayName, String keycloakUserId) {}

    /** True when an app user profile with this e-mail exists in any tenant. */
    public boolean emailExists(String email) {
        return profileRepository.existsByEmail(email);
    }

    /** First app user profile with this e-mail (any tenant), if any. */
    public Optional<UserAccount> findFirstByEmail(String email) {
        return profileRepository.findFirstByEmail(email).map(UserAccounts::toAccount);
    }

    /** Profile id of the Keycloak user in this tenant, if that user has a profile there. */
    public Optional<UUID> profileIdOf(UUID tenantId, String keycloakUserId) {
        return profileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId).map(AppUserProfile::getId);
    }

    /** Where a profile can be reached outside the app (LAC-48): e-mail and the verified phone only (never the free-text one). */
    public record ContactPoints(String email, String verifiedPhoneE164) {}

    /** Contact points of a profile of this tenant, if it exists; the phone is present only when verified (V063). */
    public Optional<ContactPoints> contactOf(UUID tenantId, UUID profileId) {
        return profileRepository.findByIdAndTenantId(profileId, tenantId)
                .map(profile -> new ContactPoints(profile.getEmail(),
                        profile.getPhoneVerifiedAt() != null ? profile.getPhoneE164() : null));
    }

    /** Keycloak user id of a profile, if the profile exists. */
    public Optional<String> keycloakUserIdOf(UUID profileId) {
        return profileRepository.findById(profileId).map(AppUserProfile::getKeycloakUserId);
    }

    /** Creates the Keycloak user with the tenant_id attribute; returns the Keycloak user id. */
    public String createKeycloakUser(String email, String firstName, String lastName, String password, String tenantId) {
        return keycloakAdminService.createUser(email, firstName, lastName, password, tenantId);
    }

    /** Deletes a Keycloak user (compensation after a failed onboarding). */
    public void deleteKeycloakUser(String keycloakUserId) {
        keycloakAdminService.deleteUser(keycloakUserId);
    }

    /** Sends the Keycloak "set your password" onboarding e-mail. */
    public void sendPasswordSetupEmail(String keycloakUserId) {
        keycloakAdminService.sendPasswordSetupEmail(keycloakUserId);
    }

    /** Persists a new app user profile with the given roles. */
    public void createProfile(UUID profileId, UUID tenantId, String keycloakUserId, String email, String displayName,
                              Set<MarketplaceRole> roles) {
        profileRepository.save(new AppUserProfile(profileId, tenantId, keycloakUserId, email, displayName, roles));
    }

    private static UserAccount toAccount(AppUserProfile profile) {
        return new UserAccount(profile.getId(), profile.getTenantId(), profile.getEmail(), profile.getDisplayName(),
                profile.getKeycloakUserId());
    }
}
