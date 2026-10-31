package com.delivery.order.api;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order side of the data-subject rights (LAC-19/LAC-63, W7-C): whether a customer still has an order in progress
 * (account deletion is refused/postponed while one exists), the order summary of the personal-data export, and the
 * pseudonymisation of the customer's order snapshots after an account deletion.
 *
 * <p>Pseudonymisation keeps what fiscal/financial retention needs (reference, vendor, items, amounts, quote/VAT
 * breakdown, status, timestamps, the profile id link and the coarse delivery area city/state) and clears the
 * identifying delivery snapshot fields (recipient name, phone, address lines, postal code, coordinates,
 * instructions, source address id) plus the customer's free-text cancellation reasons. The address snapshot trigger
 * (V047, relaxed by V071) only allows this inside a transaction with {@code app.personal_data_erasure = 'on'}.
 */
@Component
public class CustomerOrderPrivacy {
    /** Statuses in which an order is finished for the customer (no money or goods still in flight). */
    private static final Set<OrderStatus> FINISHED = EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED);
    /** Order statuses that block an account deletion: not finished and not one of the never-written legacy values. */
    public static final Set<OrderStatus> ACTIVE_STATUSES = activeStatuses();

    /** One order of the personal-data export (no delivery address details: those are in the order detail API). */
    public record OrderSummary(UUID id, String reference, String status, String cancellationReason, String vendorName,
                               BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discountTotal,
                               BigDecimal total, String deliveryCity, Instant createdAt) {}

    private final JdbcTemplate jdbc;

    public CustomerOrderPrivacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True when the customer has at least one order in an {@link #ACTIVE_STATUSES active} status in the tenant. */
    @Transactional(readOnly = true)
    public boolean hasActiveOrders(UUID tenantId, UUID customerProfileId) {
        String[] statuses = ACTIVE_STATUSES.stream().map(Enum::name).toArray(String[]::new);
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM orders WHERE tenant_id = ? AND customer_id = ? AND status = ANY (?))",
                Boolean.class, tenantId, customerProfileId, statuses);
        return Boolean.TRUE.equals(exists);
    }

    /** Every order of the customer in the tenant, newest first, as export summaries. */
    @Transactional(readOnly = true)
    public List<OrderSummary> summaries(UUID tenantId, UUID customerProfileId) {
        return jdbc.query("""
                SELECT o.id, o.reference, o.status, o.cancellation_reason, v.name AS vendor_name, o.subtotal, o.fees, o.taxes,
                       o.discount_total, o.total, o.delivery_city, o.created_at
                  FROM orders o
                  LEFT JOIN vendors v ON v.id = o.vendor_id
                 WHERE o.tenant_id = ? AND o.customer_id = ?
                 ORDER BY o.created_at DESC, o.id DESC""",
                (rs, i) -> new OrderSummary(rs.getObject("id", UUID.class), rs.getString("reference"), rs.getString("status"),
                        rs.getString("cancellation_reason"), rs.getString("vendor_name"), rs.getBigDecimal("subtotal"),
                        rs.getBigDecimal("fees"), rs.getBigDecimal("taxes"), rs.getBigDecimal("discount_total"),
                        rs.getBigDecimal("total"), rs.getString("delivery_city"), instant(rs.getTimestamp("created_at"))),
                tenantId, customerProfileId);
    }

    /**
     * Clears the identifying fields of the customer's order snapshots and cancellation-request reasons. Must run
     * inside the caller's erasure transaction (it sets the transaction-local erasure flag). Returns the number of
     * orders touched. Idempotent.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int pseudonymiseCustomerOrders(UUID tenantId, UUID customerProfileId) {
        jdbc.execute("SET LOCAL app.personal_data_erasure = 'on'");
        int orders = jdbc.update("""
                UPDATE orders
                   SET delivery_recipient_name = NULL, delivery_phone = NULL, delivery_line1 = NULL, delivery_line2 = NULL,
                       delivery_postal_code = NULL, delivery_latitude = NULL, delivery_longitude = NULL,
                       delivery_instructions = NULL, delivery_address_id = NULL
                 WHERE tenant_id = ? AND customer_id = ?""",
                tenantId, customerProfileId);
        jdbc.update("""
                UPDATE cancellation_requests c
                   SET reason = NULL
                  FROM orders o
                 WHERE o.id = c.order_id AND o.tenant_id = ? AND o.customer_id = ? AND c.reason IS NOT NULL""",
                tenantId, customerProfileId);
        return orders;
    }

    private static Set<OrderStatus> activeStatuses() {
        EnumSet<OrderStatus> active = EnumSet.allOf(OrderStatus.class);
        active.removeAll(FINISHED);
        active.removeAll(OrderStateMachine.LEGACY_STATUSES);
        return java.util.Collections.unmodifiableSet(active);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
