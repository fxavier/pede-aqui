package com.delivery.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.report.dto.ReportInterval;
import com.delivery.report.dto.SalesReportType;
import com.delivery.report.service.ReportScope;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E6 — CSV export shape (per-report headers/rows) and row-by-row streaming smoke. */
class SalesReportCsvExportTest extends AbstractSalesReportPostgresTest {

    @Test
    void exportsSummaryCsvWithHeaderAndSingleRow() throws IOException {
        String csv = export(SalesReportType.SUMMARY, new ReportScope(tenant1, null), FROM, TO);

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo("from,to,orderCount,gross,discountTotal,refunds,net,commission,averageOrderValue,deliveredCount,cancelledCount");
        assertThat(lines[1]).contains("3,400.00,15.00,130.00,270.00,40.00,133.33,1,1");
    }

    @Test
    void exportsTimeseriesCsv() throws IOException {
        String csv = export(SalesReportType.TIMESERIES, new ReportScope(tenant1, null), FROM, TO);

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("bucket,orderCount,gross,discountTotal,refunds,net,commission,averageOrderValue,deliveredCount,cancelledCount");
        assertThat(lines[1]).startsWith("2026-06-01T00:00:00Z,1,200.00");
        assertThat(lines[2]).startsWith("2026-06-02T00:00:00Z,2,200.00");
    }

    @Test
    void exportsByProductCsvFromSnapshots() throws IOException {
        String csv = export(SalesReportType.BY_PRODUCT, new ReportScope(tenant1, null), FROM, TO);

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("key,label,quantitySold,gross,refunds,net,commission,sharePercent");
        assertThat(lines[1]).isEqualTo(product1 + ",Arroz 1kg,3,300.00,30.00,270.00,30.00,75.0");
        assertThat(lines[2]).startsWith(product2 + ",Sumo 1L,2,100.00,100.00,0.00");
    }

    @Test
    void exportsByVendorAndByCategoryCsvWithDimensionHeader() throws IOException {
        String vendorCsv = export(SalesReportType.BY_VENDOR, new ReportScope(tenant1, null), FROM, TO);
        String categoryCsv = export(SalesReportType.BY_CATEGORY, new ReportScope(tenant1, null), FROM, TO);

        assertThat(vendorCsv).startsWith("key,label,gross,refunds,net,commission,sharePercent\r\n");
        assertThat(vendorCsv).contains(vendor1 + ",Vendor One,300.00,30.00,270.00,30.00,75.0");
        assertThat(categoryCsv).startsWith("key,label,gross,refunds,net,commission,sharePercent\r\n");
        assertThat(categoryCsv).contains(category1 + ",Mercearia,300.00");
    }

    @Test
    void escapesLabelsContainingDelimiters() throws IOException {
        jdbcTemplate.update("UPDATE vendors SET name = ? WHERE id = ?", "Vendor \"One\", Lda", vendor1);

        String csv = export(SalesReportType.BY_VENDOR, new ReportScope(tenant1, null), FROM, TO);

        assertThat(csv).contains("\"Vendor \"\"One\"\", Lda\"");
    }

    @Test
    void streamsLargeTimeseriesRowByRow() throws IOException {
        // 250 confirmed orders on 250 distinct days for a fresh tenant → 250 streamed buckets.
        UUID tenant = UUID.randomUUID();
        UUID vendor = UUID.randomUUID();
        insertTenant(tenant, "Bulk Tenant");
        insertVendor(vendor, tenant, "Bulk Vendor");
        Instant start = Instant.parse("2025-01-01T12:00:00Z");
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            UUID orderId = UUID.randomUUID();
            Timestamp createdAt = Timestamp.from(start.plus(i, ChronoUnit.DAYS));
            batch.add(new Object[] {orderId, tenant, "ref-" + orderId, UUID.randomUUID(), vendor,
                    "PAYMENT_CONFIRMED", "key-" + orderId, "hash-" + orderId, createdAt, createdAt});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status,
                                    subtotal, fees, taxes, discounts, discount_total, total,
                                    checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_confirmation_code_display, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 10.00, 0, 0, 0, 0, 10.00, ?, ?, '123456', ?, ?)""", batch);

        String csv = export(SalesReportType.TIMESERIES, new ReportScope(tenant, null),
                start.minus(1, ChronoUnit.DAYS), start.plus(300, ChronoUnit.DAYS));

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(251);
        assertThat(lines[1]).startsWith("2025-01-01T00:00:00Z,1,10.00");
        assertThat(lines[250]).contains(",1,10.00");
    }

    private String export(SalesReportType type, ReportScope scope, Instant from, Instant to) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(type, scope, from, to, ReportInterval.DAY, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
