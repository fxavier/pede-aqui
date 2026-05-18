package com.delivery.delivery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.security.SecurityConfig;
import com.delivery.delivery.controller.DeliveryController;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.service.DeliveryService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeliveryController.class)
@Import(SecurityConfig.class)
class DeliveryApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeliveryService deliveryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void supportsLifecycleStatusUpdatesAndCompletion() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        when(deliveryService.updateStatus(eq(deliveryId), eq(DeliveryStatus.ARRIVED_AT_VENDOR), any(), any()))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.ARRIVED_AT_VENDOR, 0));
        when(deliveryService.updateStatus(eq(deliveryId), eq(DeliveryStatus.PICKED_UP), any(), any()))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.PICKED_UP, 0));
        when(deliveryService.updateStatus(eq(deliveryId), eq(DeliveryStatus.ON_ROUTE_TO_CUSTOMER), any(), any()))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.ON_ROUTE_TO_CUSTOMER, 0));
        when(deliveryService.updateStatus(eq(deliveryId), eq(DeliveryStatus.ARRIVED_AT_CUSTOMER), any(), any()))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.ARRIVED_AT_CUSTOMER, 0));
        when(deliveryService.updateStatus(eq(deliveryId), eq(DeliveryStatus.FAILED_DELIVERY), any(), any()))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.FAILED_DELIVERY, 0));
        when(deliveryService.complete(deliveryId, "123456"))
                .thenReturn(new DeliveryResponse(deliveryId, UUID.randomUUID(), DeliveryStatus.DELIVERED, 0));

        mockMvc.perform(patch("/api/v1/deliveries/{deliveryId}/status", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"status\":\"ARRIVED_AT_VENDOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED_AT_VENDOR"));

        mockMvc.perform(patch("/api/v1/deliveries/{deliveryId}/status", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/{deliveryId}/status", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"status\":\"ON_ROUTE_TO_CUSTOMER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/{deliveryId}/status", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"status\":\"ARRIVED_AT_CUSTOMER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/deliveries/{deliveryId}/status", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"status\":\"FAILED_DELIVERY\",\"proofPhotoStorageKey\":\"proof/key.jpg\",\"cashCollectedAmount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED_DELIVERY"));

        mockMvc.perform(post("/api/v1/deliveries/{deliveryId}/complete", deliveryId)
                        .with(jwt().authorities(() -> "ROLE_COURIER"))
                        .contentType("application/json")
                        .content("{\"confirmationCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }
}
