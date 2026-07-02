package com.delivery.auth.repository;

import com.delivery.auth.entity.AppUserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for user profiles. */
public interface AppUserProfileRepository extends JpaRepository<AppUserProfile, UUID> {
    Optional<AppUserProfile> findByTenantIdAndKeycloakUserId(UUID tenantId, String keycloakUserId);
    Optional<AppUserProfile> findByKeycloakUserId(String keycloakUserId);
    Optional<AppUserProfile> findByIdAndTenantId(UUID id, UUID tenantId);
    List<AppUserProfile> findByTenantId(UUID tenantId);
    boolean existsByEmail(String email);
}
