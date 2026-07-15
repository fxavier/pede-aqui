package com.delivery.report.repository;

import com.delivery.report.dto.ReportInterval;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only native aggregation queries over immutable order data (orders, order_items,
 * refunds, commissions) for sales reports. Never reads live SKU prices for money figures;
 * live catalog joins are used only for identity/labels and the documented by-category fallback.
 */
@Repository
@Transactional(readOnly = true)
public class SalesReportRepository {

    /**
     * Revenue basis (spec AC-8.2): every status at or after PAYMENT_CONFIRMED. PENDING and
     * PAYMENT_PENDING are the only pre-payment states in the order state machine; CANCELLED,
     * REJECTED_BY_VENDOR, REFUND_PENDING and REFUNDED are post-payment outcomes in this
     * codebase (checkout confirms payment synchronously), so they stay in gross and are
     * netted out via refunds.
     */
    public static final List<String> CONFIRMED_STATUSES = List.of(
            "PAYMENT_CONFIRMED", "ACCEPTED_BY_VENDOR", "REJECTED_BY_VENDOR", "PREPARING",
            "READY_FOR_PICKUP", "DISPATCH_PENDING", "ASSIGNED_TO_COURIER", "PICKED_UP",
            "DELIVERING", "DELIVERED", "CANCELLED", "REFUND_PENDING", "REFUNDED");

    /** Approved/settled refund statuses that reduce net revenue. */
    public static final List<String> COUNTED_REFUND_STATUSES = List.of("APPROVED", "REFUNDED");

    private static final int STREAM_FETCH_SIZE = 500;

    private static final String REFUND_JOIN =
            " LEFT JOIN (SELECT order_id, SUM(amount) AS amount FROM refunds WHERE status IN (:refundStatuses) GROUP BY order_id) rf ON rf.order_id = o.id";
    private static final String COMMISSION_JOIN =
            " LEFT JOIN (SELECT order_id, SUM(commission_amount) AS amount FROM commissions GROUP BY order_id) cm ON cm.order_id = o.id";
    private static final String LINE_SUM_JOIN =
            " JOIN (SELECT order_id, SUM(line_total) AS line_sum FROM order_items GROUP BY order_id) li ON li.order_id = o.id";

    private static final String SUMMARY_COLUMNS = """
            COUNT(o.id) AS order_count,
            COALESCE(SUM(o.total), 0) AS gross,
            COALESCE(SUM(o.discount_total), 0) AS discount_total,
            COALESCE(SUM(rf.amount), 0) AS refunds,
            COALESCE(SUM(cm.amount), 0) AS commission,
            COUNT(o.id) FILTER (WHERE o.status = 'DELIVERED') AS delivered_count,
            COUNT(o.id) FILTER (WHERE o.status = 'CANCELLED') AS cancelled_count""";

    private static final RowMapper<SummaryRow> SUMMARY_MAPPER = (rs, rowNum) -> new SummaryRow(
            rs.getLong("order_count"),
            rs.getBigDecimal("gross"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("refunds"),
            rs.getBigDecimal("commission"),
            rs.getLong("delivered_count"),
            rs.getLong("cancelled_count"));

    private static final RowMapper<BucketRow> BUCKET_MAPPER = (rs, rowNum) -> new BucketRow(
            rs.getObject("bucket", OffsetDateTime.class).toInstant(),
            rs.getLong("order_count"),
            rs.getBigDecimal("gross"),
            rs.getBigDecimal("discount_total"),
            rs.getBigDecimal("refunds"),
            rs.getBigDecimal("commission"),
            rs.getLong("delivered_count"),
            rs.getLong("cancelled_count"));

    private static final RowMapper<DimensionAggregateRow> DIMENSION_MAPPER = (rs, rowNum) -> new DimensionAggregateRow(
            rs.getString("key"),
            rs.getString("label"),
            rs.getBigDecimal("gross"),
            rs.getBigDecimal("refunds"),
            rs.getBigDecimal("commission"),
            rs.getLong("quantity_sold"));

    private final NamedParameterJdbcTemplate jdbc;

    public SalesReportRepository(JdbcTemplate jdbcTemplate) {
        // Dedicated template with a bounded fetch size so CSV exports stream on a JDBC cursor
        // (Postgres streams when a fetch size is set inside a transaction) instead of
        // materialising whole result sets.
        JdbcTemplate streamingTemplate = new JdbcTemplate(jdbcTemplate.getDataSource());
        streamingTemplate.setFetchSize(STREAM_FETCH_SIZE);
        this.jdbc = new NamedParameterJdbcTemplate(streamingTemplate);
    }

    /** Aggregates window KPIs (counts, gross, discount, refunds, commission) in one query. */
    public SummaryRow summary(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        String sql = "SELECT " + SUMMARY_COLUMNS + " FROM orders o"
                + REFUND_JOIN + COMMISSION_JOIN
                + " WHERE " + orderFilter(vendorId);
        return jdbc.queryForObject(sql, params(tenantId, vendorId, from, to), SUMMARY_MAPPER);
    }

    /** Aggregates the summary KPIs into date_trunc buckets ordered by bucket start. */
    public List<BucketRow> timeseries(UUID tenantId, UUID vendorId, Instant from, Instant to, ReportInterval interval) {
        Map<String, Object> params = params(tenantId, vendorId, from, to);
        params.put("interval", interval.param());
        return jdbc.query(timeseriesSql(vendorId), params, BUCKET_MAPPER);
    }

    /** Streams timeseries buckets row-by-row for CSV export. */
    public void streamTimeseries(UUID tenantId, UUID vendorId, Instant from, Instant to, ReportInterval interval, Consumer<BucketRow> consumer) {
        Map<String, Object> params = params(tenantId, vendorId, from, to);
        params.put("interval", interval.param());
        jdbc.query(timeseriesSql(vendorId), params, rs -> {
            consumer.accept(BUCKET_MAPPER.mapRow(rs, rs.getRow()));
        });
    }

    /** Aggregates gross/refunds/commission per vendor (whole-order figures; exact attribution). */
    public List<DimensionAggregateRow> byVendor(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        return jdbc.query(byVendorSql(vendorId), params(tenantId, vendorId, from, to), DIMENSION_MAPPER);
    }

    /** Streams by-vendor rows for CSV export. */
    public void streamByVendor(UUID tenantId, UUID vendorId, Instant from, Instant to, Consumer<DimensionAggregateRow> consumer) {
        jdbc.query(byVendorSql(vendorId), params(tenantId, vendorId, from, to), rs -> {
            consumer.accept(DIMENSION_MAPPER.mapRow(rs, rs.getRow()));
        });
    }

    /** Aggregates quantity and line revenue per product strictly from order_item snapshots. */
    public List<DimensionAggregateRow> byProduct(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        return jdbc.query(byProductSql(vendorId), params(tenantId, vendorId, from, to), DIMENSION_MAPPER);
    }

    /** Streams by-product rows for CSV export. */
    public void streamByProduct(UUID tenantId, UUID vendorId, Instant from, Instant to, Consumer<DimensionAggregateRow> consumer) {
        jdbc.query(byProductSql(vendorId), params(tenantId, vendorId, from, to), rs -> {
            consumer.accept(DIMENSION_MAPPER.mapRow(rs, rs.getRow()));
        });
    }

    /**
     * Aggregates line revenue per category using the order-time snapshot when present
     * (V033, populated for new orders) and falling back to the live product→category join for
     * historical rows — documented caveat: recategorising a product retroactively moves only
     * those fallback rows.
     */
    public List<DimensionAggregateRow> byCategory(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        return jdbc.query(byCategorySql(vendorId), params(tenantId, vendorId, from, to), DIMENSION_MAPPER);
    }

    /** Streams by-category rows for CSV export. */
    public void streamByCategory(UUID tenantId, UUID vendorId, Instant from, Instant to, Consumer<DimensionAggregateRow> consumer) {
        jdbc.query(byCategorySql(vendorId), params(tenantId, vendorId, from, to), rs -> {
            consumer.accept(DIMENSION_MAPPER.mapRow(rs, rs.getRow()));
        });
    }

    /** Total order gross for the window — share denominator for by-vendor rows. */
    public BigDecimal orderGrossTotal(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        String sql = "SELECT COALESCE(SUM(o.total), 0) FROM orders o WHERE " + orderFilter(vendorId);
        return jdbc.queryForObject(sql, params(tenantId, vendorId, from, to), BigDecimal.class);
    }

    /** Total item line revenue for the window — share denominator for by-product/by-category rows. */
    public BigDecimal lineGrossTotal(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        String sql = "SELECT COALESCE(SUM(oi.line_total), 0) FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE " + orderFilter(vendorId);
        return jdbc.queryForObject(sql, params(tenantId, vendorId, from, to), BigDecimal.class);
    }

    /** Vendor ids of a tenant — used to resolve the forced scope of a VENDOR_ADMIN caller. */
    public List<UUID> findVendorIdsForTenant(UUID tenantId) {
        return jdbc.queryForList("SELECT id FROM vendors WHERE tenant_id = :tenantId ORDER BY created_at",
                Map.of("tenantId", tenantId), UUID.class);
    }

    private String timeseriesSql(UUID vendorId) {
        // Buckets are pinned to UTC boundaries (AT TIME ZONE 'UTC') so results do not depend
        // on the database session timezone.
        return "SELECT date_trunc(:interval, o.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket, " + SUMMARY_COLUMNS
                + " FROM orders o" + REFUND_JOIN + COMMISSION_JOIN
                + " WHERE " + orderFilter(vendorId)
                + " GROUP BY 1 ORDER BY 1";
    }

    private String byVendorSql(UUID vendorId) {
        return """
                SELECT o.vendor_id::text AS key,
                       COALESCE(MAX(v.name), o.vendor_id::text) AS label,
                       COALESCE(SUM(o.total), 0) AS gross,
                       COALESCE(SUM(rf.amount), 0) AS refunds,
                       COALESCE(SUM(cm.amount), 0) AS commission,
                       0 AS quantity_sold
                  FROM orders o
                  LEFT JOIN vendors v ON v.id = o.vendor_id"""
                + REFUND_JOIN + COMMISSION_JOIN
                + " WHERE " + orderFilter(vendorId)
                + " GROUP BY o.vendor_id ORDER BY gross DESC";
    }

    // Order-level refunds/commissions are apportioned to items pro-rata by line_total share,
    // so dimension nets stay meaningful without double counting.
    private String byProductSql(UUID vendorId) {
        return """
                SELECT s.product_id::text AS key,
                       COALESCE(MAX(p.name), MAX(oi.product_name_snapshot)) AS label,
                       COALESCE(SUM(oi.line_total), 0) AS gross,
                       COALESCE(SUM(COALESCE(rf.amount, 0) * oi.line_total / NULLIF(li.line_sum, 0)), 0) AS refunds,
                       COALESCE(SUM(COALESCE(cm.amount, 0) * oi.line_total / NULLIF(li.line_sum, 0)), 0) AS commission,
                       COALESCE(SUM(oi.quantity), 0) AS quantity_sold
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  JOIN skus s ON s.id = oi.sku_id
                  LEFT JOIN products p ON p.id = s.product_id"""
                + LINE_SUM_JOIN + REFUND_JOIN + COMMISSION_JOIN
                + " WHERE " + orderFilter(vendorId)
                + " GROUP BY s.product_id ORDER BY gross DESC";
    }

    private String byCategorySql(UUID vendorId) {
        return """
                SELECT COALESCE((COALESCE(oi.category_id_snapshot, p.category_id))::text, 'unknown') AS key,
                       COALESCE(MAX(c.name), 'unknown') AS label,
                       COALESCE(SUM(oi.line_total), 0) AS gross,
                       COALESCE(SUM(COALESCE(rf.amount, 0) * oi.line_total / NULLIF(li.line_sum, 0)), 0) AS refunds,
                       COALESCE(SUM(COALESCE(cm.amount, 0) * oi.line_total / NULLIF(li.line_sum, 0)), 0) AS commission,
                       COALESCE(SUM(oi.quantity), 0) AS quantity_sold
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                  LEFT JOIN skus s ON s.id = oi.sku_id
                  LEFT JOIN products p ON p.id = s.product_id
                  LEFT JOIN categories c ON c.id = COALESCE(oi.category_id_snapshot, p.category_id)"""
                + LINE_SUM_JOIN + REFUND_JOIN + COMMISSION_JOIN
                + " WHERE " + orderFilter(vendorId)
                + " GROUP BY COALESCE(oi.category_id_snapshot, p.category_id) ORDER BY gross DESC";
    }

    private String orderFilter(UUID vendorId) {
        String base = "o.tenant_id = :tenantId AND o.status IN (:confirmedStatuses)"
                + " AND o.created_at >= :fromTs AND o.created_at <= :toTs";
        return vendorId == null ? base : base + " AND o.vendor_id = :vendorId";
    }

    private Map<String, Object> params(UUID tenantId, UUID vendorId, Instant from, Instant to) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("confirmedStatuses", CONFIRMED_STATUSES);
        params.put("refundStatuses", COUNTED_REFUND_STATUSES);
        params.put("fromTs", Timestamp.from(from));
        params.put("toTs", Timestamp.from(to));
        if (vendorId != null) {
            params.put("vendorId", vendorId);
        }
        return params;
    }

    /** Raw summary aggregates as returned by the database (pre-rounding). */
    public record SummaryRow(long orderCount, BigDecimal gross, BigDecimal discountTotal,
                             BigDecimal refunds, BigDecimal commission,
                             long deliveredCount, long cancelledCount) {
    }

    /** Raw timeseries bucket aggregates as returned by the database. */
    public record BucketRow(Instant bucket, long orderCount, BigDecimal gross, BigDecimal discountTotal,
                            BigDecimal refunds, BigDecimal commission,
                            long deliveredCount, long cancelledCount) {
    }

    /** Raw per-dimension aggregates (quantitySold is 0 for non-product dimensions). */
    public record DimensionAggregateRow(String key, String label, BigDecimal gross,
                                        BigDecimal refunds, BigDecimal commission, long quantitySold) {
    }
}
