package com.delivery.notification.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes recipient-scoped notification endpoints. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    /** Capped legacy array (LAC-14): newest 200, {@code X-Truncated: true} when more exist. */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> listMine() { return notificationService.listMine().toResponse(); }

    /** Keyset page (LAC-14): {@code { items, nextCursor }}, limit 1..200 (default 50). */
    @GetMapping("/cursor")
    public CursorPage<NotificationResponse> listMinePage(
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return notificationService.listMinePage(limit, cursor);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId) { return notificationService.markRead(notificationId); }
}
