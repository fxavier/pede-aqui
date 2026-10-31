package com.delivery.payment.provider;

import com.delivery.payment.api.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Simulated M-Pesa-like PSP for dev, tests and staging (LAC-31, W7-A) — no money moves. {@link #initiate} answers
 * asynchronously (PENDING, "push sent to the phone"); after {@code app.payments.sandbox.latency} the simulated customer
 * answers and, with {@code callback-enabled}, a signed callback is POSTed to {@code callback-url}. {@link #query} reports
 * the same simulated state, so the status poller resolves attempts without callbacks too.
 *
 * <p>Scenario by the phone number's last 4 digits (else {@code default-scenario}): {@code 0001} → FAILED
 * ({@code INSUFFICIENT_BALANCE}), {@code 0002} → customer never answers (stays PENDING until the attempt expires),
 * {@code 0003} → initiate times out (attempt UNKNOWN) although the charge succeeds — resolved by query/callback.
 *
 * <p>Callback: JSON body, header {@code X-Sandbox-Signature: t=<epoch seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>},
 * accepted within {@code signature-tolerance}. The simulated PSP state lives in this instance's memory (sandbox only).
 */
@Component
public class SandboxProvider implements PaymentProvider {
    public static final String ID = "sandbox";
    public static final String SIGNATURE_HEADER = "X-Sandbox-Signature";
    static final int MIN_SECRET_LENGTH = 32;
    private static final Logger log = LoggerFactory.getLogger(SandboxProvider.class);

    /** Simulated customer behaviour. */
    public enum Scenario { SUCCEEDED, FAILED, NO_ANSWER, INITIATE_TIMEOUT }

    /** A signed callback ready to POST (tests and the dev callback sender). */
    public record SignedCallback(String signatureHeader, byte[] body) {
    }

    private record Charge(String providerReference, String ourReference, BigDecimal amount, String currency, Scenario scenario,
                          Instant decideAt) {
    }

    private final SandboxProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RestClient.Builder> restClientBuilder;
    private final Map<String, Charge> byProviderReference = new ConcurrentHashMap<>();
    private final Map<String, String> providerReferenceByOurs = new ConcurrentHashMap<>();
    private final Map<String, Outcome> settled = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();
    private volatile ScheduledExecutorService callbackScheduler;

    public SandboxProvider(SandboxProperties properties, ObjectMapper objectMapper, ObjectProvider<RestClient.Builder> restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public String id() {
        return ID;
    }

    /** Fails startup when the sandbox is the active provider without a usable webhook secret. */
    public void requireConfigured() {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("app.payments.sandbox.webhook-secret (APP_PAYMENTS_SANDBOX_WEBHOOK_SECRET) must have at least "
                    + MIN_SECRET_LENGTH + " characters when app.payments.provider=sandbox");
        }
    }

    @Override
    public InitiationResult initiate(InitiationRequest request) {
        Scenario scenario = scenarioFor(request.msisdn());
        String providerReference = "SBX-" + UUID.randomUUID();
        Charge charge = new Charge(providerReference, request.ourReference(), request.amount(), request.currency(), scenario,
                clock.instant().plus(properties.getLatency()));
        byProviderReference.put(providerReference, charge);
        providerReferenceByOurs.put(request.ourReference(), providerReference);
        if (properties.isCallbackEnabled() && scenario != Scenario.NO_ANSWER) {
            scheduleCallback(charge);
        }
        if (scenario == Scenario.INITIATE_TIMEOUT) {
            throw new ProviderUnavailableException("sandbox: simulated initiate timeout");
        }
        return new InitiationResult(Outcome.PENDING, providerReference, null);
    }

    @Override
    public StatusResult query(String providerReference, String ourReference) {
        String reference = providerReference != null ? providerReference : providerReferenceByOurs.get(ourReference);
        Charge charge = reference == null ? null : byProviderReference.get(reference);
        if (charge == null) {
            return new StatusResult(Outcome.NOT_FOUND, null, null, null);
        }
        Outcome outcome = currentOutcome(charge);
        return new StatusResult(outcome, charge.providerReference(), errorCodeFor(outcome), charge.amount());
    }

    @Override
    public CallbackEvent verifyCallback(Map<String, String> headers, byte[] body) {
        String header = headers.get(SIGNATURE_HEADER.toLowerCase());
        if (header == null) {
            throw new InvalidCallbackSignatureException("missing signature");
        }
        Long timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    throw new InvalidCallbackSignatureException("malformed timestamp");
                }
            } else if (pair.length == 2 && pair[0].equals("v1")) {
                signature = pair[1];
            }
        }
        if (timestamp == null || signature == null) {
            throw new InvalidCallbackSignatureException("malformed signature header");
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (skew > properties.getSignatureTolerance().toSeconds()) {
            throw new InvalidCallbackSignatureException("stale timestamp");
        }
        byte[] expected = hmac(timestamp, body).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidCallbackSignatureException("signature mismatch");
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            String amount = text(json, "amount");
            return new CallbackEvent(require(json, "eventId"), text(json, "providerReference"), text(json, "ourReference"),
                    Outcome.valueOf(require(json, "status")), amount == null ? null : new BigDecimal(amount), text(json, "errorCode"));
        } catch (InvalidCallbackSignatureException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCallbackSignatureException("malformed payload");
        }
    }

    // --- simulation controls (sandbox only: tests, dev tooling) ---

    /** Forces the PSP-side outcome of a charge (what {@link #query} reports from now on). */
    public void settle(String providerReference, Outcome outcome) {
        settled.put(providerReference, outcome);
    }

    /** Builds the signed callback the PSP would send for {@code providerReference}, signed now. */
    public SignedCallback callbackFor(String providerReference, Outcome outcome, String eventId) {
        Charge charge = byProviderReference.get(providerReference);
        BigDecimal amount = charge == null ? null : charge.amount();
        String ourReference = charge == null ? null : charge.ourReference();
        return signedCallback(eventId, providerReference, ourReference, outcome, amount);
    }

    /** Builds and signs an arbitrary callback (tests: unknown references, amount mismatches). */
    public SignedCallback signedCallback(String eventId, String providerReference, String ourReference, Outcome outcome,
                                         BigDecimal amount) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("eventId", eventId);
        json.put("providerReference", providerReference);
        json.put("ourReference", ourReference);
        json.put("status", outcome.name());
        json.put("amount", amount == null ? null : amount.toPlainString());
        json.put("errorCode", errorCodeFor(outcome));
        json.put("occurredAt", clock.instant().toString());
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        long timestamp = clock.instant().getEpochSecond();
        return new SignedCallback("t=" + timestamp + ",v1=" + hmac(timestamp, body), body);
    }

    /** Test seam: a fixed clock for timestamp/latency checks. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @PreDestroy
    void shutdown() {
        ScheduledExecutorService scheduler = callbackScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private Outcome currentOutcome(Charge charge) {
        Outcome forced = settled.get(charge.providerReference());
        if (forced != null) {
            return forced;
        }
        if (clock.instant().isBefore(charge.decideAt())) {
            return Outcome.PENDING;
        }
        return switch (charge.scenario()) {
            case SUCCEEDED, INITIATE_TIMEOUT -> Outcome.SUCCEEDED;
            case FAILED -> Outcome.FAILED;
            case NO_ANSWER -> Outcome.PENDING;
        };
    }

    private Scenario scenarioFor(String msisdn) {
        if (msisdn != null && msisdn.endsWith("0001")) {
            return Scenario.FAILED;
        }
        if (msisdn != null && msisdn.endsWith("0002")) {
            return Scenario.NO_ANSWER;
        }
        if (msisdn != null && msisdn.endsWith("0003")) {
            return Scenario.INITIATE_TIMEOUT;
        }
        return properties.getDefaultScenario();
    }

    private static String errorCodeFor(Outcome outcome) {
        return switch (outcome) {
            case FAILED -> "INSUFFICIENT_BALANCE";
            case EXPIRED -> "CUSTOMER_TIMEOUT";
            default -> null;
        };
    }

    private void scheduleCallback(Charge charge) {
        ScheduledExecutorService scheduler = callbackScheduler;
        if (scheduler == null) {
            synchronized (this) {
                if (callbackScheduler == null) {
                    callbackScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                        Thread thread = new Thread(runnable, "sandbox-psp-callback");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                scheduler = callbackScheduler;
            }
        }
        scheduler.schedule(() -> sendCallback(charge), Math.max(0, properties.getLatency().toMillis()), TimeUnit.MILLISECONDS);
    }

    private void sendCallback(Charge charge) {
        try {
            SignedCallback callback = callbackFor(charge.providerReference(), currentOutcome(charge), "evt-" + UUID.randomUUID());
            restClientBuilder.getIfAvailable(RestClient::builder).build().post()
                    .uri(properties.getCallbackUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, callback.signatureHeader())
                    .body(callback.body())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            // The status poller resolves the attempt anyway; never log the payload.
            log.warn("Sandbox PSP callback delivery failed: {}", e.getClass().getSimpleName());
        }
    }

    private String hmac(long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String require(JsonNode json, String field) {
        String value = text(json, field);
        if (value == null || value.isBlank()) {
            throw new InvalidCallbackSignatureException("missing " + field);
        }
        return value;
    }
}
