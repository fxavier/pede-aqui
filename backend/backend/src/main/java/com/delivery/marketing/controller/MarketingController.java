package com.delivery.marketing.controller;

import com.delivery.marketing.dto.CouponResponse;
import com.delivery.marketing.dto.CreateCouponRequest;
import com.delivery.marketing.dto.CreatePromotionRequest;
import com.delivery.marketing.dto.PromotionResponse;
import com.delivery.marketing.service.MarketingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes coupon and promotion management endpoints for marketing operations. */
@RestController
@RequestMapping("/api/v1/marketing")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class MarketingController {
    private final MarketingService service;

    public MarketingController(MarketingService service) { this.service = service; }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse createCoupon(@Valid @RequestBody CreateCouponRequest request) {
        return service.createCoupon(request);
    }

    @GetMapping("/coupons")
    public List<CouponResponse> listCoupons() { return service.listCoupons(); }

    @PatchMapping("/coupons/{id}/deactivate")
    public CouponResponse deactivateCoupon(@PathVariable UUID id) { return service.deactivateCoupon(id); }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        return service.createPromotion(request);
    }

    @GetMapping("/promotions")
    public List<PromotionResponse> listPromotions() { return service.listPromotions(); }

    @PatchMapping("/promotions/{id}/deactivate")
    public PromotionResponse deactivatePromotion(@PathVariable UUID id) { return service.deactivatePromotion(id); }
}
