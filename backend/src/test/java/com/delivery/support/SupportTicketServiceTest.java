package com.delivery.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.support.dto.CreateSupportTicketRequest;
import com.delivery.support.entity.IncidentClassification;
import com.delivery.support.entity.SupportTicket;
import com.delivery.support.entity.SupportTicketStatus;
import com.delivery.support.mapper.SupportTicketMapper;
import com.delivery.support.repository.SupportTicketRepository;
import com.delivery.support.service.SupportTicketService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportTicketServiceTest {
    @Test
    void coversCreateClassifyStatusNoteAndResolveFlows() {
        UUID tenantId = UUID.randomUUID();
        String userId = "customer-1";
        UUID ticketId = UUID.randomUUID();

        SupportTicketRepository repository = mock(SupportTicketRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of(userId));

        SupportTicket saved = new SupportTicket(ticketId, tenantId, userId, UUID.randomUUID(), "Atraso", "Pedido atrasado");
        when(repository.save(any(SupportTicket.class))).thenReturn(saved);
        when(repository.findByTenantIdAndId(tenantId, ticketId)).thenReturn(Optional.of(saved));
        when(repository.findByTenantIdAndCreatorUserId(tenantId, userId)).thenReturn(List.of(saved));

        SupportTicketService service = new SupportTicketService(repository, new SupportTicketMapper(), auditLogService, tenantContext);

        var created = service.create(new CreateSupportTicketRequest(saved.getOrderId(), "Atraso", "Pedido atrasado"));
        var mine = service.listMine();
        var classified = service.classify(ticketId, IncidentClassification.DELIVERY);
        var status = service.updateStatus(ticketId, SupportTicketStatus.IN_PROGRESS);
        var noted = service.addInternalNote(ticketId, "cliente contactado");
        var resolved = service.resolve(ticketId);

        assertThat(created.subject()).isEqualTo("Atraso");
        assertThat(mine).hasSize(1);
        assertThat(classified.classification()).isEqualTo(IncidentClassification.DELIVERY);
        assertThat(status.status()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        assertThat(noted.internalNote()).isEqualTo("cliente contactado");
        assertThat(resolved.status()).isEqualTo(SupportTicketStatus.RESOLVED);
    }
}
