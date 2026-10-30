package com.delivery.order;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.CursorPage;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.security.SecurityConfig;
import com.delivery.order.controller.CustomerOrderController;
import com.delivery.order.dto.CustomerCancellationResponse;
import com.delivery.order.dto.CustomerCancellationResponse.Outcome;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.service.CustomerCancellationService;
import com.delivery.order.service.CustomerOrderQueryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/** LAC-21: /api/v1/me/orders is CUSTOMER-only, authenticated, and forwards the cursor parameters unchanged. */
@WebMvcTest(CustomerOrderController.class)
@Import(SecurityConfig.class)
class CustomerOrderApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerOrderQueryService service;

    @MockBean
    private CustomerCancellationService cancellationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void backofficeRolesAreForbidden() throws Exception {
        for (String role : List.of("ADMIN", "OPS", "VENDOR_ADMIN", "VENDOR_STAFF", "COURIER", "FINANCE", "SUPPORT")) {
            mockMvc.perform(get("/api/v1/me/orders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/me/orders/" + UUID.randomUUID()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void customerListDefaultsToFiftyAndForwardsTheCursor() throws Exception {
        when(service.listMine(PageLimits.DEFAULT_CURSOR_LIMIT, null)).thenReturn(new CursorPage<>(List.of(), null));
        when(service.listMine(10, "abc")).thenReturn(new CursorPage<>(List.of(), null));

        mockMvc.perform(get("/api/v1/me/orders").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/me/orders").param("limit", "10").param("cursor", "abc")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());

        verify(service).listMine(PageLimits.DEFAULT_CURSOR_LIMIT, null);
        verify(service).listMine(10, "abc");
    }

    @Test
    void foreignOrderIsA404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(service.getMine(orderId)).thenThrow(new NotFoundException("Order was not found"));

        mockMvc.perform(get("/api/v1/me/orders/" + orderId).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }

    // --- LAC-26: POST /me/orders/{id}/cancel ---------------------------------------------------------

    @Test
    void cancelIsCustomerOnly() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel").with(csrf())).andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "OPS", "VENDOR_ADMIN", "COURIER", "FINANCE", "SUPPORT")) {
            mockMvc.perform(post("/api/v1/me/orders/" + orderId + "/cancel")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(cancellationService);
    }

    @Test
    void cancelAnswers200WhenCancelledAnd202WhenRequested() throws Exception {
        UUID cancelled = UUID.randomUUID();
        UUID requested = UUID.randomUUID();
        when(cancellationService.cancelMine(cancelled, null)).thenReturn(new CustomerCancellationResponse(cancelled,
                OrderStatus.CANCELLED, CancellationReason.CUSTOMER_CANCELLED, Outcome.CANCELLED, new BigDecimal("100.00"), null));
        when(cancellationService.cancelMine(requested, "mudei de ideias")).thenReturn(new CustomerCancellationResponse(requested,
                OrderStatus.PREPARING, null, Outcome.REQUESTED, null, null));

        mockMvc.perform(post("/api/v1/me/orders/" + cancelled + "/cancel").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CANCELLED"))
                .andExpect(jsonPath("$.refundAmount").value(100.00));
        mockMvc.perform(post("/api/v1/me/orders/" + requested + "/cancel").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"mudei de ideias\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("REQUESTED"));
    }

    @Test
    void cancelReasonIsBounded() throws Exception {
        mockMvc.perform(post("/api/v1/me/orders/" + UUID.randomUUID() + "/cancel").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        verifyNoInteractions(cancellationService);
    }
}
