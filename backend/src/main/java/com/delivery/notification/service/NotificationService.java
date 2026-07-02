package com.delivery.notification.service;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public NotificationService(NotificationRepository notificationRepository, AppUserProfileRepository userProfileRepository, TenantContext tenantContext) {
        this.notificationRepository = notificationRepository;
        this.userProfileRepository = userProfileRepository;
        this.tenantContext = tenantContext;
    }

    /** Creates a persisted notification record for a specific recipient. */
    @Transactional
    public NotificationResponse create(UUID recipientUserId, String recipientRole, String type, String title, String message, String businessReference) {
        Notification notification = new Notification(UUID.randomUUID(), tenantId(), recipientUserId, recipientRole, type, title, message, businessReference);
        Notification saved = notificationRepository.save(notification);
        LOGGER.info("notification.created type={} recipientRole={} businessReference={}", type, recipientRole, businessReference);
        return toResponse(saved);
    }

    /** Returns notifications that belong to the authenticated user only. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine() {
        UUID tenantId = tenantId();
        UUID profileId = currentProfileId(tenantId);
        return notificationRepository.findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantId, profileId)
                .stream()
                .map(this::toResponse)
                .toList();
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
