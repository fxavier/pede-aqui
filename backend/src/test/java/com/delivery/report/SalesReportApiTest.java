package com.delivery.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.report.controller.SalesReportController;
import com.delivery.report.dto.SalesSummaryResponse;
import com.delivery.report.service.ReportScope;
import com.delivery.report.service.SalesReportCsvExporter;
import com.delivery.report.service.SalesReportService;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Verifies report endpoint RBAC (spec §3) and the CSV export contract (AC-8.7). */
@WebMvcTest(SalesReportController.class)
@Import(SecurityConfig.class)
class SalesReportApiTest {

    private static final String WINDOW = "from=2026-06-01T00:00:00Z&to=2026-06-30T23:59:59Z";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SalesReportService service;

    @MockBean
    private SalesReportCsvExporter csvExporter;

    @MockBean
    private JwtDecoder jwtDecoder;

    private void stubSummary() {
        when(service.resolveScope(any())).thenReturn(new ReportScope(UUID.randomUUID(), null));
        when(service.summary(any(), any(), any())).thenReturn(new SalesSummaryResponse(
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-30T23:59:59Z"),
                3, new BigDecimal("400.00"), new BigDecimal("15.00"), new BigDecimal("130.00"),
                new BigDecimal("270.00"), new BigDecimal("40.00"), new BigDecimal("133.33"), 1, 1));
    }

    @Test
    void reportingRolesCanReadSummary() throws Exception {
        stubSummary();
        for (String role : new String[] {"ROLE_FINANCE", "ROLE_ADMIN", "ROLE_OPS", "ROLE_VENDOR_ADMIN"}) {
            mockMvc.perform(get("/api/v1/reports/sales/summary?" + WINDOW).with(jwt().authorities(() -> role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.net").value(270.00))
                    .andExpect(jsonPath("$.orderCount").value(3));
        }
    }

    @Test
    void nonReportingRolesAreForbidden() throws Exception {
        for (String role : new String[] {"ROLE_CUSTOMER", "ROLE_COURIER", "ROLE_SUPPORT"}) {
            mockMvc.perform(get("/api/v1/reports/sales/summary?" + WINDOW).with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/reports/sales/export?report=summary&" + WINDOW).with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidIntervalIsRejected() throws Exception {
        when(service.resolveScope(any())).thenReturn(new ReportScope(UUID.randomUUID(), null));
        mockMvc.perform(get("/api/v1/reports/sales/timeseries?" + WINDOW + "&interval=hour")
                        .with(jwt().authorities(() -> "ROLE_FINANCE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidReportAndFormatAreRejected() throws Exception {
        when(service.resolveScope(any())).thenReturn(new ReportScope(UUID.randomUUID(), null));
        mockMvc.perform(get("/api/v1/reports/sales/export?report=by-customer&" + WINDOW)
                        .with(jwt().authorities(() -> "ROLE_FINANCE")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reports/sales/export?report=summary&format=xlsx&" + WINDOW)
                        .with(jwt().authorities(() -> "ROLE_FINANCE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportStreamsCsvAttachment() throws Exception {
        when(service.resolveScope(any())).thenReturn(new ReportScope(UUID.randomUUID(), null));
        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(5);
            out.write("key,label,gross,refunds,net,commission,sharePercent\r\n".getBytes(StandardCharsets.UTF_8));
            out.write("v1,Vendor One,300.00,30.00,270.00,30.00,75.0\r\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(csvExporter).write(any(), any(), any(), any(), any(), any());

        MvcResult pending = mockMvc.perform(get("/api/v1/reports/sales/export?report=by-vendor&" + WINDOW)
                        .with(jwt().authorities(() -> "ROLE_FINANCE")))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("sales-by-vendor-2026-06-01-2026-06-30.csv");
        assertThat(result.getResponse().getContentAsString())
                .startsWith("key,label,gross,refunds,net,commission,sharePercent")
                .contains("Vendor One");
    }
}
