package com.delivery.auth.service;

import com.delivery.auth.dto.CustomerRegistrationRequest;
import com.delivery.auth.dto.CustomerRegistrationResponse;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenancyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Handles customer self-registration: creates the Keycloak user (CUSTOMER role, no tenant_id attribute)
 * and provisions its AppUserProfile in the operator tenant (ADR-0001 §2). At request time the customer's
 * tenant comes from the OIDC client; the first authenticated request re-provisions the profile if this step failed.
 */
@Service
public class CustomerRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(CustomerRegistrationService.class);

    private final AppUserProfileRepository userProfileRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final CustomerProfileProvisioningService profileProvisioningService;
    private final TenancyProperties tenancyProperties;

    public CustomerRegistrationService(AppUserProfileRepository userProfileRepository, KeycloakAdminService keycloakAdminService,
                                       CustomerProfileProvisioningService profileProvisioningService, TenancyProperties tenancyProperties) {
        this.userProfileRepository = userProfileRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.profileProvisioningService = profileProvisioningService;
        this.tenancyProperties = tenancyProperties;
    }

    /** Registers a customer in Keycloak and provisions the profile in the operator tenant. */
    public CustomerRegistrationResponse register(CustomerRegistrationRequest request) {
        if (userProfileRepository.existsByEmail(request.email())) {
            throw new BusinessException("email_exists", "Email already registered", HttpStatus.CONFLICT);
        }

        String keycloakUserId = keycloakAdminService.createCustomer(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password()
        );

        String displayName = request.firstName() + " " + request.lastName();
        try {
            profileProvisioningService.ensureCustomerProfile(tenancyProperties.getOperatorTenantId(), keycloakUserId, request.email(), displayName);
        } catch (DataAccessException ex) {
            // The Keycloak account exists; the first authenticated request provisions the profile instead.
            logger.warn("Customer profile provisioning deferred to first request: keycloakUserId={}", keycloakUserId, ex);
        }
        logger.info("Customer registered: keycloakUserId={}", keycloakUserId);

        return new CustomerRegistrationResponse(keycloakUserId, request.email(), displayName);
    }
}
