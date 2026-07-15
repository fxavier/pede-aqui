package com.delivery.marketing.repository;

import com.delivery.marketing.entity.Promotion;
import com.delivery.marketing.entity.PromotionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides persistence access for spec-002 promotions, including the atomic usage-limit increment. */
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    List<Promotion> findByTenantId(UUID tenantId);
    Optional<Promotion> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Promotion> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
    List<Promotion> findByTenantIdAndStatusAndCodeIsNull(UUID tenantId, PromotionStatus status);

    /**
     * Atomically consumes one global usage slot; returns 0 when the usage limit is already
     * reached, which must abort checkout without consuming usage (spec 002 AC-7.6).
     */
    @Modifying
    @Query("update Promotion p set p.usedCount = p.usedCount + 1 "
            + "where p.id = :id and (p.usageLimit is null or p.usedCount < p.usageLimit)")
    int incrementUsageIfWithinLimit(@Param("id") UUID id);
}
