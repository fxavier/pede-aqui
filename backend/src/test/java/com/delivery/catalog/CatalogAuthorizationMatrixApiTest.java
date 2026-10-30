package com.delivery.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.catalog.controller.CatalogController;
import com.delivery.catalog.controller.ProductVariationController;
import com.delivery.catalog.service.CatalogService;
import com.delivery.common.security.SecurityConfig;
import com.delivery.inventory.controller.InventoryController;
import com.delivery.inventory.service.InventoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** INC-05 (AS-04, AS-05): full role x endpoint matrix for catalog, variation and inventory mutations. */
@WebMvcTest({CatalogController.class, ProductVariationController.class, InventoryController.class})
@Import(SecurityConfig.class)
class CatalogAuthorizationMatrixApiTest {
    private static final List<String> ALL_ROLES = List.of(
            "ADMIN", "OPS", "VENDOR_ADMIN", "VENDOR_STAFF", "COURIER", "CUSTOMER", "FINANCE", "SUPPORT");
    private static final Set<String> MODERATORS = Set.of("ADMIN", "OPS");
    private static final Set<String> CATALOG_EDITORS = Set.of("ADMIN", "OPS", "VENDOR_ADMIN");
    /** LAC-06: categories are tenant-wide (shared by every merchant of the operator), so only OPS/ADMIN edit them. */
    private static final Set<String> CATEGORY_EDITORS = Set.of("ADMIN", "OPS");
    private static final Set<String> STOCK_EDITORS = Set.of("ADMIN", "OPS", "VENDOR_ADMIN", "VENDOR_STAFF");

    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogService catalogService;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private record Endpoint(String name, String method, String url, String body, Set<String> allowed) {
        MockHttpServletRequestBuilder request() {
            MockHttpServletRequestBuilder builder = switch (method) {
                case "POST" -> post(url);
                case "PUT" -> put(url);
                case "PATCH" -> patch(url);
                case "DELETE" -> delete(url);
                default -> throw new IllegalArgumentException(method);
            };
            if (body != null) {
                builder.contentType("application/json").content(body);
            }
            return builder;
        }
    }

    private static List<Endpoint> endpoints() {
        String productBody = "{\"vendorId\":\"" + ID + "\",\"categoryId\":\"" + ID + "\",\"name\":\"Arroz\"}";
        String skuBody = "{\"productId\":\"" + ID + "\",\"vendorId\":\"" + ID + "\",\"skuCode\":\"S1\",\"name\":\"1kg\",\"price\":10,\"initialStock\":1}";
        String categoryBody = "{\"name\":\"Bebidas\",\"vertical\":\"groceries\"}";
        String categoryUpdateBody = "{\"name\":\"Bebidas\",\"vertical\":\"groceries\",\"active\":true}";
        String groupBody = "{\"productId\":\"" + ID + "\",\"name\":\"Tamanho\",\"required\":true,\"minSelections\":0,\"maxSelections\":1,\"displayOrder\":0}";
        String groupUpdateBody = "{\"name\":\"Tamanho\",\"required\":true,\"minSelections\":0,\"maxSelections\":1,\"displayOrder\":0}";
        String optionBody = "{\"groupId\":\"" + ID + "\",\"name\":\"Grande\",\"available\":true,\"displayOrder\":0}";
        String optionUpdateBody = "{\"name\":\"Grande\",\"available\":true,\"displayOrder\":0}";
        return List.of(
                new Endpoint("createProduct", "POST", "/api/v1/catalog/products", productBody, CATALOG_EDITORS),
                new Endpoint("createSku", "POST", "/api/v1/catalog/skus", skuBody, CATALOG_EDITORS),
                new Endpoint("approveProduct", "POST", "/api/v1/catalog/products/" + ID + "/approve", null, MODERATORS),
                new Endpoint("rejectProduct", "POST", "/api/v1/catalog/products/" + ID + "/reject", null, MODERATORS),
                new Endpoint("createCategory", "POST", "/api/v1/catalog/categories", categoryBody, CATEGORY_EDITORS),
                new Endpoint("updateCategory", "PUT", "/api/v1/catalog/categories/" + ID, categoryUpdateBody, CATEGORY_EDITORS),
                new Endpoint("deleteCategory", "DELETE", "/api/v1/catalog/categories/" + ID, null, CATEGORY_EDITORS),
                new Endpoint("createVariationGroup", "POST", "/api/v1/catalog/product-variation-groups", groupBody, CATALOG_EDITORS),
                new Endpoint("updateVariationGroup", "PUT", "/api/v1/catalog/product-variation-groups/" + ID, groupUpdateBody, CATALOG_EDITORS),
                new Endpoint("deleteVariationGroup", "DELETE", "/api/v1/catalog/product-variation-groups/" + ID, null, CATALOG_EDITORS),
                new Endpoint("createVariationOption", "POST", "/api/v1/catalog/product-variation-options", optionBody, CATALOG_EDITORS),
                new Endpoint("updateVariationOption", "PUT", "/api/v1/catalog/product-variation-options/" + ID, optionUpdateBody, CATALOG_EDITORS),
                new Endpoint("deleteVariationOption", "DELETE", "/api/v1/catalog/product-variation-options/" + ID, null, CATALOG_EDITORS),
                new Endpoint("updateStock", "PATCH", "/api/v1/inventory/" + ID + "/stock", "{\"quantityAvailable\":3}", STOCK_EDITORS));
    }

    static Stream<Arguments> matrix() {
        List<Arguments> cases = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            for (String role : ALL_ROLES) {
                cases.add(Arguments.of(endpoint.name(), role, endpoint, endpoint.allowed().contains(role)));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("matrix")
    void enforcesRoleMatrix(String name, String role, Endpoint endpoint, boolean allowed) throws Exception {
        int status = mockMvc.perform(endpoint.request().with(jwt().authorities(() -> "ROLE_" + role)))
                .andReturn().getResponse().getStatus();
        if (allowed) {
            assertThat(status).as("%s as %s should be allowed", name, role).isBetween(200, 299);
        } else {
            assertThat(status).as("%s as %s should be forbidden", name, role).isEqualTo(403);
        }
    }

    @Test
    void anonymousCannotMutateCatalogOrStock() throws Exception {
        // POST /catalog/categories shares a permitAll URL with the public GET, so method security
        // (403) rather than the resource-server entry point (401) rejects it; both are denials.
        for (Endpoint endpoint : endpoints()) {
            int status = mockMvc.perform(endpoint.request()).andReturn().getResponse().getStatus();
            assertThat(status).as("anonymous %s", endpoint.name()).isIn(401, 403);
        }
    }

    @Test
    void publicBrowseEndpointsStayAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/catalog/vendors/{vendorId}/products", ID)).andExpect(status().isOk());
    }
}
