package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderListingRepository;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keyset (cursor) listing of orders for the backoffice (LAC-14), with exactly the scoping of
 * {@link OrderService#listForCurrentContext()}: own vendor for merchant users, tenant-wide for OPS/ADMIN/FINANCE,
 * every tenant for the platform super-admin. Name resolution and SUPPORT masking are reused from OrderService.
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

    /** Returns one page of the caller's orders, newest first ({@code limit} capped at 200, opaque cursor). */
    @Transactional(readOnly = true)
    public CursorPage<AdminOrderResponse> listCursorForCurrentContext(int limit, String cursor) {
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        Pageable probe = PageLimits.keysetProbe(effectiveLimit);
        List<Order> rows;
        if (accessGuard.isMerchantScoped()) {
            UUID tenantId = tenantId();
            rows = listingRepository.findVendorKeysetPage(tenantId, accessGuard.requireOwnVendorId(tenantId), position.createdAt(), position.id(), probe);
        } else if (tenantContext.isPlatformAdmin()) {
            // Platform super-admin: same cross-tenant view as GET /orders (even while impersonating, as there).
            rows = listingRepository.findAllKeysetPage(position.createdAt(), position.id(), probe);
        } else {
            rows = listingRepository.findTenantKeysetPage(tenantId(), position.createdAt(), position.id(), probe);
        }
        return CursorPage.fromProbe(rows, effectiveLimit, o -> new KeysetCursor(o.getCreatedAt(), o.getId()), orderService::toAdminResponses);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
