package com.delivery.payment.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.payment.api.PaymentProvider;
import com.delivery.payment.entity.PaymentAttemptStatus;
import com.delivery.payment.provider.PaymentProviderRegistry;
import com.delivery.payment.repository.PaymentAttemptRepository;
import com.delivery.payment.repository.PaymentWebhookEventRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles provider callbacks (LAC-31 §5, W7-A): verifies the signature with the provider ({@code 401
 * invalid_webhook_signature} otherwise, recorded for forensics), deduplicates by provider event id and applies the
 * reported outcome — all in ONE transaction, so a failed processing rolls back the dedup row and the provider's retry is
 * processed again, while a concurrent duplicate waits on the unique index and then answers {@code duplicate}.
 * Lock order: webhook event row → payment row → attempt (no other path locks event rows).
 */
@Service
public class PaymentWebhookService {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentProviderRegistry providers;
    private final PaymentWebhookEventRepository events;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentAttemptOutcomes outcomes;
    private final PaymentMetrics metrics;
    private final TransactionTemplate tx;

    public PaymentWebhookService(PaymentProviderRegistry providers, PaymentWebhookEventRepository events,
                                 PaymentAttemptRepository attemptRepository, PaymentAttemptOutcomes outcomes, PaymentMetrics metrics,
                                 PlatformTransactionManager transactionManager) {
        this.providers = providers;
        this.events = events;
        this.attemptRepository = attemptRepository;
        this.outcomes = outcomes;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Processes one callback of {@code providerId} and returns {@code processed}, {@code ignored} (repeat/stale status),
     * {@code duplicate} (event id already processed) or {@code unmatched} (no attempt with that reference). Unknown or
     * inactive provider → 404 {@code payment_provider_not_found}.
     */
    public String handle(String providerId, Map<String, String> headers, byte[] body) {
        PaymentProvider provider = providers.activeById(providerId).orElseThrow(() ->
                new BusinessException("payment_provider_not_found", "Unknown payment provider", HttpStatus.NOT_FOUND));
        String payloadHash = sha256(body);
        Instant receivedAt = Instant.now();
        PaymentProvider.CallbackEvent event;
        try {
            event = provider.verifyCallback(headers, body);
        } catch (PaymentProvider.InvalidCallbackSignatureException e) {
            events.recordInvalid(provider.id(), null, payloadHash, receivedAt);
            metrics.webhook(provider.id(), "invalid_signature");
            log.warn("Rejected payment webhook: provider={} reason={}", provider.id(), e.getMessage());
            throw new BusinessException("invalid_webhook_signature", "Webhook signature is not valid", HttpStatus.UNAUTHORIZED);
        }
        String result = tx.execute(status -> process(provider, event, payloadHash, receivedAt));
        metrics.webhook(provider.id(), result);
        return result;
    }

    private String process(PaymentProvider provider, PaymentProvider.CallbackEvent event, String payloadHash, Instant receivedAt) {
        UUID eventRow = events.claimVerified(provider.id(), truncate(event.eventId()), payloadHash, receivedAt);
        if (eventRow == null) {
            return "duplicate";
        }
        Optional<PaymentAttemptRepository.AttemptKeys> keys = Optional.empty();
        if (event.providerReference() != null) {
            keys = attemptRepository.findKeysByProviderAndProviderReference(provider.id(), event.providerReference());
        }
        if (keys.isEmpty() && event.ourReference() != null) {
            keys = attemptRepository.findKeysByOurReference(event.ourReference())
                    .filter(found -> found.getProvider().equals(provider.id()));
        }
        Instant now = Instant.now();
        if (keys.isEmpty()) {
            events.markProcessed(eventRow, "UNMATCHED", null, null, event.providerReference(), event.outcome().name(), now);
            log.warn("Payment webhook for an unknown reference: provider={}", provider.id());
            return "unmatched";
        }
        PaymentAttemptStatus target = switch (event.outcome()) {
            case PENDING -> PaymentAttemptStatus.PENDING_CUSTOMER;
            case SUCCEEDED -> PaymentAttemptStatus.SUCCEEDED;
            case FAILED -> PaymentAttemptStatus.FAILED;
            case EXPIRED -> PaymentAttemptStatus.EXPIRED;
            case NOT_FOUND -> null;
        };
        PaymentAttemptOutcomes.Applied applied = PaymentAttemptOutcomes.Applied.IGNORED;
        PaymentAttemptOutcomes.Locked locked = outcomes.lock(keys.get());
        if (target != null) {
            applied = outcomes.apply(locked, target, event.errorCode(), event.providerReference(), event.amount(), now);
        }
        boolean ignored = applied == PaymentAttemptOutcomes.Applied.IGNORED;
        events.markProcessed(eventRow, ignored ? "IGNORED" : "PROCESSED", keys.get().getTenantId(), keys.get().getId(),
                event.providerReference(), event.outcome().name(), now);
        return ignored ? "ignored" : "processed";
    }

    private static String truncate(String value) {
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
