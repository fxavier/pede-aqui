package com.delivery.marketing.repository;

import com.delivery.marketing.entity.Coupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides coupon persistence access. */
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    List<Coupon> findByTenantId(UUID tenantId);
    Optional<Coupon> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
