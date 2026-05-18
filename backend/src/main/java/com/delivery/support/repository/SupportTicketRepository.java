package com.delivery.support.repository;

import com.delivery.support.entity.SupportTicket;
import com.delivery.support.entity.SupportTicketStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Provides tenant-scoped support ticket persistence queries. */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    Optional<SupportTicket> findByTenantIdAndId(UUID tenantId, UUID id);
    List<SupportTicket> findByTenantId(UUID tenantId);
    List<SupportTicket> findByTenantIdAndCreatorUserId(UUID tenantId, String creatorUserId);
    List<SupportTicket> findByTenantIdAndStatus(UUID tenantId, SupportTicketStatus status);
    List<SupportTicket> findByTenantIdAndAssigneeUserId(UUID tenantId, String assigneeUserId);
    List<SupportTicket> findByTenantIdAndOrderId(UUID tenantId, UUID orderId);
}
