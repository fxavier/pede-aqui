package com.delivery.auth.service;

import com.delivery.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thin client of the Keycloak admin REST API: user creation, role mapping, profile sync, disable and deletion. */
@Service
public class KeycloakAdminService {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final RestTemplate restTemplate;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminService(
            RestTemplate restTemplate,
            @Value("${app.keycloak.admin.server-url}") String serverUrl,
            @Value("${app.keycloak.admin.realm}") String realm,
            @Value("${app.keycloak.admin.client-id}") String clientId,
            @Value("${app.keycloak.admin.client-secret}") String clientSecret) {
        this.restTemplate = restTemplate;
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Creates a vendor admin user with tenant_id attribute and VENDOR_ADMIN role. */
    public String createUser(String email, String firstName, String lastName, String password, String tenantId) {
        try {
            String adminToken = getAdminToken();
            String userId = createKeycloakUser(adminToken, email, firstName, lastName, password, tenantId);
            assignRole(adminToken, userId, "VENDOR_ADMIN");
            return userId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create Keycloak user", e);
            throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Creates a customer user (no tenant) with CUSTOMER role. */
    public String createCustomer(String email, String firstName, String lastName, String password) {
        try {
            String adminToken = getAdminToken();
            String userId = createKeycloakUser(adminToken, email, firstName, lastName, password, null);
            assignRole(adminToken, userId, "CUSTOMER");
            return userId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create Keycloak customer", e);
            throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Adds realm roles to an existing Keycloak user (LAC-13), so the roles stored on the profile are also issued
     * in the user's tokens. Idempotent: re-adding a role the user already has is a no-op in Keycloak.
     */
    public void assignRealmRoles(String userId, Collection<String> roleNames) {
        try {
            String adminToken = getAdminToken();
            for (String roleName : roleNames) {
                assignRole(adminToken, userId, roleName);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException("keycloak_user_not_found", "Keycloak user or role was not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Failed to assign realm roles to Keycloak user {}: {}", userId, e.getMessage());
            throw new BusinessException("keycloak_error", "Failed to assign roles in Keycloak", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getAdminToken() {
        String url = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(url, request, TokenResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("keycloak_error", "Failed to get admin token", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response.getBody().accessToken();
    }

    private String createKeycloakUser(String adminToken, String email, String firstName, String lastName, String password, String tenantId) {
        String url = serverUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", email);
        userRequest.put("email", email);
        userRequest.put("firstName", firstName);
        userRequest.put("lastName", lastName);
        userRequest.put("enabled", true);
        if (tenantId != null) {
            userRequest.put("attributes", Map.of("tenant_id", List.of(tenantId)));
        }
        userRequest.put("credentials", List.of(Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        )));

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRequest, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

            if (response.getStatusCode() != HttpStatus.CREATED) {
                throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            String location = response.getHeaders().getLocation().toString();
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                logger.warn("User creation failed due to conflict (user already exists)");
                throw new BusinessException("email_exists", "User with this email already exists", HttpStatus.CONFLICT);
            }
            if (isPasswordPolicyRejection(e)) {
                // Realm passwordPolicy rejected the credential (e.g. policy changed before the DTO rules were synced).
                logger.warn("Keycloak rejected a new user's password by the realm password policy");
                throw new BusinessException("password_policy_violation",
                        "Password does not meet the password policy: at least 10 characters, one digit, one upper-case "
                                + "and one lower-case letter, and not equal to the e-mail",
                        HttpStatus.BAD_REQUEST);
            }
            logger.error("Keycloak user creation failed with status {}", e.getStatusCode());
            throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Keycloak answers 400 with an error such as {@code invalidPasswordMinLengthMessage} or
     * "Password policy not met" when the credential violates the realm passwordPolicy.
     */
    static boolean isPasswordPolicyRejection(HttpClientErrorException e) {
        if (e.getStatusCode() != HttpStatus.BAD_REQUEST) {
            return false;
        }
        String body = e.getResponseBodyAsString().toLowerCase(java.util.Locale.ROOT);
        return body.contains("invalidpassword") || body.contains("password policy");
    }

    private void assignRole(String adminToken, String userId, String roleName) {
        String getRoleUrl = serverUrl + "/admin/realms/" + realm + "/roles/" + roleName;

        HttpHeaders getRoleHeaders = new HttpHeaders();
        getRoleHeaders.setBearerAuth(adminToken);

        HttpEntity<Void> getRoleRequest = new HttpEntity<>(getRoleHeaders);
        ResponseEntity<RoleResponse> roleResponse = restTemplate.exchange(getRoleUrl, HttpMethod.GET, getRoleRequest, RoleResponse.class);

        if (!roleResponse.getStatusCode().is2xxSuccessful() || roleResponse.getBody() == null) {
            throw new BusinessException("keycloak_error", "Failed to get role: " + roleName, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String assignRoleUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders assignRoleHeaders = new HttpHeaders();
        assignRoleHeaders.setContentType(MediaType.APPLICATION_JSON);
        assignRoleHeaders.setBearerAuth(adminToken);

        List<Map<String, String>> roleAssignment = List.of(Map.of(
                "id", roleResponse.getBody().id(),
                "name", roleName
        ));

        HttpEntity<List<Map<String, String>>> assignRoleRequest = new HttpEntity<>(roleAssignment, assignRoleHeaders);
        ResponseEntity<Void> assignResponse = restTemplate.postForEntity(assignRoleUrl, assignRoleRequest, Void.class);

        if (!assignResponse.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException("keycloak_error", "Failed to assign role: " + roleName, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Adds the UPDATE_PASSWORD required action and triggers the execute-actions onboarding email
     * so a provisioned owner sets their own password. Best-effort: failures are logged, not thrown,
     * because the tenant is already provisioned and the email can be resent from Keycloak.
     */
    public void sendPasswordSetupEmail(String userId) {
        try {
            String adminToken = getAdminToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(adminToken);

            String userUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId;
            Map<String, Object> update = Map.of("requiredActions", List.of("UPDATE_PASSWORD"));
            restTemplate.exchange(userUrl, HttpMethod.PUT, new HttpEntity<>(update, headers), Void.class);

            String emailUrl = userUrl + "/execute-actions-email";
            restTemplate.exchange(emailUrl, HttpMethod.PUT,
                    new HttpEntity<>(List.of("UPDATE_PASSWORD"), headers), Void.class);
        } catch (Exception e) {
            logger.warn("Failed to send password-setup email for Keycloak user {} - resend manually from Keycloak: {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Mirrors self-service profile changes (W7-C) onto the Keycloak user: first/last name and the {@code locale}
     * attribute. Reads the full representation first and writes it back merged, because a PUT with a partial
     * {@code attributes} map would drop the others (tenant_id, phone_e164, ...). Null arguments leave the field as is.
     */
    public void updateUserProfile(String userId, String firstName, String lastName, String locale) {
        try {
            String adminToken = getAdminToken();
            Map<String, Object> user = getUserRepresentation(adminToken, userId);
            if (firstName != null) {
                user.put("firstName", firstName);
            }
            if (lastName != null) {
                user.put("lastName", lastName);
            }
            if (locale != null) {
                user.put("attributes", withAttribute(user.get("attributes"), "locale", locale));
            }
            putUserRepresentation(adminToken, userId, user);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException("keycloak_user_not_found", "Keycloak user was not found", HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Failed to update the Keycloak profile of user {}: {}", userId, e.getClass().getSimpleName());
            throw new BusinessException("keycloak_error", "Failed to update the profile in Keycloak", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Disables the Keycloak user (account deletion, W7-C) so no new login or token refresh succeeds. Returns false when
     * the user no longer exists (already deleted); any other failure throws {@code keycloak_error}.
     */
    public boolean disableUser(String userId) {
        try {
            String adminToken = getAdminToken();
            Map<String, Object> user = getUserRepresentation(adminToken, userId);
            user.put("enabled", false);
            putUserRepresentation(adminToken, userId, user);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to disable Keycloak user {}: {}", userId, e.getClass().getSimpleName());
            throw new BusinessException("keycloak_error", "Failed to disable the Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes the Keycloak user and reports failures (account deletion, W7-C), unlike the best-effort
     * {@link #deleteUser}. A user that is already gone (404) counts as deleted.
     */
    public void deleteUserStrict(String userId) {
        try {
            String adminToken = getAdminToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            restTemplate.exchange(userUrl(userId), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        } catch (HttpClientErrorException.NotFound alreadyGone) {
            // idempotent
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to delete Keycloak user {}: {}", userId, e.getClass().getSimpleName());
            throw new BusinessException("keycloak_error", "Failed to delete the Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String userUrl(String userId) {
        return serverUrl + "/admin/realms/" + realm + "/users/" + userId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getUserRepresentation(String adminToken, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<Map> response = restTemplate.exchange(userUrl(userId), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException("keycloak_error", "Failed to read the Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new HashMap<>((Map<String, Object>) response.getBody());
    }

    private void putUserRepresentation(String adminToken, String userId, Map<String, Object> user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        restTemplate.exchange(userUrl(userId), HttpMethod.PUT, new HttpEntity<>(user, headers), Void.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> withAttribute(Object attributes, String name, String value) {
        Map<String, Object> merged = attributes instanceof Map<?, ?> existing
                ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
        merged.put(name, List.of(value));
        return merged;
    }

    public void deleteUser(String userId) {
        try {
            String adminToken = getAdminToken();
            String url = serverUrl + "/admin/realms/" + realm + "/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);
        } catch (Exception e) {
            logger.error("Failed to delete user with ID: {}: {}", userId, e.getClass().getSimpleName());
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}
    private record RoleResponse(String id, String name) {}
}
