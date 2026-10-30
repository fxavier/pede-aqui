package com.delivery.settlement.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.settlement.*} (LAC-33/34, ADR-0005). Every money rule here is part of the posting model that is
 * still "a validar" with the accountant/legal opinion (LAC-38).
 */
@Component
@ConfigurationProperties(prefix = "app.settlement")
public class SettlementProperties {
    /** Who funds a promotion discount when the order carries one. */
    public enum DiscountFunding { PLATFORM, MERCHANT }

    /** false → the ledger listeners ignore events (the test suite default; production keeps it on). */
    private boolean enabled = true;
    /** Commission rate used when a tenant has no default plan in force (fraction of the goods subtotal). */
    private BigDecimal defaultCommissionRate = new BigDecimal("0.15");
    /**
     * VAT rate embedded in the platform's own revenue (commission and fees are treated as VAT-inclusive and split into
     * net revenue + PLATFORM_VAT_PAYABLE). 0 = no VAT lines (default until the accountant confirms the treatment).
     */
    private BigDecimal platformVatRate = BigDecimal.ZERO;
    /** PLATFORM = discount booked as platform promotion expense; MERCHANT = discount reduces the merchant payable. */
    private DiscountFunding discountFundedBy = DiscountFunding.PLATFORM;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getDefaultCommissionRate() { return defaultCommissionRate; }
    public void setDefaultCommissionRate(BigDecimal defaultCommissionRate) { this.defaultCommissionRate = defaultCommissionRate; }
    public BigDecimal getPlatformVatRate() { return platformVatRate; }
    public void setPlatformVatRate(BigDecimal platformVatRate) { this.platformVatRate = platformVatRate; }
    public DiscountFunding getDiscountFundedBy() { return discountFundedBy; }
    public void setDiscountFundedBy(DiscountFunding discountFundedBy) { this.discountFundedBy = discountFundedBy; }
}
