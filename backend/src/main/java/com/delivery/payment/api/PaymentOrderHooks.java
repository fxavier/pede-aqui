package com.delivery.payment.api;

import java.util.UUID;

/**
 * What payment needs from the paid order, called synchronously inside the payment transaction (ADR-0002 C1).
 * Declared here and implemented by the order module: payment no longer reads order/delivery/stock internals, and the
 * order transition still commits or rolls back with the payment change. Lock order is unchanged: payment locks its
 * own row first, these hooks then read the order, the delivery/dispatch rows and finally inventory.
 * Every method loads the order tenant-scoped and answers 404 "Order was not found" when it is missing.
 */
public interface PaymentOrderHooks {

    /** The order facts the mock confirmation checks (ownership, state) and audits (reference). */
    record OrderForPayment(UUID customerId, boolean awaitingPayment, String reference) {}

    /** Loads the order of a payment for confirmation. */
    OrderForPayment orderForPayment(UUID tenantId, UUID orderId);

    /** Payment confirmed: order PAYMENT_PENDING -> PAYMENT_CONFIRMED. */
    void markPaymentConfirmed(UUID tenantId, UUID orderId);

    /**
     * Approved refunds now cover the whole payment: the order becomes REFUNDED (a CANCELLED order stays CANCELLED,
     * audited ORDER_REFUNDED otherwise) and, unless the goods already left the store, its delivery is retired and its
     * stock reservation released (both idempotent). Full rule on {@code PaymentService.approveRefund}.
     */
    void onPaymentFullyRefunded(UUID tenantId, UUID orderId);
}
