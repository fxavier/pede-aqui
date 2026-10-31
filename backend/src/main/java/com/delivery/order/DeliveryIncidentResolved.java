package com.delivery.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application event (LAC-44, W7-B): OPS/ADMIN resolved a delivery incident. Published inside the resolving transaction
 * and delivered after commit through the JDBC event publication registry (at-least-once — listeners must be
 * idempotent; the settlement ledger posts it once by key {@code delivery-incident:<incidentId>}). Carries ids, enum
 * names and the resolved money figures only, never personal data or free text. See docs/architecture/EVENT-CATALOG.md.
 *
 * @param costBearer           CUSTOMER, MERCHANT or PLATFORM
 * @param outcome              REFUND_FULL or REFUND_MINUS_DELIVERY_FEE
 * @param goodsDisposition     RETURN_TO_STORE or DISCARDED
 * @param refundAmount         refund requested for the customer (approved later by FINANCE; posted by RefundApproved)
 * @param retainedFeeAmount    delivery fee withheld from the customer (platform fee revenue)
 * @param merchantChargeAmount platform fees refunded because of a store fault, charged to the merchant payable
 * @param platformCompensationAmount goods value compensated to the merchant by the platform (expense)
 */
public record DeliveryIncidentResolved(UUID tenantId, UUID incidentId, UUID orderId, UUID vendorId, String costBearer, String outcome,
                                       String goodsDisposition, BigDecimal refundAmount, BigDecimal retainedFeeAmount,
                                       BigDecimal merchantChargeAmount, BigDecimal platformCompensationAmount, Instant resolvedAt) {
}
