package com.delivery.vendor.repository;

import com.delivery.vendor.entity.MerchantUser;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped persistence of merchant user ↔ vendor associations (ADR-0001, merchant_users). */
public interface MerchantUserRepository extends JpaRepository<MerchantUser, UUID> {
    List<MerchantUser> findByTenantIdAndAppUserProfileId(UUID tenantId, UUID appUserProfileId);

    List<MerchantUser> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    boolean existsByTenantIdAndAppUserProfileIdAndVendorId(UUID tenantId, UUID appUserProfileId, UUID vendorId);

    /**
     * Vendors the Keycloak principal is associated with in the tenant — one query on the guards' hot path.
     * Native join on app_user_profiles keeps {@code vendor} independent of the auth entity model.
     */
    @Query(value = """
            SELECT mu.vendor_id
              FROM merchant_users mu
              JOIN app_user_profiles p ON p.id = mu.app_user_profile_id
             WHERE mu.tenant_id = :tenantId
               AND p.keycloak_user_id = :keycloakUserId
             ORDER BY mu.created_at, mu.vendor_id
            """, nativeQuery = true)
    List<UUID> findVendorIdsByTenantAndKeycloakUser(@Param("tenantId") UUID tenantId, @Param("keycloakUserId") String keycloakUserId);
}
