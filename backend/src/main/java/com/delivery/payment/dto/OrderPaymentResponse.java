package com.delivery.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The customer's view of an order's payment (LAC-31, {@code GET /api/v1/me/orders/{orderId}/payment}): which payment to
 * start an attempt for, whether a new attempt is allowed now ({@code payable}) and the latest attempt, if any.
 */
public record OrderPaymentResponse(UUID paymentId, UUID orderId, String status, BigDecimal amount, String currency, boolean payable,
                                   PaymentAttemptResponse latestAttempt) {
}
