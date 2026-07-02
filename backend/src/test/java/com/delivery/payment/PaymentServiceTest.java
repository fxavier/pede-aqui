package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.dto.RefundRequest;
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
        AuditLogService auditLogService = mock(AuditLogService.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, "pay-key")).thenReturn(Optional.of(payment));
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
        PaymentService service = new PaymentService(paymentRepository, orderRepository, mock(RefundRepository.class), auditLogService, new PaymentMapper(), tenantContext);

        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(service.confirm(payment.getId(), "pay-key").status()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(orderRepository).findByTenantIdAndId(tenantId, orderId);
        verify(auditLogService).log("PAYMENT_CONFIRMED", "payment", payment.getId().toString(), order.getReference(), "SUCCESS");
    }

    @Test
    void vendorRejectionRefundFlowCreatesRefundRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Payment payment = new Payment(paymentId, tenantId, orderId, new BigDecimal("100.00"), "pay-key");
        
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RefundRepository refundRepository = mock(RefundRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(paymentRepository.findByTenantIdAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
        
        String idempotencyKey = "vendor-rejection-" + orderId.toString();
        Refund refund = new Refund(refundId, tenantId, paymentId, orderId, new BigDecimal("100.00"), 
            "Vendor rejection: Sem ingredientes", RefundStatus.REQUESTED, idempotencyKey);
        when(refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class))).thenReturn(refund);
        
        PaymentService service = new PaymentService(paymentRepository, mock(OrderRepository.class), 
            refundRepository, mock(AuditLogService.class), new PaymentMapper(), tenantContext);

        RefundRequest request = new RefundRequest(new BigDecimal("100.00"), "Vendor rejection: Sem ingredientes", idempotencyKey);
        var response = service.requestRefund(paymentId, request);

        assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(response.reason()).isEqualTo("Vendor rejection: Sem ingredientes");
        assertThat(response.amount()).isEqualTo(new BigDecimal("100.00"));
    }
}
