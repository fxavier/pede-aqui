package com.delivery.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.report.dto.DimensionRowResponse;
import com.delivery.report.dto.ProductDimensionRowResponse;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.mapper.SalesReportMapper;
import com.delivery.report.repository.SalesReportRepository;
import com.delivery.report.repository.SalesReportRepository.DimensionAggregateRow;
import com.delivery.report.repository.SalesReportRepository.SummaryRow;
import com.delivery.report.service.ReportScope;
import com.delivery.report.service.SalesReportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Verifies report KPI math (net/AOV/share, AC-8.3/8.5/8.8) and VENDOR_ADMIN scoping (AC-8.6). */
class SalesReportServiceTest {

    private final Instant from = Instant.parse("2026-06-01T00:00:00Z");
    private final Instant to = Instant.parse("2026-06-30T23:59:59Z");
    private final UUID tenantId = UUID.randomUUID();

    private SalesReportRepository repository;
    private TenantContext tenantContext;
    private SalesReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(SalesReportRepository.class);
        tenantContext = mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        service = new SalesReportService(repository, new SalesReportMapper(), tenantContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryComputesNetAndAverageOrderValueWithTwoDecimals() {
        when(repository.summary(tenantId, null, from, to)).thenReturn(
                new SummaryRow(3, new BigDecimal("400.00"), new BigDecimal("15.00"),
                        new BigDecimal("130.00"), new BigDecimal("40.00"), 1, 1));

        SalesSummaryResponse summary = service.summary(new ReportScope(tenantId, null), from, to);

        assertThat(summary.orderCount()).isEqualTo(3);
        assertThat(summary.gross()).isEqualByComparingTo("400.00");
        assertThat(summary.discountTotal()).isEqualByComparingTo("15.00");
        assertThat(summary.refunds()).isEqualByComparingTo("130.00");
        assertThat(summary.net()).isEqualByComparingTo("270.00");
        assertThat(summary.commission()).isEqualByComparingTo("40.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("133.33");
        assertThat(summary.deliveredCount()).isEqualTo(1);
        assertThat(summary.cancelledCount()).isEqualTo(1);
    }

    @Test
    void summaryClampsNegativeRefundsAndCommissionAndZeroOrderAov() {
        when(repository.summary(tenantId, null, from, to)).thenReturn(
                new SummaryRow(0, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("-5.00"), new BigDecimal("-1.00"), 0, 0));

        SalesSummaryResponse summary = service.summary(new ReportScope(tenantId, null), from, to);

        assertThat(summary.refunds()).isEqualByComparingTo("0.00");
        assertThat(summary.commission()).isEqualByComparingTo("0.00");
        assertThat(summary.averageOrderValue()).isEqualByComparingTo("0.00");
        assertThat(summary.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void dimensionRowsCarryShareOfGrossAndNet() {
        when(repository.byVendor(tenantId, null, from, to)).thenReturn(List.of(
                new DimensionAggregateRow("v1", "Vendor One", new BigDecimal("300.00"), new BigDecimal("30.00"), new BigDecimal("30.00"), 0),
                new DimensionAggregateRow("v2", "Vendor Two", new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("10.00"), 0)));

        List<DimensionRowResponse> rows = service.byVendor(new ReportScope(tenantId, null), from, to);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).sharePercent()).isEqualTo(75.0);
        assertThat(rows.get(0).net()).isEqualByComparingTo("270.00");
        assertThat(rows.get(1).sharePercent()).isEqualTo(25.0);
        assertThat(rows.get(1).net()).isEqualByComparingTo("0.00");
    }

    @Test
    void productRowsKeepSnapshotQuantities() {
        when(repository.byProduct(tenantId, null, from, to)).thenReturn(List.of(
                new DimensionAggregateRow("p1", "Arroz", new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO, 3)));

        List<ProductDimensionRowResponse> rows = service.byProduct(new ReportScope(tenantId, null), from, to);

        assertThat(rows.get(0).quantitySold()).isEqualTo(3);
        assertThat(rows.get(0).sharePercent()).isEqualTo(100.0);
    }

    @Test
    void rejectsInvertedWindow() {
        assertThatThrownBy(() -> service.summary(new ReportScope(tenantId, null), to, from))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("'from' must not be after 'to'");
    }

    @Test
    void vendorAdminIsForcedToOwnVendorIgnoringRequestedFilter() {
        UUID ownVendor = UUID.randomUUID();
        UUID otherVendor = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("vendor-user", "n/a", "ROLE_VENDOR_ADMIN"));
        when(repository.findVendorIdsForTenant(tenantId)).thenReturn(List.of(ownVendor));

        ReportScope scope = service.resolveScope(otherVendor);

        assertThat(scope.tenantId()).isEqualTo(tenantId);
        assertThat(scope.vendorId()).isEqualTo(ownVendor);
    }

    @Test
    void vendorAdminUsesVendorIdJwtClaimWhenPresent() {
        UUID claimedVendor = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("vendor_id", claimedVendor.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_VENDOR_ADMIN"))));

        ReportScope scope = service.resolveScope(null);

        assertThat(scope.vendorId()).isEqualTo(claimedVendor);
        verify(repository, never()).findVendorIdsForTenant(any());
    }

    @Test
    void vendorAdminWithAmbiguousVendorIsRefused() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("vendor-user", "n/a", "ROLE_VENDOR_ADMIN"));
        when(repository.findVendorIdsForTenant(tenantId)).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> service.resolveScope(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unable to resolve the vendor");
    }

    @Test
    void tenantWideRolesKeepTheRequestedVendorFilter() {
        UUID requestedVendor = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("finance-user", "n/a", "ROLE_FINANCE"));

        ReportScope scope = service.resolveScope(requestedVendor);

        assertThat(scope.vendorId()).isEqualTo(requestedVendor);
        verify(repository, never()).findVendorIdsForTenant(any());
    }

    @Test
    void adminWithVendorAdminRoleIsNotForced() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin-user", "n/a", "ROLE_VENDOR_ADMIN", "ROLE_ADMIN"));

        ReportScope scope = service.resolveScope(null);

        assertThat(scope.vendorId()).isNull();
    }

    @Test
    void missingTenantContextIsRefused() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveScope(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tenant context is required");
    }
}
