package com.delivery.order.api;

import com.delivery.order.repository.CustomerOrderQueryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only facts the rating module needs about a customer's own order (LAC-28, W7-D): its vendor, status and the
 * DELIVERED instant, so rating never reaches into order's repositories or entities. Ownership is part of the lookup
 * (tenant + order + customer profile): an order of someone else is simply absent.
 */
@Component
public class OrderRatingFacts {

    /** Status is the {@code OrderStatus} name; {@code deliveredAt} is null when the order was never delivered. */
    public record RateableOrder(UUID tenantId, UUID orderId, UUID vendorId, String status, Instant deliveredAt) {}

    private final CustomerOrderQueryRepository customerOrders;
    private final OrderSettlementFacts settlementFacts;

    public OrderRatingFacts(CustomerOrderQueryRepository customerOrders, OrderSettlementFacts settlementFacts) {
        this.customerOrders = customerOrders;
        this.settlementFacts = settlementFacts;
    }

    /** The order when it belongs to this customer profile in this tenant. */
    @Transactional(readOnly = true)
    public Optional<RateableOrder> customerOrder(UUID tenantId, UUID orderId, UUID customerProfileId) {
        if (!customerOrders.existsByTenantIdAndIdAndCustomerId(tenantId, orderId, customerProfileId)) {
            return Optional.empty();
        }
        return settlementFacts.financials(tenantId, orderId)
                .map(f -> new RateableOrder(f.tenantId(), f.orderId(), f.vendorId(), f.status(), f.deliveredAt()));
    }
}
