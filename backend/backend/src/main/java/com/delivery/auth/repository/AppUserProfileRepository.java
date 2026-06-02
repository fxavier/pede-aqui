package com.delivery.auth.repository;

import com.delivery.auth.entity.AppUserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for user profiles. */
public interface AppUserProfileRepository extends JpaRepository<AppUserProfile, UUID> {
    Optional<AppUserProfile> findByTenantIdAndKeycloakUserId(UUID tenantId, String keycloakUserId);
    Optional<AppUserProfile> findByKeycloakUserId(String keycloakUserId);
    boolean existsByEmail(String email);
}
