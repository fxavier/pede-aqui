package com.delivery.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.delivery.common.security.TenantContext;
import com.delivery.finance.entity.CashReconciliation;
import com.delivery.finance.entity.Commission;
import com.delivery.finance.mapper.FinanceMapper;
import com.delivery.finance.repository.CashReconciliationRepository;
import com.delivery.finance.repository.CommissionRepository;
import com.delivery.finance.service.FinanceService;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinanceServiceTest {
    @Test
    void summaryIncludesTransactionsCommissionsRefundsAndUnreconciledCash() {
        UUID tenantId = UUID.randomUUID();
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        CommissionRepository commissionRepository = mock(CommissionRepository.class);
        CashReconciliationRepository cashRepository = mock(CashReconciliationRepository.class);
        RefundRepository refundRepository = mock(RefundRepository.class);
        TenantContext tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));

        when(paymentRepository.findByTenantIdAndStatus(tenantId, PaymentStatus.CONFIRMED)).thenReturn(List.of(
                new Payment(UUID.randomUUID(), tenantId, UUID.randomUUID(), new BigDecimal("120.00"), "k1"),
                new Payment(UUID.randomUUID(), tenantId, UUID.randomUUID(), new BigDecimal("80.00"), "k2")));

        when(commissionRepository.findByTenantId(tenantId)).thenReturn(List.of(
                new Commission(UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("0.10"), new BigDecimal("10.00"), "PENDING")));

        when(refundRepository.findByTenantIdAndStatus(tenantId, RefundStatus.REFUNDED)).thenReturn(List.of(
                new Refund(UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("15.00"), "partial", RefundStatus.REFUNDED, "r1")));

        when(cashRepository.findByTenantIdAndStatus(tenantId, "PENDING")).thenReturn(List.of(
                new CashReconciliation(UUID.randomUUID(), tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.00"), "PENDING")));

        FinanceService service = new FinanceService(paymentRepository, commissionRepository, cashRepository, refundRepository, new FinanceMapper(), tenantContext);
        var summary = service.summary();

        assertThat(summary.confirmedPaymentsTotal()).isEqualByComparingTo("200.00");
        assertThat(summary.commissionTotal()).isEqualByComparingTo("10.00");
        assertThat(summary.refundsTotal()).isEqualByComparingTo("15.00");
        assertThat(summary.unreconciledCashTotal()).isEqualByComparingTo("25.00");
    }
}
