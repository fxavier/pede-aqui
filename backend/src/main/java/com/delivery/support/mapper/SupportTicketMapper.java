package com.delivery.support.mapper;

import com.delivery.support.dto.SupportTicketResponse;
import com.delivery.support.entity.SupportTicket;
import org.springframework.stereotype.Component;

/** Maps support ticket entities into customer and backoffice read models. */
@Component
public class SupportTicketMapper {
    public SupportTicketResponse toCustomerResponse(SupportTicket ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getClassification(),
                null,
                ticket.getAssigneeUserId(),
                ticket.getCreatedAt());
    }

    public SupportTicketResponse toBackofficeResponse(SupportTicket ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getClassification(),
                ticket.getInternalNotes(),
                ticket.getAssigneeUserId(),
                ticket.getCreatedAt());
    }
}
