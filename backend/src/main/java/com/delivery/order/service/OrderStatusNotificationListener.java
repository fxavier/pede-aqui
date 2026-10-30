package com.delivery.order.service;

import com.delivery.notification.api.NotificationDispatcher;
import com.delivery.notification.api.NotificationRequest;
import com.delivery.order.OrderStatusChanged;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Customer notifications for order progress (LAC-48, W6-C): after commit, {@code ACCEPTED_BY_VENDOR},
 * {@code READY_FOR_PICKUP} and {@code DELIVERED} transitions are handed to {@link NotificationDispatcher} with the
 * template keys {@code order_accepted}, {@code order_ready_for_pickup}, {@code order_delivered} (the active templates
 * decide the channels — PUSH by default). Lives in {@code order} because order already depends on notification (a
 * notification-side listener would create a cycle). Idempotent: the dedup key is {@code orderId + newStatus} (each
 * status is reached at most once — the state table is acyclic), so a redelivered event writes nothing.
 * Cancellations are not handled here: their paths already create in-app notifications, which fan out themselves.
 */
@Component
public class OrderStatusNotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderStatusNotificationListener.class);
    private static final Map<String, String> TEMPLATE_BY_STATUS = Map.of(
            "ACCEPTED_BY_VENDOR", "order_accepted",
            "READY_FOR_PICKUP", "order_ready_for_pickup",
            "DELIVERED", "order_delivered");

    private final OrderRepository orderRepository;
    private final NotificationDispatcher dispatcher;

    public OrderStatusNotificationListener(OrderRepository orderRepository, NotificationDispatcher dispatcher) {
        this.orderRepository = orderRepository;
        this.dispatcher = dispatcher;
    }

    /** Dispatches the customer notification of a notifying transition; other transitions are ignored. */
    @ApplicationModuleListener
    public void on(OrderStatusChanged event) {
        String templateKey = TEMPLATE_BY_STATUS.get(event.newStatus());
        if (templateKey == null) {
            return;
        }
        Optional<Order> order = orderRepository.findByTenantIdAndId(event.tenantId(), event.orderId());
        if (order.isEmpty() || order.get().getCustomerId() == null) {
            LOGGER.warn("notification.skipped orderId={} status={} reason=order_or_customer_missing", event.orderId(), event.newStatus());
            return;
        }
        String reference = java.util.Objects.requireNonNullElse(order.get().getReference(), "");
        dispatcher.dispatch(new NotificationRequest(event.tenantId(), order.get().getCustomerId(), "CUSTOMER", templateKey, reference,
                Map.of("orderReference", reference), NotificationDispatcher.ALL_CHANNELS,
                "order-status:" + event.orderId() + ":" + event.newStatus()));
    }
}
