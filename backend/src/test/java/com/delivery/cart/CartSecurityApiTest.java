package com.delivery.cart;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.controller.CartController;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.repository.CartRepository;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.security.SecurityConfig;
import com.delivery.inventory.entity.InventoryItem;
import com.delivery.inventory.repository.InventoryItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;

@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
class CartSecurityApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private PricingService pricingService;

    // CartController resolves the tenant for the pricing preview (INC-13).
    @MockitoBean
    private com.delivery.common.security.TenantContext tenantContext;

    @MockitoBean
    private CartRepository cartRepository;

    @MockitoBean
    private SkuRepository skuRepository;

    @MockitoBean
    private InventoryItemRepository inventoryItemRepository;

    @MockitoBean
    private AppUserProfileRepository userProfileRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID customer1Id = UUID.randomUUID();
    private final UUID customer2Id = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID skuId = UUID.randomUUID();

    @Test
    void pathCustomerIdCannotTargetAnotherCustomersCart() throws Exception {
        // Cross-customer cart access is prevented by construction, not by a 403: the
        // {customerId} path segment is never used to scope the operation. The controller
        // forwards only the request body, and CartService resolves the owner from the JWT
        // (self-scoping is verified in CartServiceTest). So posting to another customer's
        // path acts on the caller's own cart — customer2Id below never reaches the service.
        String keycloakUserId = "customer1-keycloak-id";
        AppUserProfile customer1 = new AppUserProfile(customer1Id, tenantId, keycloakUserId, "customer1@example.com", "Customer 1", java.util.Set.of());

        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(java.util.Optional.of(customer1));

        Jwt jwt = createJwt(keycloakUserId);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "vendorId", vendorId.toString(),
                "skuId", skuId.toString(),
                "quantity", 1
        ));

        mockMvc.perform(post("/api/v1/customers/{customerId}/cart/items", customer2Id)
                        .with(jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // Only the body args are forwarded; the path's customer2Id is not passed through.
        verify(cartService).addItem(vendorId, skuId, 1);
    }

    @Test
    void allowsCustomerToAddToOwnCart() throws Exception {
        String keycloakUserId = "customer1-keycloak-id";
        AppUserProfile customer1 = new AppUserProfile(customer1Id, tenantId, keycloakUserId, "customer1@example.com", "Customer 1", java.util.Set.of());
        
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(java.util.Optional.of(customer1));

        Jwt jwt = createJwt(keycloakUserId);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "vendorId", vendorId.toString(),
                "skuId", skuId.toString(),
                "quantity", 1
        ));

        mockMvc.perform(post("/api/v1/customers/{customerId}/cart/items", customer1Id)
                        .with(jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(cartService).addItem(vendorId, skuId, 1);
    }

    @Test
    void rejectsUnauthenticatedCartAccess() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "vendorId", vendorId.toString(),
                "skuId", skuId.toString(),
                "quantity", 1
        ));

        mockMvc.perform(post("/api/v1/customers/{customerId}/cart/items", customer1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cartService);
    }

    private Jwt createJwt(String keycloakUserId) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", keycloakUserId, "tenant_id", tenantId.toString())
        );
    }
}