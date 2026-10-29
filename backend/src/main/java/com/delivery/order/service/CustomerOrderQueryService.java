package com.delivery.order.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.CustomerOrderDetailResponse;
import com.delivery.order.dto.CustomerOrderSummaryResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.PricingQuoteSnapshot;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.CustomerOrderQueryRepository;
import com.delivery.vendor.api.VendorNames;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only order history of the authenticated customer (LAC-21): a keyset-paged list and the detail of one order.
 * Ownership is resolved from the JWT (tenant + Keycloak subject → profile id) and enforced in the query itself,
 * so an order of another customer or tenant is a 404, never a 403 that would reveal it exists.
 */
@Service
public class CustomerOrderQueryService {
    private static final String UNKNOWN_VENDOR = "Unknown";

    private final CustomerOrderQueryRepository repository;
    private final UserAccounts userAccounts;
    private final VendorNames vendorNames;
    private final OrderMapper mapper;
    private final TenantContext tenantContext;

    public CustomerOrderQueryService(CustomerOrderQueryRepository repository, UserAccounts userAccounts,
                                     VendorNames vendorNames, OrderMapper mapper, TenantContext tenantContext) {
        this.repository = repository;
        this.userAccounts = userAccounts;
        this.vendorNames = vendorNames;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** One page of the caller's own orders, newest first ({@code limit} clamped to 1..200, opaque cursor). */
    @Transactional(readOnly = true)
    public CursorPage<CustomerOrderSummaryResponse> listMine(int limit, String cursor) {
        int effectiveLimit = PageLimits.cursorLimit(limit);
        KeysetCursor position = KeysetCursor.decodeOrStart(cursor);
        UUID tenantId = tenantId();
        Optional<UUID> customerId = currentCustomerId(tenantId);
        if (customerId.isEmpty()) {
            // No profile in this tenant yet: the customer simply has no orders here.
            return new CursorPage<>(List.of(), null);
        }
        List<Order> rows = repository.findCustomerKeysetPage(tenantId, customerId.get(), position.createdAt(), position.id(),
                PageLimits.keysetProbe(effectiveLimit));
        return CursorPage.fromProbe(rows, effectiveLimit, o -> new KeysetCursor(o.getCreatedAt(), o.getId()), this::toSummaries);
    }

    /** The caller's own order with items, address snapshot and totals; 404 when it is not theirs. */
    @Transactional(readOnly = true)
    public CustomerOrderDetailResponse getMine(UUID orderId) {
        UUID tenantId = tenantId();
        Order order = currentCustomerId(tenantId)
                .flatMap(customerId -> repository.findByTenantIdAndIdAndCustomerId(tenantId, orderId, customerId))
                .orElseThrow(() -> new NotFoundException("Order was not found"));
        PricingQuoteSnapshot quote = order.getPricingQuote();
        return new CustomerOrderDetailResponse(order.getId(), order.getReference(), order.getStatus(), order.getCancellationReason(),
                order.getVendorId(), vendorNames(Set.of(order.getVendorId())).getOrDefault(order.getVendorId(), UNKNOWN_VENDOR),
                order.getCreatedAt(), order.getItems().stream().map(mapper::toItemResponse).toList(),
                OrderMapper.toAddressResponse(order.getDeliveryAddress()),
                // The entity clears the plain PIN when the order is delivered, cancelled or refunded.
                order.getDeliveryConfirmationCodeDisplay(),
                order.getSubtotal(), order.getFees(),
                quote == null ? null : quote.getDeliveryFee(), quote == null ? null : quote.getServiceFee(),
                order.getTaxes(), order.getDiscountTotal(), order.getTotal(),
                quote == null ? null : quote.getDeliveryFeeBasis(), quote == null ? null : quote.getDistanceKm());
    }

    private List<CustomerOrderSummaryResponse> toSummaries(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = vendorNames(orders.stream().map(Order::getVendorId).collect(Collectors.toSet()));
        Map<UUID, Long> itemCounts = repository.countItemsByOrderIds(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        return orders.stream()
                .map(o -> new CustomerOrderSummaryResponse(o.getId(), o.getReference(), o.getStatus(), o.getCancellationReason(),
                        o.getVendorId(), names.getOrDefault(o.getVendorId(), UNKNOWN_VENDOR),
                        itemCounts.getOrDefault(o.getId(), 0L).intValue(),
                        o.getSubtotal(), o.getFees(), o.getTaxes(), o.getDiscountTotal(), o.getTotal(), o.getCreatedAt()))
                .toList();
    }

    private Map<UUID, String> vendorNames(Set<UUID> vendorIds) {
        return vendorNames.namesOf(vendorIds);
    }

    private Optional<UUID> currentCustomerId(UUID tenantId) {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        return userAccounts.profileIdOf(tenantId, keycloakUserId);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
