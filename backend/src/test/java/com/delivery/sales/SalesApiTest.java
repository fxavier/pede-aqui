package com.delivery.sales;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.SecurityConfig;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.sales.controller.SalesController;
import com.delivery.sales.dto.SalesActionResponse;
import com.delivery.sales.dto.SalesNotificationType;
import com.delivery.sales.dto.SalesPageResponse;
import com.delivery.sales.service.SalesService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SalesController.class)
@Import(SecurityConfig.class)
class SalesApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SalesService salesService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final UUID orderId = UUID.randomUUID();

    // --- C8: role matrix ---

    @Test
    void salesListIsReadableByEveryBackofficeRoleButNotCustomersOrCouriers() throws Exception {
        when(salesService.search(any())).thenReturn(new SalesPageResponse(List.of(), 0, 20, 0));
        for (String role : List.of("ROLE_ADMIN", "ROLE_OPS", "ROLE_FINANCE", "ROLE_VENDOR_ADMIN", "ROLE_SUPPORT")) {
            mockMvc.perform(get("/api/v1/sales/orders").with(jwt().authorities(() -> role)))
                    .andExpect(status().isOk());
        }
        for (String role : List.of("ROLE_CUSTOMER", "ROLE_COURIER")) {
            mockMvc.perform(get("/api/v1/sales/orders").with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void cancelAllowsOpsAdminSupportVendorAdminOnly() throws Exception {
        when(salesService.cancel(eq(orderId), any())).thenReturn(new SalesActionResponse(orderId, "PA-1", "CANCELLED"));
        for (String role : List.of("ROLE_ADMIN", "ROLE_OPS", "ROLE_SUPPORT", "ROLE_VENDOR_ADMIN")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/cancel", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"reason\":\"customer asked\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        }
        for (String role : List.of("ROLE_FINANCE", "ROLE_CUSTOMER")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/cancel", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"reason\":\"customer asked\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void refundAllowsOpsAdminFinanceOnly() throws Exception {
        when(salesService.refund(eq(orderId), any(), any(), any()))
                .thenReturn(new RefundResponse(UUID.randomUUID(), UUID.randomUUID(), orderId, new BigDecimal("20.00"), "partial", RefundStatus.REQUESTED));
        for (String role : List.of("ROLE_ADMIN", "ROLE_OPS", "ROLE_FINANCE")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/refund", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"amount\":20.0,\"reason\":\"partial\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REQUESTED"));
        }
        for (String role : List.of("ROLE_SUPPORT", "ROLE_VENDOR_ADMIN", "ROLE_CUSTOMER")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/refund", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"amount\":20.0,\"reason\":\"partial\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void statusOverrideAllowsAdminOpsOnly() throws Exception {
        when(salesService.statusOverride(eq(orderId), any(), any()))
                .thenReturn(new SalesActionResponse(orderId, "PA-1", "PREPARING"));
        for (String role : List.of("ROLE_ADMIN", "ROLE_OPS")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/status-override", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"targetStatus\":\"PREPARING\",\"reason\":\"unstick\"}"))
                    .andExpect(status().isOk());
        }
        for (String role : List.of("ROLE_FINANCE", "ROLE_SUPPORT", "ROLE_VENDOR_ADMIN")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/status-override", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"targetStatus\":\"PREPARING\",\"reason\":\"unstick\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // --- C8: 409/422/403 business paths surface with the right status codes ---

    @Test
    void cancelInNonCancellableStateReturns409() throws Exception {
        when(salesService.cancel(eq(orderId), any()))
                .thenThrow(new BusinessException("sale_not_cancellable", "too late", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/cancel", orderId)
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"reason\":\"too late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refundAboveCapReturns422AndForwardsIdempotencyKey() throws Exception {
        when(salesService.refund(eq(orderId), any(), any(), eq("retry-1")))
                .thenThrow(new BusinessException("invalid_refund_amount", "cap exceeded", HttpStatus.UNPROCESSABLE_ENTITY));

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/refund", orderId)
                        .with(jwt().authorities(() -> "ROLE_FINANCE"))
                        .header("Idempotency-Key", "retry-1")
                        .contentType("application/json")
                        .content("{\"amount\":999.0,\"reason\":\"too much\"}"))
                .andExpect(status().isUnprocessableEntity());

        verify(salesService).refund(eq(orderId), eq(new BigDecimal("999.0")), eq("too much"), eq("retry-1"));
    }

    @Test
    void statusOverrideWhenGateOffReturns403() throws Exception {
        when(salesService.statusOverride(eq(orderId), any(), any()))
                .thenThrow(new BusinessException("status_override_disabled", "Status override is disabled", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/status-override", orderId)
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"CANCELLED\",\"reason\":\"stuck\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusOverrideOutsideAllowListReturns409() throws Exception {
        when(salesService.statusOverride(eq(orderId), any(), any()))
                .thenThrow(new BusinessException("transition_not_allowed", "not allowed", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/status-override", orderId)
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"DELIVERED\",\"reason\":\"shortcut\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void statusOverrideWithoutReasonIs400() throws Exception {
        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/status-override", orderId)
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resendNotificationReturns202WithAnEmptyBodyAndNeverTheOtp() throws Exception {
        mockMvc.perform(post("/api/v1/sales/orders/{orderId}/resend-notification", orderId)
                        .with(jwt().authorities(() -> "ROLE_SUPPORT"))
                        .contentType("application/json")
                        .content("{\"type\":\"DELIVERY_CODE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(salesService).resendNotification(orderId, SalesNotificationType.DELIVERY_CODE);
    }

    @Test
    void resendNotificationRejectsCouriersAndCustomers() throws Exception {
        for (String role : List.of("ROLE_COURIER", "ROLE_CUSTOMER", "ROLE_FINANCE")) {
            mockMvc.perform(post("/api/v1/sales/orders/{orderId}/resend-notification", orderId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"type\":\"CONFIRMATION\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
