package com.delivery.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.catalog.controller.PriceModerationController;
import com.delivery.catalog.controller.ProductManagementController;
import com.delivery.catalog.dto.PendingPriceChangeResponse;
import com.delivery.catalog.dto.PriceUpdateResponse;
import com.delivery.catalog.dto.ProductEditResponse;
import com.delivery.catalog.service.PriceModerationService;
import com.delivery.catalog.service.ProductService;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest({ProductManagementController.class, PriceModerationController.class})
@Import(SecurityConfig.class)
class CatalogManagementApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private PriceModerationService moderationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final UUID productId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();

    private ProductEditResponse sampleProduct() {
        return new ProductEditResponse(productId, UUID.randomUUID(), UUID.randomUUID(), "Arroz", "Pacote 1kg",
                "ACTIVE", false, null, new BigDecimal("100.00"), null, Instant.now());
    }

    // --- @PreAuthorize 403 matrix ---

    @Test
    void rejectsRolesWithoutCatalogEditAccess() throws Exception {
        for (String role : List.of("ROLE_CUSTOMER", "ROLE_COURIER", "ROLE_FINANCE", "ROLE_SUPPORT")) {
            mockMvc.perform(patch("/api/v1/catalog/products/{id}", productId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"name\":\"Novo\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"price\":10}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/catalog/products/{id}/image", productId)
                            .with(jwt().authorities(() -> role))
                            .contentType("application/json")
                            .content("{\"storageKey\":\"tenants/x/uploads/a.jpg\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/catalog/products/{id}/image", productId)
                            .with(jwt().authorities(() -> role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void vendorAdminCannotAccessModerationEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/moderation/price-changes")
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/catalog/moderation/price-changes/{skuId}/approve", skuId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/catalog/moderation/price-changes/{skuId}/reject", skuId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossVendorEditIsForbiddenForVendorAdmin() throws Exception {
        when(productService.updateProduct(eq(productId), any()))
                .thenThrow(new BusinessException("vendor_access_denied", "You can only manage products of your own vendor", HttpStatus.FORBIDDEN));

        mockMvc.perform(patch("/api/v1/catalog/products/{id}", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isForbidden());
    }

    // --- happy paths ---

    @Test
    void vendorAdminCanPatchProduct() throws Exception {
        when(productService.updateProduct(eq(productId), any())).thenReturn(sampleProduct());

        mockMvc.perform(patch("/api/v1/catalog/products/{id}", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"Arroz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Arroz"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void priceUpdateReturnsReviewFlag() throws Exception {
        when(productService.updatePrice(productId, new BigDecimal("150.00")))
                .thenReturn(new PriceUpdateResponse(skuId, new BigDecimal("100.00"), new BigDecimal("150.00"), true));

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"price\":150.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewRequired").value(true))
                .andExpect(jsonPath("$.currentPrice").value(100.00))
                .andExpect(jsonPath("$.pendingPrice").value(150.00));
    }

    @Test
    void pendingConflictSurfacesAs409() throws Exception {
        when(productService.updatePrice(eq(productId), any()))
                .thenThrow(new BusinessException("price_change_pending", "pending", HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"price\":150.00}"))
                .andExpect(status().isConflict());
    }

    @Test
    void opsCanSetAndClearImage() throws Exception {
        when(productService.setImage(eq(productId), any())).thenReturn(sampleProduct());

        mockMvc.perform(put("/api/v1/catalog/products/{id}/image", productId)
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"storageKey\":\"tenants/t/uploads/product/u/1-a.jpg\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/catalog/products/{id}/image", productId)
                        .with(jwt().authorities(() -> "ROLE_OPS")))
                .andExpect(status().isNoContent());
        verify(productService).clearImage(productId);
    }

    @Test
    void adminCanListApproveAndRejectPendingPrices() throws Exception {
        when(moderationService.listPending()).thenReturn(List.of(new PendingPriceChangeResponse(
                skuId, productId, "Arroz", UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("150.00"),
                new BigDecimal("50.0000"), "vendor-user-1", Instant.now())));

        mockMvc.perform(get("/api/v1/catalog/moderation/price-changes")
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skuId").value(skuId.toString()))
                .andExpect(jsonPath("$[0].deltaPercent").value(50.0));

        mockMvc.perform(post("/api/v1/catalog/moderation/price-changes/{skuId}/approve", skuId)
                        .with(jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
        verify(moderationService).approve(skuId);

        mockMvc.perform(post("/api/v1/catalog/moderation/price-changes/{skuId}/reject", skuId)
                        .with(jwt().authorities(() -> "ROLE_OPS"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Aumento excessivo\"}"))
                .andExpect(status().isOk());
        verify(moderationService).reject(skuId, "Aumento excessivo");
    }

    // --- validation 400s ---

    @Test
    void rejectsInvalidProductPayloads() throws Exception {
        String longName = "a".repeat(141);
        mockMvc.perform(patch("/api/v1/catalog/products/{id}", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest());

        String longDescription = "d".repeat(2001);
        mockMvc.perform(patch("/api/v1/catalog/products/{id}", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"description\":\"" + longDescription + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonPositiveOrMissingPrice() throws Exception {
        mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"price\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"price\":-5}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/catalog/products/{id}/price", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankStorageKeyAndBlankRejectionReason() throws Exception {
        mockMvc.perform(put("/api/v1/catalog/products/{id}/image", productId)
                        .with(jwt().authorities(() -> "ROLE_VENDOR_ADMIN"))
                        .contentType("application/json")
                        .content("{\"storageKey\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/catalog/moderation/price-changes/{skuId}/reject", skuId)
                        .with(jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType("application/json")
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
