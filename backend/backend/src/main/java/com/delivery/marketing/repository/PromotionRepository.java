package com.delivery.marketing.repository;

import com.delivery.marketing.entity.Promotion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides promotion persistence access. */
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    List<Promotion> findByTenantId(UUID tenantId);
    Optional<Promotion> findByTenantIdAndId(UUID tenantId, UUID id);
}
