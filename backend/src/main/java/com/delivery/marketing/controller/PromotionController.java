package com.delivery.marketing.controller;

import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.dto.PromotionUpsertRequest;
import com.delivery.marketing.entity.PromotionStatus;
import com.delivery.marketing.service.PromotionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes spec-002 promotion CRUD and lifecycle endpoints (vendor-scoped for VENDOR_ADMIN, per spec §3). */
@RestController
@RequestMapping("/api/v1/marketing/promotions")
@PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN')")
public class PromotionController {
    private final PromotionService service;

    public PromotionController(PromotionService service) { this.service = service; }

    @GetMapping
    public List<PromotionResponse> list(@RequestParam(required = false) PromotionStatus status,
                                        @RequestParam(required = false) UUID vendorId) {
        return service.list(status, vendorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse create(@Valid @RequestBody PromotionUpsertRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{promotionId}")
    public PromotionResponse update(@PathVariable UUID promotionId, @Valid @RequestBody PromotionUpsertRequest request) {
        return service.update(promotionId, request);
    }

    @DeleteMapping("/{promotionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID promotionId) {
        service.delete(promotionId);
    }

    @PostMapping("/{promotionId}/activate")
    public PromotionResponse activate(@PathVariable UUID promotionId) {
        return service.activate(promotionId);
    }

    @PostMapping("/{promotionId}/pause")
    public PromotionResponse pause(@PathVariable UUID promotionId) {
        return service.pause(promotionId);
    }
}
