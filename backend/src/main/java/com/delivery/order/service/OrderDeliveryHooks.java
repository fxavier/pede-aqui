package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.delivery.api.DeliveryAddressResponse;
import com.delivery.delivery.api.DeliveryFailure;
import com.delivery.delivery.api.DeliveryOrderHooks;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final OrderStateMachine stateMachine;
    private DeliveryIncidentService incidentService;

    public OrderDeliveryHooks(OrderRepository orderRepository, OrderStockService orderStockService, OrderStateMachine stateMachine) {
        this.orderRepository = orderRepository;
        this.orderStockService = orderStockService;
        this.stateMachine = stateMachine;
    }

    /** 409 order_not_deliverable when the order was refunded or cancelled; 404 when it does not exist. */
    @Override
    public void requireDeliverable(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        if (NON_DELIVERABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("order_not_deliverable", "The order was " + order.getStatus() + " and can no longer be delivered", HttpStatus.CONFLICT);
        }
    }

    /**
     * INC-07 (COR-01): pickup consumes the reservation (no-op if already committed). W4-A: the order follows the
     * courier, ASSIGNED_TO_COURIER -> PICKED_UP; an order in any other status (legacy rows, an ops override) is left
     * as it is — the sync never moves an order backwards or outside the transition table.
     */
    @Override
    public void onPickedUp(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        if (order.getStatus() == OrderStatus.ASSIGNED_TO_COURIER) {
            stateMachine.transition(order, OrderStatus.PICKED_UP, "DELIVERY_PICKED_UP");
        }
        orderStockService.commit(order);
    }

    /** W4-A: courier on the way / at the customer: ASSIGNED_TO_COURIER or PICKED_UP -> DELIVERING; otherwise unchanged. */
    @Override
    public void onOutForDelivery(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        if (order.getStatus() == OrderStatus.ASSIGNED_TO_COURIER || order.getStatus() == OrderStatus.PICKED_UP) {
            stateMachine.transition(order, OrderStatus.DELIVERING, "DELIVERY_ON_ROUTE");
        }
    }

    /** Order DELIVERED (clears the plain PIN) and the stock lifecycle closed (no-op if pickup committed it). */
    @Override
    public void onDelivered(UUID tenantId, UUID orderId) {
        Order order = findOrder(tenantId, orderId);
        stateMachine.transition(order, OrderStatus.DELIVERED, "DELIVERY_COMPLETED"); // from ASSIGNED_TO_COURIER/PICKED_UP/DELIVERING
        orderStockService.commit(order);
    }

    /**
     * Order CANCELLED with reason DELIVERY_FAILED (LAC-44), its reservation released (INC-07; no-op once pickup committed
     * the stock; a pre-pickup STORE_FAULT releases it) and the delivery incident opened for OPS. No refund here: the
     * refund and who bears the cost are decided when OPS resolve the incident.
     */
    @Override
    public void onDeliveryFailed(UUID tenantId, UUID orderId, DeliveryFailure failure) {
        Order order = findOrder(tenantId, orderId);
        stateMachine.cancel(order, CancellationReason.DELIVERY_FAILED, null);
        orderStockService.release(order);
        if (incidentService != null && failure != null) {
            incidentService.open(order, failure);
        }
    }

    /** LAC-44: opens the delivery incident; optional so slice tests without the incident service keep working. */
    @Autowired(required = false)
    public void setIncidentService(DeliveryIncidentService incidentService) {
        this.incidentService = incidentService;
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
