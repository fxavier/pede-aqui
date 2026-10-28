package com.delivery.common.pricing;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Delivery fee with its provenance: the basis, the straight-line distance used (null when unknown) and the ids
 * and versions of the fee policy and distance rule that produced it (null when not applicable).
 */
public record DeliveryFeeQuote(
        BigDecimal amount,
        DeliveryFeeBasis basis,
        BigDecimal distanceKm,
        UUID feePolicyId,
        Long feePolicyVersion,
        UUID deliveryFeeRuleId,
        Long deliveryFeeRuleVersion) {}
