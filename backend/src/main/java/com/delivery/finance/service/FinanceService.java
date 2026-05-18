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
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
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

    public FinanceService(
            PaymentRepository paymentRepository,
            CommissionRepository commissionRepository,
            CashReconciliationRepository cashReconciliationRepository,
            RefundRepository refundRepository,
            FinanceMapper financeMapper,
            TenantContext tenantContext) {
        this.paymentRepository = paymentRepository;
        this.commissionRepository = commissionRepository;
        this.cashReconciliationRepository = cashReconciliationRepository;
        this.refundRepository = refundRepository;
        this.financeMapper = financeMapper;
        this.tenantContext = tenantContext;
    }

    /** Returns payment transactions visible to finance for the current tenant. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions() {
        UUID tenantId = tenantId();
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getTenantId().equals(tenantId))
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

    /** Returns tenant refunds for finance review workflows. */
    @Transactional(readOnly = true)
    public java.util.List<RefundFinanceResponse> listRefunds() {
        return refundRepository.findAll().stream()
                .filter(item -> item.getTenantId().equals(tenantId()))
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
