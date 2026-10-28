package com.delivery.order.entity;

import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.common.pricing.PriceQuote;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable copy of the pricing quote applied at checkout (V048, INC-13): fee amounts, rates, the delivery fee
 * basis and distance, and the ids/versions of the fee policy and distance rule used. Insert-only here and guarded
 * by a database trigger. Null on the order for pre-INC-13 orders.
 */
@Embeddable
public class PricingQuoteSnapshot {
    @Column(name = "quote_engine_version", updatable = false)
    private String engineVersion;
    @Column(name = "quote_delivery_fee", updatable = false)
    private BigDecimal deliveryFee;
    @Column(name = "quote_service_fee", updatable = false)
    private BigDecimal serviceFee;
    @Column(name = "quote_service_fee_rate", updatable = false)
    private BigDecimal serviceFeeRate;
    @Column(name = "quote_tax_rate", updatable = false)
    private BigDecimal taxRate;
    @Enumerated(EnumType.STRING)
    @Column(name = "quote_delivery_fee_basis", updatable = false)
    private DeliveryFeeBasis deliveryFeeBasis;
    @Column(name = "quote_distance_km", updatable = false)
    private BigDecimal distanceKm;
    @Column(name = "quote_fee_policy_id", updatable = false)
    private UUID feePolicyId;
    @Column(name = "quote_fee_policy_version", updatable = false)
    private Long feePolicyVersion;
    @Column(name = "quote_delivery_fee_rule_id", updatable = false)
    private UUID deliveryFeeRuleId;
    @Column(name = "quote_delivery_fee_rule_version", updatable = false)
    private Long deliveryFeeRuleVersion;
    @Column(name = "quoted_at", updatable = false)
    private Instant quotedAt;

    protected PricingQuoteSnapshot() {}

    /** Snapshot of an engine quote taken now. */
    public static PricingQuoteSnapshot of(PriceQuote quote) {
        PricingQuoteSnapshot snapshot = new PricingQuoteSnapshot();
        snapshot.engineVersion = quote.engineVersion();
        snapshot.deliveryFee = quote.deliveryFee();
        snapshot.serviceFee = quote.serviceFee();
        snapshot.serviceFeeRate = quote.serviceFeeRate();
        snapshot.taxRate = quote.taxRate();
        snapshot.deliveryFeeBasis = quote.delivery().basis();
        snapshot.distanceKm = quote.delivery().distanceKm();
        snapshot.feePolicyId = quote.delivery().feePolicyId();
        snapshot.feePolicyVersion = quote.delivery().feePolicyVersion();
        snapshot.deliveryFeeRuleId = quote.delivery().deliveryFeeRuleId();
        snapshot.deliveryFeeRuleVersion = quote.delivery().deliveryFeeRuleVersion();
        snapshot.quotedAt = Instant.now();
        return snapshot;
    }

    public String getEngineVersion() { return engineVersion; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public BigDecimal getServiceFeeRate() { return serviceFeeRate; }
    public BigDecimal getTaxRate() { return taxRate; }
    public DeliveryFeeBasis getDeliveryFeeBasis() { return deliveryFeeBasis; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public UUID getFeePolicyId() { return feePolicyId; }
    public Long getFeePolicyVersion() { return feePolicyVersion; }
    public UUID getDeliveryFeeRuleId() { return deliveryFeeRuleId; }
    public Long getDeliveryFeeRuleVersion() { return deliveryFeeRuleVersion; }
    public Instant getQuotedAt() { return quotedAt; }
}
