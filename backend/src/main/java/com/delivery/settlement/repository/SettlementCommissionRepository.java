package com.delivery.settlement.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to commission plans (V066) and the {@code commissions} table (V008; its only writer since W6-B). The
 * {@code commissions} rows stay readable through finance's {@code Commission} entity — settlement writes them by SQL
 * so it does not depend on finance's internal types.
 */
@Repository
public class SettlementCommissionRepository {
    /** Where a rate came from; {@code planId} is null for a per-vendor rate or the configured fallback. */
    public record ResolvedRate(BigDecimal rate, UUID planId, String source) {}

    private final JdbcTemplate jdbc;

    public SettlementCommissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The commission rate in force for the vendor at {@code at}: the vendor's override (own rate or assigned plan,
     * latest {@code effective_from} still in force), else the tenant's default plan in force, else empty.
     */
    public Optional<ResolvedRate> resolve(UUID tenantId, UUID vendorId, Instant at) {
        Timestamp when = Timestamp.from(at);
        List<ResolvedRate> override = jdbc.query("""
                SELECT COALESCE(o.rate, p.rate) AS rate, o.plan_id
                  FROM vendor_commission_overrides o
                  LEFT JOIN commission_plans p ON p.id = o.plan_id
                 WHERE o.tenant_id = ? AND o.vendor_id = ? AND o.effective_from <= ? AND (o.effective_to IS NULL OR o.effective_to > ?)
                 ORDER BY o.effective_from DESC
                 LIMIT 1""",
                (rs, i) -> new ResolvedRate(rs.getBigDecimal("rate"), rs.getObject("plan_id", UUID.class), "VENDOR_OVERRIDE"),
                tenantId, vendorId, when, when);
        if (!override.isEmpty() && override.get(0).rate() != null) {
            return Optional.of(override.get(0));
        }
        return jdbc.query("""
                SELECT id, rate FROM commission_plans
                 WHERE tenant_id = ? AND is_default AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                 ORDER BY effective_from DESC
                 LIMIT 1""",
                (rs, i) -> new ResolvedRate(rs.getBigDecimal("rate"), rs.getObject("id", UUID.class), "DEFAULT_PLAN"),
                tenantId, when, when).stream().findFirst();
    }

    /** Records the accrued (gross) commission of an order once; a repeat for the same order does nothing. */
    public void insertAccrued(UUID tenantId, UUID orderId, UUID vendorId, BigDecimal basis, BigDecimal rate, BigDecimal amount,
                              UUID planId, String source, UUID ledgerTransactionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO commissions (id, tenant_id, order_id, vendor_id, basis_amount, commission_rate, commission_amount,
                                         status, created_at, updated_at, plan_id, currency, source, ledger_transaction_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACCRUED', ?, ?, ?, 'MZN', ?, ?)
                ON CONFLICT (tenant_id, order_id) DO NOTHING""",
                UUID.randomUUID(), tenantId, orderId, vendorId, basis, rate, amount, now, now, planId, source, ledgerTransactionId);
    }
}
