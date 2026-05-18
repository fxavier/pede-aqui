package com.delivery.support.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.support.dto.CreateSupportTicketRequest;
import com.delivery.support.dto.SupportTicketResponse;
import com.delivery.support.entity.SupportTicket;
import com.delivery.support.entity.SupportTicketStatus;
import com.delivery.support.mapper.SupportTicketMapper;
import com.delivery.support.repository.SupportTicketRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles support ticket creation, visibility, classification, and resolution workflows. */
@Service
public class SupportTicketService {
    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMapper supportTicketMapper;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;

    public SupportTicketService(SupportTicketRepository supportTicketRepository, SupportTicketMapper supportTicketMapper, AuditLogService auditLogService, TenantContext tenantContext) {
        this.supportTicketRepository = supportTicketRepository;
        this.supportTicketMapper = supportTicketMapper;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
    }

    /** Creates a support ticket scoped to current tenant and current authenticated creator. */
    @Transactional
    public SupportTicketResponse create(CreateSupportTicketRequest request) {
        SupportTicket ticket = supportTicketRepository.save(new SupportTicket(UUID.randomUUID(), tenantId(), userId(), request.orderId(), request.subject(), request.description()));
        return supportTicketMapper.toCustomerResponse(ticket);
    }

    /** Lists only current customer tickets and hides internal notes by design. */
    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listMine() {
        return supportTicketRepository.findByTenantIdAndCreatorUserId(tenantId(), userId()).stream().map(supportTicketMapper::toCustomerResponse).toList();
    }

    /** Lists all tenant support tickets for support/admin backoffice users. */
    @Transactional(readOnly = true)
    public List<SupportTicketResponse> listBackoffice() {
        return supportTicketRepository.findByTenantId(tenantId()).stream().map(supportTicketMapper::toBackofficeResponse).toList();
    }

    /** Classifies incident type for a ticket and writes an audit entry. */
    @Transactional
    public SupportTicketResponse classify(UUID ticketId, com.delivery.support.entity.IncidentClassification classification) {
        SupportTicket ticket = find(ticketId);
        ticket.classify(classification);
        auditLogService.log("SUPPORT_CLASSIFY", "support_ticket", ticketId.toString(), null, "SUCCESS");
        return supportTicketMapper.toBackofficeResponse(ticket);
    }

    /** Updates ticket status for support workflow and writes an audit entry. */
    @Transactional
    public SupportTicketResponse updateStatus(UUID ticketId, SupportTicketStatus status) {
        SupportTicket ticket = find(ticketId);
        ticket.setStatus(status);
        auditLogService.log("SUPPORT_STATUS", "support_ticket", ticketId.toString(), null, "SUCCESS");
        return supportTicketMapper.toBackofficeResponse(ticket);
    }

    /** Adds internal note visible only to support/admin and writes an audit entry. */
    @Transactional
    public SupportTicketResponse addInternalNote(UUID ticketId, String note) {
        SupportTicket ticket = find(ticketId);
        ticket.addInternalNote(note, userId());
        auditLogService.log("SUPPORT_INTERNAL_NOTE", "support_ticket", ticketId.toString(), null, "SUCCESS");
        return supportTicketMapper.toBackofficeResponse(ticket);
    }

    /** Resolves ticket and records support assignee and audit event. */
    @Transactional
    public SupportTicketResponse resolve(UUID ticketId) {
        SupportTicket ticket = find(ticketId);
        ticket.resolve(userId());
        auditLogService.log("SUPPORT_RESOLVE", "support_ticket", ticketId.toString(), null, "SUCCESS");
        return supportTicketMapper.toBackofficeResponse(ticket);
    }

    private SupportTicket find(UUID ticketId) {
        return supportTicketRepository.findByTenantIdAndId(tenantId(), ticketId).orElseThrow(() -> new NotFoundException("Support ticket was not found"));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }

    private String userId() {
        return tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
    }
}
