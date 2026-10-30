/**
 * Public API of the payment module (ADR-0002 C1): {@link com.delivery.payment.api.PaymentOrderHooks}, the order-side
 * effects payment needs inside its own transactions (implemented by the order module, so payment does not depend on
 * order while confirmation and full-refund transitions stay atomic), and
 * {@link com.delivery.payment.api.PaymentStatusHistoryReader} (W4-A, read-only payment status history) and
 * {@link com.delivery.payment.api.PaymentSettlementFacts} (W6-B, read-only payment/refund facts for the settlement ledger).
 * Payment events ({@code PaymentConfirmed}, {@code RefundApproved}) live in the module base package.
 */
@NamedInterface("api")
package com.delivery.payment.api;

import org.springframework.modulith.NamedInterface;
