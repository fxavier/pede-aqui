package com.delivery.payment.mapper;

import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.entity.Payment;
import org.springframework.stereotype.Component;

/** Converts payment entities to API DTOs. */
@Component
public class PaymentMapper {
    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }
}
