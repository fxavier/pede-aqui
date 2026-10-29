package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.service.CustomerProfileProvisioningService;
import com.delivery.auth.service.CustomerProfileProvisioningService.PhoneSync;
import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.security.TenancyProperties;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LAC-18 backend part (W5-D, ADR-0004): a customer token from the Keycloak phone login carries
 * {@code phone_number} + {@code phone_number_verified}; the TenantRequestFilter mirrors the verified number onto
 * the customer profile (V063) and {@code GET /me} exposes it. Real Postgres + production security chain; only the
 * JwtDecoder is stubbed.
 */
class CustomerVerifiedPhoneE2ETest extends PostgresApiE2ETestBase {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private CustomerProfileProvisioningService provisioningService;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID operatorTenant;

    @BeforeEach
    void stubDecoder() {
        operatorTenant = tenancyProperties.getOperatorTenantId();
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
    }

    /** A random valid Mozambican mobile, so reruns against a reused database never collide. */
    private static String randomPhone() {
        return String.format("+25884%07d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000L));
    }

    @Test
    void verifiedPhoneClaimIsStoredOnTheProfileAndReturnedByMe() throws Exception {
        String sub = UUID.randomUUID().toString();
        String phone = randomPhone();
        String token = phoneToken(sub, phone, true);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneE164").value(phone))
                .andExpect(jsonPath("$.phoneVerifiedAt").isNotEmpty());

        Map<String, Object> row = profileRow(sub);
        assertThat(row.get("phone_e164")).isEqualTo(phone);
        assertThat(row.get("phone_verified_at")).isNotNull();
        // Phone-only customers have no email claim: the profile falls back to the subject (column is NOT NULL).
        assertThat(row.get("email")).isEqualTo(sub);
    }

    @Test
    void sameNumberAgainIsUnchangedAndKeepsTheFirstVerificationInstant() throws Exception {
        String sub = UUID.randomUUID().toString();
        String phone = randomPhone();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + phoneToken(sub, phone, true)))
                .andExpect(status().isOk());
        Object firstVerifiedAt = profileRow(sub).get("phone_verified_at");

        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, sub, phone, true)).isEqualTo(PhoneSync.UNCHANGED);
        assertThat(profileRow(sub).get("phone_verified_at")).isEqualTo(firstVerifiedAt);
    }

    @Test
    void unverifiedOrInvalidClaimsAreIgnored() throws Exception {
        String sub = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + phoneToken(sub, randomPhone(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneE164").doesNotExist());
        assertThat(profileRow(sub).get("phone_e164")).isNull();

        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, sub, "841234567", true)).isEqualTo(PhoneSync.INVALID);
        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, sub, "+258211234567", true)).isEqualTo(PhoneSync.INVALID);
        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, sub, randomPhone(), null)).isEqualTo(PhoneSync.NOT_VERIFIED);
        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, UUID.randomUUID().toString(), randomPhone(), true))
                .isEqualTo(PhoneSync.NO_PROFILE);
        assertThat(profileRow(sub).get("phone_e164")).isNull();
    }

    @Test
    void numberAlreadyOnAnotherProfileOfTheTenantIsNotLinkedAndTheRequestStillSucceeds() throws Exception {
        String phone = randomPhone();
        String owner = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + phoneToken(owner, phone, true)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + phoneToken(other, phone, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneE164").doesNotExist());

        assertThat(profileRow(owner).get("phone_e164")).isEqualTo(phone);
        assertThat(profileRow(other).get("phone_e164")).isNull();
        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, other, phone, true)).isEqualTo(PhoneSync.TAKEN_IN_TENANT);
    }

    @Test
    void changedNumberReplacesTheOldOne() throws Exception {
        String sub = UUID.randomUUID().toString();
        String first = randomPhone();
        String second = String.format("+25886%07d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 10_000_000L));
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + phoneToken(sub, first, true))).andExpect(status().isOk());

        assertThat(provisioningService.syncVerifiedPhone(operatorTenant, sub, second, true)).isEqualTo(PhoneSync.UPDATED);
        assertThat(profileRow(sub).get("phone_e164")).isEqualTo(second);
    }

    @Test
    void databaseEnforcesE164AndUniquenessPerTenant() {
        UUID tenant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id, name, slug, status, default_currency, created_at, updated_at) VALUES (?, 'T', ?, 'ACTIVE', 'MZN', ?, ?)",
                tenant, "phone-" + tenant, now, now);
        String phone = randomPhone();
        insertProfile(tenant, phone, now);

        assertThatThrownBy(() -> insertProfile(tenant, phone, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProfile(tenant, "841234567", now)).isInstanceOf(DataIntegrityViolationException.class);
        // Same number in another tenant is allowed (unique per tenant); NULLs never collide.
        insertProfile(operatorTenant, phone, now);
        insertProfile(tenant, null, now);
        insertProfile(tenant, null, now);
    }

    private void insertProfile(UUID tenant, String phone, Timestamp now) {
        jdbc.update("""
                INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, preferred_language,
                                               created_at, updated_at, phone_e164, phone_verified_at)
                VALUES (?, ?, ?, 'x@example.test', 'X', 'ACTIVE', 'pt', ?, ?, ?, ?)""",
                UUID.randomUUID(), tenant, UUID.randomUUID().toString(), now, now, phone, phone == null ? null : now);
    }

    private Map<String, Object> profileRow(String sub) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT email, phone_e164, phone_verified_at FROM app_user_profiles WHERE tenant_id = ? AND keycloak_user_id = ?",
                operatorTenant, sub);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    /** What Keycloak issues after a phone login on pede-aqui-web: CUSTOMER, no email, no tenant_id. */
    private String phoneToken(String subject, String phone, boolean verified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("azp", "pede-aqui-web");
        claims.put("name", "Ana Mussa");
        claims.put("preferred_username", "phone-" + subject);
        claims.put("phone_number", phone);
        claims.put("phone_number_verified", verified);
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER")));
        String value = "test-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }
}
