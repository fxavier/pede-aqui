package com.delivery.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One ledger transaction with its lines (GET /api/v1/finance/ledger/transactions). */
public record LedgerTransactionResponse(UUID id, String type, String idempotencyKey, String sourceType, UUID sourceId,
                                        UUID orderId, UUID vendorId, String currency, Instant occurredAt, Instant createdAt,
                                        String description, List<Entry> entries) {

    /** A line: exactly one of debit/credit is positive; {@code ownerId} = vendor/courier for payable accounts. */
    public record Entry(String accountType, UUID ownerId, BigDecimal debit, BigDecimal credit, String currency) {}
}
