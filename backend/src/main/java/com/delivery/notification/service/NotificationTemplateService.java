package com.delivery.notification.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.config.NotificationProperties;
import com.delivery.notification.dto.NotificationTemplateResponse;
import com.delivery.notification.dto.UpdateNotificationTemplateRequest;
import com.delivery.notification.entity.NotificationTemplate;
import com.delivery.notification.repository.NotificationTemplateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification templates (LAC-48): resolves the effective text of a (key, channel) for a tenant — the tenant's
 * override, else the platform default — and lets OPS/ADMIN list and override them (audited). Only keys/channels that
 * have a platform default can be overridden (the code decides which facts notify), and only safe placeholders are
 * accepted: a PIN/OTP can never be put into a template through the API.
 */
@Service
public class NotificationTemplateService {
    /** Placeholders the dispatchers fill today. Extend together with the callers' params. */
    public static final Set<String> SAFE_PLACEHOLDERS = Set.of("orderReference");
    static final int MAX_SMS_BODY = 320;
    static final int MAX_BODY = 2000;

    private final NotificationTemplateRepository repository;
    private final NotificationProperties properties;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public NotificationTemplateService(NotificationTemplateRepository repository, NotificationProperties properties,
                                       TenantContext tenantContext, AuditLogService auditLogService) {
        this.repository = repository;
        this.properties = properties;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    /** The template used for this tenant: the tenant override if present, else the platform default (active or not). */
    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> effective(UUID tenantId, String key, NotificationChannel channel) {
        String locale = properties.getLocale();
        return repository.findByTenantIdAndTemplateKeyAndChannelAndLocale(tenantId, key, channel, locale)
                .or(() -> repository.findDefault(key, channel, locale));
    }

    /** Effective templates of the caller's tenant (defaults merged with overrides), sorted by key and channel. */
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> listForCurrentTenant() {
        UUID tenantId = tenantId();
        Map<String, NotificationTemplate> effective = new LinkedHashMap<>();
        for (NotificationTemplate template : repository.findDefaultsAndOverrides(tenantId, properties.getLocale())) {
            String slot = template.getTemplateKey() + "/" + template.getChannel();
            if (template.getTenantId() != null || !effective.containsKey(slot)) {
                effective.put(slot, template);
            }
        }
        return effective.values().stream().map(NotificationTemplateService::toResponse).toList();
    }

    /**
     * Creates or replaces the caller tenant's override of a (key, channel). 404 {@code template_not_found} when no
     * platform default exists; 400 {@code template_placeholder_not_allowed} / {@code template_too_long} for invalid
     * content. Every change bumps the version and is audited ({@code NOTIFICATION_TEMPLATE_UPDATED}, no body text).
     */
    @Transactional
    public NotificationTemplateResponse updateForCurrentTenant(String key, NotificationChannel channel,
                                                               UpdateNotificationTemplateRequest request) {
        UUID tenantId = tenantId();
        String locale = properties.getLocale();
        NotificationTemplate base = repository.findDefault(key, channel, locale)
                .orElseThrow(() -> new NotFoundException("Notification template was not found"));
        validate(channel, request);
        String title = channel == NotificationChannel.SMS ? null : blankToNull(request.title());
        NotificationTemplate override = repository.findByTenantIdAndTemplateKeyAndChannelAndLocale(tenantId, key, channel, locale)
                .orElse(null);
        if (override == null) {
            override = NotificationTemplate.overrideOf(tenantId, key, channel, locale, base.getVersion());
            override.update(title, request.body(), request.active(), actor(), true);
            override = repository.save(override);
        } else {
            override.update(title, request.body(), request.active(), actor(), true);
        }
        auditLogService.log(AuditActions.NOTIFICATION_TEMPLATE_UPDATED, "notification_template", override.getId().toString(),
                key + "/" + channel, "SUCCESS",
                Map.of("templateKey", key, "channel", channel.name(), "version", override.getVersion(), "active", override.isActive()));
        return toResponse(override);
    }

    private static void validate(NotificationChannel channel, UpdateNotificationTemplateRequest request) {
        Set<String> used = new java.util.LinkedHashSet<>(TemplateRenderer.placeholders(request.body()));
        used.addAll(TemplateRenderer.placeholders(request.title()));
        for (String name : used) {
            if (TemplateRenderer.isSensitive(name) || !SAFE_PLACEHOLDERS.contains(name)) {
                throw new BusinessException("template_placeholder_not_allowed",
                        "Placeholder {" + name + "} is not allowed; allowed: " + SAFE_PLACEHOLDERS, HttpStatus.BAD_REQUEST);
            }
        }
        int max = channel == NotificationChannel.SMS ? MAX_SMS_BODY : MAX_BODY;
        if (request.body().length() > max) {
            throw new BusinessException("template_too_long", "Template body exceeds " + max + " characters", HttpStatus.BAD_REQUEST);
        }
    }

    static NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return new NotificationTemplateResponse(template.getTemplateKey(), template.getChannel().name(), template.getLocale(),
                template.getTitle(), template.getBody(), template.isActive(), template.getVersion(),
                template.getTenantId() == null ? "DEFAULT" : "TENANT", template.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String actor() {
        return tenantContext.currentKeycloakUserId().orElse("system");
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
