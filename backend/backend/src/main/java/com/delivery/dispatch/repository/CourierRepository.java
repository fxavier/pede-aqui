package com.delivery.dispatch.repository;

import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.entity.CourierVerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides courier queries for dispatch eligibility and profile updates. */
public interface CourierRepository extends JpaRepository<Courier, UUID> {
    Optional<Courier> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Courier> findByTenantIdAndUserProfileId(UUID tenantId, UUID userProfileId);
    List<Courier> findByTenantId(UUID tenantId);
    List<Courier> findByTenantIdAndVerificationStatusAndAvailable(UUID tenantId, CourierVerificationStatus verificationStatus, boolean available);
    List<Courier> findByTenantIdAndVerificationStatusAndAvailableAndOperatingZoneId(UUID tenantId, CourierVerificationStatus verificationStatus, boolean available, UUID operatingZoneId);
    long countByTenantIdAndAvailable(UUID tenantId, boolean available);
}
