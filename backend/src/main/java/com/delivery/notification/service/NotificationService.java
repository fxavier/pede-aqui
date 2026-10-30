package com.delivery.notification.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.api.NotificationDispatcher;
import com.delivery.notification.api.NotificationRequest;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.repository.NotificationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles persisted notification creation and recipient-scoped reads. */
@Service
public class NotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final AppUserProfileRepository userProfileRepository;
    private final TenantContext tenantContext;
    private NotificationDispatcher dispatcher;

    public NotificationService(NotificationRepository notificationRepository, AppUserProfileRepository userProfileRepository, TenantContext tenantContext) {
        this.notificationRepository = notificationRepository;
        this.userProfileRepository = userProfileRepository;
        this.tenantContext = tenantContext;
    }

    /** Optional (LAC-48): without it (slice tests) only the in-app row is written. */
    @Autowired(required = false)
    public void setDispatcher(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Creates a persisted notification record for a specific recipient. */
    @Transactional
    public NotificationResponse create(UUID recipientUserId, String recipientRole, String type, String title, String message, String businessReference) {
        return createForTenant(tenantId(), recipientUserId, recipientRole, type, title, message, businessReference);
    }

    /** {@link #create} for an explicit tenant: scheduled jobs (W4-A timeouts) run without a tenant context. */
    @Transactional
    public NotificationResponse createForTenant(UUID tenantId, UUID recipientUserId, String recipientRole, String type, String title, String message, String businessReference) {
        Notification notification = new Notification(UUID.randomUUID(), tenantId, recipientUserId, recipientRole, type, title, message, businessReference);
        Notification saved = notificationRepository.save(notification);
        LOGGER.info("notification.created type={} recipientRole={} businessReference={}", type, recipientRole, businessReference);
        fanOut(saved);
        return toResponse(saved);
    }

    /** Returns the authenticated user's newest notifications only, capped at 200 (legacy array, LAC-14). */
    @Transactional(readOnly = true)
    public CappedList<NotificationResponse> listMine() {
        UUID tenantId = tenantId();
        UUID profileId = currentProfileId(tenantId);
        return CappedList.of(notificationRepository.findByTenantIdAndRecipientUserId(tenantId, profileId, PageLimits.legacyCap()), this::toResponse);
    }

    /** Returns one keyset page of the authenticated user's notifications, newest first (limit capped, opaque cursor). */
    @Transactional(readOnly = true)
    public CursorPage<NotificationResponse> listMinePage(int limit, String cursor) {
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        UUID tenantId = tenantId();
        UUID profileId = currentProfileId(tenantId);
        List<Notification> probe = notificationRepository.findKeysetPage(tenantId, profileId, position.createdAt(), position.id(),
                PageLimits.keysetProbe(effectiveLimit));
        return CursorPage.fromProbe(probe, effectiveLimit, n -> new KeysetCursor(n.getCreatedAt(), n.getId()),
                rows -> rows.stream().map(this::toResponse).toList());
    }

    /** Marks one of the current user's notifications as read. */
    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UUID tenantId = tenantId();
        UUID profileId = currentProfileId(tenantId);
        Notification notification = notificationRepository.findByTenantIdAndId(tenantId, notificationId)
                .filter(item -> item.getRecipientUserId().equals(profileId))
                .orElseThrow(() -> new NotFoundException("Notification was not found"));
        notification.markRead();
        return toResponse(notification);
    }

    /**
     * LAC-48: the same fact on the external channels, in this transaction (outbox rows only). Only types with an active
     * PUSH/SMS/EMAIL template go out — the in-app {@code message} (which may hold a free-text reason or, for
     * {@code order_delivery_code}, the PIN) is never reused; templates get only {@code orderReference}. Keyed by the
     * in-app row id: one fan-out per in-app notification.
     */
    private void fanOut(Notification notification) {
        if (dispatcher == null) {
            return;
        }
        Map<String, String> params = new HashMap<>();
        if (notification.getBusinessReference() != null) {
            params.put("orderReference", notification.getBusinessReference());
        }
        dispatcher.dispatch(new NotificationRequest(notification.getTenantId(), notification.getRecipientUserId(),
                notification.getRecipientRole(), notification.getType(), notification.getBusinessReference(), params,
                NotificationDispatcher.EXTERNAL_CHANNELS, "inapp:" + notification.getId()));
    }

    private UUID currentProfileId(UUID tenantId) {
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        return userProfileRepository
                .findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"))
                .getId();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getBusinessReference(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
