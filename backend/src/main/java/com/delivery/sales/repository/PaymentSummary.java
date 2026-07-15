package com.delivery.sales.repository;

import com.delivery.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Closed projection over payments for the sales lens (exposes provider, which the entity does not). */
public interface PaymentSummary {
    UUID getId();
    UUID getOrderId();
    BigDecimal getAmount();
    String getProvider();
    PaymentStatus getStatus();
}
