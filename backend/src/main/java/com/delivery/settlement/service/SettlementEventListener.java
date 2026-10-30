package com.delivery.settlement.service;

import com.delivery.order.OrderStatusChanged;
import com.delivery.payment.PaymentConfirmed;
import com.delivery.payment.RefundApproved;
import com.delivery.settlement.config.SettlementProperties;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the settlement ledger from the transactional events (LAC-33, LAC-47): {@link PaymentConfirmed},
 * {@link RefundApproved} and {@link OrderStatusChanged} (DELIVERED, CANCELLED). {@code @ApplicationModuleListener} =
 * after commit, async, own transaction, tracked in {@code event_publication}: a failure leaves the publication
 * incomplete and {@code EventPublicationRetryJob} redelivers it; postings are idempotent by key, so redelivery and
 * duplicates are harmless. Switched off with {@code app.settlement.enabled=false} (the test-suite default).
 */
@Component
public class SettlementEventListener {
    private final LedgerPostingService postings;
    private final SettlementProperties properties;

    public SettlementEventListener(LedgerPostingService postings, SettlementProperties properties) {
        this.postings = postings;
        this.properties = properties;
    }

    /** Captured payment → customer funds held. */
    @ApplicationModuleListener
    public void on(PaymentConfirmed event) {
        if (properties.isEnabled()) {
            postings.recordPaymentCaptured(event.tenantId(), event.paymentId());
        }
    }

    /** Approved refund → refunds payable (from funds held, or reversing the delivery recognition). */
    @ApplicationModuleListener
    public void on(RefundApproved event) {
        if (properties.isEnabled()) {
            postings.recordRefundApproved(event.tenantId(), event.refundId(), event.paymentId(), event.orderId(), event.amount(),
                    event.approvedAt());
        }
    }

    /** DELIVERED → merchant payable/commission/fees; CANCELLED → retained cancellation fee, if any. */
    @ApplicationModuleListener
    public void on(OrderStatusChanged event) {
        if (!properties.isEnabled()) {
            return;
        }
        switch (event.newStatus()) {
            case "DELIVERED" -> postings.recordOrderDelivered(event.tenantId(), event.orderId());
            case "CANCELLED" -> postings.recordCancellation(event.tenantId(), event.orderId());
            default -> { }
        }
    }
}
