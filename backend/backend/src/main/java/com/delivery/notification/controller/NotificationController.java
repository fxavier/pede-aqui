package com.delivery.notification.controller;

import com.delivery.notification.dto.NotificationResponse;
import com.delivery.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes recipient-scoped notification endpoints. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    @GetMapping
    public List<NotificationResponse> listMine() { return notificationService.listMine(); }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable UUID notificationId) { return notificationService.markRead(notificationId); }
}
