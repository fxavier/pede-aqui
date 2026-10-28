package com.delivery.geo.repository;

import com.delivery.geo.entity.DeliveryFeeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for vendor distance-band delivery fee rules. */
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

    /** Active rules of many vendors at once (search); callers must still match rule.tenantId to the vendor's tenant. */
    List<DeliveryFeeRule> findByVendorIdInAndActiveTrue(Collection<UUID> vendorIds);

    void deleteByTenantIdAndVendorId(UUID tenantId, UUID vendorId);
}