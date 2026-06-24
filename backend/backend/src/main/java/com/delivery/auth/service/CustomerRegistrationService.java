package com.delivery.auth.service;

import com.delivery.auth.dto.CustomerRegistrationRequest;
import com.delivery.auth.dto.CustomerRegistrationResponse;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Handles customer self-registration — creates a Keycloak user with CUSTOMER role, no tenant. */
@Service
public class CustomerRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(CustomerRegistrationService.class);

    private final AppUserProfileRepository userProfileRepository;
    private final KeycloakAdminService keycloakAdminService;

    public CustomerRegistrationService(AppUserProfileRepository userProfileRepository, KeycloakAdminService keycloakAdminService) {
        this.userProfileRepository = userProfileRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

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
        logger.info("Customer registered: keycloakUserId={}, email={}", keycloakUserId, request.email());

        return new CustomerRegistrationResponse(keycloakUserId, request.email(), displayName);
    }
}
