package com.delivery.privacy.controller;

import com.delivery.privacy.dto.DeletionRequestResponse;
import com.delivery.privacy.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer's account deletion request (LAC-19, W7-C): request, cancel within the reflection period, read. CUSTOMER only. */
@RestController
@RequestMapping("/api/v1/me/deletion-request")
@PreAuthorize("hasRole('CUSTOMER')")
public class AccountDeletionController {
    private final AccountDeletionService service;

    public AccountDeletionController(AccountDeletionService service) {
        this.service = service;
    }

    @Operation(operationId = "requestAccountDeletion", summary = "Request the deletion of the caller's account",
            description = "202 with a PENDING request scheduled after the reflection period (default 30 days), 200 with the "
                    + "already open request; 409 active_orders_present while an order is in progress.")
    @PostMapping
    public ResponseEntity<DeletionRequestResponse> request() {
        AccountDeletionService.RequestOutcome outcome = service.request();
        return ResponseEntity.status(outcome.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(outcome.request());
    }

    @Operation(operationId = "cancelAccountDeletion", summary = "Cancel the pending account deletion request",
            description = "404 deletion_request_not_found when nothing is pending; 409 deletion_already_processed once erased.")
    @DeleteMapping
    public DeletionRequestResponse cancel() {
        return service.cancel();
    }

    @Operation(operationId = "getAccountDeletionRequest", summary = "Get the caller's latest account deletion request", description = "404 deletion_request_not_found when none.")
    @GetMapping
    public DeletionRequestResponse latest() {
        return service.latest();
    }
}
