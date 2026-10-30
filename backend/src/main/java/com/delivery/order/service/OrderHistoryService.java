package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.order.dto.OrderHistoryResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.repository.OrderStatusHistoryRepository;
import com.delivery.payment.api.PaymentStatusHistoryReader;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads an order's status history and its payment's status history (V058, W4-A). Tenant-scoped; merchant users only
 * see their own vendor's orders (another vendor's order answers 404, like a missing one) and get no actor ids.
 */
@Service
public class OrderHistoryService {
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentStatusHistoryReader paymentHistory;
    private final TenantContext tenantContext;
    private final MerchantScopeGuard merchantScopeGuard;

    public OrderHistoryService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                               PaymentStatusHistoryReader paymentHistory, TenantContext tenantContext,
                               MerchantScopeGuard merchantScopeGuard) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.paymentHistory = paymentHistory;
        this.tenantContext = tenantContext;
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /** Returns the order's status transitions and payment status changes, oldest first. */
    @Transactional(readOnly = true)
    public OrderHistoryResponse history(UUID orderId) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        boolean merchant = merchantScopeGuard.isMerchantScoped();
        if (merchant && !merchantScopeGuard.requireOwnVendorId(tenantId).equals(order.getVendorId())) {
            throw new NotFoundException("Order was not found");
        }
        List<OrderHistoryResponse.OrderStatusHistoryEntry> orderRows = historyRepository.findByTenantIdAndOrderIdOrderByCreatedAtAscIdAsc(tenantId, orderId).stream()
                .map(row -> new OrderHistoryResponse.OrderStatusHistoryEntry(row.getFromStatus() == null ? null : row.getFromStatus().name(),
                        row.getToStatus().name(), row.getActorType(), merchant ? null : row.getActorId(), row.getReason(),
                        row.getCorrelationId(), row.getCreatedAt()))
                .toList();
        List<OrderHistoryResponse.PaymentStatusHistoryEntry> paymentRows = paymentHistory.forOrder(tenantId, orderId).stream()
                .map(row -> new OrderHistoryResponse.PaymentStatusHistoryEntry(row.paymentId(), row.fromStatus(), row.toStatus(), row.actorType(),
                        merchant ? null : row.actorId(), row.reason(), row.correlationId(), row.createdAt()))
                .toList();
        return new OrderHistoryResponse(order.getId(), order.getStatus().name(), orderRows, paymentRows);
    }
}
