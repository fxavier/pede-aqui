package com.delivery.order.service;

import com.delivery.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Customer cancellation policy per order phase (LAC-26, W5-A; product decision):
 * <ul>
 *   <li>{@link Phase#FREE} — {@code PAYMENT_PENDING}, {@code PAYMENT_CONFIRMED} (not yet accepted): cancelled at once,
 *       a captured payment is refunded in full;</li>
 *   <li>{@link Phase#REQUEST} — {@code ACCEPTED_BY_VENDOR}, {@code PREPARING}: the customer can only request it; OPS
 *       approves (fee = {@code app.orders.cancellation.fee-rate} × subtotal withheld from the refund) or rejects;</li>
 *   <li>{@link Phase#ALREADY_CANCELLED} — {@code CANCELLED}: a repeat call returns the current state;</li>
 *   <li>{@link Phase#NOT_CANCELLABLE} — everything else ({@code READY_FOR_PICKUP} onwards, {@code REFUNDED}, legacy
 *       rows): 409 {@code order_not_cancellable}.</li>
 * </ul>
 * Pure functions, no state; the money rule is {@link #fee} + {@link #refundAfterFee}.
 */
public final class CustomerCancellationPolicy {
    /** Cancellation phase of an order status from the customer's point of view. */
    public enum Phase { FREE, REQUEST, ALREADY_CANCELLED, NOT_CANCELLABLE }

    public static final Set<OrderStatus> FREE_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED));
    public static final Set<OrderStatus> REQUEST_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.PREPARING));

    private CustomerCancellationPolicy() {
    }

    /** The phase of {@code status}. */
    public static Phase phaseOf(OrderStatus status) {
        if (FREE_STATUSES.contains(status)) return Phase.FREE;
        if (REQUEST_STATUSES.contains(status)) return Phase.REQUEST;
        if (status == OrderStatus.CANCELLED) return Phase.ALREADY_CANCELLED;
        return Phase.NOT_CANCELLABLE;
    }

    /** Quoted fee: {@code subtotal × rate}, HALF_UP to 2 decimals, never negative. */
    public static BigDecimal fee(BigDecimal subtotal, BigDecimal rate) {
        if (subtotal == null || rate == null || subtotal.signum() <= 0 || rate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** The fee actually withheld: the quoted fee capped at what is still refundable (never negative). */
    public static BigDecimal appliedFee(BigDecimal quotedFee, BigDecimal refundable) {
        BigDecimal cap = refundable == null || refundable.signum() < 0 ? BigDecimal.ZERO : refundable;
        return quotedFee.min(cap).setScale(2, RoundingMode.HALF_UP);
    }

    /** Refund after withholding the fee: {@code refundable − fee}, HALF_UP to 2 decimals, never negative. */
    public static BigDecimal refundAfterFee(BigDecimal refundable, BigDecimal fee) {
        BigDecimal remainder = (refundable == null ? BigDecimal.ZERO : refundable).subtract(fee);
        return remainder.signum() < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : remainder.setScale(2, RoundingMode.HALF_UP);
    }
}
