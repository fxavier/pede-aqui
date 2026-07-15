package com.delivery.report.mapper;

import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.SalesBucketResponse;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.repository.SalesReportRepository.BucketRow;
import com.delivery.report.repository.SalesReportRepository.DimensionAggregateRow;
import com.delivery.report.repository.SalesReportRepository.SummaryRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Converts raw report aggregates into response DTOs, applying the money rules of AC-8.8:
 * MZN with 2 decimals, non-negative refunds/commission, net = gross − refunds,
 * AOV = gross / orderCount.
 */
@Component
public class SalesReportMapper {

    public SalesSummaryResponse toSummary(Instant from, Instant to, SummaryRow row) {
        BigDecimal gross = money(row.gross());
        BigDecimal refunds = nonNegative(money(row.refunds()));
        return new SalesSummaryResponse(
                from,
                to,
                row.orderCount(),
                gross,
                money(row.discountTotal()),
                refunds,
                gross.subtract(refunds),
                nonNegative(money(row.commission())),
                averageOrderValue(gross, row.orderCount()),
                row.deliveredCount(),
                row.cancelledCount());
    }

    public SalesBucketResponse toBucket(BucketRow row) {
        BigDecimal gross = money(row.gross());
        BigDecimal refunds = nonNegative(money(row.refunds()));
        return new SalesBucketResponse(
                row.bucket(),
                row.orderCount(),
                gross,
                money(row.discountTotal()),
                refunds,
                gross.subtract(refunds),
                nonNegative(money(row.commission())),
                averageOrderValue(gross, row.orderCount()),
                row.deliveredCount(),
                row.cancelledCount());
    }

    public DimensionRowResponse toDimension(DimensionAggregateRow row, BigDecimal totalGross) {
        BigDecimal gross = money(row.gross());
        BigDecimal refunds = nonNegative(money(row.refunds()));
        return new DimensionRowResponse(
                row.key(),
                row.label(),
                gross,
                refunds,
                gross.subtract(refunds),
                nonNegative(money(row.commission())),
                sharePercent(gross, totalGross));
    }

    public ProductDimensionRowResponse toProductDimension(DimensionAggregateRow row, BigDecimal totalGross) {
        BigDecimal gross = money(row.gross());
        BigDecimal refunds = nonNegative(money(row.refunds()));
        return new ProductDimensionRowResponse(
                row.key(),
                row.label(),
                gross,
                refunds,
                gross.subtract(refunds),
                nonNegative(money(row.commission())),
                sharePercent(gross, totalGross),
                row.quantitySold());
    }

    /** Normalises a database amount to MZN scale 2 (null-safe). */
    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value.max(BigDecimal.ZERO.setScale(2));
    }

    private static BigDecimal averageOrderValue(BigDecimal gross, long orderCount) {
        if (orderCount == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return gross.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
    }

    private static double sharePercent(BigDecimal gross, BigDecimal totalGross) {
        if (totalGross == null || totalGross.signum() == 0) {
            return 0.0;
        }
        return gross.multiply(BigDecimal.valueOf(100)).divide(totalGross, 2, RoundingMode.HALF_UP).doubleValue();
    }
}
