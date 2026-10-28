package com.delivery.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;

/**
 * End-to-end API test of INC-12 (AS-02, FR-003) on the full application context with real Postgres (Flyway
 * incl. V046/V047): address CRUD with ownership, checkout with an immutable address snapshot, the legacy
 * no-address window, and who may read the delivery address (assigned courier only; vendor gets the area).
 * Keycloak is replaced only at the JwtDecoder and admin-client seams, as in CustomerTenantResolutionE2ETest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerAddressCheckoutE2ETest {
    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    private static final String ADDRESSES = "/api/v1/customers/me/addresses";

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping address E2E test: no Docker environment and no SPRING_DATASOURCE_URL override");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        PostgresTestContainerConfig.POSTGRES.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
    }

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;
    @MockitoBean private KeycloakAdminService keycloakAdminService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private UUID skuId;

    @BeforeEach
    void seed() {
        tenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        Timestamp now = Timestamp.from(Instant.now());
        vendorId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja Moradas', 'ACTIVE', 'APPROVED', ?, ?)",
                vendorId, tenant, now, now);
        jdbc.update("INSERT INTO categories (id, tenant_id, name, slug, active, created_at, updated_at) VALUES (?, ?, 'Mercearia', ?, TRUE, ?, ?)",
                categoryId, tenant, "addr-" + categoryId, now, now);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, vendor_id, category_id, name, status,
                                      requires_prescription_metadata, manual_validation_required, prohibited_fuel, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Arroz', 'ACTIVE', FALSE, FALSE, FALSE, ?, ?)""",
                productId, tenant, vendorId, categoryId, now, now);
        jdbc.update("INSERT INTO skus (id, tenant_id, product_id, sku_code, name, price, active, created_at, updated_at) VALUES (?, ?, ?, ?, 'Arroz 1kg', 18.00, TRUE, ?, ?)",
                skuId, tenant, productId, "sku-" + skuId, now, now);
        jdbc.update("INSERT INTO inventory_items (id, tenant_id, vendor_id, sku_id, quantity_available, quantity_reserved, status, created_at, updated_at, version) VALUES (?, ?, ?, ?, 50, 0, 'ACTIVE', ?, ?, 0)",
                UUID.randomUUID(), tenant, vendorId, skuId, now, now);
    }

    // ---- Address API -------------------------------------------------------------------------------

    @Test
    void customerManagesOwnAddressesAndThePinIsStoredAsGeography() throws Exception {
        String ana = customerToken();
        UUID id = createAddress(ana, "Casa", -25.9692, 32.5732);

        mockMvc.perform(withBearer(get(ADDRESSES), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].line2").value("Polana"))
                .andExpect(jsonPath("$[0].latitude").value(-25.9692));
        assertThat(jdbc.queryForObject("SELECT ST_Y(location::geometry) FROM customer_addresses WHERE id = ?", Double.class, id)).isEqualTo(-25.9692);
        assertThat(jdbc.queryForObject("SELECT ST_X(location::geometry) FROM customer_addresses WHERE id = ?", Double.class, id)).isEqualTo(32.5732);

        mockMvc.perform(withBearer(put(ADDRESSES + "/{id}", id), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Trabalho", "Av. 24 de Julho 50", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Trabalho"))
                .andExpect(jsonPath("$.latitude").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT location IS NULL FROM customer_addresses WHERE id = ?", Boolean.class, id)).isTrue();

        mockMvc.perform(withBearer(delete(ADDRESSES + "/{id}", id), ana)).andExpect(status().isNoContent());
        mockMvc.perform(withBearer(get(ADDRESSES + "/{id}", id), ana)).andExpect(status().isNotFound());
    }

    @Test
    void anotherCustomersAddressIsNotFoundForEveryOperation() throws Exception {
        String ana = customerToken();
        String bruno = customerToken();
        UUID anas = createAddress(ana, "Casa", -25.9692, 32.5732);

        mockMvc.perform(withBearer(get(ADDRESSES), bruno)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(withBearer(get(ADDRESSES + "/{id}", anas), bruno)).andExpect(status().isNotFound());
        mockMvc.perform(withBearer(put(ADDRESSES + "/{id}", anas), bruno).contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Hack", "Rua X", null, null))).andExpect(status().isNotFound());
        mockMvc.perform(withBearer(delete(ADDRESSES + "/{id}", anas), bruno)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT label FROM customer_addresses WHERE id = ?", String.class, anas)).isEqualTo("Casa");
    }

    @Test
    void invalidCoordinatesAndNonCustomersAreRejected() throws Exception {
        String ana = customerToken();
        mockMvc.perform(withBearer(post(ADDRESSES), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Casa", "Rua 1", -25.9, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_coordinates"));
        mockMvc.perform(withBearer(post(ADDRESSES), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Casa", "Rua 1", 95.0, 32.5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        String courier = courierToken(UUID.randomUUID().toString());
        mockMvc.perform(withBearer(get(ADDRESSES), courier)).andExpect(status().isForbidden());
    }

    // ---- Checkout ----------------------------------------------------------------------------------

    @Test
    void checkoutWithAddressStoresAnImmutableSnapshot() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, "Casa", -25.9692, 32.5732);
        UUID cartId = addToCart(ana);

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(cartId, "addr-" + cartId, addressId)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(jsonPath("$.deliveryAddress.line1").value("Av. Julius Nyerere 1200"))
                .andExpect(jsonPath("$.deliveryAddress.latitude").value(-25.9692))
                .andExpect(jsonPath("$.deliveryAddress.instructions").value("Portão verde"));
        UUID orderId = jdbc.queryForObject("SELECT id FROM orders WHERE cart_id = ?", UUID.class, cartId);

        // Editing and then deleting the saved address never moves the order.
        mockMvc.perform(withBearer(put(ADDRESSES + "/{id}", addressId), ana).contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Casa", "Outra Rua 9", -19.84, 34.83))).andExpect(status().isOk());
        mockMvc.perform(withBearer(delete(ADDRESSES + "/{id}", addressId), ana)).andExpect(status().isNoContent());

        Map<String, Object> snapshot = jdbc.queryForMap("SELECT delivery_address_id, delivery_line1, delivery_line2, delivery_city, delivery_latitude, "
                + "delivery_longitude, delivery_recipient_name, delivery_instructions FROM orders WHERE id = ?", orderId);
        assertThat(snapshot.get("delivery_address_id")).isEqualTo(addressId);
        assertThat(snapshot.get("delivery_line1")).isEqualTo("Av. Julius Nyerere 1200");
        assertThat(snapshot.get("delivery_line2")).isEqualTo("Polana");
        assertThat(snapshot.get("delivery_city")).isEqualTo("Maputo");
        assertThat(snapshot.get("delivery_latitude")).isEqualTo(-25.9692);
        assertThat(snapshot.get("delivery_longitude")).isEqualTo(32.5732);
        assertThat(snapshot.get("delivery_recipient_name")).isEqualTo("Ana Teste");
        assertThat(snapshot.get("delivery_instructions")).isEqualTo("Portão verde");
    }

    @Test
    void checkoutWithAnotherCustomersAddressIsNotFoundAndCreatesNoOrder() throws Exception {
        String ana = customerToken();
        String bruno = customerToken();
        UUID anas = createAddress(ana, "Casa", -25.9692, 32.5732);
        UUID brunosCart = addToCart(bruno);

        mockMvc.perform(withBearer(post("/api/v1/checkout"), bruno).contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(brunosCart, "steal-" + brunosCart, anas)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE cart_id = ?", Integer.class, brunosCart)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM carts WHERE id = ?", String.class, brunosCart)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory_items WHERE sku_id = ?", Integer.class, skuId)).isZero();
    }

    @Test
    void sameKeyWithADifferentAddressIsAConflict() throws Exception {
        String ana = customerToken();
        UUID home = createAddress(ana, "Casa", -25.9692, 32.5732);
        UUID work = createAddress(ana, "Trabalho", -25.97, 32.58);
        UUID cartId = addToCart(ana);
        String key = "addr-replay-" + cartId;

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(cartId, key, home))).andExpect(status().isCreated());
        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(cartId, key, home))).andExpect(status().isOk());
        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutJson(cartId, key, work)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
    }

    @Test
    void legacyCheckoutWithoutAddressStillWorksDuringTheWindowAndIsFlaggedDeprecated() throws Exception {
        String ana = customerToken();
        UUID cartId = addToCart(ana);

        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"legacy-" + cartId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT delivery_line1 FROM orders WHERE cart_id = ?", String.class, cartId)).isNull();
    }

    // ---- Who sees the delivery address -------------------------------------------------------------

    @Test
    void onlyTheAssignedCourierReadsTheFullAddressWhileWorkingTheDelivery() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, "Casa", -25.9692, 32.5732);
        UUID cartId = addToCart(ana);
        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(cartId, "courier-" + cartId, addressId))).andExpect(status().isCreated());
        UUID orderId = jdbc.queryForObject("SELECT id FROM orders WHERE cart_id = ?", UUID.class, cartId);
        UUID deliveryId = jdbc.queryForObject("SELECT id FROM deliveries WHERE order_id = ?", UUID.class, orderId);

        String assignedSub = UUID.randomUUID().toString();
        String otherSub = UUID.randomUUID().toString();
        UUID assignedCourier = courier(assignedSub);
        courier(otherSub);
        jdbc.update("UPDATE deliveries SET courier_id = ?, status = 'ASSIGNED' WHERE id = ?", assignedCourier, deliveryId);

        // Offered but not accepted yet: no address.
        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), courierToken(assignedSub)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("delivery_address_unavailable"));

        jdbc.update("UPDATE deliveries SET status = 'ACCEPTED' WHERE id = ?", deliveryId);
        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), courierToken(assignedSub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line1").value("Av. Julius Nyerere 1200"))
                .andExpect(jsonPath("$.phone").value("+258840000000"))
                .andExpect(jsonPath("$.latitude").value(-25.9692))
                .andExpect(jsonPath("$.instructions").value("Portão verde"));

        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), courierToken(otherSub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("delivery_access_denied"));
        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), ana)).andExpect(status().isForbidden());
        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), vendorToken())).andExpect(status().isForbidden());

        // Finished delivery: the address is no longer exposed, even to the courier who delivered it.
        jdbc.update("UPDATE deliveries SET status = 'DELIVERED' WHERE id = ?", deliveryId);
        mockMvc.perform(withBearer(get("/api/v1/deliveries/{id}/address", deliveryId), courierToken(assignedSub)))
                .andExpect(status().isConflict());
    }

    @Test
    void vendorOrderListCarriesOnlyTheDeliveryArea() throws Exception {
        String ana = customerToken();
        UUID addressId = createAddress(ana, "Casa", -25.9692, 32.5732);
        UUID cartId = addToCart(ana);
        mockMvc.perform(withBearer(post("/api/v1/checkout"), ana).contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(cartId, "vendor-" + cartId, addressId))).andExpect(status().isCreated());

        mockMvc.perform(withBearer(get("/api/v1/orders"), vendorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryArea").value("Maputo"))
                .andExpect(content().string(not(containsString("Julius Nyerere"))))
                .andExpect(content().string(not(containsString("+258840000000"))))
                .andExpect(content().string(not(containsString("Portão verde"))));
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private UUID createAddress(String token, String label, Double latitude, Double longitude) throws Exception {
        String body = mockMvc.perform(withBearer(post(ADDRESSES), token).contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(label, "Av. Julius Nyerere 1200", latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String addressJson(String label, String line1, Double latitude, Double longitude) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("label", label);
        body.put("recipientName", "Ana Teste");
        body.put("phone", "+258840000000");
        body.put("line1", line1);
        body.put("line2", "Polana");
        body.put("city", "Maputo");
        body.put("latitude", latitude);
        body.put("longitude", longitude);
        body.put("deliveryInstructions", "Portão verde");
        return objectMapper.writeValueAsString(body);
    }

    private static String checkoutJson(UUID cartId, String key, UUID addressId) {
        return "{\"cartId\":\"" + cartId + "\",\"idempotencyKey\":\"" + key + "\",\"addressId\":\"" + addressId + "\"}";
    }

    private UUID addToCart(String token) throws Exception {
        String cart = mockMvc.perform(withBearer(post("/api/v1/customers/{customerId}/cart/items", UUID.randomUUID()), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"skuId\":\"" + skuId + "\",\"quantity\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(cart).get("id").asText());
    }

    private UUID courier(String keycloakUserId) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID profileId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Estafeta', 'ACTIVE', ?, ?)",
                profileId, tenant, keycloakUserId, "courier-" + keycloakUserId + "@example.test", now, now);
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, verification_status, available, created_at, updated_at) VALUES (?, ?, ?, 'APPROVED', TRUE, ?, ?)",
                courierId, tenant, profileId, now, now);
        return courierId;
    }

    private String customerToken() {
        String subject = UUID.randomUUID().toString();
        return token(subject, Map.of("azp", "pede-aqui-web", "email", "cliente-" + subject + "@example.test", "name", "Cliente Teste"), "CUSTOMER");
    }

    private String courierToken(String subject) {
        return token(subject, Map.of("azp", "pede-aqui-courier-mobile", "tenant_id", tenant.toString()), "COURIER");
    }

    private String vendorToken() {
        return token("vendor-" + UUID.randomUUID(), Map.of("azp", "delivery-app", "tenant_id", tenant.toString(), "vendor_id", vendorId.toString()), "VENDOR_ADMIN");
    }

    private String token(String subject, Map<String, Object> extraClaims, String... realmRoles) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
