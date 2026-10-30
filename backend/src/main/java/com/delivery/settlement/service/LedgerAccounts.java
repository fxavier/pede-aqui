package com.delivery.settlement.service;

import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.repository.LedgerRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves ledger accounts, creating missing ones on demand inside the caller's transaction
 * ({@code INSERT … ON CONFLICT DO NOTHING}, then re-read). Callers resolve all accounts of a posting in the global
 * {@link LedgerAccountType} order before taking its idempotency key, so concurrent first-time creations queue in one
 * order; a residual deadlock (only possible while two postings create the same new accounts at once) aborts one
 * listener transaction, which the event registry retries.
 */
@Component
public class LedgerAccounts {
    private final LedgerRepository repository;

    public LedgerAccounts(LedgerRepository repository) {
        this.repository = repository;
    }

    /** The id of the tenant's account of this type/owner, created if missing. */
    public UUID idOf(UUID tenantId, LedgerAccountType type, UUID ownerId) {
        if (type.ownerType() != LedgerAccountType.OwnerType.PLATFORM && ownerId == null) {
            throw new IllegalArgumentException(type + " needs an owner id");
        }
        UUID owner = type.ownerType() == LedgerAccountType.OwnerType.PLATFORM ? null : ownerId;
        return repository.findAccountId(tenantId, type, owner).orElseGet(() -> {
            repository.insertAccountIfAbsent(tenantId, type, owner);
            return repository.findAccountId(tenantId, type, owner)
                    .orElseThrow(() -> new IllegalStateException("ledger account not created: " + type));
        });
    }
}
