package com.delivery.tenant.repository;

import com.delivery.tenant.entity.ProvisioningRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for the tenant-provisioning idempotency ledger. */
public interface ProvisioningRequestRepository extends JpaRepository<ProvisioningRequest, UUID> {
    Optional<ProvisioningRequest> findByIdempotencyKey(String idempotencyKey);

    long countByCreatedAtAfter(Instant createdAt);

    @Query("SELECT COUNT(p) FROM ProvisioningRequest p WHERE p.createdAt > :createdAt AND p.ownerEmail LIKE :domainPattern")
    long countByEmailDomainSince(@Param("domainPattern") String domainPattern, @Param("createdAt") Instant createdAt);
}
