package com.delivery.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.PostgresApiE2ETestBase;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenancyProperties;
import com.delivery.privacy.service.AccountErasureService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W7-C (LAC-19/LAC-63) on the production wiring with real Postgres: profile settings (PATCH /me + Keycloak sync +
 * notification preferences), the account deletion lifecycle (request, idempotent repeat, cancel, active-order refusal,
 * role gates), the account-deletion job's pseudonymisation result field by field (and its postponement and Keycloak
 * retry), the personal-data export (content, audit, rate limit) and consents. Keycloak is the mocked admin client.
 */
class PrivacyApiE2ETest extends PostgresApiE2ETestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenancyProperties tenancyProperties;
    @Autowired private AccountErasureService erasureService;

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();
    private UUID tenant;
    private UUID vendor;
    private String sub;
    private UUID profile;
    private String token;

    @BeforeEach
    void seed() {
        reset(keycloakAdminService);
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            Jwt jwt = tokens.get(invocation.<String>getArgument(0));
            if (jwt == null) throw new BadJwtException("unknown test token");
            return jwt;
        });
        tenant = tenancyProperties.getOperatorTenantId();
        vendor = vendor();
        sub = UUID.randomUUID().toString();
        profile = profile(sub);
        token = customerToken(sub);
    }

    // --- profile settings ---

    @Test
    void profileSettingsArePatchedPartiallySyncedToKeycloakAndAudited() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/me/profile"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Maria"))
                .andExpect(jsonPath("$.preferredLocale").value("pt"))
                .andExpect(jsonPath("$.notificationPreferences.push").value(true))
                .andExpect(jsonPath("$.notificationPreferences.sms").value(false));

        mockMvc.perform(json(patch("/api/v1/me"), token,
                        "{\"displayName\":\"  Ana Sousa Machava \",\"preferredLocale\":\"pt-MZ\",\"notificationPreferences\":{\"sms\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Sousa Machava"))
                .andExpect(jsonPath("$.preferredLocale").value("pt-MZ"))
                .andExpect(jsonPath("$.notificationPreferences.push").value(true))
                .andExpect(jsonPath("$.notificationPreferences.sms").value(true))
                .andExpect(jsonPath("$.notificationPreferences.email").value(false));
        verify(keycloakAdminService).updateUserProfile(sub, "Ana", "Sousa Machava", "pt-MZ");
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_user_profiles WHERE id = ?", String.class, profile))
                .isEqualTo("Ana Sousa Machava");
        assertThat(jdbc.queryForObject("SELECT sms_enabled FROM user_notification_preferences WHERE user_profile_id = ?",
                Boolean.class, profile)).isTrue();
        assertThat(jdbc.queryForObject("SELECT details->>'fields' FROM audit_logs WHERE action = 'PROFILE_UPDATED' AND target_id = ?",
                String.class, profile.toString())).isEqualTo("displayName,preferredLocale,notificationPreferences");

        // Only preferences: Keycloak is not called again.
        reset(keycloakAdminService);
        mockMvc.perform(json(patch("/api/v1/me"), token, "{\"notificationPreferences\":{\"push\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationPreferences.push").value(false))
                .andExpect(jsonPath("$.notificationPreferences.sms").value(true));
        verify(keycloakAdminService, never()).updateUserProfile(any(), any(), any(), any());
    }

    @Test
    void profilePatchValidatesAndRollsBackWhenKeycloakFails() throws Exception {
        mockMvc.perform(json(patch("/api/v1/me"), token, "{\"preferredLocale\":\"fr-FR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_locale"));
        mockMvc.perform(json(patch("/api/v1/me"), token, "{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
        mockMvc.perform(json(patch("/api/v1/me"), token, "{\"displayName\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/v1/me"), null, "{\"displayName\":\"X\"}")).andExpect(status().isUnauthorized());

        doThrow(new BusinessException("keycloak_error", "down", HttpStatus.INTERNAL_SERVER_ERROR))
                .when(keycloakAdminService).updateUserProfile(eq(sub), anyString(), anyString(), isNull());
        mockMvc.perform(json(patch("/api/v1/me"), token, "{\"displayName\":\"Outro Nome\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("keycloak_error"));
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_user_profiles WHERE id = ?", String.class, profile))
                .isEqualTo("Ana Maria");
    }

    // --- account deletion lifecycle ---

    @Test
    void deletionRequestIsIdempotentCancellableAndGated() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/me/deletion-request"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("deletion_request_not_found"));
        mockMvc.perform(bearer(delete("/api/v1/me/deletion-request"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("deletion_request_not_found"));

        MvcResult created = mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        Instant requestedAt = Instant.parse(body.get("requestedAt").asText());
        Instant scheduledFor = Instant.parse(body.get("scheduledFor").asText());
        assertThat(java.time.Duration.between(requestedAt, scheduledFor)).isEqualTo(java.time.Duration.ofDays(30));

        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(body.get("id").asText()));
        mockMvc.perform(bearer(get("/api/v1/me/deletion-request"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(bearer(delete("/api/v1/me/deletion-request"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        mockMvc.perform(bearer(delete("/api/v1/me/deletion-request"), token)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action IN ('ACCOUNT_DELETION_REQUESTED','ACCOUNT_DELETION_CANCELLED') AND target_id = ?",
                body.get("id").asText())).isEqualTo(2);

        // A new request after a cancellation is a new row.
        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token)).andExpect(status().isAccepted());
        assertThat(count("SELECT count(*) FROM account_deletion_requests WHERE profile_id = ?", profile)).isEqualTo(2);

        // Gates: anonymous 401; staff 403 (role); a customer that is also a courier is not a customer principal → 403.
        mockMvc.perform(post("/api/v1/me/deletion-request")).andExpect(status().isUnauthorized());
        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), staffToken("OPS"))).andExpect(status().isForbidden());
        String mixedSub = UUID.randomUUID().toString();
        profile(mixedSub);
        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token(mixedSub, Map.of("azp", "pede-aqui-web",
                        "tenant_id", tenant.toString()), "CUSTOMER", "COURIER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_deletion_not_supported"));
    }

    @Test
    void deletionIsRefusedWhileAnOrderIsInProgress() throws Exception {
        UUID active = order(profile, "PREPARING");
        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("active_orders_present"));
        assertThat(count("SELECT count(*) FROM account_deletion_requests WHERE profile_id = ?", profile)).isZero();

        jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE id = ?", active);
        mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token)).andExpect(status().isAccepted());
    }

    @Test
    void jobPseudonymisesTheAccountAfterTheReflectionPeriodKeepingFiscalFields() throws Exception {
        UUID delivered = order(profile, "DELIVERED");
        jdbc.update("""
                INSERT INTO cancellation_requests (id, tenant_id, order_id, status, closure_reason, reason, requested_by, decided_by,
                    fee_rate, fee_amount, refund_amount, requested_at, decided_at, updated_at)
                VALUES (?, ?, ?, 'REJECTED', NULL, 'Mudei de ideias, liguem para 841234567', ?, 'ops', 0.10, 1.00, NULL, now(), now(), now())""",
                UUID.randomUUID(), tenant, delivered, profile);
        UUID otherCustomer = profile(UUID.randomUUID().toString());
        UUID otherCustomerOrder = order(otherCustomer, "DELIVERED");
        // W7-D ratings: the customer's comment is personal data; the vendor reply is not the customer's.
        UUID myRating = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ratings (id, tenant_id, order_id, rater_user_id, rated_vendor_id, stars, comment, vendor_reply,
                                     vendor_replied_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 4, 'Sou a Ana, liguem para 841234567', 'Obrigado pela preferência', now(), now(), now())""",
                myRating, tenant, delivered, profile, vendor);
        UUID otherRating = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ratings (id, tenant_id, order_id, rater_user_id, rated_vendor_id, stars, comment, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 5, 'Excelente', now(), now())""",
                otherRating, tenant, otherCustomerOrder, otherCustomer, vendor);
        seedPersonalData();
        jdbc.update("UPDATE app_user_profiles SET phone = '840000001', full_name = 'Ana Maria Machava', nif = ?, "
                + "phone_e164 = ?, phone_verified_at = now() WHERE id = ?", "1" + profile.toString().substring(0, 8), "+25884" + digits(7), profile);

        UUID requestId = requestDeletion();
        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.SKIPPED); // not due yet
        makeDue(requestId);

        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.COMPLETED);
        verify(keycloakAdminService).disableUser(sub);
        verify(keycloakAdminService).deleteUserStrict(sub);

        Map<String, Object> p = jdbc.queryForMap("SELECT * FROM app_user_profiles WHERE id = ?", profile);
        assertThat(p.get("email")).isEqualTo(UserAccounts.pseudonymisedEmail(profile));
        assertThat(p.get("display_name")).isEqualTo("Cliente removido");
        assertThat(p.get("status")).isEqualTo("DELETED");
        assertThat(p.get("keycloak_user_id")).isEqualTo(sub);
        for (String column : List.of("phone", "phone_e164", "phone_verified_at", "full_name", "nif", "date_of_birth", "address", "avatar_storage_key")) {
            assertThat(p.get(column)).as(column).isNull();
        }
        assertThat(count("SELECT count(*) FROM customer_addresses WHERE customer_id = ?", profile)).isZero();
        assertThat(count("SELECT count(*) FROM customer_favorite_vendors WHERE customer_id = ?", profile)).isZero();
        assertThat(count("SELECT count(*) FROM device_tokens WHERE user_profile_id = ?", profile)).isZero();
        assertThat(count("SELECT count(*) FROM notifications WHERE recipient_user_id = ?", profile)).isZero();
        assertThat(count("SELECT count(*) FROM user_notification_preferences WHERE user_profile_id = ?", profile)).isZero();

        Map<String, Object> o = jdbc.queryForMap("SELECT * FROM orders WHERE id = ?", delivered);
        for (String column : List.of("delivery_recipient_name", "delivery_phone", "delivery_line1", "delivery_line2", "delivery_postal_code",
                "delivery_latitude", "delivery_longitude", "delivery_instructions", "delivery_address_id")) {
            assertThat(o.get(column)).as(column).isNull();
        }
        assertThat(o.get("delivery_city")).isEqualTo("Maputo");
        assertThat(o.get("customer_id")).isEqualTo(profile);
        assertThat(o.get("total").toString()).isEqualTo("100.00");
        assertThat(o.get("reference").toString()).startsWith("PRIV-");
        assertThat(jdbc.queryForObject("SELECT reason FROM cancellation_requests WHERE order_id = ?", String.class, delivered)).isNull();
        // Rating kept (stars still count, FK to the pseudonymised profile intact), only the customer's comment cleared.
        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM ratings WHERE id = ?", myRating);
        assertThat(r.get("comment")).isNull();
        assertThat(r).containsEntry("rater_user_id", profile).containsEntry("stars", 4)
                .containsEntry("vendor_reply", "Obrigado pela preferência");
        assertThat(jdbc.queryForObject("SELECT comment FROM ratings WHERE id = ?", String.class, otherRating)).isEqualTo("Excelente");
        assertThat(count("SELECT count(*) FROM app_user_profiles WHERE id = ?", profile)).isEqualTo(1);
        // Another customer's order is untouched, and the snapshot stays immutable outside the erasure flag.
        assertThat(jdbc.queryForObject("SELECT delivery_line1 FROM orders WHERE id = ?", String.class, otherCustomerOrder)).isNotNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET delivery_line1 = 'x' WHERE id = ?", otherCustomerOrder))
                .hasMessageContaining("immutable");

        assertThat(jdbc.queryForObject("SELECT status FROM account_deletion_requests WHERE id = ?", String.class, requestId)).isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ACCOUNT_PSEUDONYMISED' AND target_id = ?", profile.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT details->>'addressesDeleted' FROM audit_logs WHERE action = 'ACCOUNT_PSEUDONYMISED' AND target_id = ?",
                String.class, profile.toString())).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT details->>'ratingCommentsCleared' FROM audit_logs WHERE action = 'ACCOUNT_PSEUDONYMISED' AND target_id = ?",
                String.class, profile.toString())).isEqualTo("1");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ACCOUNT_DELETION_COMPLETED' AND target_id = ?", requestId.toString())).isEqualTo(1);

        // Idempotent: a second run does nothing; the deleted subject's token no longer reaches its profile.
        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.SKIPPED);
        mockMvc.perform(bearer(get("/api/v1/me/profile"), token)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM app_user_profiles WHERE keycloak_user_id = ?", sub)).isEqualTo(1);
    }

    @Test
    void jobPostponesWhileAnOrderIsActiveAndRetriesAFailedKeycloakDeletion() throws Exception {
        UUID requestId = requestDeletion();
        makeDue(requestId);
        UUID active = order(profile, "PAYMENT_CONFIRMED");

        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.POSTPONED);
        Map<String, Object> r = jdbc.queryForMap("SELECT status, attempts, last_error FROM account_deletion_requests WHERE id = ?", requestId);
        assertThat(r).containsEntry("status", "PENDING").containsEntry("attempts", 1).containsEntry("last_error", "active_orders_present");
        verify(keycloakAdminService, never()).disableUser(anyString());

        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", active);
        doThrow(new BusinessException("keycloak_error", "down", HttpStatus.INTERNAL_SERVER_ERROR))
                .when(keycloakAdminService).deleteUserStrict(sub);
        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.FAILED);
        r = jdbc.queryForMap("SELECT status, attempts, last_error FROM account_deletion_requests WHERE id = ?", requestId);
        assertThat(r).containsEntry("status", "ERASED").containsEntry("attempts", 2).containsEntry("last_error", "keycloak_error");
        assertThat(jdbc.queryForObject("SELECT status FROM app_user_profiles WHERE id = ?", String.class, profile)).isEqualTo("DELETED");
        // The pseudonymised customer can no longer cancel.
        mockMvc.perform(bearer(delete("/api/v1/me/deletion-request"), token)).andExpect(status().isNotFound());

        reset(keycloakAdminService);
        assertThat(erasureService.process(requestId)).isEqualTo(AccountErasureService.Outcome.COMPLETED);
        verify(keycloakAdminService, never()).disableUser(anyString());
        verify(keycloakAdminService).deleteUserStrict(sub);
    }

    @Test
    void processDueRecordsARunRow() {
        long before = count("SELECT count(*) FROM data_retention_runs WHERE job = 'account-deletion'", null);
        erasureService.processDue();
        assertThat(count("SELECT count(*) FROM data_retention_runs WHERE job = 'account-deletion'", null)).isEqualTo(before + 1);
    }

    // --- export ---

    @Test
    void exportContainsOnlyTheSubjectsDataIsAuditedAndRateLimited() throws Exception {
        order(profile, "DELIVERED");
        order(profile(UUID.randomUUID().toString()), "DELIVERED");
        seedPersonalData();
        mockMvc.perform(json(put("/api/v1/me/consents/PRIVACY_POLICY"), token, "{\"version\":\"2026-10\"}")).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(bearer(get("/api/v1/me/data-export"), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"pede-aqui-dados-")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        JsonNode export = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(export.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(export.at("/profile/id").asText()).isEqualTo(profile.toString());
        assertThat(export.at("/profile/email").asText()).isEqualTo(sub + "@example.test");
        assertThat(export.get("addresses")).hasSize(1);
        assertThat(export.at("/addresses/0/line1").asText()).isEqualTo("Rua da Resistência 10");
        assertThat(export.get("orders")).hasSize(1);
        assertThat(export.at("/orders/0/vendorName").asText()).isEqualTo("Loja Privacidade");
        assertThat(export.get("favouriteVendorIds")).hasSize(1);
        assertThat(export.get("registeredPushDevices").asInt()).isEqualTo(1);
        assertThat(export.toString()).doesNotContain("fcm-privacy-token");
        assertThat(export.get("consents")).hasSize(1);
        assertThat(export.at("/consents/0/purpose").asText()).isEqualTo("PRIVACY_POLICY");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'PERSONAL_DATA_EXPORTED' AND target_id = ?", profile.toString())).isEqualTo(1);

        mockMvc.perform(bearer(get("/api/v1/me/data-export"), token)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/me/data-export"), token)).andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/me/data-export"), token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        mockMvc.perform(get("/api/v1/me/data-export")).andExpect(status().isUnauthorized());
    }

    // --- consents ---

    @Test
    void consentsAreVersionedAndWithdrawable() throws Exception {
        mockMvc.perform(bearer(get("/api/v1/me/consents"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.purpose=='MARKETING')].active").value(false));
        mockMvc.perform(json(put("/api/v1/me/consents/MARKETING"), token, "{\"version\":\"2020-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("consent_version_outdated"));
        mockMvc.perform(json(put("/api/v1/me/consents/NEWSLETTER"), token, "{\"version\":\"2026-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_consent_purpose"));
        mockMvc.perform(json(put("/api/v1/me/consents/MARKETING"), token, "{\"version\":\"2026-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(json(put("/api/v1/me/consents/MARKETING"), token, "{\"version\":\"2026-10\"}")).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM consents WHERE subject_profile_id = ?", profile)).isEqualTo(1);

        mockMvc.perform(bearer(delete("/api/v1/me/consents/MARKETING"), token)).andExpect(status().isNoContent());
        mockMvc.perform(bearer(delete("/api/v1/me/consents/MARKETING"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("consent_not_found"));
        assertThat(count("SELECT count(*) FROM consents WHERE subject_profile_id = ? AND withdrawn_at IS NOT NULL", profile)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action IN ('CONSENT_GRANTED','CONSENT_WITHDRAWN') AND details->>'profileId' = ?",
                profile.toString())).isEqualTo(2);
    }

    // --- helpers ---

    private UUID requestDeletion() throws Exception {
        MvcResult created = mockMvc.perform(bearer(post("/api/v1/me/deletion-request"), token))
                .andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
    }

    private void makeDue(UUID requestId) {
        jdbc.update("UPDATE account_deletion_requests SET requested_at = now() - interval '31 days', scheduled_for = now() - interval '1 day' WHERE id = ?",
                requestId);
    }

    private void seedPersonalData() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO customer_addresses (id, tenant_id, customer_id, label, recipient_name, phone, line1, line2, city, state,
                                                postal_code, latitude, longitude, delivery_instructions, created_at, updated_at)
                VALUES (?, ?, ?, 'Casa', 'Ana', '841112223', 'Rua da Resistência 10', NULL, 'Maputo', 'Maputo', '1100', -25.96, 32.58, 'Portão azul', ?, ?)""",
                UUID.randomUUID(), tenant, profile, now, now);
        jdbc.update("INSERT INTO customer_favorite_vendors (id, tenant_id, customer_id, vendor_id, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, profile, vendor, now);
        String deviceToken = "fcm-privacy-token-" + UUID.randomUUID();
        jdbc.update("INSERT INTO device_tokens (id, tenant_id, user_profile_id, platform, token, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, 'ANDROID', ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, profile, deviceToken, UUID.randomUUID().toString().replace("-", ""), now, now);
        jdbc.update("INSERT INTO notifications (id, tenant_id, recipient_user_id, recipient_role, type, title, message, created_at) VALUES (?, ?, ?, 'CUSTOMER', 'order_delivered', 'Entregue', 'A sua encomenda foi entregue', ?)",
                UUID.randomUUID(), tenant, profile, now);
        jdbc.update("INSERT INTO user_notification_preferences (id, tenant_id, user_profile_id, push_enabled, sms_enabled, email_enabled, created_at, updated_at) VALUES (?, ?, ?, TRUE, TRUE, FALSE, ?, ?)",
                UUID.randomUUID(), tenant, profile, now, now);
    }

    private UUID vendor() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO vendors (id, tenant_id, name, status, verification_status, created_at, updated_at) VALUES (?, ?, 'Loja Privacidade', 'ACTIVE', 'APPROVED', ?, ?)",
                id, tenant, now, now);
        return id;
    }

    private UUID profile(String keycloakUserId) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO app_user_profiles (id, tenant_id, keycloak_user_id, email, display_name, status, preferred_language, created_at, updated_at) VALUES (?, ?, ?, ?, 'Ana Maria', 'ACTIVE', 'pt', ?, ?)",
                id, tenant, keycloakUserId, keycloakUserId + "@example.test", now, now);
        jdbc.update("INSERT INTO app_user_profile_roles (app_user_profile_id, role) VALUES (?, 'CUSTOMER')", id);
        return id;
    }

    private UUID order(UUID customerId, String status) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO orders (id, tenant_id, reference, customer_id, vendor_id, status, subtotal, fees, taxes,
                                    discounts, discount_total, total, checkout_idempotency_key, delivery_confirmation_code_hash,
                                    delivery_recipient_name, delivery_phone, delivery_line1, delivery_line2, delivery_city,
                                    delivery_state, delivery_postal_code, delivery_latitude, delivery_longitude, delivery_instructions,
                                    delivery_address_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 90, 10, 0, 0, 0, 100, ?, 'hash', 'Ana', '+258840000000', 'Av. Julius Nyerere 100',
                        'Flat 2', 'Maputo', 'Maputo', '1100', -25.97, 32.57, 'Tocar à campainha', ?, ?, ?)""",
                id, tenant, "PRIV-" + id.toString().substring(0, 8), customerId, vendor, status, "priv-" + id, UUID.randomUUID(), ts, ts);
        return id;
    }

    private long count(String sql, Object arg) {
        Long value = arg == null ? jdbc.queryForObject(sql, Long.class) : jdbc.queryForObject(sql, Long.class, arg);
        return value == null ? 0 : value;
    }

    private static String digits(int n) {
        StringBuilder out = new StringBuilder();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) out.append(random.nextInt(10));
        return out.toString();
    }

    private String customerToken(String subject) {
        return token(subject, Map.of("azp", "pede-aqui-web"), "CUSTOMER");
    }

    private String staffToken(String role) {
        return token(role.toLowerCase() + "-" + UUID.randomUUID(), Map.of("azp", "delivery-app", "tenant_id", tenant.toString()), role);
    }

    private String token(String subject, Map<String, Object> extraClaims, String... realmRoles) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("email", subject + "@example.test");
        claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
        String value = "privacy-token-" + UUID.randomUUID();
        tokens.put(value, new Jwt(value, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), claims));
        return value;
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return bearer(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
