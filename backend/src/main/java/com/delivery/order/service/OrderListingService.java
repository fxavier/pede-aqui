package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.pagination.CappedList;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderListingRepository;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backoffice order listings (LAC-14): the capped legacy array ({@code GET /orders}), the offset page
 * ({@code /orders/page}) and the keyset page ({@code /orders/cursor}), all with one scoping rule: own vendor for
 * merchant users, tenant-wide for OPS/ADMIN, and for the platform super-admin the impersonated tenant when
 * {@code X-Tenant-Id} is sent, every tenant otherwise (W3-D). Name resolution and SUPPORT masking are reused
 * from OrderService.
 */
@Service
public class OrderListingService {
    private final OrderListingRepository listingRepository;
    private final OrderService orderService;
    private final TenantContext tenantContext;
    private final SalesAccessGuard accessGuard;

    public OrderListingService(OrderListingRepository listingRepository, OrderService orderService, TenantContext tenantContext,
                               MerchantScopeGuard merchantScopeGuard) {
        this.listingRepository = listingRepository;
        this.orderService = orderService;
        this.tenantContext = tenantContext;
        this.accessGuard = new SalesAccessGuard(merchantScopeGuard);
    }

    /** Returns the caller's newest 200 orders (legacy array; {@code truncated} when more exist). */
    @Transactional(readOnly = true)
    public CappedList<AdminOrderResponse> listForCurrentContext() {
        Page<Order> orders = page(resolveScope(), PageLimits.legacyCap());
        return new CappedList<>(orderService.toAdminResponses(orders.getContent()), orders.hasNext());
    }

    /** Returns one offset page of the caller's orders, newest first (page >= 0, size clamped to 1..100). */
    @Transactional(readOnly = true)
    public AdminOrderPageResponse listPageForCurrentContext(int page, int size) {
        Pageable pageable = PageLimits.page(page, size);
        Page<Order> result = page(resolveScope(), pageable);
        return new AdminOrderPageResponse(orderService.toAdminResponses(result.getContent()),
                pageable.getPageNumber(), pageable.getPageSize(), result.getTotalElements());
    }

    /** Returns one page of the caller's orders, newest first ({@code limit} capped at 200, opaque cursor). */
    @Transactional(readOnly = true)
    public CursorPage<AdminOrderResponse> listCursorForCurrentContext(int limit, String cursor) {
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        Pageable probe = PageLimits.keysetProbe(effectiveLimit);
        Scope scope = resolveScope();
        List<Order> rows;
        if (scope.vendorId() != null) {
            rows = listingRepository.findVendorKeysetPage(scope.tenantId(), scope.vendorId(), position.createdAt(), position.id(), probe);
        } else if (scope.tenantId() != null) {
            rows = listingRepository.findTenantKeysetPage(scope.tenantId(), position.createdAt(), position.id(), probe);
        } else {
            rows = listingRepository.findAllKeysetPage(position.createdAt(), position.id(), probe);
        }
        return CursorPage.fromProbe(rows, effectiveLimit, o -> new KeysetCursor(o.getCreatedAt(), o.getId()), orderService::toAdminResponses);
    }

    private Page<Order> page(Scope scope, Pageable pageable) {
        if (scope.vendorId() != null) {
            return listingRepository.findByTenantIdAndVendorId(scope.tenantId(), scope.vendorId(), pageable);
        }
        if (scope.tenantId() != null) {
            return listingRepository.findByTenantId(scope.tenantId(), pageable);
        }
        return listingRepository.findAllBy(pageable);
    }

    /**
     * Merchant users → (tenant, own vendor). Platform super-admin → the impersonated tenant when X-Tenant-Id was sent
     * (TenantRequestFilter already validated and audited it), else no tenant at all (cross-tenant view). Everyone
     * else → its tenant, 403 tenant_required when none resolves.
     */
    private Scope resolveScope() {
        if (accessGuard.isMerchantScoped()) {
            UUID tenantId = requireTenantId();
            return new Scope(tenantId, accessGuard.requireOwnVendorId(tenantId));
        }
        if (tenantContext.isPlatformAdmin()) {
            Optional<UUID> impersonated = tenantContext.currentTenantId();
            return new Scope(impersonated.orElse(null), null);
        }
        return new Scope(requireTenantId(), null);
    }

    private UUID requireTenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }

    /** tenantId null = every tenant (platform super-admin only); vendorId null = whole tenant. */
    private record Scope(UUID tenantId, UUID vendorId) {}
}
