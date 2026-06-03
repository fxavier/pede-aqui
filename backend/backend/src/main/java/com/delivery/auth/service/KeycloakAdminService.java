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

import java.util.List;
import java.util.Map;

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

    public String createUser(String email, String firstName, String lastName, String password, String tenantId) {
        try {
            String adminToken = getAdminToken();
            String userId = createKeycloakUser(adminToken, email, firstName, lastName, password, tenantId);
            assignVendorAdminRole(adminToken, userId);
            return userId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create Keycloak user for email: {}", email, e);
            throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
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

        Map<String, Object> userRequest = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "attributes", Map.of("tenant_id", List.of(tenantId)),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                ))
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRequest, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

            if (response.getStatusCode() != HttpStatus.CREATED) {
                throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Extract user ID from Location header
            String location = response.getHeaders().getLocation().toString();
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Keycloak returns 409 when username/email already exists
                logger.warn("User creation failed due to conflict (user already exists): {}", email);
                throw new BusinessException("email_exists", "User with this email already exists", HttpStatus.CONFLICT);
            }
            // Re-throw other HTTP client errors as generic keycloak errors
            logger.error("Keycloak user creation failed with status {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException("keycloak_error", "Failed to create Keycloak user", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void assignVendorAdminRole(String adminToken, String userId) {
        // First, get the VENDOR_ADMIN role
        String getRoleUrl = serverUrl + "/admin/realms/" + realm + "/roles/VENDOR_ADMIN";

        HttpHeaders getRoleHeaders = new HttpHeaders();
        getRoleHeaders.setBearerAuth(adminToken);

        HttpEntity<Void> getRoleRequest = new HttpEntity<>(getRoleHeaders);
        ResponseEntity<RoleResponse> roleResponse = restTemplate.exchange(getRoleUrl, HttpMethod.GET, getRoleRequest, RoleResponse.class);

        if (!roleResponse.getStatusCode().is2xxSuccessful() || roleResponse.getBody() == null) {
            throw new BusinessException("keycloak_error", "Failed to get VENDOR_ADMIN role", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Assign the role to the user
        String assignRoleUrl = serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

        HttpHeaders assignRoleHeaders = new HttpHeaders();
        assignRoleHeaders.setContentType(MediaType.APPLICATION_JSON);
        assignRoleHeaders.setBearerAuth(adminToken);

        List<Map<String, String>> roleAssignment = List.of(Map.of(
                "id", roleResponse.getBody().id(),
                "name", "VENDOR_ADMIN"
        ));

        HttpEntity<List<Map<String, String>>> assignRoleRequest = new HttpEntity<>(roleAssignment, assignRoleHeaders);
        ResponseEntity<Void> assignResponse = restTemplate.postForEntity(assignRoleUrl, assignRoleRequest, Void.class);

        if (!assignResponse.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException("keycloak_error", "Failed to assign VENDOR_ADMIN role", HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
            logger.error("Failed to delete user with ID: {}", userId, e);
            // Best-effort cleanup: log error but do not throw exception
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}
    private record RoleResponse(String id, String name) {}
}