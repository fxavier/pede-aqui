package com.delivery.sales.dto;

import java.time.Instant;
import java.util.UUID;

/** Sales list filter parameters (status is the raw query value, validated in the service). */
public record SalesSearchFilter(
        Instant from,
        Instant to,
        String status,
        UUID vendorId,
        UUID productId,
        UUID skuId,
        String paymentProvider,
        String q,
        int page,
        int size) {}
