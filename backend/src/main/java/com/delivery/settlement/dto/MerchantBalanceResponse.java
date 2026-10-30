package com.delivery.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A merchant's ledger balance (MERCHANT_PAYABLE): {@code balance} = what the platform owes the merchant
 * (credits − debits); {@code lastPostedAt} is null when nothing was posted yet.
 */
public record MerchantBalanceResponse(UUID vendorId, String currency, BigDecimal balance, BigDecimal totalCredits,
                                      BigDecimal totalDebits, Instant lastPostedAt) {}
