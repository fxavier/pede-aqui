package com.delivery.dashboard.service;

import com.delivery.order.OrderStatusChanged;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * First consumer of the transactional event pipeline (LAC-47): counts order status transitions for operational
 * dashboards ({@code order_status_transitions_total{from,to}}) and logs them. Observation only — it changes no state,
 * so redelivery (at-least-once) can only over-count a metric, never corrupt data.
 *
 * <p>{@code @ApplicationModuleListener} = after-commit, asynchronous, own transaction, tracked in the
 * {@code event_publication} registry: if this method throws, the publication stays incomplete and is resubmitted by
 * {@code EventPublicationRetryJob}.
 */
@Component
public class OrderStatusMetricsListener {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusMetricsListener.class);

    private final MeterRegistry meterRegistry;

    public OrderStatusMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Counts and logs one order status transition. */
    @ApplicationModuleListener
    public void on(OrderStatusChanged event) {
        meterRegistry.counter("order.status.transitions", "from", event.previousStatus(), "to", event.newStatus()).increment();
        log.info("Order {} (tenant {}) status {} -> {}", event.orderId(), event.tenantId(), event.previousStatus(), event.newStatus());
    }
}
