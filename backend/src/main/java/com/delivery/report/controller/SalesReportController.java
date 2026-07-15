package com.delivery.report.controller;

import com.delivery.common.exception.BusinessException;
import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.ReportInterval;
import com.delivery.report.dto.SalesBucketResponse;
import com.delivery.report.dto.SalesReportType;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.service.ReportScope;
import com.delivery.report.service.SalesReportCsvExporter;
import com.delivery.report.service.SalesReportService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Exposes tenant-scoped sales reports (summary, timeseries, dimensions, CSV export) for
 * FINANCE/ADMIN/OPS and vendor-scoped VENDOR_ADMIN callers (spec §3, US-8).
 */
@RestController
@RequestMapping("/api/v1/reports/sales")
public class SalesReportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final SalesReportService service;
    private final SalesReportCsvExporter csvExporter;

    public SalesReportController(SalesReportService service, SalesReportCsvExporter csvExporter) {
        this.service = service;
        this.csvExporter = csvExporter;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public SalesSummaryResponse summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId) {
        return service.summary(service.resolveScope(vendorId), from, to);
    }

    @GetMapping("/timeseries")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public List<SalesBucketResponse> timeseries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "day") String interval) {
        return service.timeseries(service.resolveScope(vendorId), from, to, ReportInterval.fromParam(interval));
    }

    @GetMapping("/by-vendor")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public List<DimensionRowResponse> byVendor(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId) {
        return service.byVendor(service.resolveScope(vendorId), from, to);
    }

    @GetMapping("/by-product")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public List<ProductDimensionRowResponse> byProduct(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId) {
        return service.byProduct(service.resolveScope(vendorId), from, to);
    }

    @GetMapping("/by-category")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public List<DimensionRowResponse> byCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId) {
        return service.byCategory(service.resolveScope(vendorId), from, to);
    }

    /** Streams any report as a CSV attachment; scope and parameters are resolved before streaming starts. */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN','OPS','VENDOR_ADMIN')")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam String report,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "day") String interval) {
        if (!"csv".equalsIgnoreCase(format)) {
            throw new BusinessException("unsupported_format", "Only format=csv is supported", HttpStatus.BAD_REQUEST);
        }
        SalesReportType reportType = SalesReportType.fromParam(report);
        ReportInterval reportInterval = ReportInterval.fromParam(interval);
        service.validateWindow(from, to);
        // Security/tenant context is request-bound; resolve everything here so the async
        // streaming thread only executes pre-scoped queries.
        ReportScope scope = service.resolveScope(vendorId);
        String filename = "sales-" + reportType.param() + "-" + datePart(from) + "-" + datePart(to) + ".csv";
        StreamingResponseBody body = out -> csvExporter.write(reportType, scope, from, to, reportInterval, out);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static String datePart(Instant instant) {
        return instant.toString().substring(0, 10);
    }
}
