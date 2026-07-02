package com.delivery.order.mapper;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.dto.OrderItemResponse;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.dto.TrackingResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Converts order entities to API DTOs. */
@Component
public class OrderMapper {
    private final AppUserProfileRepository userProfileRepository;
    private final TenantContext tenantContext;

    public OrderMapper(AppUserProfileRepository userProfileRepository, TenantContext tenantContext) {
        this.userProfileRepository = userProfileRepository;
        this.tenantContext = tenantContext;
    }

    /** Customer-only response that includes delivery code - only for order owners */
    public OrderResponse toCustomerResponse(Order order) {
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), order.getDeliveryConfirmationCodeDisplay(),
                null, null, null, null);
    }

    /** Customer-only response with names - only for order owners */
    public OrderResponse toCustomerResponse(Order order, String customerName, String vendorName) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), order.getDeliveryConfirmationCodeDisplay(),
                customerName, vendorName, order.getCreatedAt(), items);
    }

    /** Admin/vendor response that excludes delivery code */
    public AdminOrderResponse toAdminResponse(Order order, String customerName, String vendorName) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new AdminOrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), customerName, vendorName, order.getCreatedAt(), items);
    }

    /** Context-aware response that includes delivery code only for the customer who owns the order */
    public OrderResponse toResponse(Order order) {
        if (isCustomerOwner(order.getCustomerId())) {
            return toCustomerResponse(order);
        }
        // For non-owners, return response without delivery code
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), null, null, null, null, null);
    }

    /** Context-aware response with names that includes delivery code only for the customer who owns the order */
    public OrderResponse toResponse(Order order, String customerName, String vendorName) {
        if (isCustomerOwner(order.getCustomerId())) {
            return toCustomerResponse(order, customerName, vendorName);
        }
        // For non-owners, return response without delivery code
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getTotal(), null, customerName, vendorName, order.getCreatedAt(), items);
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductNameSnapshot(),
                item.getSkuNameSnapshot(), item.getUnitPriceSnapshot(),
                item.getQuantity(), item.getLineTotal());
    }

    public TrackingResponse toTrackingResponse(Order order) {
        return new TrackingResponse(order.getId(), order.getReference(), order.getStatus(), order.getDeliveryConfirmationCodeDisplay());
    }

    /** Check if the current authenticated user is the customer who owns this order */
    private boolean isCustomerOwner(UUID orderCustomerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        
        // Check if user has CUSTOMER role
        boolean isCustomer = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()));
        
        if (!isCustomer) {
            return false;
        }
        
        // Use the same logic as OrderTrackingService to get current user
        Optional<UUID> tenantId = tenantContext.currentTenantId();
        Optional<String> keycloakUserId = tenantContext.currentKeycloakUserId();
        
        if (tenantId.isEmpty() || keycloakUserId.isEmpty()) {
            return false;
        }
        
        Optional<AppUserProfile> currentUser = userProfileRepository
                .findByTenantIdAndKeycloakUserId(tenantId.get(), keycloakUserId.get());
        
        return currentUser.map(user -> user.getId().equals(orderCustomerId)).orElse(false);
    }
}
