package com.delivery.order.entity;

import com.delivery.common.pricing.DeliveryFeeBasis;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.common.pricing.VatBreakdown;
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
 * by a database trigger. Null on the order for pre-INC-13 orders. Since V064 (W6-A) it also carries the VAT contained
 * in the total (goods by category after discount, goods/fees VAT, net) and the checkout quote id used, if any; those
 * are null for pre-W6-A orders (whose VAT is the tax line orders.taxes).
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
    @Column(name = "quote_id", updatable = false)
    private UUID quoteId;
    @Column(name = "quote_tax_inclusive", updatable = false)
    private Boolean taxInclusive;
    @Column(name = "quote_goods_standard_gross", updatable = false)
    private BigDecimal goodsStandardGross;
    @Column(name = "quote_goods_zero_gross", updatable = false)
    private BigDecimal goodsZeroGross;
    @Column(name = "quote_goods_exempt_gross", updatable = false)
    private BigDecimal goodsExemptGross;
    @Column(name = "quote_goods_vat", updatable = false)
    private BigDecimal goodsVat;
    @Column(name = "quote_fees_vat", updatable = false)
    private BigDecimal feesVat;
    @Column(name = "quote_vat_total", updatable = false)
    private BigDecimal vatTotal;
    @Column(name = "quote_net_total", updatable = false)
    private BigDecimal netTotal;

    protected PricingQuoteSnapshot() {}

    /** Snapshot of an engine quote taken now, without VAT breakdown (kept for callers that have no discount yet). */
    public static PricingQuoteSnapshot of(PriceQuote quote) {
        return of(quote, null, null);
    }

    /** Snapshot of an engine quote taken now with the VAT of the charged total and the checkout quote id (nullable). */
    public static PricingQuoteSnapshot of(PriceQuote quote, VatBreakdown vat, UUID quoteId) {
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
        snapshot.quoteId = quoteId;
        if (vat != null) {
            snapshot.taxInclusive = vat.taxInclusive();
            snapshot.goodsStandardGross = vat.goodsStandardGross();
            snapshot.goodsZeroGross = vat.goodsZeroGross();
            snapshot.goodsExemptGross = vat.goodsExemptGross();
            snapshot.goodsVat = vat.goodsVat();
            snapshot.feesVat = vat.feesVat();
            snapshot.vatTotal = vat.vatTotal();
            snapshot.netTotal = vat.netTotal();
        }
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
    public UUID getQuoteId() { return quoteId; }
    public Boolean getTaxInclusive() { return taxInclusive; }
    public BigDecimal getGoodsStandardGross() { return goodsStandardGross; }
    public BigDecimal getGoodsZeroGross() { return goodsZeroGross; }
    public BigDecimal getGoodsExemptGross() { return goodsExemptGross; }
    public BigDecimal getGoodsVat() { return goodsVat; }
    public BigDecimal getFeesVat() { return feesVat; }
    public BigDecimal getVatTotal() { return vatTotal; }
    public BigDecimal getNetTotal() { return netTotal; }
}
