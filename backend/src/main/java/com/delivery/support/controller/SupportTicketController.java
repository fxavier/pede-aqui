package com.delivery.support.controller;

import com.delivery.support.dto.ClassifyTicketRequest;
import com.delivery.support.dto.CreateSupportTicketRequest;
import com.delivery.support.dto.InternalNoteRequest;
import com.delivery.support.dto.SupportTicketResponse;
import com.delivery.support.dto.UpdateTicketStatusRequest;
import com.delivery.support.service.SupportTicketService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes customer and backoffice support ticket workflows. */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportTicketController {
    private final SupportTicketService service;

    public SupportTicketController(SupportTicketService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT','ADMIN')")
    public SupportTicketResponse create(@Valid @RequestBody CreateSupportTicketRequest request) {
        return service.create(request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<SupportTicketResponse> mine() { return service.listMine(); }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public List<SupportTicketResponse> listBackoffice() { return service.listBackoffice(); }

    @PatchMapping("/{ticketId}/classify")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public SupportTicketResponse classify(@PathVariable UUID ticketId, @Valid @RequestBody ClassifyTicketRequest request) {
        return service.classify(ticketId, request.classification());
    }

    @PatchMapping("/{ticketId}/status")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public SupportTicketResponse updateStatus(@PathVariable UUID ticketId, @Valid @RequestBody UpdateTicketStatusRequest request) {
        return service.updateStatus(ticketId, request.status());
    }

    @PatchMapping("/{ticketId}/internal-note")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public SupportTicketResponse addInternalNote(@PathVariable UUID ticketId, @Valid @RequestBody InternalNoteRequest request) {
        return service.addInternalNote(ticketId, request.internalNote());
    }

    @PatchMapping("/{ticketId}/resolve")
    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    public SupportTicketResponse resolve(@PathVariable UUID ticketId) { return service.resolve(ticketId); }
}
