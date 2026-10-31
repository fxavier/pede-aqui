package com.delivery.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * LAC-29: {@code /api/v1/marketing/coupons} contract as the backoffice /marketing page uses it (create, list,
 * deactivate), pinned before the endpoint became a façade over the spec-002 {@code promotion} table and kept
 * identical after: same JSON members, status codes and error codes.
 */
class LegacyCouponFacadeE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendorId;
    private String ops;

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
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, available, created_at, updated_at) "
                + "VALUES (?, ?, 'Loja Cupao', 'ACTIVE', 'APPROVED', TRUE, ?, ?)", vendorId, tenant, now, now);
        ops = staffToken("OPS");
    }

    @Test
    void createReturnsTheCouponShapeWithUppercasedCode() throws Exception {
        String code = "lgc" + suffix();
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "PERCENTAGE", "15", "100.00", 50, vendorId,
                                "2026-01-01T00:00:00Z", "2099-06-30T23:59:59Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(15))
                .andExpect(jsonPath("$.minOrderAmount").value(100.0))
                .andExpect(jsonPath("$.maxUses").value(50))
                .andExpect(jsonPath("$.usesCount").value(0))
                .andExpect(jsonPath("$.vendorId").value(vendorId.toString()))
                .andExpect(jsonPath("$.validFrom").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.validUntil").value("2099-06-30T23:59:59Z"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void openEndedCouponKeepsANullValidUntil() throws Exception {
        String code = "OPEN" + suffix();
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "FIXED_AMOUNT", "20", null, null, null, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validUntil").isEmpty())
                .andExpect(jsonPath("$.vendorId").isEmpty())
                .andExpect(jsonPath("$.maxUses").isEmpty())
                .andExpect(jsonPath("$.minOrderAmount").isEmpty());
    }

    @Test
    void duplicateCodeIsAConflict() throws Exception {
        String code = "DUP" + suffix();
        create(code);
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code.toLowerCase(), "PERCENTAGE", "10", null, null, vendorId, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_coupon_code"));
    }

    @Test
    void listContainsCreatedCouponsAndDeactivateIsOneWay() throws Exception {
        String code = "LST" + suffix();
        String id = create(code).get("id").asText();

        JsonNode list = objectMapper.readTree(mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), ops))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsText("code")).contains(code);

        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", id), ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", id), ops))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_inactive"));
        mockMvc.perform(withBearer(patch("/api/v1/marketing/coupons/{id}/deactivate", UUID.randomUUID()), ops))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminAndOpsMayUseTheEndpoint() throws Exception {
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("ADMIN"))).andExpect(status().isOk());
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("VENDOR_ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(withBearer(get("/api/v1/marketing/coupons"), staffToken("FINANCE"))).andExpect(status().isForbidden());
    }

    @Test
    void missingRequiredFieldsAreValidationErrors() throws Exception {
        mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discountType\":\"PERCENTAGE\",\"discountValue\":10}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private JsonNode create(String code) throws Exception {
        String body = mockMvc.perform(withBearer(post("/api/v1/marketing/coupons"), ops).contentType(MediaType.APPLICATION_JSON)
                        .content(couponJson(code, "PERCENTAGE", "10", null, null, vendorId, "2026-01-01T00:00:00Z", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String couponJson(String code, String type, String value, String minOrder, Integer maxUses, UUID vendor,
                              String validFrom, String validUntil) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("discountType", type);
        body.put("discountValue", new java.math.BigDecimal(value));
        if (minOrder != null) body.put("minOrderAmount", new java.math.BigDecimal(minOrder));
        if (maxUses != null) body.put("maxUses", maxUses);
        if (vendor != null) body.put("vendorId", vendor.toString());
        body.put("validFrom", validFrom);
        if (validUntil != null) body.put("validUntil", validUntil);
        return objectMapper.writeValueAsString(body);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String staffToken(String role) {
        Map<String, Object> claims = new HashMap<>(Map.of("azp", "delivery-app", "tenant_id", tenant.toString()));
        claims.put("sub", role.toLowerCase() + "-" + UUID.randomUUID());
        claims.put("realm_access", Map.of("roles", List.of(role)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
