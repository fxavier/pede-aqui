package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.delivery.payment.api.PaymentProvider;
import com.delivery.payment.api.PaymentProvider.Outcome;
import com.delivery.payment.provider.SandboxProperties;
import com.delivery.payment.provider.SandboxProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;

/** Sandbox PSP (LAC-31, W7-A): HMAC signature verification, replay window, scenarios and simulated status. */
class SandboxProviderTest {
    private static final String SECRET = "test-sandbox-webhook-secret-0123456789abcdef";
    private static final Instant T0 = Instant.parse("2026-10-31T10:00:00Z");

    private SandboxProperties properties;
    private SandboxProvider provider;

    @BeforeEach
    void setUp() {
        properties = new SandboxProperties();
        properties.setWebhookSecret(SECRET);
        properties.setLatency(Duration.ofSeconds(3));
        provider = newProvider(properties);
        provider.setClock(Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void aCorrectlySignedCallbackVerifiesAndParses() {
        SandboxProvider.SignedCallback callback = provider.signedCallback("evt-1", "SBX-1", "PA1", Outcome.SUCCEEDED, new BigDecimal("12.50"));
        PaymentProvider.CallbackEvent event = provider.verifyCallback(headers(callback.signatureHeader()), callback.body());
        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.providerReference()).isEqualTo("SBX-1");
        assertThat(event.ourReference()).isEqualTo("PA1");
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(event.amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void tamperedBodyWrongSecretMissingHeaderAndStaleTimestampAreRejected() {
        SandboxProvider.SignedCallback callback = provider.signedCallback("evt-1", "SBX-1", "PA1", Outcome.SUCCEEDED, BigDecimal.TEN);
        byte[] tampered = new String(callback.body(), StandardCharsets.UTF_8).replace("SUCCEEDED", "FAILED").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> provider.verifyCallback(headers(callback.signatureHeader()), tampered))
                .isInstanceOf(PaymentProvider.InvalidCallbackSignatureException.class).hasMessageContaining("mismatch");
        assertThatThrownBy(() -> provider.verifyCallback(Map.of(), callback.body()))
                .isInstanceOf(PaymentProvider.InvalidCallbackSignatureException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> provider.verifyCallback(headers("garbage"), callback.body()))
                .isInstanceOf(PaymentProvider.InvalidCallbackSignatureException.class);

        SandboxProperties other = new SandboxProperties();
        other.setWebhookSecret("another-secret-of-sufficient-length-000000");
        SandboxProvider otherProvider = newProvider(other);
        otherProvider.setClock(Clock.fixed(T0, ZoneOffset.UTC));
        assertThatThrownBy(() -> otherProvider.verifyCallback(headers(callback.signatureHeader()), callback.body()))
                .isInstanceOf(PaymentProvider.InvalidCallbackSignatureException.class);

        provider.setClock(Clock.fixed(T0.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));
        assertThatThrownBy(() -> provider.verifyCallback(headers(callback.signatureHeader()), callback.body()))
                .isInstanceOf(PaymentProvider.InvalidCallbackSignatureException.class).hasMessageContaining("stale");
    }

    @Test
    void initiateIsAsyncAndQueryFollowsTheSimulatedCustomer() {
        PaymentProvider.InitiationResult result = provider.initiate(request("PA-ok", "+258841234567"));
        assertThat(result.outcome()).isEqualTo(Outcome.PENDING);
        assertThat(result.providerReference()).startsWith("SBX-");
        assertThat(provider.query(result.providerReference(), null).outcome()).isEqualTo(Outcome.PENDING);
        provider.setClock(Clock.fixed(T0.plusSeconds(4), ZoneOffset.UTC));
        assertThat(provider.query(result.providerReference(), null).outcome()).isEqualTo(Outcome.SUCCEEDED);
        assertThat(provider.query(null, "PA-ok").outcome()).as("by our reference").isEqualTo(Outcome.SUCCEEDED);
        assertThat(provider.query("SBX-unknown", "PA-unknown").outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void phoneSuffixSelectsTheScenario() {
        String failed = provider.initiate(request("PA-f", "+258840000001")).providerReference();
        String silent = provider.initiate(request("PA-n", "+258840000002")).providerReference();
        assertThatThrownBy(() -> provider.initiate(request("PA-t", "+258840000003")))
                .isInstanceOf(PaymentProvider.ProviderUnavailableException.class);
        provider.setClock(Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));
        PaymentProvider.StatusResult failedStatus = provider.query(failed, null);
        assertThat(failedStatus.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(failedStatus.errorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(provider.query(silent, null).outcome()).isEqualTo(Outcome.PENDING);
        assertThat(provider.query(null, "PA-t").outcome()).as("timed-out initiate still charged").isEqualTo(Outcome.SUCCEEDED);
        provider.settle(silent, Outcome.EXPIRED);
        assertThat(provider.query(silent, null).outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void secretMustBeLongEnoughWhenActive() {
        SandboxProperties weak = new SandboxProperties();
        weak.setWebhookSecret("short");
        assertThatThrownBy(() -> newProvider(weak).requireConfigured()).isInstanceOf(IllegalStateException.class);
        provider.requireConfigured();
    }

    private static PaymentProvider.InitiationRequest request(String ourReference, String msisdn) {
        return new PaymentProvider.InitiationRequest(UUID.randomUUID(), ourReference, new BigDecimal("100.00"), "MZN", msisdn, "test");
    }

    private static Map<String, String> headers(String signature) {
        return Map.of(SandboxProvider.SIGNATURE_HEADER.toLowerCase(), signature);
    }

    private static SandboxProvider newProvider(SandboxProperties properties) {
        return new SandboxProvider(properties, new ObjectMapper(), new StaticListableBeanFactory().getBeanProvider(RestClient.Builder.class));
    }
}
