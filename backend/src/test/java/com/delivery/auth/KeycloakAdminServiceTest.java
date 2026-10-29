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

    private void expectCreateUser(HttpStatus status, String body) {
        server.expect(requestTo(KC + "/admin/realms/delivery/users")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }
}
