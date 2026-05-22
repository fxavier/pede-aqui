package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    @Test
    void duplicateConfirmationReturnsOneConfirmedResult() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment(UUID.randomUUID(), tenantId, orderId, new BigDecimal("21.50"), "pay-key");
        Order order = new Order(UUID.randomUUID(), tenantId, "PA-1", UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("12.00"), "checkout-key", "hash", "123456");
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, "pay-key")).thenReturn(Optional.of(payment));
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
        PaymentService service = new PaymentService(paymentRepository, orderRepository, mock(RefundRepository.class), mock(AuditLogService.class), new PaymentMapper(), tenantContext);

        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(orderRepository).findByTenantIdAndId(tenantId, orderId);
    }
}
