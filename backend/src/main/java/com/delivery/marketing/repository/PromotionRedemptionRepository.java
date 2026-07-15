package com.delivery.marketing.repository;

import com.delivery.marketing.entity.PromotionRedemption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides persistence access for promotion redemptions (per-customer usage ledger). */
public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, UUID> {
    long countByPromotionIdAndCustomerId(UUID promotionId, UUID customerId);

    /**
     * Finds orders in the current transaction (or crashed leftovers, which atomicity prevents)
     * that carry the promotion but have no redemption row yet — used by the checkout resolver
     * to attach the redemption to the order id created after discount resolution.
     */
    @Query("select o.id from Order o where o.tenantId = :tenantId and o.customerId = :customerId "
            + "and o.appliedPromotionId = :promotionId "
            + "and not exists (select r.id from PromotionRedemption r where r.promotionId = :promotionId and r.orderId = o.id)")
    List<UUID> findUnredeemedOrderIds(@Param("tenantId") UUID tenantId,
                                      @Param("customerId") UUID customerId,
                                      @Param("promotionId") UUID promotionId);
}
