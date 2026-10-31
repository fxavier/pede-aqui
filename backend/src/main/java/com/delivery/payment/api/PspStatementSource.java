package com.delivery.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Source of a PSP settlement statement for the daily reconciliation (LAC-32 step 1) — PSP-specific (file over SFTP,
 * portal export, API), so this package defines only the seam. When an implementation exists, the payment-reconciliation
 * job will import its lines and match them by provider reference (MATCHED / MISSING_IN_PSP / MISSING_LOCALLY /
 * AMOUNT_MISMATCH) and post the PSP fees to the ledger; until then the job compares attempts with payments only.
 */
public interface PspStatementSource {

    /** Provider id this statement belongs to (same as {@link PaymentProvider#id()}). */
    String providerId();

    /** Settled lines of {@code day} (provider's settlement date). */
    List<StatementLine> linesFor(LocalDate day);

    /** One settled charge or reversal as reported by the PSP. */
    record StatementLine(String providerReference, BigDecimal amount, String currency, BigDecimal fee, Instant settledAt) {
    }
}
