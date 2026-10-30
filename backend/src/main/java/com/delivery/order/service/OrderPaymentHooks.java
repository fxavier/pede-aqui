package com.delivery.order.service;

import com.delivery.common.exception.NotFoundException;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.api.DeliveryRetirementService;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.api.PaymentOrderHooks;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Order side of payment confirmation and full refund (ADR-0002 C1): implements the payment API's
 * {@link PaymentOrderHooks}, called by PaymentService inside its transaction after it locked the payment row (lock
 * order rule: payment -> order -> delivery/dispatch -> inventory). Logic moved verbatim from PaymentService.
 */
@Component
public class OrderPaymentHooks implements PaymentOrderHooks {
    /** Order statuses that by themselves mean the goods left the store (V043 COMMIT set) — covers rows without a delivery. */
    private static final Set<OrderStatus> ORDER_STATUSES_PAST_PICKUP = EnumSet.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final DeliveryRetirementService deliveryRetirementService;
    private final OrderStockService orderStockService;
    private final AuditLogService auditLogService;
    private final OrderStateMachine stateMachine;

    public OrderPaymentHooks(OrderRepository orderRepository, DeliveryRetirementService deliveryRetirementService,
                             OrderStockService orderStockService, AuditLogService auditLogService, OrderStateMachine stateMachine) {
        this.stateMachine = stateMachine;
        this.orderRepository = orderRepository;
        this.deliveryRetirementService = deliveryRetirementService;
        this.orderStockService = orderStockService;
        this.auditLogService = auditLogService;
    }

    /** Customer, PAYMENT_PENDING flag and reference of the payment's order (404 when missing). */
    @Override
    public OrderForPayment orderForPayment(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        return new OrderForPayment(order.getCustomerId(), order.getStatus() == OrderStatus.PAYMENT_PENDING, order.getReference());
    }

    /** PAYMENT_PENDING -> PAYMENT_CONFIRMED through the state machine (409 invalid_order_transition otherwise). */
    @Override
    public void markPaymentConfirmed(UUID tenantId, UUID orderId) {
        stateMachine.transition(findOrder(tenantId, orderId), OrderStatus.PAYMENT_CONFIRMED, "PAYMENT_CONFIRMED");
    }

    /**
     * Full coverage closes what the order's delivery row allows: unless the goods already left the store (delivery
     * status picked up or later, or an order status past pickup — V043 rule) the delivery is retired (CANCELLED, active
     * dispatch job CANCELLED) and the stock reservation released; both idempotent. The order becomes REFUNDED unless
     * it is CANCELLED (order and financial state are independent) or already REFUNDED; the state machine refuses
     * (409 invalid_order_transition, everything rolls back) a full refund of an order that was never paid
     * (PAYMENT_PENDING/PENDING).
     */
    @Override
    public void onPaymentFullyRefunded(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        // Decide on the stock before the REFUNDED transition changes the order status it reads. One delivery per order
        // (uq_deliveries_tenant_order); absent only for legacy rows.
        boolean goodsLeftTheStore = deliveryRetirementService.isGoodsLeftTheStore(tenantId, order.getId())
                || ORDER_STATUSES_PAST_PICKUP.contains(order.getStatus());
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REFUNDED) {
            stateMachine.transition(order, OrderStatus.REFUNDED, "FULL_REFUND_APPROVED");
            auditLogService.log("ORDER_REFUNDED", "order", order.getId().toString(), order.getReference(), "SUCCESS");
        }
        if (!goodsLeftTheStore) {
            deliveryRetirementService.retireBeforePickup(tenantId, order.getId(), "order fully refunded");
            orderStockService.release(order); // INC-07 (COR-01): the sale is undone before pickup; no-op if already released/committed
        }
    }

    private Order findOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
    }
}
