package com.delivery.catalog.controller;

import com.delivery.catalog.dto.PendingPriceChangeResponse;
import com.delivery.catalog.dto.RejectPriceChangeRequest;
import com.delivery.catalog.service.PriceModerationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OPS/ADMIN moderation queue for over-threshold price changes (spec 002 US-4). */
@RestController
@RequestMapping("/api/v1/catalog/moderation/price-changes")
@PreAuthorize("hasAnyRole('OPS','ADMIN')")
public class PriceModerationController {
    private final PriceModerationService moderationService;

    public PriceModerationController(PriceModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping
    public List<PendingPriceChangeResponse> listPending() {
        return moderationService.listPending();
    }

    @PostMapping("/{skuId}/approve")
    public void approve(@PathVariable UUID skuId) {
        moderationService.approve(skuId);
    }

    @PostMapping("/{skuId}/reject")
    public void reject(@PathVariable UUID skuId, @Valid @RequestBody RejectPriceChangeRequest request) {
        moderationService.reject(skuId, request.reason());
    }
}
