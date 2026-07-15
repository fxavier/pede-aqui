package com.delivery.catalog.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds app.catalog.price-review settings that gate the price-change moderation flow. */
@Component
@ConfigurationProperties(prefix = "app.catalog.price-review")
public class CatalogPriceReviewProperties {
    /** false → all price edits apply in place (audit-only mode). */
    private boolean enabled = true;
    /** |Δ%| strictly greater than this triggers moderation. */
    private BigDecimal thresholdPercent = new BigDecimal("20");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getThresholdPercent() { return thresholdPercent; }
    public void setThresholdPercent(BigDecimal thresholdPercent) { this.thresholdPercent = thresholdPercent; }
}
