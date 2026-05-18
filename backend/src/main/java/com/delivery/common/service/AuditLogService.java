package com.delivery.common.service;

import com.delivery.common.dto.AuditLogResponse;
import com.delivery.common.entity.AuditLog;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.repository.AuditLogRepository;
import com.delivery.common.security.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists and exposes audit entries for sensitive admin and ops actions. */
@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;

    public AuditLogService(AuditLogRepository auditLogRepository, TenantContext tenantContext) {
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
    }

    /** Writes a sensitive action audit entry without recording secret values. */
    @Transactional
    public void log(String action, String targetType, String targetId, String businessReference, String result) {
        UUID tenantId = tenantId();
        String actor = tenantContext.currentKeycloakUserId().orElse("unknown");
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), tenantId, actor, action, targetType, targetId, businessReference, result));
    }

    /** Lists tenant-scoped audit entries for admin and operations visibility. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> list() {
        return auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId()).stream()
                .map(item -> new AuditLogResponse(item.getId(), item.getActorUserId(), item.getAction(), item.getTargetType(), item.getTargetId(), item.getBusinessReference(), item.getResult(), item.getCreatedAt()))
                .toList();
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
