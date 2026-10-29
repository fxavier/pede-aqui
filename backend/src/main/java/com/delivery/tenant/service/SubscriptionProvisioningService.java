package com.delivery.tenant.service;

import com.delivery.tenant.dto.MerchantRegistrationRequest;
import com.delivery.tenant.dto.MerchantRegistrationResponse;
import com.delivery.tenant.dto.TenantProvisioningRequest;
import com.delivery.tenant.dto.TenantProvisioningResponse;
import com.delivery.auth.api.UserAccounts;
import com.delivery.tenant.entity.ProvisioningRequest;
import com.delivery.tenant.repository.ProvisioningRequestRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.tenant.entity.Tenant;
import com.delivery.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotently provisions a tenant + owner from a thin subscription lead by mapping it onto the
 * existing merchant-registration saga. Effectively-once via the provisioning_requests ledger and a
 * per-email Postgres advisory lock. In the tenant module since ADR-0002 W3-A (C8); users go through
 * {@code auth.api.UserAccounts}.
 */
@Service
public class SubscriptionProvisioningService {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionProvisioningService.class);
    private static final int MAX_SLUG_LENGTH = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantRegistrationService merchantRegistrationService;
    private final UserAccounts userAccounts;
    private final TenantRepository tenantRepository;
    private final ProvisioningRequestRepository provisioningRequestRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int maxTenantsPerHour;
    private final int maxPerEmailDomainPerHour;

    @PersistenceContext
    private EntityManager entityManager;

    public SubscriptionProvisioningService(
            MerchantRegistrationService merchantRegistrationService,
            UserAccounts userAccounts,
            TenantRepository tenantRepository,
            ProvisioningRequestRepository provisioningRequestRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.provisioning.max-tenants-per-hour:50}") int maxTenantsPerHour,
            @Value("${app.provisioning.max-per-email-domain-per-hour:10}") int maxPerEmailDomainPerHour) {
        this.merchantRegistrationService = merchantRegistrationService;
        this.userAccounts = userAccounts;
        this.tenantRepository = tenantRepository;
        this.provisioningRequestRepository = provisioningRequestRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxTenantsPerHour = maxTenantsPerHour;
        this.maxPerEmailDomainPerHour = maxPerEmailDomainPerHour;
    }

    /** Creates-or-returns the tenant for a lead; replays and duplicate emails never create twins. */
    public TenantProvisioningResponse provision(String idempotencyKey, TenantProvisioningRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        enforceCreationCaps(email);

        Outcome outcome;
        try {
            outcome = transactionTemplate.execute(status -> provisionInTransaction(idempotencyKey, email, request));
        } catch (BusinessException e) {
            TenantProvisioningResponse existing = resolveConflict(e, idempotencyKey, email);
            if (existing != null) {
                return existing;
            }
            recordFailure(idempotencyKey, email, request.externalRef());
            throw mapDownstream(e);
        } catch (DataIntegrityViolationException e) {
            // Same-key race: another request inserted our idempotency row first.
            TenantProvisioningResponse cached = cachedResponse(idempotencyKey);
            if (cached != null) {
                return cached;
            }
            throw new BusinessException("conflict", "Provisioning for this idempotency key is in progress", HttpStatus.CONFLICT);
        } catch (Exception e) {
            recordFailure(idempotencyKey, email, request.externalRef());
            logger.error("Tenant provisioning failed for email {}", email, e);
            throw new BusinessException("downstream_db", "Provisioning failed on a downstream dependency", HttpStatus.BAD_GATEWAY);
        }

        if (outcome.keycloakUserIdForOnboarding() != null) {
            userAccounts.sendPasswordSetupEmail(outcome.keycloakUserIdForOnboarding());
        }
        return outcome.response();
    }

    private Outcome provisionInTransaction(String idempotencyKey, String email, TenantProvisioningRequest request) {
        lockEmail(email);

        ProvisioningRequest existingRow = provisioningRequestRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingRow != null && ProvisioningRequest.STATUS_SUCCEEDED.equals(existingRow.getStatus())) {
            return new Outcome(replayResponse(existingRow), null);
        }

        UserAccounts.UserAccount existingProfile = userAccounts.findFirstByEmail(email).orElse(null);
        if (existingProfile != null) {
            return new Outcome(existingTenantResponse(existingProfile), null);
        }

        MerchantRegistrationResponse saga = merchantRegistrationService.provisionTenant(toRegistrationRequest(request, email));
        String keycloakUserId = userAccounts.keycloakUserIdOf(saga.userProfileId()).orElse(null);

        TenantProvisioningResponse response = new TenantProvisioningResponse(
                saga.tenantId(), saga.tenantSlug(), saga.userProfileId(), saga.email(),
                saga.displayName(), "ACTIVE", true, false);

        ProvisioningRequest row = existingRow != null
                ? existingRow
                : new ProvisioningRequest(UUID.randomUUID(), idempotencyKey, email, request.externalRef(),
                        ProvisioningRequest.STATUS_SUCCEEDED);
        row.markSucceeded(saga.tenantId(), toJson(response));
        provisioningRequestRepository.save(row);

        return new Outcome(response, keycloakUserId);
    }

    private MerchantRegistrationRequest toRegistrationRequest(TenantProvisioningRequest request, String email) {
        String contactName = request.contactName().trim();
        int space = contactName.indexOf(' ');
        String firstName = space < 0 ? contactName : contactName.substring(0, space);
        String lastName = space < 0 ? contactName : contactName.substring(space + 1).trim();

        return new MerchantRegistrationRequest(
                request.companyName(),
                generateUniqueSlug(request.companyName()),
                "MZN",
                null, null, null, null,
                "MZ",
                request.city(),
                null,
                email,
                request.phone(),
                firstName,
                lastName,
                email,
                request.phone(),
                generatePassword(),
                null, null);
    }

    /** Slugifies the company name and appends -2, -3, ... until the slug is free. */
    private String generateUniqueSlug(String companyName) {
        String base = Normalizer.normalize(companyName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (base.isBlank()) {
            base = "tenant";
        }
        base = trimSlug(base, MAX_SLUG_LENGTH);

        String slug = base;
        int suffix = 2;
        while (tenantRepository.existsBySlug(slug)) {
            String tail = "-" + suffix++;
            slug = trimSlug(base, MAX_SLUG_LENGTH - tail.length()) + tail;
        }
        return slug;
    }

    private String trimSlug(String value, int maxLength) {
        String trimmed = value.length() > maxLength ? value.substring(0, maxLength) : value;
        return trimmed.replaceAll("-+$", "");
    }

    /** Random throwaway credential; the owner sets their real password via the onboarding email. */
    private String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Serializes concurrent provisioning for one email so duplicates observe committed state. */
    private void lockEmail(String email) {
        // IS NULL turns the void-typed lock function into a boolean Hibernate can map
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1)) IS NULL")
                .setParameter(1, email)
                .getSingleResult();
    }

    private TenantProvisioningResponse replayResponse(ProvisioningRequest row) {
        try {
            TenantProvisioningResponse cached = objectMapper.readValue(row.getResponseJson(), TenantProvisioningResponse.class);
            return asExisting(cached);
        } catch (Exception e) {
            throw new BusinessException("downstream_db", "Stored provisioning response is unreadable", HttpStatus.BAD_GATEWAY);
        }
    }

    private TenantProvisioningResponse existingTenantResponse(UserAccounts.UserAccount profile) {
        Tenant tenant = tenantRepository.findById(profile.tenantId())
                .orElseThrow(() -> new BusinessException("downstream_db", "Tenant missing for existing profile", HttpStatus.BAD_GATEWAY));
        return new TenantProvisioningResponse(
                tenant.getId(), tenant.getSlug(), profile.profileId(), profile.email(),
                profile.displayName(), tenant.getStatus(), false, true);
    }

    private TenantProvisioningResponse asExisting(TenantProvisioningResponse response) {
        return new TenantProvisioningResponse(
                response.tenantId(), response.tenantSlug(), response.ownerUserId(), response.ownerEmail(),
                response.displayName(), response.status(), false, true);
    }

    /** email_exists mid-saga means someone else owns the email: return their tenant if visible. */
    private TenantProvisioningResponse resolveConflict(BusinessException e, String idempotencyKey, String email) {
        if (!"email_exists".equals(e.getCode()) && !"tenant_slug_exists".equals(e.getCode())) {
            return null;
        }
        TenantProvisioningResponse cached = cachedResponse(idempotencyKey);
        if (cached != null) {
            return cached;
        }
        return userAccounts.findFirstByEmail(email)
                .map(this::existingTenantResponse)
                .orElse(null);
    }

    private TenantProvisioningResponse cachedResponse(String idempotencyKey) {
        return provisioningRequestRepository.findByIdempotencyKey(idempotencyKey)
                .filter(row -> ProvisioningRequest.STATUS_SUCCEEDED.equals(row.getStatus()))
                .map(this::replayResponse)
                .orElse(null);
    }

    private BusinessException mapDownstream(BusinessException e) {
        if ("keycloak_error".equals(e.getCode())) {
            return new BusinessException("downstream_idp", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if ("registration_error".equals(e.getCode())) {
            return new BusinessException("downstream_db", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if ("email_exists".equals(e.getCode()) || "tenant_slug_exists".equals(e.getCode())) {
            return new BusinessException("conflict", e.getMessage(), HttpStatus.CONFLICT);
        }
        return e;
    }

    /** Guards provision-on-subscribe abuse: hard hourly caps, overall and per email domain. */
    private void enforceCreationCaps(String email) {
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        if (provisioningRequestRepository.countByCreatedAtAfter(oneHourAgo) >= maxTenantsPerHour) {
            // ponytail: log.error is the alert channel; wire real alerting when monitoring lands
            logger.error("Provisioning hourly cap reached ({} tenants/h) - rejecting new leads", maxTenantsPerHour);
            throw new BusinessException("rate_limited", "Provisioning hourly cap reached", HttpStatus.TOO_MANY_REQUESTS);
        }
        String domain = email.substring(email.indexOf('@') + 1);
        if (provisioningRequestRepository.countByEmailDomainSince("%@" + domain, oneHourAgo) >= maxPerEmailDomainPerHour) {
            logger.error("Provisioning per-domain cap reached for {} - rejecting lead", domain);
            throw new BusinessException("rate_limited", "Too many provisioning requests for this email domain", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private void recordFailure(String idempotencyKey, String email, String externalRef) {
        try {
            ProvisioningRequest row = provisioningRequestRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseGet(() -> new ProvisioningRequest(
                            UUID.randomUUID(), idempotencyKey, email, externalRef, ProvisioningRequest.STATUS_FAILED));
            row.markFailed();
            provisioningRequestRepository.save(row);
        } catch (Exception e) {
            logger.warn("Could not record failed provisioning attempt for key {}: {}", idempotencyKey, e.getMessage());
        }
    }

    private String toJson(TenantProvisioningResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize provisioning response", e);
        }
    }

    private record Outcome(TenantProvisioningResponse response, String keycloakUserIdForOnboarding) {
    }
}
