package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Maps Keycloak admin-API failures on user creation to the API error contract. */
class KeycloakAdminServiceTest {
    private static final String KC = "http://kc.test";
    private MockRestServiceServer server;
    private KeycloakAdminService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new KeycloakAdminService(restTemplate, KC, "delivery", "delivery-admin-service", "secret");
        server.expect(requestTo(KC + "/realms/delivery/protocol/openid-connect/token")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"adm\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void passwordPolicyRejectionBecomes400PasswordPolicyViolation() {
        expectCreateUser(HttpStatus.BAD_REQUEST,
                "{\"error\":\"invalidPasswordMinLengthMessage\",\"error_description\":\"Invalid password: minimum length 10.\"}");
        assertThatThrownBy(() -> service.createCustomer("ana@example.test", "Ana", "M", "short"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("password_policy_violation");
                    org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void legacyPolicyNotMetMessageIsAlsoRecognised() {
        expectCreateUser(HttpStatus.BAD_REQUEST, "{\"errorMessage\":\"Password policy not met\"}");
        assertThatThrownBy(() -> service.createUser("ana@example.test", "Ana", "M", "short", "t-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("password_policy_violation"));
    }

    @Test
    void otherBadRequestStaysKeycloakError() {
        expectCreateUser(HttpStatus.BAD_REQUEST, "{\"errorMessage\":\"error-invalid-email\"}");
        assertThatThrownBy(() -> service.createCustomer("ana@example.test", "Ana", "M", "Segredo1234"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("keycloak_error");
                    org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    @Test
    void conflictStaysEmailExists() {
        expectCreateUser(HttpStatus.CONFLICT, "{\"errorMessage\":\"User exists with same email\"}");
        assertThatThrownBy(() -> service.createCustomer("ana@example.test", "Ana", "M", "Segredo1234"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("email_exists"));
    }

    @Test
    void profileUpdateMergesIntoTheFullRepresentationKeepingOtherAttributes() {
        String user = KC + "/admin/realms/delivery/users/u-1";
        server.expect(requestTo(user)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"u-1\",\"username\":\"ana\",\"firstName\":\"Old\",\"enabled\":true,"
                        + "\"attributes\":{\"tenant_id\":[\"t-1\"],\"phone_e164\":[\"+258840000000\"]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(user)).andExpect(method(HttpMethod.PUT))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.firstName").value("Ana"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.lastName").value("Sousa"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.attributes.locale[0]").value("pt-MZ"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.attributes.tenant_id[0]").value("t-1"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.attributes.phone_e164[0]").value("+258840000000"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.enabled").value(true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.updateUserProfile("u-1", "Ana", "Sousa", "pt-MZ");
        server.verify();
    }

    @Test
    void disableReturnsFalseForAnAlreadyDeletedUser() {
        server.expect(requestTo(KC + "/admin/realms/delivery/users/gone")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        org.assertj.core.api.Assertions.assertThat(service.disableUser("gone")).isFalse();
    }

    @Test
    void disableWritesEnabledFalse() {
        String user = KC + "/admin/realms/delivery/users/u-2";
        server.expect(requestTo(user)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"u-2\",\"enabled\":true,\"attributes\":{\"tenant_id\":[\"t\"]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(user)).andExpect(method(HttpMethod.PUT))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.enabled").value(false))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.attributes.tenant_id[0]").value("t"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        org.assertj.core.api.Assertions.assertThat(service.disableUser("u-2")).isTrue();
        server.verify();
    }

    @Test
    void strictDeleteTreats404AsDone() {
        server.expect(requestTo(KC + "/admin/realms/delivery/users/gone")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        service.deleteUserStrict("gone");
        server.verify();
    }

    @Test
    void strictDeleteReportsOtherFailures() {
        server.expect(requestTo(KC + "/admin/realms/delivery/users/u-3")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> service.deleteUserStrict("u-3"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("keycloak_error"));
    }

    private void expectCreateUser(HttpStatus status, String body) {
        server.expect(requestTo(KC + "/admin/realms/delivery/users")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }
}
