package com.delivery.notification.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.TenantContext;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.dto.NotificationDeliveryResponse;
import com.delivery.notification.entity.NotificationDelivery;
import com.delivery.notification.repository.NotificationDeliveryRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SUPPORT/OPS/ADMIN view of the notification delivery log (LAC-48): tenant-scoped, paged (max 100), newest first,
 * masked — recipients only in masked form and no rendered text.
 */
@Service
public class NotificationDeliveryLogService {
    private final NotificationDeliveryRepository repository;
    private final TenantContext tenantContext;

    public NotificationDeliveryLogService(NotificationDeliveryRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    /** One page of the caller tenant's deliveries; every filter is optional. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationDeliveryResponse> search(NotificationDelivery.Status status, NotificationChannel channel,
                                                             UUID recipientProfileId, String businessReference, int page, int size) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        String reference = businessReference == null || businessReference.isBlank() ? null : businessReference.trim();
        return PageResponse.of(repository.search(tenantId, status, channel, recipientProfileId, reference, PageLimits.page(page, size)),
                NotificationDeliveryLogService::toResponse);
    }

    private static NotificationDeliveryResponse toResponse(NotificationDelivery delivery) {
        return new NotificationDeliveryResponse(delivery.getId(), delivery.getChannel().name(), delivery.getTemplateKey(),
                delivery.getTemplateVersion(), delivery.getStatus().name(), delivery.getAttempts(), delivery.getLastError(),
                delivery.getRecipientProfileId(), delivery.getRecipientMasked(), delivery.getBusinessReference(),
                delivery.getProviderMessageId(), delivery.getCreatedAt(), delivery.getNextAttemptAt(), delivery.getSentAt());
    }
}
