package com.delivery.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds app.delivery settings for the customer delivery PIN (attempt limit and HMAC secret). */
@Component
@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryCodeProperties {
    /** Failed PIN submissions allowed before the delivery code is locked for OPS review. */
    private int maxCodeAttempts = 5;
    /** HMAC-SHA256 key for PIN hashes. Defaulted only in the dev profile; must be set in prod. */
    private String codeHmacSecret;

    public int getMaxCodeAttempts() { return maxCodeAttempts; }
    public void setMaxCodeAttempts(int maxCodeAttempts) { this.maxCodeAttempts = maxCodeAttempts; }
    public String getCodeHmacSecret() { return codeHmacSecret; }
    public void setCodeHmacSecret(String codeHmacSecret) { this.codeHmacSecret = codeHmacSecret; }
}
