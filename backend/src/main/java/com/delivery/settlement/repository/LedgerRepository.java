package com.delivery.settlement.repository;

import com.delivery.common.pagination.KeysetCursor;
import com.delivery.settlement.entity.LedgerAccountType;
import com.delivery.settlement.entity.LedgerTransactionType;
import com.delivery.settlement.service.PostingRules.Line;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to the append-only ledger tables (V065). Plain SQL on purpose: idempotent inserts need
 * {@code ON CONFLICT DO NOTHING}, the tables are never updated, and balances are derived sums. Every query is
 * tenant-scoped.
 */
@Repository
public class LedgerRepository {
    /** Ledger currency; V065 constrains every row to MZN for now (one currency per tenant, ADR-0003). */
    public static final String CURRENCY = "MZN";

    /** A transaction header for listings. */
    public record TransactionRow(UUID id, String idempotencyKey, String type, String sourceType, UUID sourceId, UUID orderId,
                                 UUID vendorId, String currency, Instant occurredAt, String description, Instant createdAt) {}

    /** A line with its account, for listings and reversals. */
    public record EntryRow(UUID transactionId, LedgerAccountType accountType, UUID ownerId, BigDecimal debit, BigDecimal credit,
                           String currency) {}

    /** Summed debits/credits of one account. */
    public record AccountTotals(BigDecimal debit, BigDecimal credit, Instant lastPostedAt) {}

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** The account id, if the account exists. */
    public Optional<UUID> findAccountId(UUID tenantId, LedgerAccountType type, UUID ownerId) {
        List<UUID> ids = ownerId == null
                ? jdbc.queryForList("SELECT id FROM ledger_accounts WHERE tenant_id = ? AND account_type = ? AND owner_id IS NULL AND currency = ?",
                        UUID.class, tenantId, type.name(), CURRENCY)
                : jdbc.queryForList("SELECT id FROM ledger_accounts WHERE tenant_id = ? AND account_type = ? AND owner_id = ? AND currency = ?",
                        UUID.class, tenantId, type.name(), ownerId, CURRENCY);
        return ids.stream().findFirst();
    }

    /** Creates the account unless it exists (concurrent creators: one insert wins, the others do nothing). */
    public void insertAccountIfAbsent(UUID tenantId, LedgerAccountType type, UUID ownerId) {
        jdbc.update("""
                INSERT INTO ledger_accounts (id, tenant_id, account_type, kind, owner_type, owner_id, currency)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING""",
                UUID.randomUUID(), tenantId, type.name(), type.kind().name(), type.ownerType().name(), ownerId, CURRENCY);
    }

    /**
     * Inserts the transaction header unless the tenant already has one with this idempotency key. Returns the new id,
     * or empty for a duplicate (a concurrent insert of the same key waits for the other transaction and then does
     * nothing).
     */
    public Optional<UUID> insertTransaction(UUID tenantId, String idempotencyKey, LedgerTransactionType type, String sourceType,
                                            UUID sourceId, UUID orderId, UUID vendorId, Instant occurredAt, String description) {
        List<UUID> ids = jdbc.queryForList("""
                INSERT INTO ledger_transactions (id, tenant_id, idempotency_key, transaction_type, source_type, source_id,
                                                 order_id, vendor_id, currency, occurred_at, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                RETURNING id""",
                UUID.class, UUID.randomUUID(), tenantId, idempotencyKey, type.name(), sourceType, sourceId, orderId, vendorId,
                CURRENCY, Timestamp.from(occurredAt), description);
        return ids.stream().findFirst();
    }

    /** Appends one line (exactly one of debit/credit is positive). */
    public void insertEntry(UUID tenantId, UUID transactionId, UUID accountId, BigDecimal debit, BigDecimal credit) {
        jdbc.update("INSERT INTO ledger_entries (id, tenant_id, transaction_id, account_id, debit, credit, currency) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, transactionId, accountId, debit, credit, CURRENCY);
    }

    /** The signed lines (debit − credit) of the tenant's transaction with this key, empty when it does not exist. */
    public List<Line> linesOf(UUID tenantId, String idempotencyKey) {
        return jdbc.query("""
                SELECT a.account_type, a.owner_id, e.debit - e.credit AS signed
                  FROM ledger_transactions t
                  JOIN ledger_entries e ON e.transaction_id = t.id
                  JOIN ledger_accounts a ON a.id = e.account_id
                 WHERE t.tenant_id = ? AND t.idempotency_key = ?
                 ORDER BY e.created_at, e.id""",
                (rs, i) -> new Line(LedgerAccountType.valueOf(rs.getString("account_type")), rs.getObject("owner_id", UUID.class),
                        rs.getBigDecimal("signed")),
                tenantId, idempotencyKey);
    }

    /** True when the tenant has a transaction with this key. */
    public boolean exists(UUID tenantId, String idempotencyKey) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, tenantId, idempotencyKey);
        return count != null && count > 0;
    }

    /** Summed debits/credits of one account (zeros when it has no lines or does not exist). */
    public AccountTotals totals(UUID tenantId, LedgerAccountType type, UUID ownerId) {
        String ownerFilter = ownerId == null ? "a.owner_id IS NULL" : "a.owner_id = :owner";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenant", tenantId).addValue("type", type.name())
                .addValue("owner", ownerId).addValue("currency", CURRENCY);
        return named.queryForObject("""
                SELECT COALESCE(SUM(e.debit), 0) AS debit, COALESCE(SUM(e.credit), 0) AS credit, MAX(e.created_at) AS last_at
                  FROM ledger_accounts a
                  JOIN ledger_entries e ON e.account_id = a.id
                 WHERE a.tenant_id = :tenant AND a.account_type = :type AND a.currency = :currency""" + " AND " + ownerFilter,
                params, (rs, i) -> new AccountTotals(rs.getBigDecimal("debit"), rs.getBigDecimal("credit"), instant(rs, "last_at")));
    }

    /**
     * Keyset page of transaction headers, newest first ({@code created_at DESC, id DESC}; served by
     * idx_ledger_transactions_tenant_created_id / _tenant_vendor_created), optionally for one order or vendor.
     */
    public List<TransactionRow> page(UUID tenantId, UUID orderId, UUID vendorId, KeysetCursor cursor, int probe) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, idempotency_key, transaction_type, source_type, source_id, order_id, vendor_id, currency,
                       occurred_at, description, created_at
                  FROM ledger_transactions
                 WHERE tenant_id = :tenant
                   AND (created_at < :cAt OR (created_at = :cAt AND id < :cId))""");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenant", tenantId)
                .addValue("cAt", Timestamp.from(cursor.createdAt())).addValue("cId", cursor.id()).addValue("limit", probe);
        if (orderId != null) {
            sql.append(" AND order_id = :order");
            params.addValue("order", orderId);
        }
        if (vendorId != null) {
            sql.append(" AND vendor_id = :vendor");
            params.addValue("vendor", vendorId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");
        return named.query(sql.toString(), params, (rs, i) -> new TransactionRow(rs.getObject("id", UUID.class),
                rs.getString("idempotency_key"), rs.getString("transaction_type"), rs.getString("source_type"),
                rs.getObject("source_id", UUID.class), rs.getObject("order_id", UUID.class), rs.getObject("vendor_id", UUID.class),
                rs.getString("currency"), instant(rs, "occurred_at"), rs.getString("description"), instant(rs, "created_at")));
    }

    /** Lines of the given transactions (same tenant), in insertion order. */
    public List<EntryRow> entriesOf(UUID tenantId, Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return List.of();
        }
        return named.query("""
                SELECT e.transaction_id, a.account_type, a.owner_id, e.debit, e.credit, e.currency
                  FROM ledger_entries e
                  JOIN ledger_accounts a ON a.id = e.account_id
                 WHERE e.tenant_id = :tenant AND e.transaction_id IN (:ids)
                 ORDER BY e.created_at, e.id""",
                Map.of("tenant", tenantId, "ids", new ArrayList<>(transactionIds)),
                (rs, i) -> new EntryRow(rs.getObject("transaction_id", UUID.class), LedgerAccountType.valueOf(rs.getString("account_type")),
                        rs.getObject("owner_id", UUID.class), rs.getBigDecimal("debit"), rs.getBigDecimal("credit"), rs.getString("currency")));
    }

    /** Tenants whose entries do not sum to zero (Σ debit ≠ Σ credit) in some currency — must always be empty. */
    public List<UUID> imbalancedTenants() {
        return jdbc.queryForList("""
                SELECT DISTINCT tenant_id FROM (
                    SELECT tenant_id FROM ledger_entries GROUP BY tenant_id, currency HAVING SUM(debit) <> SUM(credit)) x""",
                UUID.class);
    }

    /** Number of transactions whose lines do not balance — the deferred trigger makes this impossible; checked anyway. */
    public long unbalancedTransactions() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id FROM ledger_entries GROUP BY transaction_id, currency HAVING SUM(debit) <> SUM(credit)) x""",
                Long.class);
        return count == null ? 0 : count;
    }

    /** Number of liability accounts with a debit balance (owed less than zero) — a symptom worth a warning. */
    public long negativeLiabilityAccounts() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_account_balances WHERE kind = 'LIABILITY' AND balance < 0", Long.class);
        return count == null ? 0 : count;
    }

    /** True when the vendor exists in the tenant (balance endpoint 404). Plain SQL: settlement reads no vendor types. */
    public boolean vendorExists(UUID tenantId, UUID vendorId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM vendors WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, vendorId);
        return count != null && count > 0;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
