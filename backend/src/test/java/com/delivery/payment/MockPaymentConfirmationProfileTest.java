package com.delivery.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.payment.controller.MockPaymentConfirmationController;
import com.delivery.payment.controller.PaymentController;
import com.delivery.payment.service.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * LAC-01 definitive fix (W7-A): under the {@code prod} profile the mock confirmation endpoint is not mapped at all —
 * even with {@code app.payments.mock-confirmation.enabled=true} — while the rest of the payment API is; outside prod
 * it stays mapped (and flag-gated, see {@link PaymentConfirmationApiTest}).
 */
class MockPaymentConfirmationProfileTest {

    @WebMvcTest(controllers = {MockPaymentConfirmationController.class, PaymentController.class})
    @Import(SecurityConfig.class)
    @TestPropertySource(properties = "app.payments.mock-confirmation.enabled=true")
    abstract static class Base {
        @Autowired MockMvc mockMvc;
        @Autowired ApplicationContext context;
        @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
        @MockitoBean PaymentService paymentService;
        @MockitoBean JwtDecoder jwtDecoder;

        boolean confirmMapped() {
            return handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPatternValues().stream())
                    .anyMatch(pattern -> pattern.equals("/api/v1/payments/{paymentId}/confirm"));
        }

        boolean refundsMapped() {
            return handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPatternValues().stream())
                    .anyMatch(pattern -> pattern.equals("/api/v1/payments/{paymentId}/refunds"));
        }
    }

    @Nested
    @ActiveProfiles("prod")
    class Prod extends Base {
        @Test
        void confirmEndpointIsNotMappedAndAnswers404ForEveryone() throws Exception {
            assertThat(context.getBeanNamesForType(MockPaymentConfirmationController.class)).isEmpty();
            assertThat(confirmMapped()).isFalse();
            assertThat(refundsMapped()).isTrue();
            for (String role : new String[] {"ROLE_ADMIN", "ROLE_OPS", "ROLE_CUSTOMER"}) {
                mockMvc.perform(post("/api/v1/payments/{id}/confirm", UUID.randomUUID()).with(jwt().authorities(() -> role))
                                .contentType("application/json").content("{\"idempotencyKey\":\"k-1\"}"))
                        .andExpect(status().isNotFound());
            }
            verify(paymentService, never()).confirm(any(), any());
        }
    }

    @Nested
    @ActiveProfiles("dev")
    class Dev extends Base {
        @Test
        void confirmEndpointStaysMappedOutsideProd() {
            assertThat(context.getBeanNamesForType(MockPaymentConfirmationController.class)).hasSize(1);
            assertThat(confirmMapped()).isTrue();
        }
    }
}
