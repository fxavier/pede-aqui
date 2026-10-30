package com.delivery.notification.controller;

import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.dto.NotificationTemplateResponse;
import com.delivery.notification.dto.UpdateNotificationTemplateRequest;
import com.delivery.notification.service.NotificationTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OPS/ADMIN management of the tenant's notification templates (LAC-48); updates are audited. */
@RestController
@RequestMapping("/api/v1/ops/notification-templates")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class NotificationTemplateController {
    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    /** Effective templates (platform defaults merged with this tenant's overrides). */
    @GetMapping
    public List<NotificationTemplateResponse> list() {
        return templateService.listForCurrentTenant();
    }

    /** Creates/replaces this tenant's override of one (key, channel). */
    @PutMapping("/{templateKey}/{channel}")
    public NotificationTemplateResponse update(@PathVariable String templateKey, @PathVariable NotificationChannel channel,
                                               @Valid @RequestBody UpdateNotificationTemplateRequest request) {
        return templateService.updateForCurrentTenant(templateKey, channel, request);
    }
}
