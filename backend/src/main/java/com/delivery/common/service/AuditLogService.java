package com.delivery.common.service;

import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.dto.AuditLogResponse;
import com.delivery.common.entity.AuditLog;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.observability.BusinessMetrics;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.repository.AuditLogRepository;
import com.delivery.common.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Persists and exposes audit entries for sensitive admin and ops actions. Each entry also records the request
 * context (LAC-15): correlation id (MDC set by {@link CorrelationIdFilter}), remote address and User-Agent when
 * written inside an HTTP request, and optional structured {@code details} whose secret-looking keys are masked.
 */
@Service
public class AuditLogService {
    static final int USER_AGENT_MAX = 512;
    static final int IP_MAX = 64;
    static final int CORRELATION_ID_MAX = 128;
    static final String MASK = "***";
    /** Key words whose values are never stored: PINs/delivery codes, OTPs, passwords, tokens, secrets. */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "pin", "code", "otp", "password", "passcode", "passwd", "token", "secret", "authorization", "apikey", "cvv");

    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public AuditLogService(AuditLogRepository auditLogRepository, TenantContext tenantContext) {
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
    }

    /** Optional: slice tests without a meter registry keep the no-op metrics. */
    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) {
        this.metrics = metrics;
    }

    /** Writes a sensitive action audit entry without recording secret values. */
    @Transactional
    public void log(String action, String targetType, String targetId, String businessReference, String result) {
        log(action, targetType, targetId, businessReference, result, null);
    }

    /** Writes an audit entry with structured details (secret-looking keys are masked before persisting). */
    @Transactional
    public void log(String action, String targetType, String targetId, String businessReference, String result, Map<String, Object> details) {
        logForTenant(tenantId(), action, targetType, targetId, businessReference, result, details);
    }

    /**
     * Writes an audit entry for an explicit tenant: platform-level actions (tenant creation/status) run without a
     * tenant context and are recorded against the tenant they affect.
     */
    @Transactional
    public void logForTenant(UUID tenantId, String action, String targetType, String targetId, String businessReference, String result, Map<String, Object> details) {
        String actor = tenantContext.currentKeycloakUserId().orElse("unknown");
        HttpServletRequest request = currentRequest();
        AuditLog entry = new AuditLog(UUID.randomUUID(), tenantId, actor, action, targetType, targetId, businessReference, result)
                .withContext(
                        request == null ? null : truncate(request.getRemoteAddr(), IP_MAX),
                        request == null ? null : truncate(request.getHeader("User-Agent"), USER_AGENT_MAX),
                        truncate(MDC.get(CorrelationIdFilter.MDC_KEY), CORRELATION_ID_MAX),
                        details == null ? null : maskMap(details));
        auditLogRepository.save(entry);
        // LAC-50: audit_events_total{action,result}, only once the business transaction commits. Covers payments
        // confirmed (PAYMENT_CONFIRMED), refunds approved (REFUND_APPROVED), PIN lockouts (DELIVERY_CODE_LOCKED)…
        metrics.incrementAfterCommit(BusinessMetrics.AUDIT_EVENTS,
                "action", BusinessMetrics.safeAction(action), "result", BusinessMetrics.safeAction(result));
    }

    /** Lists the newest tenant audit entries, capped at {@link PageLimits#LEGACY_LIST_CAP} (legacy array, LAC-14). */
    @Transactional(readOnly = true)
    public CappedList<AuditLogResponse> list() {
        return CappedList.of(auditLogRepository.findByTenantId(tenantId(), PageLimits.legacyCap()), AuditLogService::toResponse);
    }

    /** Lists one keyset page of tenant audit entries, newest first ({@code limit} capped, opaque cursor). */
    @Transactional(readOnly = true)
    public CursorPage<AuditLogResponse> listPage(int limit, String cursor) {
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        List<AuditLog> probe = auditLogRepository.findKeysetPage(tenantId(), position.createdAt(), position.id(), PageLimits.keysetProbe(effectiveLimit));
        return CursorPage.fromProbe(probe, effectiveLimit, item -> new KeysetCursor(item.getCreatedAt(), item.getId()),
                rows -> rows.stream().map(AuditLogService::toResponse).toList());
    }

    static AuditLogResponse toResponse(AuditLog item) {
        return new AuditLogResponse(item.getId(), item.getActorUserId(), item.getAction(), item.getTargetType(), item.getTargetId(),
                item.getBusinessReference(), item.getResult(), item.getCreatedAt(),
                item.getIp(), item.getUserAgent(), item.getCorrelationId(), item.getDetails());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Copies the map, replacing values of secret-looking keys (at any depth) with {@value #MASK}. */
    private static Map<String, Object> maskMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            copy.put(name, isSensitive(name) ? MASK : maskValue(value));
        });
        return copy;
    }

    private static Object maskValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return maskMap(map);
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(AuditLogService::maskValue).toList();
        }
        return value;
    }

    /** Splits camelCase / snake_case / kebab-case keys into words and matches them against the sensitive list. */
    static boolean isSensitive(String key) {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[_\\-.]", " ").toLowerCase(Locale.ROOT);
        for (String word : spaced.split("\\s+")) {
            if (SENSITIVE_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
