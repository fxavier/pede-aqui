package com.delivery.settlement.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.KeysetCursor;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.TenantContext;
import com.delivery.settlement.dto.LedgerTransactionResponse;
import com.delivery.settlement.dto.MerchantBalanceResponse;
import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.repository.LedgerRepository;
import com.delivery.settlement.repository.LedgerRepository.AccountTotals;
import com.delivery.settlement.repository.LedgerRepository.EntryRow;
import com.delivery.settlement.repository.LedgerRepository.TransactionRow;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the settlement ledger: merchant balances and the keyset transaction journal (tenant-scoped). */
@Service
public class LedgerQueryService {
    private final LedgerRepository ledger;
    private final TenantContext tenantContext;
    private final MerchantScopeGuard merchantScopeGuard;

    public LedgerQueryService(LedgerRepository ledger, TenantContext tenantContext, MerchantScopeGuard merchantScopeGuard) {
        this.ledger = ledger;
        this.tenantContext = tenantContext;
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /**
     * What the platform owes the merchant (MERCHANT_PAYABLE credits − debits). FINANCE/ADMIN: any vendor of the tenant;
     * VENDOR_ADMIN: only its own vendor (403 vendor_access_denied / vendor_scope_unresolved otherwise). Unknown vendor → 404.
     */
    @Transactional(readOnly = true)
    public MerchantBalanceResponse merchantBalance(UUID vendorId) {
        UUID tenantId = tenantId();
        if (!merchantScopeGuard.hasTenantWideRole()) {
            merchantScopeGuard.ensureCallerOwnsVendor(vendorId, "Access to this vendor's balance is not permitted");
        }
        if (!ledger.vendorExists(tenantId, vendorId)) {
            throw new NotFoundException("Vendor was not found");
        }
        AccountTotals totals = ledger.totals(tenantId, LedgerAccountType.MERCHANT_PAYABLE, vendorId);
        return new MerchantBalanceResponse(vendorId, LedgerRepository.CURRENCY, totals.credit().subtract(totals.debit()),
                totals.credit(), totals.debit(), totals.lastPostedAt());
    }

    /** Ledger transactions with their lines, newest first, keyset paged; optional order/vendor filters. */
    @Transactional(readOnly = true)
    public CursorPage<LedgerTransactionResponse> transactions(int limit, String cursor, UUID orderId, UUID vendorId) {
        UUID tenantId = tenantId();
        int pageLimit = PageLimits.cursorLimit(limit);
        List<TransactionRow> probe = ledger.page(tenantId, orderId, vendorId, KeysetCursor.decodeOrStart(cursor), pageLimit + 1);
        return CursorPage.fromProbe(probe, pageLimit, row -> new KeysetCursor(row.createdAt(), row.id()), rows -> {
            Map<UUID, List<EntryRow>> entries = ledger.entriesOf(tenantId, rows.stream().map(TransactionRow::id).toList()).stream()
                    .collect(Collectors.groupingBy(EntryRow::transactionId));
            return rows.stream().map(row -> new LedgerTransactionResponse(row.id(), row.type(), row.idempotencyKey(), row.sourceType(),
                    row.sourceId(), row.orderId(), row.vendorId(), row.currency(), row.occurredAt(), row.createdAt(), row.description(),
                    entries.getOrDefault(row.id(), List.of()).stream()
                            .map(e -> new LedgerTransactionResponse.Entry(e.accountType().name(), e.ownerId(), e.debit(), e.credit(), e.currency()))
                            .toList()))
                    .toList();
        });
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
