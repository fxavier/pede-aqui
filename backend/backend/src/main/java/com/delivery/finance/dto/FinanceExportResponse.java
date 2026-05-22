package com.delivery.finance.dto;

import java.util.List;

/** Export payload aggregating core finance lists for simple downloads. */
public record FinanceExportResponse(
        List<TransactionResponse> transactions,
        List<CommissionResponse> commissions,
        List<RefundFinanceResponse> refunds,
        List<CashReconciliationResponse> cashReconciliations) {}
