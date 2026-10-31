package com.delivery.privacy.controller;

import com.delivery.privacy.dto.ConsentResponse;
import com.delivery.privacy.dto.GrantConsentRequest;
import com.delivery.privacy.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's versioned consents (LAC-63, W7-C): TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING. Any authenticated profile. */
@RestController
@RequestMapping("/api/v1/me/consents")
@PreAuthorize("isAuthenticated()")
public class ConsentController {
    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @Operation(operationId = "listMyConsents", summary = "List the caller's consent state per purpose")
    @GetMapping
    public List<ConsentResponse> list() {
        return service.list();
    }

    @Operation(operationId = "grantMyConsent", summary = "Grant a consent for the current text version",
            description = "409 consent_version_outdated for any other version; 400 invalid_consent_purpose for an unknown purpose.")
    @PutMapping("/{purpose}")
    public ConsentResponse grant(@PathVariable String purpose, @Valid @RequestBody GrantConsentRequest request) {
        return service.grant(purpose, request.version());
    }

    @Operation(operationId = "withdrawMyConsent", summary = "Withdraw a consent", description = "404 consent_not_found when none is active.")
    @DeleteMapping("/{purpose}")
    public ResponseEntity<Void> withdraw(@PathVariable String purpose) {
        service.withdraw(purpose);
        return ResponseEntity.noContent().build();
    }
}
