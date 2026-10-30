package com.delivery.settlement.entity;

/** Kinds of ledger transaction (V065 CHECK) and the prefix of their idempotency key (ADR-0005). */
public enum LedgerTransactionType {
    /** Payment captured: cash clearing ↔ customer funds held. Key {@code payment-captured:<paymentId>}. */
    PAYMENT_CAPTURED("payment-captured:"),
    /** Order delivered: funds held → merchant payable + commission + fees. Key {@code order-delivered:<orderId>}. */
    ORDER_DELIVERED("order-delivered:"),
    /** Refund approved before delivery: funds held → refunds payable. Key {@code refund-approved:<refundId>}. */
    REFUND_BEFORE_DELIVERY("refund-approved:"),
    /** Refund approved after delivery: proportional reversal → refunds payable. Key {@code refund-approved:<refundId>}. */
    REFUND_AFTER_DELIVERY("refund-approved:"),
    /** Customer cancellation fee withheld (LAC-26): funds held → merchant + commission. Key {@code cancellation-fee:<orderId>}. */
    CANCELLATION_FEE_RETAINED("cancellation-fee:");

    private final String keyPrefix;

    LedgerTransactionType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /** The idempotency key of this transaction for the given source id. */
    public String key(java.util.UUID sourceId) {
        return keyPrefix + sourceId;
    }
}
