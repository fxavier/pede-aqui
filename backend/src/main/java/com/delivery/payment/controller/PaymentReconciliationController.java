package com.delivery.payment.controller;

import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.payment.dto.ReconciliationCaseResponse;
import com.delivery.payment.dto.ResolveReconciliationCaseRequest;
import com.delivery.payment.entity.PaymentReconciliationCase;
import com.delivery.payment.service.PaymentReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OPS/FINANCE view of payment reconciliation cases (LAC-32, W7-A): tenant-scoped paged list and resolution. */
@RestController
@RequestMapping("/api/v1/payments/reconciliation-cases")
@PreAuthorize("hasAnyRole('OPS','FINANCE','ADMIN')")
public class PaymentReconciliationController {
    private final PaymentReconciliationService service;

    public PaymentReconciliationController(PaymentReconciliationService service) {
        this.service = service;
    }

    @Operation(summary = "Payment reconciliation cases of the tenant, newest first (status OPEN|RESOLVED, optional)")
    @GetMapping
    public PageResponse<ReconciliationCaseResponse> list(@RequestParam(required = false) PaymentReconciliationCase.Status status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "" + PageLimits.DEFAULT_PAGE_SIZE) int size) {
        return service.list(status, page, size);
    }

    @Operation(summary = "Resolve a reconciliation case with a note (idempotent; audited)")
    @PostMapping("/{caseId}/resolve")
    public ReconciliationCaseResponse resolve(@PathVariable UUID caseId, @Valid @RequestBody ResolveReconciliationCaseRequest request) {
        return service.resolve(caseId, request.note());
    }
}
