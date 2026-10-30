package com.delivery.common.repository;

import com.delivery.common.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Provides tenant-scoped persistence access for audit entries. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByTenantId(UUID tenantId, Pageable pageable);

    /** Keyset page (LAC-14): entries strictly older than {@code (createdAt, id)}, newest first; index from V054. */
    @Query("""
            select a from AuditLog a
             where a.tenantId = :tenantId
               and (a.createdAt < :createdAt or (a.createdAt = :createdAt and a.id < :id))
             order by a.createdAt desc, a.id desc""")
    List<AuditLog> findKeysetPage(@Param("tenantId") UUID tenantId, @Param("createdAt") Instant createdAt, @Param("id") UUID id, Pageable limit);
}
