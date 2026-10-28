package com.delivery.report.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.ReportInterval;
import com.delivery.report.dto.SalesBucketResponse;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.mapper.SalesReportMapper;
import com.delivery.report.repository.SalesReportRepository;
import com.delivery.report.repository.SalesReportRepository.DimensionAggregateRow;
import com.delivery.vendor.api.MerchantScopeGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Assembles sales report DTOs from immutable order aggregates and enforces report scoping:
 * tenant isolation always, plus a forced own-vendor filter for VENDOR_ADMIN callers (AC-8.6), resolved by the
 * shared {@link MerchantScopeGuard} (ADR-0001 step 3).
 */
@Service
public class SalesReportService {

    private final SalesReportRepository repository;
    private final SalesReportMapper mapper;
    private final TenantContext tenantContext;
    private final MerchantScopeGuard merchantScopeGuard;

    public SalesReportService(SalesReportRepository repository, SalesReportMapper mapper, TenantContext tenantContext,
                              MerchantScopeGuard merchantScopeGuard) {
        this.repository = repository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.merchantScopeGuard = merchantScopeGuard;
    }

    /**
     * Resolves the tenant and effective vendor filter for the current caller. VENDOR_ADMINs
     * (without a wider role) are always forced to their own vendor, ignoring any requested
     * vendorId; other roles keep the optional filter.
     */
    public ReportScope resolveScope(UUID requestedVendorId) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "A tenant context is required for sales reports", HttpStatus.FORBIDDEN));
        // VENDOR_ADMIN is only forced to its vendor when no tenant-wide reporting role is present.
        if (merchantScopeGuard.isVendorAdminScoped()) {
            return new ReportScope(tenantId, merchantScopeGuard.requireOwnVendorId(tenantId));
        }
        return new ReportScope(tenantId, requestedVendorId);
    }

    /** Rejects inverted reporting windows before any query runs. */
    public void validateWindow(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new BusinessException("invalid_report_window", "'from' must not be after 'to'", HttpStatus.BAD_REQUEST);
        }
    }

    /** Returns window KPI totals (AC-8.3). */
    public SalesSummaryResponse summary(ReportScope scope, Instant from, Instant to) {
        validateWindow(from, to);
        return mapper.toSummary(from, to, repository.summary(scope.tenantId(), scope.vendorId(), from, to));
    }

    /** Returns the summary KPIs bucketed by day/week/month via date_trunc (AC-8.4). */
    public List<SalesBucketResponse> timeseries(ReportScope scope, Instant from, Instant to, ReportInterval interval) {
        validateWindow(from, to);
        return repository.timeseries(scope.tenantId(), scope.vendorId(), from, to, interval).stream()
                .map(mapper::toBucket)
                .toList();
    }

    /** Returns per-vendor rows with share of gross (AC-8.5); a single row for VENDOR_ADMIN. */
    public List<DimensionRowResponse> byVendor(ReportScope scope, Instant from, Instant to) {
        validateWindow(from, to);
        List<DimensionAggregateRow> rows = repository.byVendor(scope.tenantId(), scope.vendorId(), from, to);
        BigDecimal totalGross = totalGross(rows);
        return rows.stream().map(row -> mapper.toDimension(row, totalGross)).toList();
    }

    /** Returns per-product rows with snapshot quantities and line revenue (AC-8.5, AC-8.1). */
    public List<ProductDimensionRowResponse> byProduct(ReportScope scope, Instant from, Instant to) {
        validateWindow(from, to);
        List<DimensionAggregateRow> rows = repository.byProduct(scope.tenantId(), scope.vendorId(), from, to);
        BigDecimal totalGross = totalGross(rows);
        return rows.stream().map(row -> mapper.toProductDimension(row, totalGross)).toList();
    }

    /** Returns per-category rows using the order-time snapshot with live-join fallback (AC-8.5). */
    public List<DimensionRowResponse> byCategory(ReportScope scope, Instant from, Instant to) {
        validateWindow(from, to);
        List<DimensionAggregateRow> rows = repository.byCategory(scope.tenantId(), scope.vendorId(), from, to);
        BigDecimal totalGross = totalGross(rows);
        return rows.stream().map(row -> mapper.toDimension(row, totalGross)).toList();
    }

    private static BigDecimal totalGross(List<DimensionAggregateRow> rows) {
        return rows.stream()
                .map(row -> SalesReportMapper.money(row.gross()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
