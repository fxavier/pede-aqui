package com.delivery.finance.controller;

import com.delivery.finance.dto.CashReconciliationResponse;
import com.delivery.finance.dto.CommissionResponse;
import com.delivery.finance.dto.FinanceExportResponse;
import com.delivery.finance.dto.FinanceSummaryResponse;
import com.delivery.finance.dto.PayoutStatusResponse;
import com.delivery.finance.dto.RefundFinanceResponse;
import com.delivery.finance.dto.TransactionResponse;
import com.delivery.finance.service.FinanceService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes finance transactions, commissions, reconciliation, and summary endpoints. */
@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
public class FinanceController {
    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) { this.financeService = financeService; }

    @GetMapping("/transactions")
    public List<TransactionResponse> transactions() { return financeService.listTransactions(); }

    @GetMapping("/commissions")
    public List<CommissionResponse> commissions() { return financeService.listCommissions(); }

    @GetMapping("/cash-reconciliation")
    public List<CashReconciliationResponse> cashReconciliation() { return financeService.listCashReconciliations(); }

    @GetMapping("/summary")
    public FinanceSummaryResponse summary() { return financeService.summary(); }

    @GetMapping("/refunds")
    public List<RefundFinanceResponse> refunds() { return financeService.listRefunds(); }

    @PatchMapping("/refunds/{id}/approve")
    public RefundFinanceResponse approveRefund(@PathVariable UUID id) { return financeService.approveRefund(id); }

    @PatchMapping("/refunds/{id}/reject")
    public RefundFinanceResponse rejectRefund(@PathVariable UUID id) { return financeService.rejectRefund(id); }

    @GetMapping("/payout-status")
    public PayoutStatusResponse payoutStatus() { return financeService.payoutStatus(); }

    @GetMapping("/export")
    public FinanceExportResponse export() { return financeService.export(); }
}
