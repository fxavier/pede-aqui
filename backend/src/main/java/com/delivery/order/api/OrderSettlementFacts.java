package com.delivery.order.api;

import com.delivery.order.entity.CancellationRequestStatus;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.entity.OrderStatusHistory;
import com.delivery.order.repository.CancellationRequestRepository;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only order facts for the settlement ledger (LAC-33/34, W6-B): the immutable money snapshot taken at checkout,
 * when the order was delivered (from {@code order_status_history}) and the cancellation fee actually withheld (from
 * the approved {@code cancellation_requests} row), so settlement never reaches into order's repositories or entities.
 * Tenant-scoped; statuses are returned as names; no personal data.
 */
@Component
public class OrderSettlementFacts {
    /**
     * Money snapshot of an order: {@code subtotal} = goods, {@code fees} = delivery + service fee, {@code taxes} = tax
     * charged on the goods, {@code discountTotal} = promotion discount, {@code total} = what the customer was charged.
     * {@code deliveredAt} is the DELIVERED transition instant (null when never delivered); {@code retainedCancellationFee}
     * is the fee withheld by an approved customer cancellation (LAC-26), zero otherwise.
     */
    public record OrderFinancials(UUID tenantId, UUID orderId, UUID vendorId, String status, String cancellationReason,
                                  BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discountTotal,
                                  BigDecimal total, Instant deliveredAt, Instant cancelledAt,
                                  BigDecimal retainedCancellationFee) {}

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CancellationRequestRepository cancellationRequestRepository;

    public OrderSettlementFacts(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                                CancellationRequestRepository cancellationRequestRepository) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.cancellationRequestRepository = cancellationRequestRepository;
    }

    /** The order's money snapshot and settlement-relevant instants, if the order exists in the tenant. */
    @Transactional(readOnly = true)
    public Optional<OrderFinancials> financials(UUID tenantId, UUID orderId) {
        return orderRepository.findByTenantIdAndId(tenantId, orderId).map(order -> {
            Instant deliveredAt = firstTransitionTo(order, OrderStatus.DELIVERED);
            Instant cancelledAt = firstTransitionTo(order, OrderStatus.CANCELLED);
            BigDecimal fee = cancellationRequestRepository
                    .findFirstByTenantIdAndOrderIdOrderByRequestedAtDescIdDesc(tenantId, orderId)
                    .filter(request -> request.getStatus() == CancellationRequestStatus.APPROVED)
                    .map(request -> request.getFeeAmount() == null ? BigDecimal.ZERO : request.getFeeAmount())
                    .orElse(BigDecimal.ZERO);
            return new OrderFinancials(order.getTenantId(), order.getId(), order.getVendorId(), order.getStatus().name(),
                    order.getCancellationReason() == null ? null : order.getCancellationReason().name(),
                    order.getSubtotal(), order.getFees(), order.getTaxes(), order.getDiscountTotal(), order.getTotal(),
                    deliveredAt, cancelledAt, fee);
        });
    }

    /**
     * First history instant at which the order entered {@code target}. Orders older than V058 have no history row: a
     * row still in that status falls back to its last update instant.
     */
    private Instant firstTransitionTo(Order order, OrderStatus target) {
        return historyRepository.findByTenantIdAndOrderIdOrderByCreatedAtAscIdAsc(order.getTenantId(), order.getId()).stream()
                .filter(row -> row.getToStatus() == target)
                .map(OrderStatusHistory::getCreatedAt)
                .findFirst()
                .orElse(order.getStatus() == target ? order.getUpdatedAt() : null);
    }
}
