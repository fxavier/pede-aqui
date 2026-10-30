package com.delivery.order.api;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.observability.BusinessMetrics;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.StatusChangeActor;
import com.delivery.order.OrderStatusChanged;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.entity.OrderStatusHistory;
import com.delivery.order.repository.OrderStatusHistoryRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The single place where an order changes status (LAC-25, W4-A). Holds the explicit transition table of the current
 * {@link OrderStatus} enum (derived from the code paths that existed before it: checkout, mock payment confirmation,
 * vendor fulfilment, dispatch, courier delivery, sales cancel/override, seller rejection, full refund, timeout jobs),
 * refuses anything else with 409 {@code invalid_order_transition} <b>before</b> touching the order, and for every
 * accepted transition — in the caller's transaction — applies it, appends an {@code order_status_history} row (V058)
 * and publishes {@link OrderStatusChanged} (at-least-once after commit; consumers must be idempotent).
 *
 * <p>{@link Order} has no public status setters: it only accepts a {@link Change}, which only this class can create.
 * Callers keep their own business guards (ownership, lock order rule, idempotent repeats, narrower policies such as
 * the sales override allow-list); the table is the outer bound every path must stay inside.
 *
 * <p>Legacy/unreachable values (kept so existing rows load and can still be closed): {@code PENDING},
 * {@code REJECTED_BY_VENDOR}, {@code DISPATCH_PENDING}, {@code REFUND_PENDING} are never written. Since W4-A
 * {@code PICKED_UP} and {@code DELIVERING} are written (courier pickup / on the way, synced from the delivery).
 */
@Component
public class OrderStateMachine {
    /** Allowed targets per source status. Terminal: CANCELLED, REFUNDED. */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTable();
    /** Enum values no code path writes any more; rows in them can only be closed (see table). */
    public static final Set<OrderStatus> LEGACY_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            OrderStatus.PENDING, OrderStatus.REJECTED_BY_VENDOR, OrderStatus.DISPATCH_PENDING, OrderStatus.REFUND_PENDING));

    private final OrderStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantContext tenantContext;
    private BusinessMetrics metrics = BusinessMetrics.NOOP;

    public OrderStateMachine(OrderStatusHistoryRepository historyRepository, ApplicationEventPublisher eventPublisher,
                             TenantContext tenantContext) {
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.tenantContext = tenantContext;
    }

    /** Optional: unit/slice tests without a meter registry keep the no-op metrics (LAC-50). */
    @Autowired(required = false)
    public void setBusinessMetrics(BusinessMetrics metrics) { this.metrics = metrics; }

    private static Map<OrderStatus, Set<OrderStatus>> buildTable() {
        Map<OrderStatus, Set<OrderStatus>> table = new EnumMap<>(OrderStatus.class);
        // Legacy pre-payment status: only cancellable (sales cancel allowed it).
        table.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CANCELLED));
        // Checkout -> payment confirmed (mock/PSP); sales cancel or PaymentExpiryJob (PAYMENT_EXPIRED).
        table.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED));
        // Vendor accept; seller rejection / sales cancel / override / AcceptanceTimeoutJob -> CANCELLED; full refund.
        table.put(OrderStatus.PAYMENT_CONFIRMED, EnumSet.of(OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        table.put(OrderStatus.ACCEPTED_BY_VENDOR, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        table.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        // Dispatch offer to a courier; no cancel once ready (sales cancel is pre-dispatch only), refund still possible.
        table.put(OrderStatus.READY_FOR_PICKUP, EnumSet.of(OrderStatus.ASSIGNED_TO_COURIER, OrderStatus.REFUNDED));
        // Legacy: only the sales override DISPATCH_PENDING -> ASSIGNED_TO_COURIER ever read it.
        table.put(OrderStatus.DISPATCH_PENDING, EnumSet.of(OrderStatus.ASSIGNED_TO_COURIER, OrderStatus.REFUNDED));
        // Courier lifecycle (synced from the delivery): pickup, on the way, PIN completion; failed delivery -> CANCELLED.
        table.put(OrderStatus.ASSIGNED_TO_COURIER, EnumSet.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING,
                OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        table.put(OrderStatus.PICKED_UP, EnumSet.of(OrderStatus.DELIVERING, OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        table.put(OrderStatus.DELIVERING, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.REFUNDED));
        // A refund approved after delivery still marks the order REFUNDED.
        table.put(OrderStatus.DELIVERED, EnumSet.of(OrderStatus.REFUNDED));
        // Legacy rows: only a full refund approval can still move them.
        table.put(OrderStatus.REJECTED_BY_VENDOR, EnumSet.of(OrderStatus.REFUNDED));
        table.put(OrderStatus.REFUND_PENDING, EnumSet.of(OrderStatus.REFUNDED));
        table.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        table.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
        table.replaceAll((from, targets) -> Collections.unmodifiableSet(targets));
        return Collections.unmodifiableMap(table);
    }

    /** True when {@code from -> to} is in the transition table (self-transitions never are). */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** The allowed targets of a status (empty for terminal ones). */
    public static Set<OrderStatus> allowedTargets(OrderStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    /** Records the creation of a new order (history row {@code null -> PAYMENT_PENDING}); call after it is persisted. */
    public void recordCreated(Order order) {
        writeHistory(order, null, order.getStatus(), "CHECKOUT");
        metrics.incrementAfterCommit(BusinessMetrics.ORDERS_CREATED);
    }

    /** Moves the order to {@code target}; 409 invalid_order_transition (no side effect) when not in the table. */
    public void transition(Order order, OrderStatus target, String reason) {
        apply(order, new Change(target, null, null), reason);
    }

    /** Cancels the order recording why (stored in orders.cancellation_reason and in the history row). */
    public void cancel(Order order, CancellationReason cancellationReason, String note) {
        String reason = note == null || note.isBlank() ? cancellationReason.name() : cancellationReason.name() + ": " + note;
        apply(order, new Change(OrderStatus.CANCELLED, cancellationReason, null), reason);
    }

    /** Seller rejection: CANCELLED + SELLER_REJECTED, the vendor's text kept in orders.rejection_reason. */
    public void cancelBySellerRejection(Order order, String vendorReason) {
        apply(order, new Change(OrderStatus.CANCELLED, CancellationReason.SELLER_REJECTED, vendorReason), CancellationReason.SELLER_REJECTED.name());
    }

    private void apply(Order order, Change change, String reason) {
        OrderStatus from = order.getStatus();
        if (!canTransition(from, change.target())) {
            throw new BusinessException("invalid_order_transition",
                    "Order cannot move from " + from + " to " + change.target(), HttpStatus.CONFLICT);
        }
        order.applyStatusChange(change);
        writeHistory(order, from, change.target(), reason);
        eventPublisher.publishEvent(new OrderStatusChanged(order.getTenantId(), order.getId(), order.getVendorId(),
                from.name(), change.target().name(), Instant.now()));
        if (change.target() == OrderStatus.CANCELLED) {
            // reason = the CancellationReason enum (bounded), "other" for cancels that do not record one (sales, failed delivery).
            CancellationReason why = change.cancellationReason();
            metrics.incrementAfterCommit(BusinessMetrics.ORDERS_CANCELLED, "reason", why == null ? "other" : why.name().toLowerCase());
        }
    }

    private void writeHistory(Order order, OrderStatus from, OrderStatus to, String reason) {
        StatusChangeActor actor = StatusChangeActor.current(tenantContext);
        historyRepository.save(new OrderStatusHistory(order.getTenantId(), order.getId(), from, to, actor.actorType(),
                actor.actorId(), truncate(reason), actor.correlationId(), Instant.now()));
    }

    private static String truncate(String reason) {
        return reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    /**
     * Permission to change an order's status, only constructible here: {@link Order#applyStatusChange} requires one, so
     * no code path can change a status without going through the table, the history and the event.
     */
    public static final class Change {
        private final OrderStatus target;
        private final CancellationReason cancellationReason;
        private final String rejectionReason;

        private Change(OrderStatus target, CancellationReason cancellationReason, String rejectionReason) {
            this.target = target;
            this.cancellationReason = cancellationReason;
            this.rejectionReason = rejectionReason;
        }

        public OrderStatus target() { return target; }
        public CancellationReason cancellationReason() { return cancellationReason; }
        public String rejectionReason() { return rejectionReason; }
    }
}
