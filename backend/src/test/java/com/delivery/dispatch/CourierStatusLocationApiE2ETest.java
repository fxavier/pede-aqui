package com.delivery.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.TestParents;
import com.delivery.delivery.service.DeliveryService;
import com.delivery.dispatch.config.CourierLocationProperties;
import com.delivery.dispatch.service.CourierStatusService;
import com.delivery.dispatch.service.DispatchJobRetirement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W5-B (LAC-42, LAC-40) on the production wiring: courier status endpoints (new and deprecated), OPS suspend/reinstate,
 * location consent and ingestion with their error codes, request validation, the OPS zone view and its role gate,
 * the {@code courier-location} rate-limit group, and that the delivery/dispatch hooks are wired to the status service.
 */
class CourierStatusLocationApiE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CourierLocationProperties properties;
    @Autowired private DeliveryService deliveryService;
    @Autowired private DispatchJobRetirement dispatchJobRetirement;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID zone;
    private UUID courierId;
    private String courierToken;
    private String opsToken;

    @BeforeEach
    void seed() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 'MZN', now(), now())",
                tenant, "Tenant " + tenant, "slug-" + tenant);
        zone = TestParents.zone(jdbc, tenant, UUID.randomUUID());
        String sub = "courier-" + UUID.randomUUID();
        courierId = courier(sub);
        courierToken = token(sub, "COURIER");
        opsToken = token("ops-" + UUID.randomUUID(), "OPS");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM courier_location_history WHERE tenant_id = ?", tenant);
        jdbc.update("DELETE FROM courier_last_locations WHERE tenant_id = ?", tenant);
        jdbc.update("DELETE FROM couriers WHERE tenant_id = ?", tenant);
        // app_user_profiles / zones / tenant rows stay: audit_logs (append-only) reference this tenant.
    }

    @Test
    void courierGoesOnlineAndOfflineWithTheNewAndTheDeprecatedEndpoint() throws Exception {
        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.maxActiveDeliveries").value(1));

        mockMvc.perform(json(patch("/api/v1/couriers/me/availability"), courierToken, "{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{\"status\":\"BUSY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_courier_status"));
        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(put("/api/v1/couriers/me/status"), opsToken, "{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void opsSuspendBlocksTheCourierUntilReinstated() throws Exception {
        mockMvc.perform(json(patch("/api/v1/couriers/" + courierId + "/suspend"), opsToken, "{\"reason\":\"documentos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courier_suspended"));
        mockMvc.perform(json(patch("/api/v1/couriers/" + courierId + "/suspend"), opsToken, "{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/v1/couriers/" + courierId + "/suspend"), courierToken, "{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(patch("/api/v1/couriers/" + courierId + "/reinstate"), opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));
        mockMvc.perform(bearer(patch("/api/v1/couriers/" + courierId + "/reinstate"), opsToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courier_not_suspended"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE target_id = ? AND action IN ('COURIER_SUSPENDED','COURIER_REINSTATED')",
                Integer.class, courierId.toString())).isEqualTo(2);
    }

    @Test
    void locationsNeedConsentAndDutyThenAreAcceptedAndVisibleToOps() throws Exception {
        String body = "{\"points\":[" + point(-25.9692, 32.5732, 8) + "]}";
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("location_consent_required"));

        mockMvc.perform(bearer(get("/api/v1/couriers/me/location-consent"), courierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(properties.getConsentVersion()))
                .andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(json(put("/api/v1/couriers/me/location-consent"), courierToken, "{\"version\":\"old\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("location_consent_outdated"));
        mockMvc.perform(json(put("/api/v1/couriers/me/location-consent"), courierToken,
                        "{\"version\":\"" + properties.getConsentVersion() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("courier_off_duty"));

        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{\"status\":\"AVAILABLE\"}")).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.dropped").value(0));

        mockMvc.perform(bearer(get("/api/v1/ops/couriers/locations?zoneId=" + zone), opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courierId").value(courierId.toString()))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].latitude").value(-25.9692))
                .andExpect(jsonPath("$[0].stale").value(false));
        mockMvc.perform(bearer(get("/api/v1/ops/couriers/locations?zoneId=" + zone), courierToken))
                .andExpect(status().isForbidden());

        // Going offline deletes the latest position: OPS no longer sees the courier.
        mockMvc.perform(json(put("/api/v1/couriers/me/status"), courierToken, "{\"status\":\"OFFLINE\"}")).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/ops/couriers/locations?zoneId=" + zone), opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidPointsAndBatchSizesAreValidationErrors() throws Exception {
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, "{\"points\":[" + point(91, 32.5, 8) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, "{\"points\":[" + point(-25.9, 181, 8) + "]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, "{\"points\":[" + point(-25.9, 32.5, -1) + "]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, "{\"points\":[]}"))
                .andExpect(status().isBadRequest());
        StringBuilder many = new StringBuilder("{\"points\":[");
        for (int i = 0; i < 51; i++) many.append(i == 0 ? "" : ",").append(point(-25.9, 32.5, 8));
        mockMvc.perform(json(post("/api/v1/couriers/me/locations"), courierToken, many.append("]}").toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void courierLocationRateLimitGroupAppliesPerCourier() throws Exception {
        String sub = "courier-rl-" + UUID.randomUUID();
        courier(sub);
        String token = token(sub, "COURIER");
        String body = "{\"points\":[" + point(-25.9692, 32.5732, 8) + "]}";
        int firstLimitedAt = -1;
        for (int i = 0; i < 45 && firstLimitedAt < 0; i++) {
            int code = mockMvc.perform(json(post("/api/v1/couriers/me/locations"), token, body)).andReturn().getResponse().getStatus();
            if (code == 429) firstLimitedAt = i;
        }
        // Capacity 30 per minute (greedy refill may add a token or two while the loop runs).
        assertThat(firstLimitedAt).isBetween(30, 44);
    }

    @Test
    void deliveryAndRetirementHooksAreWiredToTheCourierStatusService() {
        Object deliveryListener = ReflectionTestUtils.getField((Object) AopTestUtils.getUltimateTargetObject(deliveryService), "courierListener");
        Object retirementListener = ReflectionTestUtils.getField((Object) AopTestUtils.getUltimateTargetObject(dispatchJobRetirement), "courierListener");
        assertThat(AopUtils.getTargetClass(deliveryListener)).isEqualTo(CourierStatusService.class);
        assertThat(AopUtils.getTargetClass(retirementListener)).isEqualTo(CourierStatusService.class);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    /** Approved, OFFLINE courier in the test tenant/zone for the Keycloak subject. */
    private UUID courier(String sub) {
        UUID profile = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'Courier', 'ACTIVE', now(), now())",
                profile, tenant, sub, profile + "@example.test");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO couriers (id, tenant_id, user_profile_id, operating_zone_id, verification_status, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'APPROVED', 'OFFLINE', now(), now())",
                id, tenant, profile, zone);
        return id;
    }

    private static String point(double lat, double lng, double accuracy) {
        return "{\"latitude\":" + lat + ",\"longitude\":" + lng + ",\"accuracyMeters\":" + accuracy
                + ",\"speedMps\":3.5,\"headingDegrees\":90,\"recordedAt\":\"" + Instant.now() + "\"}";
    }

    private String token(String subject, String... realmRoles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("tenant_id", tenant.toString());
        claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return bearer(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
