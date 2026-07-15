package com.delivery.sales.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds app.sales.status-override settings gating the manual status-override action (default off). */
@Component
@ConfigurationProperties(prefix = "app.sales.status-override")
public class SalesStatusOverrideProperties {
    /** false → POST /sales/orders/{id}/status-override is rejected with 403. */
    private boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
