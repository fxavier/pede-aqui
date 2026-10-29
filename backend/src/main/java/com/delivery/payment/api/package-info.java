/**
 * Public API of the payment module (ADR-0002 C1): {@link com.delivery.payment.api.PaymentOrderHooks}, the order-side
 * effects payment needs inside its own transactions (implemented by the order module, so payment does not depend on
 * order while confirmation and full-refund transitions stay atomic), and
 * {@link com.delivery.payment.api.PaymentStatusHistoryReader} (W4-A, read-only payment status history).
 */
@NamedInterface("api")
package com.delivery.payment.api;

import org.springframework.modulith.NamedInterface;
