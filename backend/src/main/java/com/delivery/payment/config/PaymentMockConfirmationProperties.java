package com.delivery.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds app.payments.mock-confirmation settings gating the development-only manual payment confirmation (default off). */
@Component
@ConfigurationProperties(prefix = "app.payments.mock-confirmation")
public class PaymentMockConfirmationProperties {
    /** false → POST /payments/{id}/confirm answers 404 as if the endpoint did not exist. */
    private boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
