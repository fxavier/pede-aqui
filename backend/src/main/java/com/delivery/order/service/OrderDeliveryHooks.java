package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.delivery.api.DeliveryAddressResponse;
import com.delivery.delivery.api.DeliveryOrderHooks;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Order side of the courier delivery lifecycle (ADR-0002 C2): implements the delivery API's
 * {@link DeliveryOrderHooks}, which DeliveryService calls inside its transaction. Same rules and order of effects as
 * before the module split — the order transition and the stock lifecycle (INC-07) commit or roll back together with
 * the delivery status.
 */
@Component
public class OrderDeliveryHooks implements DeliveryOrderHooks {
    /**
     * Order statuses in which the goods must not move any further: REFUNDED (money returned; pre-pickup deliveries are
     * retired by PaymentService.approveRefund), CANCELLED (sales cancel, override-cancel, seller rejection, failed
     * delivery) and the legacy REJECTED_BY_VENDOR. All three are terminal: no code path leaves them. REFUND_PENDING is
     * not included: no code path writes it, and a pending refund may still be rejected. DELIVERED is guarded by the
     * delivery's own status (delivery_already_completed).
     */
    private static final Set<OrderStatus> NON_DELIVERABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.REFUNDED, OrderStatus.CANCELLED, OrderStatus.REJECTED_BY_VENDOR);

    private final OrderRepository orderRepository;
    private final OrderStockService orderStockService;

    public OrderDeliveryHooks(OrderRepository orderRepository, OrderStockService orderStockService) {
        this.orderRepository = orderRepository;
        this.orderStockService = orderStockService;
    }

    /** 409 order_not_deliverable when the order was refunded or cancelled; 404 when it does not exist. */
    @Override
    public void requireDeliverable(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        if (NON_DELIVERABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("order_not_deliverable", "The order was " + order.getStatus() + " and can no longer be delivered", HttpStatus.CONFLICT);
        }
    }

    /** INC-07 (COR-01): pickup consumes the reservation; no-op if already committed. */
    @Override
    public void onPickedUp(UUID tenantId, UUID orderId) {
        orderStockService.commit(findOrder(tenantId, orderId));
    }

    /** Order DELIVERED (clears the plain PIN) and the stock lifecycle closed (no-op if pickup committed it). */
    @Override
    public void onDelivered(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        order.markDelivered();
        orderStockService.commit(order);
    }

    /** Order CANCELLED and its reservation released (INC-07; no-op once pickup committed the stock). */
    @Override
    public void onDeliveryFailed(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        order.markCancelled();
        orderStockService.release(order);
    }

    /** The order's checkout address snapshot mapped to the API shape; empty for orders placed without an address. */
    @Override
    public Optional<DeliveryAddressResponse> deliveryAddress(UUID tenantId, UUID orderId) {
        return Optional.ofNullable(OrderMapper.toAddressResponse(findOrder(tenantId, orderId).getDeliveryAddress()));
    }

    private Order findOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
    }
}
