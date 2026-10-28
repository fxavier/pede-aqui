package com.delivery.finance.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.finance.dto.CashReconciliationResponse;
import com.delivery.finance.dto.CommissionResponse;
import com.delivery.finance.dto.FinanceExportResponse;
import com.delivery.finance.dto.PayoutStatusResponse;
import com.delivery.finance.dto.RefundFinanceResponse;
import com.delivery.finance.dto.FinanceSummaryResponse;
import com.delivery.finance.dto.TransactionResponse;
import com.delivery.finance.mapper.FinanceMapper;
import com.delivery.finance.repository.CashReconciliationRepository;
import com.delivery.finance.repository.CommissionRepository;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides tenant-scoped finance lists and aggregate summaries. */
@Service
public class FinanceService {
    private final PaymentRepository paymentRepository;
    private final CommissionRepository commissionRepository;
    private final CashReconciliationRepository cashReconciliationRepository;
    private final RefundRepository refundRepository;
    private final FinanceMapper financeMapper;
    private final TenantContext tenantContext;
    private final PaymentService paymentService;

    public FinanceService(
            PaymentRepository paymentRepository,
            CommissionRepository commissionRepository,
            CashReconciliationRepository cashReconciliationRepository,
            RefundRepository refundRepository,
            FinanceMapper financeMapper,
            TenantContext tenantContext,
            PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.commissionRepository = commissionRepository;
        this.cashReconciliationRepository = cashReconciliationRepository;
        this.refundRepository = refundRepository;
        this.financeMapper = financeMapper;
        this.tenantContext = tenantContext;
        this.paymentService = paymentService;
    }

    /** Returns payment transactions visible to finance for the current tenant. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions() {
        UUID tenantId = tenantId();
        return paymentRepository.findByTenantId(tenantId).stream()
                .map(financeMapper::toTransactionResponse)
                .toList();
    }

    /** Returns marketplace commission records for the current tenant. */
    @Transactional(readOnly = true)
    public List<CommissionResponse> listCommissions() {
        return commissionRepository.findByTenantId(tenantId()).stream().map(financeMapper::toCommissionResponse).toList();
    }

    /** Returns cash reconciliation records for the current tenant. */
    @Transactional(readOnly = true)
    public List<CashReconciliationResponse> listCashReconciliations() {
        return cashReconciliationRepository.findByTenantId(tenantId()).stream().map(financeMapper::toCashReconciliationResponse).toList();
    }

    /** Computes top-level finance summary totals. */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary() {
        UUID tenantId = tenantId();
        BigDecimal confirmedPaymentsTotal = paymentRepository.findByTenantIdAndStatus(tenantId, PaymentStatus.CONFIRMED).stream()
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal commissionTotal = commissionRepository.findByTenantId(tenantId).stream()
                .map(item -> item.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundsTotal = refundRepository.findByTenantIdAndStatus(tenantId, RefundStatus.REFUNDED).stream()
                .map(item -> item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unreconciledCashTotal = cashReconciliationRepository.findByTenantIdAndStatus(tenantId, "PENDING").stream()
                .map(item -> item.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceSummaryResponse(confirmedPaymentsTotal, commissionTotal, refundsTotal, unreconciledCashTotal);
    }

    /**
     * Approves a pending refund through PaymentService so status guards, audit and the full-refund →
     * order REFUNDED transition apply (INC-06). Administrative record only: no money moves (no PSP).
     */
    @Transactional
    public RefundFinanceResponse approveRefund(UUID id) {
        return toRefundFinanceResponse(paymentService.approveRefund(id));
    }

    /** Rejects a pending refund through PaymentService (same guards and audit as /payments/refunds). */
    @Transactional
    public RefundFinanceResponse rejectRefund(UUID id) {
        return toRefundFinanceResponse(paymentService.rejectRefund(id));
    }

    private static RefundFinanceResponse toRefundFinanceResponse(RefundResponse refund) {
        return new RefundFinanceResponse(refund.id(), refund.paymentId(), refund.orderId(), refund.amount(), refund.reason(), refund.status());
    }

    /** Returns tenant refunds for finance review workflows. */
    @Transactional(readOnly = true)
    public java.util.List<RefundFinanceResponse> listRefunds() {
        UUID tenantId = tenantId();
        return refundRepository.findByTenantId(tenantId).stream()
                .map(financeMapper::toRefundFinanceResponse)
                .toList();
    }

    /** Returns pending and settled commission totals as payout status. */
    @Transactional(readOnly = true)
    public PayoutStatusResponse payoutStatus() {
        UUID tenantId = tenantId();
        BigDecimal pending = commissionRepository.findByTenantIdAndStatus(tenantId, "PENDING").stream()
                .map(item -> item.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settled = commissionRepository.findByTenantIdAndStatus(tenantId, "SETTLED").stream()
                .map(item -> item.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayoutStatusResponse(pending, settled);
    }

    /** Builds a basic export payload for transactions, commissions, refunds, and COD records. */
    @Transactional(readOnly = true)
    public FinanceExportResponse export() {
        return new FinanceExportResponse(listTransactions(), listCommissions(), listRefunds(), listCashReconciliations());
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
