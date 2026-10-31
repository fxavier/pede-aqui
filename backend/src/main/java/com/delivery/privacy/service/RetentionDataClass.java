package com.delivery.privacy.service;

import java.time.Duration;

/**
 * Data classes purged by the retention cleaner (LAC-63, W7-C) with their default periods (gaps register, "a validar
 * com jurista") and the SQL shape of the purge: table, the timestamp compared with the cutoff, an extra condition that
 * protects rows still in use, and the transaction-local purge flag an append-only table requires.
 *
 * <p>Not here because they already have their own job (aligned, not duplicated): {@code courier_location_history}
 * (30 days, {@code courier-location-retention}), {@code checkout_quotes} (expired + 1 day, {@code checkout-quote-cleanup}),
 * completed {@code event_publication} rows (7 days, {@code event-publication-cleanup}); {@code courier_last_locations}
 * is deleted when the courier goes off duty. Financial/fiscal records (orders, payments, refunds, ledger) are never
 * purged here (legal period to be confirmed, LAC-38).
 */
public enum RetentionDataClass {
    /** Append-only audit trail: 24 months; DELETE only with {@code app.audit_logs_purge} (V053). */
    AUDIT_LOGS("audit-logs", "audit_logs", "created_at", null, "app.audit_logs_purge", Duration.ofDays(730)),
    /** Outbox/delivery log of push/SMS/e-mail (masked recipients): 180 days, never a row still QUEUED. */
    NOTIFICATION_DELIVERIES("notification-deliveries", "notification_deliveries", "created_at", "status <> 'QUEUED'", null,
            Duration.ofDays(180)),
    /** In-app inbox: 180 days. */
    NOTIFICATIONS("notifications", "notifications", "created_at", null, null, Duration.ofDays(180)),
    /** Order status trail (V058, append-only): kept as long as fiscal records (10 years, a validar, LAC-38). */
    ORDER_STATUS_HISTORY("order-status-history", "order_status_history", "created_at", null, "app.status_history_purge",
            Duration.ofDays(3650)),
    /** Payment status trail (V058, append-only): same as the order trail. */
    PAYMENT_STATUS_HISTORY("payment-status-history", "payment_status_history", "created_at", null, "app.status_history_purge",
            Duration.ofDays(3650)),
    /** M2M provisioning idempotency records (owner e-mail): 24 h after they finished; in-flight rows are kept. */
    PROVISIONING_REQUESTS("provisioning-requests", "provisioning_requests", "updated_at", "status IN ('SUCCEEDED', 'FAILED')",
            null, Duration.ofDays(1)),
    /** The cleaner's own run log: 12 months. */
    DATA_RETENTION_RUNS("data-retention-runs", "data_retention_runs", "started_at", null, null, Duration.ofDays(365));

    private final String key;
    private final String table;
    private final String timestampColumn;
    private final String extraCondition;
    private final String purgeFlag;
    private final Duration defaultRetention;

    RetentionDataClass(String key, String table, String timestampColumn, String extraCondition, String purgeFlag,
                       Duration defaultRetention) {
        this.key = key;
        this.table = table;
        this.timestampColumn = timestampColumn;
        this.extraCondition = extraCondition;
        this.purgeFlag = purgeFlag;
        this.defaultRetention = defaultRetention;
    }

    /** Configuration key and metric tag ({@code app.privacy.retention.classes.<key>}). */
    public String key() { return key; }
    public String table() { return table; }
    public Duration defaultRetention() { return defaultRetention; }
    /** Transaction-local setting that unlocks DELETE on an append-only table, or null. */
    public String purgeFlag() { return purgeFlag; }

    /**
     * One bounded batch: deletes up to {@code ?2} rows older than the cutoff {@code ?1}. No ORDER BY on purpose: every
     * matching row is expired, and sorting the whole backlog per batch would defeat the BRIN index (V071).
     */
    public String batchDeleteSql() {
        String where = timestampColumn + " < ?" + (extraCondition == null ? "" : " AND " + extraCondition);
        return "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " WHERE " + where + " LIMIT ?)";
    }
}
