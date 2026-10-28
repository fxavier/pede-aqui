package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.security.SecurityConfig;
import com.delivery.common.security.TenancyProperties;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.controller.PaymentController;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP-level tests for POST /payments/{id}/confirm with the real service and security config (INC-01, AS-01). */
class PaymentConfirmationApiTest {
    static final String TENANT = "0b5f6a43-2d0c-4c6e-8f1a-3c2b1a0f9e8d";
    static final UUID TENANT_ID = UUID.fromString(TENANT);
    static final UUID ORDER_ID = UUID.randomUUID();
    static final UUID OWNER_PROFILE_ID = UUID.randomUUID();
    static final String KEY = "checkout-key-1";

    @WebMvcTest(PaymentController.class)
    @Import({SecurityConfig.class, PaymentService.class, PaymentMapper.class, TenantContext.class})
    @EnableConfigurationProperties({PaymentMockConfirmationProperties.class, TenancyProperties.class})
    // Customers resolve their tenant from the OIDC client (ADR-0001), so map the test client to this tenant.
    @TestPropertySource(properties = "app.tenancy.customer-clients.pede-aqui-web=" + TENANT)
    abstract static class Base {
        @Autowired MockMvc mockMvc;
        @MockBean PaymentRepository paymentRepository;
        @MockBean OrderRepository orderRepository;
        @MockBean RefundRepository refundRepository;
        @MockBean AuditLogService auditLogService;
        @MockBean AppUserProfileRepository userProfileRepository;
        @MockBean JwtDecoder jwtDecoder;
        @MockBean com.delivery.delivery.repository.DeliveryRepository deliveryRepository;
        @MockBean com.delivery.order.service.OrderStockService orderStockService;

        Payment payment;
        Order order;

        @BeforeEach
        void seed() {
            payment = new Payment(UUID.randomUUID(), TENANT_ID, ORDER_ID, new BigDecimal("12.00"), KEY);
            order = new Order(ORDER_ID, TENANT_ID, "PA-1", OWNER_PROFILE_ID, UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ONE,
                    BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("12.00"), KEY, "hash", "000000");
            when(paymentRepository.findByTenantIdAndIdempotencyKey(TENANT_ID, KEY)).thenReturn(Optional.of(payment));
            when(paymentRepository.findByTenantIdAndId(TENANT_ID, payment.getId())).thenReturn(Optional.of(payment));
            when(orderRepository.findByTenantIdAndId(TENANT_ID, ORDER_ID)).thenReturn(Optional.of(order));
            profile("kc-owner", OWNER_PROFILE_ID);
            profile("kc-other", UUID.randomUUID());
        }

        void profile(String subject, UUID id) {
            when(userProfileRepository.findByTenantIdAndKeycloakUserId(TENANT_ID, subject))
                    .thenReturn(Optional.of(new AppUserProfile(id, TENANT_ID, subject, subject + "@example.test", subject, Set.of())));
        }

        MockHttpServletRequestBuilder confirmAs(String subject, String role) {
            return post("/api/v1/payments/{paymentId}/confirm", payment.getId())
                    .with(jwt().jwt(j -> {
                                j.subject(subject);
                                if ("CUSTOMER".equals(role)) {
                                    j.claim(TenantContext.AUTHORIZED_PARTY_CLAIM, "pede-aqui-web");
                                } else {
                                    j.claim(TenantContext.TENANT_ID_CLAIM, TENANT_ID.toString());
                                }
                            })
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json")
                    .content("{\"idempotencyKey\":\"" + KEY + "\"}");
        }

        void assertUnchanged() {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            verify(auditLogService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @TestPropertySource(properties = "app.payments.mock-confirmation.enabled=true")
    class FlagEnabled extends Base {
        @Test
        void owningCustomerConfirmsAndOrderBecomesPaymentConfirmed() throws Exception {
            mockMvc.perform(confirmAs("kc-owner", "CUSTOMER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
        }

        @Test
        void repeatedConfirmationIsIdempotentWithASingleAuditLog() throws Exception {
            mockMvc.perform(confirmAs("kc-owner", "CUSTOMER")).andExpect(status().isOk());
            mockMvc.perform(confirmAs("kc-owner", "CUSTOMER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
            verify(auditLogService, times(1)).log("PAYMENT_CONFIRMED", "payment", payment.getId().toString(), "PA-1", "SUCCESS");
        }

        @Test
        void anotherCustomerOfTheSameTenantGets404() throws Exception {
            mockMvc.perform(confirmAs("kc-other", "CUSTOMER")).andExpect(status().isNotFound());
            assertUnchanged();
        }

        @Test
        void vendorAdminGets403() throws Exception {
            mockMvc.perform(confirmAs("kc-vendor", "VENDOR_ADMIN")).andExpect(status().isForbidden());
            assertUnchanged();
        }

        @Test
        void courierGets403() throws Exception {
            mockMvc.perform(confirmAs("kc-courier", "COURIER")).andExpect(status().isForbidden());
            assertUnchanged();
        }

        @Test
        void opsCanConfirm() throws Exception {
            mockMvc.perform(confirmAs("kc-ops", "OPS")).andExpect(status().isOk());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CONFIRMED);
        }

        @Test
        void unauthenticatedGets401() throws Exception {
            mockMvc.perform(post("/api/v1/payments/{paymentId}/confirm", payment.getId())
                            .contentType("application/json").content("{\"idempotencyKey\":\"" + KEY + "\"}"))
                    .andExpect(status().isUnauthorized());
            assertUnchanged();
        }
    }

    @Nested
    @TestPropertySource(properties = "app.payments.mock-confirmation.enabled=false")
    class FlagDisabled extends Base {
        @Test
        void everyRoleGets404() throws Exception {
            for (String[] caller : new String[][] {{"kc-owner", "CUSTOMER"}, {"kc-ops", "OPS"}, {"kc-admin", "ADMIN"},
                    {"kc-vendor", "VENDOR_ADMIN"}, {"kc-courier", "COURIER"}}) {
                mockMvc.perform(confirmAs(caller[0], caller[1])).andExpect(status().isNotFound());
            }
            assertUnchanged();
        }
    }
}
