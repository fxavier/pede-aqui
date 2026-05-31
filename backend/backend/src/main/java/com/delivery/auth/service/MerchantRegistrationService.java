package com.delivery.auth.service;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class MerchantRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantRegistrationService.class);

    private final TenantRepository tenantRepository;
    private final AppUserProfileRepository appUserProfileRepository;
    private final KeycloakAdminService keycloakAdminService;

    public MerchantRegistrationService(
            TenantRepository tenantRepository,
            AppUserProfileRepository appUserProfileRepository,
            KeycloakAdminService keycloakAdminService) {
        this.tenantRepository = tenantRepository;
        this.appUserProfileRepository = appUserProfileRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    public MerchantRegistrationResponse register(MerchantRegistrationRequest request) {
        // Step 1: Validate slug and email uniqueness
        if (tenantRepository.existsBySlug(request.companySlug())) {
            throw new BusinessException("tenant_slug_exists", "Company slug already exists", HttpStatus.CONFLICT);
        }

        if (appUserProfileRepository.existsByEmail(request.email())) {
            throw new BusinessException("email_exists", "Email already exists", HttpStatus.CONFLICT);
        }

        try {
            // Step 2: Generate tenant ID first so it can be embedded in the Keycloak user attributes
            UUID tenantId = UUID.randomUUID();

            // Step 3: Create user in Keycloak with tenant_id attribute so it appears in the JWT
            String keycloakUserId = keycloakAdminService.createUser(
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.password(),
                    tenantId.toString()
            );

            // Step 4: Persist tenant
            Tenant tenant = new Tenant(tenantId, request.companyName(), request.companySlug(), request.defaultCurrency());
            tenantRepository.save(tenant);

            // Step 4: Create user profile
            UUID userProfileId = UUID.randomUUID();
            String displayName = request.firstName() + " " + request.lastName();
            AppUserProfile userProfile = new AppUserProfile(
                    userProfileId,
                    tenantId,
                    keycloakUserId,
                    request.email(),
                    displayName,
                    Set.of(MarketplaceRole.VENDOR_ADMIN)
            );
            appUserProfileRepository.save(userProfile);

            logger.info("Successfully registered merchant: tenantId={}, userProfileId={}, email={}", 
                       tenantId, userProfileId, request.email());

            return new MerchantRegistrationResponse(
                    tenantId,
                    request.companySlug(),
                    userProfileId,
                    request.email(),
                    displayName
            );

        } catch (BusinessException e) {
            // Check if this is an email_exists error from Keycloak - this is expected for retries
            if ("email_exists".equals(e.getCode())) {
                logger.info("Merchant registration failed: email already exists in Keycloak: {}", request.email());
            } else {
                logger.error("Merchant registration failed with business error: {} for email: {}", e.getCode(), request.email());
            }
            // Re-throw business exceptions (including email_exists from Keycloak conflicts)
            throw e;
        } catch (Exception e) {
            // Log the error for manual cleanup if DB fails after Keycloak succeeds
            logger.warn("Failed to complete merchant registration for email: {} - Keycloak user may need manual cleanup", 
                       request.email(), e);
            throw new BusinessException("registration_error", "Failed to complete registration", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}