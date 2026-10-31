package com.delivery.payment.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code app.payments.sandbox.*}: the simulated M-Pesa-like provider's secret, scenario and callback settings. */
@Component
@ConfigurationProperties(prefix = "app.payments.sandbox")
public class SandboxProperties {
    /** HMAC-SHA256 key of the callback signature (≥ 32 chars when the sandbox is the active provider). */
    private String webhookSecret = "";
    /** Accepted clock skew of the signed timestamp (replay window). */
    private Duration signatureTolerance = Duration.ofMinutes(5);
    /** Outcome when the phone number has no scenario suffix. */
    private SandboxProvider.Scenario defaultScenario = SandboxProvider.Scenario.SUCCEEDED;
    /** Simulated time the customer takes to answer the push on the phone. */
    private Duration latency = Duration.ofSeconds(3);
    /** Deliver the signed callback over HTTP to {@link #callbackUrl} (dev); off in tests, which post it themselves. */
    private boolean callbackEnabled = false;
    private String callbackUrl = "http://localhost:8080/api/v1/payments/webhooks/sandbox";
    /** The prod profile refuses the sandbox unless this is true (staging only — it confirms payments without money). */
    private boolean allowInProd = false;

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public Duration getSignatureTolerance() { return signatureTolerance; }
    public void setSignatureTolerance(Duration signatureTolerance) { this.signatureTolerance = signatureTolerance; }
    public SandboxProvider.Scenario getDefaultScenario() { return defaultScenario; }
    public void setDefaultScenario(SandboxProvider.Scenario defaultScenario) { this.defaultScenario = defaultScenario; }
    public Duration getLatency() { return latency; }
    public void setLatency(Duration latency) { this.latency = latency; }
    public boolean isCallbackEnabled() { return callbackEnabled; }
    public void setCallbackEnabled(boolean callbackEnabled) { this.callbackEnabled = callbackEnabled; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public boolean isAllowInProd() { return allowInProd; }
    public void setAllowInProd(boolean allowInProd) { this.allowInProd = allowInProd; }
}
