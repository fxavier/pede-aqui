package com.delivery.notification.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.dto.NotificationDeliveryResponse;
import com.delivery.notification.entity.NotificationDelivery;
import com.delivery.notification.service.NotificationDeliveryLogService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Masked notification delivery log for SUPPORT (and OPS/ADMIN), LAC-48. */
@RestController
@RequestMapping("/api/v1/support/notification-deliveries")
@PreAuthorize("hasAnyRole('SUPPORT','OPS','ADMIN')")
public class NotificationDeliveryLogController {
    private final NotificationDeliveryLogService logService;

    public NotificationDeliveryLogController(NotificationDeliveryLogService logService) {
        this.logService = logService;
    }

    /** {@code { content, page, size, totalElements }}, newest first, size 1..100 (default 20). */
    @GetMapping
    public PageResponse<NotificationDeliveryResponse> search(
            @RequestParam(required = false) NotificationDelivery.Status status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) UUID recipientProfileId,
            @RequestParam(required = false) String businessReference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return logService.search(status, channel, recipientProfileId, businessReference, page, size);
    }
}
