package com.delivery.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.repository.CartRepository;
import com.delivery.common.security.SecurityConfig;
import com.delivery.order.controller.CheckoutController;
import com.delivery.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(CheckoutController.class)
@Import(SecurityConfig.class)
class CheckoutSecurityApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartRepository cartRepository;

    @MockitoBean
    private AppUserProfileRepository userProfileRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID customer1Id = UUID.randomUUID();
    private final UUID customer2Id = UUID.randomUUID();
    private final UUID vendorId = UUID.randomUUID();
    private final UUID cart1Id = UUID.randomUUID();
    private final UUID cart2Id = UUID.randomUUID();

    @Test
    void checkoutControllerDelegatesOwnershipEnforcementToService() throws Exception {
        // The checkout endpoint takes only a cartId in the body, so the controller cannot
        // know who owns the cart — ownership is enforced inside OrderService.createOrder and
        // is verified in CheckoutOwnershipTest. This slice test only asserts that the
        // controller forwards the request to the service verbatim, without its own check.
        Jwt jwt = createJwt("customer1-keycloak-id");

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "cartId", cart2Id.toString(),
                "idempotencyKey", "test-key-123"
        ));

        mockMvc.perform(post("/api/v1/checkout")
                        .with(jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        verify(orderService).checkout(cart2Id, "test-key-123");
    }

    @Test
    void allowsCustomerToCheckoutOwnCart() throws Exception {
        String keycloakUserId = "customer1-keycloak-id";
        AppUserProfile customer1 = new AppUserProfile(customer1Id, tenantId, keycloakUserId, "customer1@example.com", "Customer 1", java.util.Set.of());
        Cart customer1Cart = new Cart(cart1Id, tenantId, customer1Id, vendorId);
        
        when(userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId))
                .thenReturn(java.util.Optional.of(customer1));
        when(cartRepository.findByTenantIdAndId(tenantId, cart1Id))
                .thenReturn(java.util.Optional.of(customer1Cart));

        Jwt jwt = createJwt(keycloakUserId);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "cartId", cart1Id.toString(),
                "idempotencyKey", "test-key-123"
        ));

        mockMvc.perform(post("/api/v1/checkout")
                        .with(jwt().jwt(jwt).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        verify(orderService).checkout(cart1Id, "test-key-123");
    }

    @Test
    void rejectsUnauthenticatedCheckout() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "cartId", cart1Id.toString(),
                "idempotencyKey", "test-key-123"
        ));

        mockMvc.perform(post("/api/v1/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orderService);
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