package com.delivery.common.repository;

import com.delivery.common.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped persistence access for audit entries. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
