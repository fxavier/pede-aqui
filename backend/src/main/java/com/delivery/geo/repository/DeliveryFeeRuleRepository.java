package com.delivery.geo.repository;

import com.delivery.geo.entity.DeliveryFeeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryFeeRuleRepository extends JpaRepository<DeliveryFeeRule, UUID> {

    List<DeliveryFeeRule> findByTenantIdAndVendorId(UUID tenantId, UUID vendorId);

    List<DeliveryFeeRule> findByTenantIdAndVendorIdAndActive(UUID tenantId, UUID vendorId, Boolean active);

    @Query("SELECT d FROM DeliveryFeeRule d WHERE d.tenantId = :tenantId AND d.vendorId = :vendorId " +
           "AND d.active = true AND d.minKm <= :distance AND d.maxKm >= :distance")
    Optional<DeliveryFeeRule> findApplicableRuleForDistance(@Param("tenantId") UUID tenantId,
                                                            @Param("vendorId") UUID vendorId,
                                                            @Param("distance") BigDecimal distance);

    @Query("SELECT d FROM DeliveryFeeRule d WHERE d.tenantId = :tenantId AND d.vendorId = :vendorId " +
           "AND d.active = true ORDER BY d.minKm ASC")
    List<DeliveryFeeRule> findActiveRulesOrderedByDistance(@Param("tenantId") UUID tenantId,
                                                           @Param("vendorId") UUID vendorId);

    void deleteByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
}