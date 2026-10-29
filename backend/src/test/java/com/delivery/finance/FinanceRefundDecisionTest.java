package com.delivery.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.finance.mapper.FinanceMapper;
import com.delivery.finance.repository.CashReconciliationRepository;
import com.delivery.finance.repository.CommissionRepository;
import com.delivery.finance.service.FinanceService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** /finance/refunds/{id}/approve|reject must apply the same guards as the payment refund path (INC-06, AS-06, COR-04). */
class FinanceRefundDecisionTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private RefundRepository refundRepository;
    private FinanceService financeService;
    private Payment payment;
    private Order order;

    @BeforeEach
    void setUp() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        refundRepository = mock(RefundRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        payment = new Payment(UUID.randomUUID(), tenantId, orderId, new BigDecimal("50.00"), "pay-key");
        payment.confirm();
        order = new Order(orderId, tenantId, "PA-9", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50.00"), "checkout-key", "hash", "000000");
        order.markRefundPending();
        when(paymentRepository.findByTenantIdAndIdForUpdate(tenantId, payment.getId())).thenReturn(Optional.of(payment));
        when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));

        AuditLogService paymentAudit = mock(AuditLogService.class);
        PaymentService paymentService = new PaymentService(paymentRepository, new com.delivery.order.service.OrderPaymentHooks(orderRepository, org.mockito.Mockito.mock(com.delivery.delivery.api.DeliveryRetirementService.class), org.mockito.Mockito.mock(com.delivery.order.service.OrderStockService.class), paymentAudit), refundRepository, paymentAudit,
                new PaymentMapper(), tenantContext, mock(AppUserProfileRepository.class), new PaymentMockConfirmationProperties());
        financeService = new FinanceService(paymentRepository, mock(CommissionRepository.class), mock(CashReconciliationRepository.class),
                refundRepository, new FinanceMapper(), tenantContext, paymentService);
    }

    @Test
    void rejectedRefundCannotBeApprovedViaFinance() {
        Refund rejected = refund(RefundStatus.REJECTED);

        assertThatThrownBy(() -> financeService.approveRefund(rejected.getId()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
    }

    @Test
    void completedRefundCannotBeRejectedViaFinance() {
        Refund completed = refund(RefundStatus.REFUNDED);

        assertThatThrownBy(() -> financeService.rejectRefund(completed.getId()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(completed.getStatus()).isEqualTo(RefundStatus.REFUNDED);
    }

    @Test
    void fullApprovalViaFinanceMarksOrderRefunded() {
        Refund requested = refund(RefundStatus.REQUESTED);

        assertThat(financeService.approveRefund(requested.getId()).status()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void refundOfAnotherTenantIsNotFoundViaFinance() {
        UUID foreignId = UUID.randomUUID();
        when(refundRepository.findByTenantIdAndId(tenantId, foreignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financeService.approveRefund(foreignId))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Refund refund(RefundStatus status) {
        Refund refund = new Refund(UUID.randomUUID(), tenantId, payment.getId(), orderId, new BigDecimal("50.00"), "full", status, "r-" + status);
        when(refundRepository.findByTenantIdAndId(tenantId, refund.getId())).thenReturn(Optional.of(refund));
        when(refundRepository.findByTenantIdAndPaymentId(tenantId, payment.getId())).thenReturn(List.of(refund));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        return refund;
    }
}
