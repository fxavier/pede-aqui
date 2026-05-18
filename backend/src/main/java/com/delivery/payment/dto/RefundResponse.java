package com.delivery.payment.dto;

import com.delivery.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Refund response returned after approval/rejection actions. */
public record RefundResponse(UUID id, UUID paymentId, UUID orderId, BigDecimal amount, String reason, RefundStatus status) {}
