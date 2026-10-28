package com.delivery.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.exception.GlobalExceptionHandler;
import com.delivery.common.security.SecurityConfig;
import com.delivery.delivery.controller.DeliveryController;
import com.delivery.delivery.controller.OperationsDeliveryController;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.exception.InvalidDeliveryCodeException;
import com.delivery.delivery.service.DeliveryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

/** Role gating of the OPS PIN-unlock endpoint and HTTP mapping of PIN lockout (INC-02). */
@WebMvcTest({OperationsDeliveryController.class, DeliveryController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OperationsDeliveryApiTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private DeliveryService deliveryService;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void opsAndAdminCanUnlockCode() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        when(deliveryService.unlockCode(deliveryId))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.ARRIVED_AT_CUSTOMER, 0));

        for (String role : new String[] {"ROLE_OPS", "ROLE_ADMIN"}) {
            mockMvc.perform(post("/api/v1/ops/deliveries/{id}/unlock-code", deliveryId).with(jwt().authorities(() -> role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.confirmationAttempts").value(0));
        }
    }

    @Test
    void courierCustomerAndVendorCannotUnlockCode() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        for (String role : new String[] {"ROLE_COURIER", "ROLE_CUSTOMER", "ROLE_VENDOR_ADMIN", "ROLE_SUPPORT"}) {
            mockMvc.perform(post("/api/v1/ops/deliveries/{id}/unlock-code", deliveryId).with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
        verify(deliveryService, never()).unlockCode(any());
    }

    @Test
    void opsCannotCallCompleteEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"confirmationCode\":\"123456\"}"))
                .andExpect(status().isForbidden());
        verify(deliveryService, never()).complete(any(), anyString());
    }

    @Test
    void lockedCodeMapsTo423() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        when(deliveryService.complete(any(), anyString())).thenThrow(InvalidDeliveryCodeException.locked());

        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"confirmationCode\":\"123456\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("delivery_code_locked"));
    }

    @Test
    void missingCodeIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/deliveries/{id}/complete", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(deliveryService, never()).complete(any(), any());
    }
}
