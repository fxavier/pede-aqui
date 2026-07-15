package com.delivery.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.ReportInterval;
import com.delivery.report.dto.SalesBucketResponse;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.service.ReportScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * E5 — aggregation math against a seeded Postgres dataset with discounts, refunds and
 * commissions: summary KPIs, timeseries bucketing, snapshot-based product figures,
 * vendor scoping and the by-category snapshot + live-join fallback.
 */
class SalesReportAggregationTest extends AbstractSalesReportPostgresTest {

    @Test
    void summaryAggregatesPaymentConfirmedOrdersOnly() {
        SalesSummaryResponse summary = service.summary(new ReportScope(tenant1, null), FROM, TO);

        // O1 + O2 + O3; O4 (PAYMENT_PENDING) and O5 (other tenant) excluded.
        assertThat(summary.orderCount()).isEqualTo(3);
        assertThat(summary.gross()).isEqualByComparingTo("400.00");
        assertThat(summary.discountTotal()).isEqualByComparingTo("15.00");
        // Counted refunds: 30 APPROVED (O1) + 100 REFUNDED (O3); REJECTED/REQUESTED ignored.
        assertThat(summary.refunds()).isEqualByComparingTo("130.00");
        assertThat(summary.net()).isEqualByComparingTo("270.00");
        assertThat(summary.commission()).isEqualByComparingTo("40.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("133.33");
        assertThat(summary.deliveredCount()).isEqualTo(1);
        assertThat(summary.cancelledCount()).isEqualTo(1);
    }

    @Test
    void summaryIsTenantIsolated() {
        SalesSummaryResponse summary = service.summary(new ReportScope(tenant2, null), FROM, TO);

        assertThat(summary.orderCount()).isEqualTo(1);
        assertThat(summary.gross()).isEqualByComparingTo("500.00");
        assertThat(summary.refunds()).isEqualByComparingTo("0.00");
    }

    @Test
    void vendorFilterScopesEveryFigure() {
        SalesSummaryResponse summary = service.summary(new ReportScope(tenant1, vendor1), FROM, TO);

        assertThat(summary.orderCount()).isEqualTo(2);
        assertThat(summary.gross()).isEqualByComparingTo("300.00");
        assertThat(summary.refunds()).isEqualByComparingTo("30.00");
        assertThat(summary.commission()).isEqualByComparingTo("30.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("150.00");
        assertThat(summary.cancelledCount()).isZero();
    }

    @Test
    void timeseriesBucketsByDay() {
        List<SalesBucketResponse> buckets = service.timeseries(new ReportScope(tenant1, null), FROM, TO, ReportInterval.DAY);

        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0).bucket()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(buckets.get(0).orderCount()).isEqualTo(1);
        assertThat(buckets.get(0).gross()).isEqualByComparingTo("200.00");
        assertThat(buckets.get(0).refunds()).isEqualByComparingTo("30.00");
        assertThat(buckets.get(0).net()).isEqualByComparingTo("170.00");
        assertThat(buckets.get(1).bucket()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(buckets.get(1).orderCount()).isEqualTo(2);
        assertThat(buckets.get(1).gross()).isEqualByComparingTo("200.00");
        assertThat(buckets.get(1).refunds()).isEqualByComparingTo("100.00");
    }

    @Test
    void timeseriesBucketsByMonthCollapseTheWindow() {
        List<SalesBucketResponse> buckets = service.timeseries(new ReportScope(tenant1, null), FROM, TO, ReportInterval.MONTH);

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).bucket()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(buckets.get(0).gross()).isEqualByComparingTo("400.00");
        assertThat(buckets.get(0).averageOrderValue()).isEqualByComparingTo("133.33");
    }

    @Test
    void byVendorReturnsSharesAndExactRefundAttribution() {
        List<DimensionRowResponse> rows = service.byVendor(new ReportScope(tenant1, null), FROM, TO);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).key()).isEqualTo(vendor1.toString());
        assertThat(rows.get(0).label()).isEqualTo("Vendor One");
        assertThat(rows.get(0).gross()).isEqualByComparingTo("300.00");
        assertThat(rows.get(0).refunds()).isEqualByComparingTo("30.00");
        assertThat(rows.get(0).net()).isEqualByComparingTo("270.00");
        assertThat(rows.get(0).commission()).isEqualByComparingTo("30.00");
        assertThat(rows.get(0).sharePercent()).isEqualTo(75.0);
        assertThat(rows.get(1).key()).isEqualTo(vendor2.toString());
        assertThat(rows.get(1).gross()).isEqualByComparingTo("100.00");
        assertThat(rows.get(1).net()).isEqualByComparingTo("0.00");
        assertThat(rows.get(1).sharePercent()).isEqualTo(25.0);
    }

    @Test
    void byVendorWithForcedVendorScopeReturnsSingleRow() {
        List<DimensionRowResponse> rows = service.byVendor(new ReportScope(tenant1, vendor1), FROM, TO);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).key()).isEqualTo(vendor1.toString());
        assertThat(rows.get(0).sharePercent()).isEqualTo(100.0);
    }

    @Test
    void byProductReadsQuantitiesAndRevenueFromSnapshotsNotLivePrices() {
        List<ProductDimensionRowResponse> before = service.byProduct(new ReportScope(tenant1, null), FROM, TO);

        assertThat(before).hasSize(2);
        assertThat(before.get(0).key()).isEqualTo(product1.toString());
        assertThat(before.get(0).label()).isEqualTo("Arroz 1kg");
        assertThat(before.get(0).quantitySold()).isEqualTo(3);
        assertThat(before.get(0).gross()).isEqualByComparingTo("300.00");
        assertThat(before.get(0).refunds()).isEqualByComparingTo("30.00");
        assertThat(before.get(0).sharePercent()).isEqualTo(75.0);
        assertThat(before.get(1).key()).isEqualTo(product2.toString());
        assertThat(before.get(1).quantitySold()).isEqualTo(2);
        assertThat(before.get(1).gross()).isEqualByComparingTo("100.00");
        assertThat(before.get(1).refunds()).isEqualByComparingTo("100.00");

        // AC-8.1: changing the live SKU price after the sale must not move report figures.
        jdbcTemplate.update("UPDATE skus SET price = 999.99 WHERE id = ?", sku1);

        List<ProductDimensionRowResponse> after = service.byProduct(new ReportScope(tenant1, null), FROM, TO);
        assertThat(after).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void byCategoryUsesSnapshotAndFallsBackToLiveJoinForHistoricalRows() {
        List<DimensionRowResponse> rows = service.byCategory(new ReportScope(tenant1, null), FROM, TO);

        // O1's item is snapshot-pinned to category1; O2's pre-snapshot row falls back to the
        // live product→category join (also category1); O3's item is snapshotted to category2.
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).key()).isEqualTo(category1.toString());
        assertThat(rows.get(0).label()).isEqualTo("Mercearia");
        assertThat(rows.get(0).gross()).isEqualByComparingTo("300.00");
        assertThat(rows.get(1).key()).isEqualTo(category2.toString());
        assertThat(rows.get(1).gross()).isEqualByComparingTo("100.00");

        // Recategorising product1 moves only the fallback row (O2); the snapshot row (O1) stays.
        jdbcTemplate.update("UPDATE products SET category_id = ? WHERE id = ?", category2, product1);

        List<DimensionRowResponse> after = service.byCategory(new ReportScope(tenant1, null), FROM, TO);
        assertThat(after).hasSize(2);
        DimensionRowResponse snapshotPinned = after.stream().filter(r -> r.key().equals(category1.toString())).findFirst().orElseThrow();
        DimensionRowResponse moved = after.stream().filter(r -> r.key().equals(category2.toString())).findFirst().orElseThrow();
        assertThat(snapshotPinned.gross()).isEqualByComparingTo("200.00");
        assertThat(moved.gross()).isEqualByComparingTo("200.00");
    }
}
