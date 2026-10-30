package com.delivery.notification.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.observability.BusinessMetrics;
import com.delivery.notification.config.NotificationProperties;
import com.delivery.notification.entity.DeviceToken;
import com.delivery.notification.entity.NotificationDelivery.Status;
import com.delivery.notification.repository.DeviceTokenRepository;
import com.delivery.notification.repository.NotificationDeliveryStore;
import com.delivery.notification.repository.NotificationDeliveryStore.ClaimedDelivery;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.sender.EmailSender;
import com.delivery.notification.sender.PushSender;
import com.delivery.notification.sender.SendResult;
import com.delivery.notification.sender.SendResult.Outcome;
import com.delivery.notification.sender.SmsSender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends queued notification deliveries (LAC-48 outbox consumer, run by {@code NotificationDeliveryJob}). Work-queue
 * pattern: one short transaction claims a batch ({@code FOR UPDATE SKIP LOCKED}, attempts + 1, lease), the provider
 * calls happen <b>outside</b> any transaction, and each result is written by a conditional update. Preferences are
 * re-checked at send time (the user may have opted out since the row was queued) and recipients are resolved now
 * (devices, verified phone, e-mail) — nothing but the masked form is stored. Retries: exponential backoff up to
 * {@code app.notifications.max-attempts}, then FAILED.
 */
@Service
public class NotificationDeliveryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

    private final NotificationDeliveryStore store;
    private final TransactionTemplate transactionTemplate;
    private final NotificationProperties properties;
    private final PushSender pushSender;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final UserAccounts userAccounts;
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public NotificationDeliveryWorker(NotificationDeliveryStore store, PlatformTransactionManager transactionManager,
                                      NotificationProperties properties, PushSender pushSender, SmsSender smsSender,
                                      EmailSender emailSender, DeviceTokenRepository deviceTokenRepository,
                                      UserNotificationPreferenceRepository preferenceRepository, UserAccounts userAccounts) {
        this.store = store;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.pushSender = pushSender;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
        this.deviceTokenRepository = deviceTokenRepository;
        this.preferenceRepository = preferenceRepository;
        this.userAccounts = userAccounts;
    }

    @Autowired(required = false)
    public void setMetrics(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    /** Per-tick summary for the job log line (counts only, no PII). */
    public record Summary(int claimed, Map<Status, Integer> results, int retried, int lostClaims) {
        @Override
        public String toString() {
            return "claimed=" + claimed + " results=" + results + " retried=" + retried + " lostClaims=" + lostClaims;
        }
    }

    /** Claims one batch of due deliveries and sends them; returns what happened. Never throws for a single row. */
    public Summary deliverDue() {
        List<ClaimedDelivery> claimed = transactionTemplate.execute(status ->
                store.claimDue(properties.getBatchSize(), properties.getLease()));
        Map<Status, Integer> results = new EnumMap<>(Status.class);
        int retried = 0;
        int lost = 0;
        for (ClaimedDelivery delivery : claimed == null ? List.<ClaimedDelivery>of() : claimed) {
            Attempt attempt;
            try {
                attempt = attempt(delivery);
            } catch (RuntimeException ex) {
                LOGGER.warn("notification.delivery.error id={} channel={} error={}", delivery.id(), delivery.channel(),
                        ex.getClass().getSimpleName());
                attempt = Attempt.retry("internal_error", null);
            }
            Status status = attempt.status;
            Duration retryIn = null;
            if (attempt.retry) {
                if (delivery.attempts() >= properties.getMaxAttempts()) {
                    status = Status.FAILED;
                } else {
                    status = Status.QUEUED;
                    retryIn = properties.backoffAfter(delivery.attempts());
                    retried++;
                }
            }
            boolean recorded = store.recordResult(delivery.id(), delivery.attempts(), status, retryIn, attempt.error,
                    attempt.providerMessageId, attempt.recipientMasked);
            if (!recorded) {
                lost++; // the lease expired and another attempt owns the row now
                continue;
            }
            results.merge(status, 1, Integer::sum);
            metrics.increment(BusinessMetrics.NOTIFICATION_DELIVERIES, "channel", delivery.channel().name().toLowerCase(),
                    "result", attempt.retry && status == Status.QUEUED ? "retry" : status.name().toLowerCase());
        }
        return new Summary(claimed == null ? 0 : claimed.size(), results, retried, lost);
    }

    private Attempt attempt(ClaimedDelivery delivery) {
        ChannelPreferences preferences = ChannelPreferences.of(preferenceRepository
                .findByTenantIdAndUserProfileId(delivery.tenantId(), delivery.recipientProfileId()).orElse(null));
        if (!preferences.allows(delivery.channel())) {
            return Attempt.terminal(Status.SKIPPED, "preference_disabled", null, null);
        }
        return switch (delivery.channel()) {
            case PUSH -> push(delivery);
            case SMS -> sms(delivery);
            case EMAIL -> email(delivery);
            case IN_APP -> Attempt.terminal(Status.SENT, null, null, null); // never queued; kept total for safety
        };
    }

    private Attempt push(ClaimedDelivery delivery) {
        List<DeviceToken> devices = deviceTokenRepository.findForRecipient(delivery.tenantId(), delivery.recipientProfileId(),
                PageRequest.of(0, Math.max(1, properties.getMaxDevicesPerUser())));
        if (devices.isEmpty()) {
            return Attempt.terminal(Status.SKIPPED, "no_device", null, null);
        }
        List<String> sentTo = new ArrayList<>();
        String firstMessageId = null;
        String retryCode = null;
        String permanentCode = null;
        int invalid = 0;
        for (DeviceToken device : devices) {
            SendResult result = safeSend(() -> pushSender.send(device.getPlatform(), device.getToken(), delivery.title(),
                    delivery.body(), delivery.templateKey()));
            switch (result.outcome()) {
                case SENT -> {
                    sentTo.add(RecipientMasking.token(device.getToken()));
                    if (firstMessageId == null) {
                        firstMessageId = result.providerMessageId();
                    }
                }
                case RETRYABLE -> retryCode = result.errorCode();
                case PERMANENT -> permanentCode = result.errorCode();
                case INVALID_RECIPIENT -> {
                    invalid++;
                    transactionTemplate.executeWithoutResult(status -> deviceTokenRepository.deleteInvalid(device.getId()));
                }
            }
        }
        if (!sentTo.isEmpty()) {
            String masked = sentTo.get(0) + (sentTo.size() > 1 ? " +" + (sentTo.size() - 1) : "");
            return Attempt.terminal(Status.SENT, null, firstMessageId, masked);
        }
        if (retryCode != null) {
            return Attempt.retry(retryCode, null);
        }
        if (invalid == devices.size()) {
            return Attempt.terminal(Status.FAILED, "invalid_recipient", null, null);
        }
        return Attempt.terminal(Status.FAILED, permanentCode == null ? "provider_rejected" : permanentCode, null, null);
    }

    private Attempt sms(ClaimedDelivery delivery) {
        String phone = userAccounts.contactOf(delivery.tenantId(), delivery.recipientProfileId())
                .map(UserAccounts.ContactPoints::verifiedPhoneE164).orElse(null);
        if (phone == null) {
            return Attempt.terminal(Status.SKIPPED, "no_verified_phone", null, null);
        }
        return fromResult(safeSend(() -> smsSender.send(phone, delivery.body(), delivery.templateKey())), RecipientMasking.phone(phone));
    }

    private Attempt email(ClaimedDelivery delivery) {
        String address = userAccounts.contactOf(delivery.tenantId(), delivery.recipientProfileId())
                .map(UserAccounts.ContactPoints::email).filter(value -> !value.isBlank()).orElse(null);
        if (address == null) {
            return Attempt.terminal(Status.SKIPPED, "no_email", null, null);
        }
        String subject = delivery.title() == null ? "Pede Aqui" : delivery.title();
        return fromResult(safeSend(() -> emailSender.send(address, subject, delivery.body(), delivery.templateKey())),
                RecipientMasking.email(address));
    }

    private static Attempt fromResult(SendResult result, String masked) {
        return switch (result.outcome()) {
            case SENT -> Attempt.terminal(Status.SENT, null, result.providerMessageId(), masked);
            case RETRYABLE -> Attempt.retry(code(result, "provider_unavailable"), masked);
            case PERMANENT -> Attempt.terminal(Status.FAILED, code(result, "provider_rejected"), null, masked);
            case INVALID_RECIPIENT -> Attempt.terminal(Status.FAILED, code(result, "invalid_recipient"), null, masked);
        };
    }

    private static String code(SendResult result, String fallback) {
        String code = result.errorCode();
        return code == null || !code.matches("[a-z0-9_]{1,80}") ? fallback : code;
    }

    private static SendResult safeSend(java.util.function.Supplier<SendResult> call) {
        try {
            SendResult result = call.get();
            return result == null ? SendResult.retryable("provider_no_result") : result;
        } catch (RuntimeException ex) {
            return SendResult.retryable("provider_exception");
        }
    }

    /** Result of one attempt before the retry policy is applied. */
    private record Attempt(Status status, boolean retry, String error, String providerMessageId, String recipientMasked) {
        static Attempt terminal(Status status, String error, String providerMessageId, String recipientMasked) {
            return new Attempt(status, false, error, providerMessageId, recipientMasked);
        }

        static Attempt retry(String error, String recipientMasked) {
            return new Attempt(Status.QUEUED, true, error, null, recipientMasked);
        }
    }
}
