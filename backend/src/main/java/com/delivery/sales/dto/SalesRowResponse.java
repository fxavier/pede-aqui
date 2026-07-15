package com.delivery.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Commercial sales-list row projected from an order (customerName is masked for SUPPORT). */
public record SalesRowResponse(
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
        String paymentProvider) {}
