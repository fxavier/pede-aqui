package com.delivery.report.service;

import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.ReportInterval;
import com.delivery.report.dto.SalesBucketResponse;
import com.delivery.report.dto.SalesReportType;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.mapper.SalesReportMapper;
import com.delivery.report.repository.SalesReportRepository;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Streams sales reports as CSV row-by-row: rows come off a JDBC cursor (bounded fetch size)
 * and are written straight to the response stream, so exports never materialise a full
 * result set in memory (AC-8.7).
 */
@Service
public class SalesReportCsvExporter {

    private static final String[] SUMMARY_HEADER = {
            "from", "to", "orderCount", "gross", "discountTotal", "refunds", "net",
            "commission", "averageOrderValue", "deliveredCount", "cancelledCount"};
    private static final String[] TIMESERIES_HEADER = {
            "bucket", "orderCount", "gross", "discountTotal", "refunds", "net",
            "commission", "averageOrderValue", "deliveredCount", "cancelledCount"};
    private static final String[] DIMENSION_HEADER = {
            "key", "label", "gross", "refunds", "net", "commission", "sharePercent"};
    private static final String[] PRODUCT_HEADER = {
            "key", "label", "quantitySold", "gross", "refunds", "net", "commission", "sharePercent"};

    private final SalesReportRepository repository;
    private final SalesReportMapper mapper;
    private final SalesReportService service;

    public SalesReportCsvExporter(SalesReportRepository repository, SalesReportMapper mapper, SalesReportService service) {
        this.repository = repository;
        this.mapper = mapper;
        this.service = service;
    }

    /** Writes the requested report as CSV to the output stream, streaming rows as they arrive. */
    public void write(SalesReportType report, ReportScope scope, Instant from, Instant to,
                      ReportInterval interval, OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try {
            switch (report) {
                case SUMMARY -> writeSummary(scope, from, to, writer);
                case TIMESERIES -> writeTimeseries(scope, from, to, interval, writer);
                case BY_VENDOR -> writeByVendor(scope, from, to, writer);
                case BY_PRODUCT -> writeByProduct(scope, from, to, writer);
                case BY_CATEGORY -> writeByCategory(scope, from, to, writer);
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        writer.flush();
    }

    private void writeSummary(ReportScope scope, Instant from, Instant to, BufferedWriter writer) {
        writeRow(writer, (Object[]) SUMMARY_HEADER);
        SalesSummaryResponse s = service.summary(scope, from, to);
        writeRow(writer, s.from(), s.to(), s.orderCount(), s.gross(), s.discountTotal(), s.refunds(),
                s.net(), s.commission(), s.averageOrderValue(), s.deliveredCount(), s.cancelledCount());
    }

    private void writeTimeseries(ReportScope scope, Instant from, Instant to, ReportInterval interval, BufferedWriter writer) {
        writeRow(writer, (Object[]) TIMESERIES_HEADER);
        repository.streamTimeseries(scope.tenantId(), scope.vendorId(), from, to, interval, row -> {
            SalesBucketResponse b = mapper.toBucket(row);
            writeRow(writer, b.bucket(), b.orderCount(), b.gross(), b.discountTotal(), b.refunds(),
                    b.net(), b.commission(), b.averageOrderValue(), b.deliveredCount(), b.cancelledCount());
        });
    }

    private void writeByVendor(ReportScope scope, Instant from, Instant to, BufferedWriter writer) {
        writeRow(writer, (Object[]) DIMENSION_HEADER);
        BigDecimal totalGross = repository.orderGrossTotal(scope.tenantId(), scope.vendorId(), from, to);
        repository.streamByVendor(scope.tenantId(), scope.vendorId(), from, to, row -> {
            DimensionRowResponse d = mapper.toDimension(row, totalGross);
            writeRow(writer, d.key(), d.label(), d.gross(), d.refunds(), d.net(), d.commission(), d.sharePercent());
        });
    }

    private void writeByProduct(ReportScope scope, Instant from, Instant to, BufferedWriter writer) {
        writeRow(writer, (Object[]) PRODUCT_HEADER);
        BigDecimal totalGross = repository.lineGrossTotal(scope.tenantId(), scope.vendorId(), from, to);
        repository.streamByProduct(scope.tenantId(), scope.vendorId(), from, to, row -> {
            ProductDimensionRowResponse d = mapper.toProductDimension(row, totalGross);
            writeRow(writer, d.key(), d.label(), d.quantitySold(), d.gross(), d.refunds(), d.net(), d.commission(), d.sharePercent());
        });
    }

    private void writeByCategory(ReportScope scope, Instant from, Instant to, BufferedWriter writer) {
        writeRow(writer, (Object[]) DIMENSION_HEADER);
        BigDecimal totalGross = repository.lineGrossTotal(scope.tenantId(), scope.vendorId(), from, to);
        repository.streamByCategory(scope.tenantId(), scope.vendorId(), from, to, row -> {
            DimensionRowResponse d = mapper.toDimension(row, totalGross);
            writeRow(writer, d.key(), d.label(), d.gross(), d.refunds(), d.net(), d.commission(), d.sharePercent());
        });
    }

    private void writeRow(BufferedWriter writer, Object... values) {
        try {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    line.append(',');
                }
                line.append(escape(values[i]));
            }
            writer.write(line.toString());
            writer.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
