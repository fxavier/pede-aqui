package com.delivery.notification.api;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationDelivery.Status;
import com.delivery.notification.entity.NotificationTemplate;
import com.delivery.notification.repository.NotificationDeliveryStore;
import com.delivery.notification.repository.NotificationDeliveryStore.NewDelivery;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.UserNotificationPreferenceRepository;
import com.delivery.notification.service.ChannelPreferences;
import com.delivery.notification.service.NotificationTemplateService;
import com.delivery.notification.service.TemplateRenderer;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point of multi-channel notifications (LAC-48, W6-C). For each requested channel with an <b>active</b> template
 * for the key it renders the pt-PT text and writes one {@code notification_deliveries} row in the caller's transaction
 * (outbox): external channels (PUSH/SMS/EMAIL) as QUEUED — or SKIPPED {@code preference_disabled} when the user opted
 * out — for the {@code notification-delivery} job to send; IN_APP directly as SENT together with the inbox row. The
 * per-channel idempotency key ({@code dedupKey:channel}, UNIQUE) makes a repeated dispatch a no-op, so at-least-once
 * callers (event listeners) are safe. Nothing is sent to a provider here: a provider failure can never roll back or
 * slow down the business transaction.
 */
@Service
public class NotificationDispatcher {
    /** Channels the in-app fan-out of {@code NotificationService} tries (IN_APP is the call itself). */
    public static final Set<NotificationChannel> EXTERNAL_CHANNELS = EnumSet.of(NotificationChannel.PUSH, NotificationChannel.SMS,
            NotificationChannel.EMAIL);
    /** Every channel, for event-driven notifications whose templates decide the channels. */
    public static final Set<NotificationChannel> ALL_CHANNELS = EnumSet.allOf(NotificationChannel.class);

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationTemplateService templateService;
    private final NotificationDeliveryStore deliveryStore;
    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;

    public NotificationDispatcher(NotificationTemplateService templateService, NotificationDeliveryStore deliveryStore,
                                  NotificationRepository notificationRepository,
                                  UserNotificationPreferenceRepository preferenceRepository) {
        this.templateService = templateService;
        this.deliveryStore = deliveryStore;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Writes the delivery rows of one notification (joins the caller's transaction). Returns how many rows were
     * written; 0 when no channel has an active template or every channel was already dispatched for this key.
     */
    @Transactional
    public int dispatch(NotificationRequest request) {
        ChannelPreferences preferences = null;
        int written = 0;
        for (NotificationChannel channel : request.channels().stream().sorted(Comparator.naturalOrder()).toList()) {
            Optional<NotificationTemplate> found = templateService.effective(request.tenantId(), request.templateKey(), channel);
            if (found.isEmpty() || !found.get().isActive()) {
                continue;
            }
            NotificationTemplate template = found.get();
            Map<String, String> params = request.params();
            String title = TemplateRenderer.render(template.getTitle(), params, template.isAllowSensitivePlaceholders());
            String body = TemplateRenderer.render(template.getBody(), params, template.isAllowSensitivePlaceholders());
            Status status = Status.QUEUED;
            String lastError = null;
            if (channel == NotificationChannel.IN_APP) {
                status = Status.SENT;
            } else {
                if (preferences == null) {
                    preferences = ChannelPreferences.of(preferenceRepository
                            .findByTenantIdAndUserProfileId(request.tenantId(), request.recipientProfileId()).orElse(null));
                }
                if (!preferences.allows(channel)) {
                    status = Status.SKIPPED;
                    lastError = "preference_disabled";
                }
            }
            Optional<UUID> inserted = deliveryStore.insertIfAbsent(new NewDelivery(request.tenantId(), request.recipientProfileId(),
                    channel, request.templateKey(), template.getVersion(), template.getLocale(), title, body,
                    request.businessReference(), status, lastError, request.dedupKey() + ":" + channel.name()));
            if (inserted.isEmpty()) {
                continue; // already dispatched for this fact and channel
            }
            written++;
            if (channel == NotificationChannel.IN_APP) {
                notificationRepository.save(new Notification(UUID.randomUUID(), request.tenantId(), request.recipientProfileId(),
                        request.recipientRole() == null ? "CUSTOMER" : request.recipientRole(), request.templateKey(),
                        title == null || title.isBlank() ? request.templateKey() : title, body, request.businessReference()));
            }
        }
        if (written > 0) {
            LOGGER.info("notification.dispatched template={} rows={} businessReference={}", request.templateKey(), written,
                    request.businessReference());
        }
        return written;
    }
}
