package com.delivery.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.catalog.controller.CatalogController;
import com.delivery.catalog.dto.ProductResponse;
import com.delivery.catalog.service.CatalogService;
import com.delivery.common.security.SecurityConfig;
import com.delivery.inventory.controller.InventoryController;
import com.delivery.inventory.dto.InventoryResponse;
import com.delivery.inventory.service.InventoryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CatalogController.class, InventoryController.class})
@Import(SecurityConfig.class)
class CatalogInventoryApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogService catalogService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void vendorCanCreateProduct() throws Exception {
        UUID vendorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(catalogService.createProduct(any()))
                .thenReturn(new ProductResponse(UUID.randomUUID(), vendorId, categoryId, "Arroz", "Pacote 1kg", "PENDING", false, false, Map.of(), null, null, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/catalog/products")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"vendorId\":\"" + vendorId + "\",\"categoryId\":\"" + categoryId + "\",\"name\":\"Arroz\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Arroz"));
    }

    @Test
    void vendorCanUpdateStock() throws Exception {
        UUID inventoryId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        when(inventoryService.updateAvailable(inventoryId, 8))
                .thenReturn(new InventoryResponse(inventoryId, skuId, 8, 0));

        mockMvc.perform(patch("/api/v1/inventory/{inventoryItemId}/stock", inventoryId)
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"quantityAvailable\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(8));
    }

    @Test
    void rejectsNegativeStock() throws Exception {
        UUID inventoryId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/inventory/{inventoryItemId}/stock", inventoryId)
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"quantityAvailable\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
