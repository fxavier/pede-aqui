package com.delivery.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.payment.controller.PaymentController;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class RefundApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void financeCanApproveRejectAndDuplicateIdempotencyReturnsSameRefund() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        RefundResponse requested = new RefundResponse(refundId, paymentId, UUID.randomUUID(), new BigDecimal("20.00"), "partial", RefundStatus.REQUESTED);
        RefundResponse approved = new RefundResponse(refundId, paymentId, requested.orderId(), new BigDecimal("20.00"), "partial", RefundStatus.REFUNDED);
        RefundResponse rejected = new RefundResponse(UUID.randomUUID(), paymentId, requested.orderId(), new BigDecimal("10.00"), "invalid", RefundStatus.REJECTED);

        when(paymentService.requestRefund(any(), any())).thenReturn(requested);
        when(paymentService.approveRefund(refundId)).thenReturn(approved);
        when(paymentService.rejectRefund(rejected.id())).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refunds", paymentId)
                        .with(jwt().authorities(() -> "ROLE_FINANCE"))
                        .contentType("application/json")
                        .content("{\"amount\":20.0,\"reason\":\"partial\",\"idempotencyKey\":\"refund-idem-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refunds", paymentId)
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"amount\":20.0,\"reason\":\"partial\",\"idempotencyKey\":\"refund-idem-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(refundId.toString()));

        mockMvc.perform(post("/api/v1/payments/refunds/{refundId}/approve", refundId)
                        .with(jwt().authorities(() -> "ROLE_FINANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(post("/api/v1/payments/refunds/{refundId}/reject", rejected.id())
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
