package com.delivery.auth.api;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.common.security.MarketplaceRole;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Identity facade of the auth module (ADR-0002 C8/C9): the Keycloak user and app-user-profile operations that tenant
 * onboarding and subscription provisioning need, so those flows live in the tenant module without reaching into the
 * auth repositories, entities or Keycloak client. Thin pass-through: exceptions of the underlying calls (for example
 * {@code email_exists}) propagate unchanged and no transaction is opened here (callers own it).
 */
@Service
public class UserAccounts {
    private final AppUserProfileRepository profileRepository;
    private final KeycloakAdminService keycloakAdminService;

    public UserAccounts(AppUserProfileRepository profileRepository, KeycloakAdminService keycloakAdminService) {
        this.profileRepository = profileRepository;
        this.keycloakAdminService = keycloakAdminService;
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
