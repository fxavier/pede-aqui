package com.delivery.settlement.controller;

import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.settlement.dto.LedgerTransactionResponse;
import com.delivery.settlement.dto.MerchantBalanceResponse;
import com.delivery.settlement.service.LedgerQueryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read endpoints of the settlement ledger (LAC-33/34): merchant balance and the transaction journal. */
@RestController
@RequestMapping("/api/v1/finance")
public class SettlementController {
    private final LedgerQueryService queryService;

    public SettlementController(LedgerQueryService queryService) {
        this.queryService = queryService;
    }

    /** What the platform owes the merchant; VENDOR_ADMIN only for its own vendor. */
    @GetMapping("/merchants/{vendorId}/balance")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','VENDOR_ADMIN')")
    public MerchantBalanceResponse merchantBalance(@PathVariable UUID vendorId) {
        return queryService.merchantBalance(vendorId);
    }

    /** Ledger transactions with lines, newest first ({@code {items, nextCursor}}); optional orderId/vendorId filters. */
    @GetMapping("/ledger/transactions")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public CursorPage<LedgerTransactionResponse> transactions(
            @RequestParam(defaultValue = "" + PageLimits.DEFAULT_CURSOR_LIMIT) int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID vendorId) {
        return queryService.transactions(limit, cursor, orderId, vendorId);
    }
}
