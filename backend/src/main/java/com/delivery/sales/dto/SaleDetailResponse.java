package com.delivery.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sale detail: the commercial row (flattened, per contract allOf) plus snapshots, promotion, payments, refunds, and commission. */
public record SaleDetailResponse(
        UUID orderId,
        String reference,
        Instant createdAt,
        UUID vendorId,
        String vendorName,
        String customerName,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal fees,
        BigDecimal taxes,
        BigDecimal discountTotal,
        BigDecimal total,
        String orderStatus,
        String paymentStatus,
        String paymentProvider,
        List<SaleLineItemResponse> items,
        UUID appliedPromotionId,
        List<SalePaymentResponse> payments,
        List<SaleRefundResponse> refunds,
        BigDecimal commission) {}
