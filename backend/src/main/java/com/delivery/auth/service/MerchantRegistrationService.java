package com.delivery.auth.service;

import com.delivery.auth.dto.MerchantRegistrationRequest;
import com.delivery.auth.dto.MerchantRegistrationResponse;
import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.MarketplaceRole;
import com.delivery.common.security.TenancyProperties;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import com.delivery.vendor.entity.MerchantRole;
import com.delivery.vendor.entity.MerchantUser;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.MerchantUserRepository;
import com.delivery.vendor.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Merchant onboarding saga (Keycloak user + profile + vendor + merchant_users), in two flavours (ADR-0001):
 * <ul>
 *   <li>{@link #register}: self-service {@code POST /register} — a PENDING vendor and its OWNER inside the operator
 *       tenant; <b>never</b> creates a tenant (step 4, closes SEC-09's "mint a tenant" path);</li>
 *   <li>{@link #provisionTenant}: the M2M subscription provisioning path (ADR-0001 §5) — a new tenant with its owner
 *       and bootstrap vendor, kept for white-label operators.</li>
 * </ul>
 * On any failure after the Keycloak user was created, the user is deleted again (compensation).
 */
@Service
@Transactional
public class MerchantRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(MerchantRegistrationService.class);

    private final TenantRepository tenantRepository;
    private final AppUserProfileRepository appUserProfileRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final VendorRepository vendorRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final TenancyProperties tenancyProperties;

    public MerchantRegistrationService(
            TenantRepository tenantRepository,
            AppUserProfileRepository appUserProfileRepository,
            KeycloakAdminService keycloakAdminService,
            VendorRepository vendorRepository,
            MerchantUserRepository merchantUserRepository,
            TenancyProperties tenancyProperties) {
        this.tenantRepository = tenantRepository;
        this.appUserProfileRepository = appUserProfileRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.vendorRepository = vendorRepository;
        this.merchantUserRepository = merchantUserRepository;
        this.tenancyProperties = tenancyProperties;
    }

    /**
     * Self-service merchant registration: creates the Keycloak user (tenant_id = operator), the VENDOR_ADMIN profile,
     * a vendor pending verification and the OWNER association — all in the operator tenant. The request's
     * {@code companySlug}/{@code defaultCurrency} are accepted for compatibility but no longer create anything.
     */
    public MerchantRegistrationResponse register(MerchantRegistrationRequest request) {
        Tenant operator = tenantRepository.findById(tenancyProperties.getOperatorTenantId())
                .orElseThrow(() -> new BusinessException("operator_tenant_missing",
                        "The operator tenant is not provisioned", HttpStatus.INTERNAL_SERVER_ERROR));
        return onboard(request, operator, false);
    }

    /** M2M provisioning: creates a NEW tenant (slug must be free) with its owner and bootstrap vendor. */
    public MerchantRegistrationResponse provisionTenant(MerchantRegistrationRequest request) {
        if (tenantRepository.existsBySlug(request.companySlug())) {
            throw new BusinessException("tenant_slug_exists", "Company slug already exists", HttpStatus.CONFLICT);
        }
        Tenant tenant = new Tenant(UUID.randomUUID(), request.companyName(), request.companySlug(), request.defaultCurrency());
        return onboard(request, tenant, true);
    }

    private MerchantRegistrationResponse onboard(MerchantRegistrationRequest request, Tenant tenant, boolean persistTenant) {
        if (appUserProfileRepository.existsByEmail(request.email())) {
            throw new BusinessException("email_exists", "Email already exists", HttpStatus.CONFLICT);
        }
        UUID tenantId = tenant.getId();
        String keycloakUserId = null;
        try {
            // Keycloak first: the tenant_id attribute must be on the user before any token is issued.
            keycloakUserId = keycloakAdminService.createUser(
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    request.password(),
                    tenantId.toString()
            );

            if (persistTenant) {
                tenantRepository.save(tenant);
            }

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

            // Vendor is created pending verification (VendorVerificationStatus.PENDING) and unavailable.
            UUID vendorId = UUID.randomUUID();
            Vendor vendor = new Vendor(
                    vendorId,
                    tenantId,
                    request.companyName(),
                    null,
                    null,
                    null,
                    displayName,
                    request.taxNumber(),
                    request.companyPhone(),
                    request.address(),
                    null,
                    null
            );
            vendorRepository.save(vendor);

            merchantUserRepository.save(new MerchantUser(UUID.randomUUID(), tenantId, vendorId, userProfileId, MerchantRole.OWNER));

            logger.info("Registered merchant: tenantId={}, vendorId={}, userProfileId={}, newTenant={}",
                    tenantId, vendorId, userProfileId, persistTenant);

            return new MerchantRegistrationResponse(
                    tenantId,
                    tenant.getSlug(),
                    userProfileId,
                    request.email(),
                    displayName,
                    vendorId,
                    vendor.getVerificationStatus().name()
            );

        } catch (BusinessException e) {
            // email_exists from Keycloak (409) is an expected outcome of a retry; everything else is logged as an error.
            if ("email_exists".equals(e.getCode())) {
                logger.info("Merchant registration refused: email already exists in Keycloak");
            } else {
                logger.error("Merchant registration failed with business error {}", e.getCode());
            }
            throw e;
        } catch (Exception e) {
            // Compensation: the Keycloak user exists but nothing was persisted — remove it so the email can retry.
            if (keycloakUserId != null) {
                try {
                    keycloakAdminService.deleteUser(keycloakUserId);
                    logger.info("Cleaned up Keycloak user {} after registration failure", keycloakUserId);
                } catch (Exception deleteException) {
                    logger.error("Failed to delete Keycloak user {} during compensation - manual cleanup required: {}",
                            keycloakUserId, deleteException.getMessage(), deleteException);
                }
            }
            logger.error("Failed to complete merchant registration (tenantId={})", tenantId, e);
            throw new BusinessException("registration_error", "Failed to complete registration", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
