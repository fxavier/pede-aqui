package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts.SelfProfile;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.api.CustomerOrderPrivacy;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.dto.DeletionRequestResponse;
import com.delivery.privacy.entity.AccountDeletionRequest;
import com.delivery.privacy.entity.AccountDeletionStatus;
import com.delivery.privacy.repository.AccountDeletionRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Customer-facing account deletion (LAC-19, W7-C): request (refused while an order is in progress), cancel during the
 * reflection period, and read the latest request. The pseudonymisation itself runs later in {@link AccountErasureService}.
 * Only customer-only principals may self-delete; staff and courier accounts are closed by an administrator.
 */
@Service
public class AccountDeletionService {
    /** Requests still in flight: at most one per profile (V071 partial unique index). */
    static final Set<AccountDeletionStatus> OPEN = EnumSet.of(AccountDeletionStatus.PENDING, AccountDeletionStatus.ERASED);

    private final DataSubjectResolver subjects;
    private final AccountDeletionRequestRepository repository;
    private final CustomerOrderPrivacy orderPrivacy;
    private final AuditLogService auditLogService;
    private final PrivacyProperties properties;
    private final TransactionTemplate tx;
    private Clock clock = Clock.systemUTC();

    public AccountDeletionService(DataSubjectResolver subjects, AccountDeletionRequestRepository repository,
                                  CustomerOrderPrivacy orderPrivacy, AuditLogService auditLogService,
                                  PrivacyProperties properties, TransactionTemplate tx) {
        this.subjects = subjects;
        this.repository = repository;
        this.orderPrivacy = orderPrivacy;
        this.auditLogService = auditLogService;
        this.properties = properties;
        this.tx = tx;
    }

    /** Test seam for a fixed clock. */
    @Autowired(required = false)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Outcome of {@link #request()}: the request and whether this call created it. */
    public record RequestOutcome(DeletionRequestResponse request, boolean created) {}

    /**
     * Creates a PENDING request scheduled after the reflection period, or returns the open one (idempotent). 409
     * {@code active_orders_present} while the customer has an order in progress; 403
     * {@code account_deletion_not_supported} for non-customer principals. A concurrent duplicate loses on the
     * partial unique index and re-reads the winner.
     */
    public RequestOutcome request() {
        SelfProfile subject = requireCustomerSubject();
        try {
            return tx.execute(status -> createOrReturnOpen(subject));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return tx.execute(status -> repository.lockOpen(subject.tenantId(), subject.profileId(), OPEN)
                    .map(open -> new RequestOutcome(DeletionRequestResponse.of(open), false))
                    .orElseThrow(() -> concurrentDuplicate));
        }
    }

    private RequestOutcome createOrReturnOpen(SelfProfile subject) {
        Optional<AccountDeletionRequest> open = repository.lockOpen(subject.tenantId(), subject.profileId(), OPEN);
        if (open.isPresent()) {
            return new RequestOutcome(DeletionRequestResponse.of(open.get()), false);
        }
        if (orderPrivacy.hasActiveOrders(subject.tenantId(), subject.profileId())) {
            throw new BusinessException("active_orders_present",
                    "The account has orders in progress; request the deletion once they are delivered or cancelled",
                    HttpStatus.CONFLICT);
        }
        Instant now = clock.instant();
        AccountDeletionRequest request = new AccountDeletionRequest(UUID.randomUUID(), subject.tenantId(), subject.profileId(),
                subject.keycloakUserId(), now, now.plus(properties.getDeletion().getReflectionPeriod()));
        repository.saveAndFlush(request);
        auditLogService.logForTenant(subject.tenantId(), AuditActions.ACCOUNT_DELETION_REQUESTED, "account_deletion_request",
                request.getId().toString(), "scheduled_for=" + request.getScheduledFor(), "SUCCESS",
                Map.of("profileId", subject.profileId().toString()));
        return new RequestOutcome(DeletionRequestResponse.of(request), true);
    }

    /**
     * Cancels the PENDING request. 404 {@code deletion_request_not_found} when nothing is pending; 409
     * {@code deletion_already_processed} once the job has already pseudonymised the account.
     */
    public DeletionRequestResponse cancel() {
        SelfProfile subject = requireCustomerSubject();
        return tx.execute(status -> {
            AccountDeletionRequest open = repository.lockOpen(subject.tenantId(), subject.profileId(), OPEN)
                    .orElseThrow(AccountDeletionService::notFound);
            if (open.getStatus() != AccountDeletionStatus.PENDING) {
                throw new BusinessException("deletion_already_processed", "The account deletion is already being processed",
                        HttpStatus.CONFLICT);
            }
            open.cancel(clock.instant());
            repository.saveAndFlush(open);
            auditLogService.logForTenant(subject.tenantId(), AuditActions.ACCOUNT_DELETION_CANCELLED, "account_deletion_request",
                    open.getId().toString(), null, "SUCCESS", Map.of("profileId", subject.profileId().toString()));
            return DeletionRequestResponse.of(open);
        });
    }

    /** The caller's latest request (any status); 404 {@code deletion_request_not_found} when there never was one. */
    public DeletionRequestResponse latest() {
        SelfProfile subject = requireCustomerSubject();
        return tx.execute(status -> repository.findFirstByTenantIdAndProfileIdOrderByRequestedAtDesc(subject.tenantId(), subject.profileId())
                .map(DeletionRequestResponse::of)
                .orElseThrow(AccountDeletionService::notFound));
    }

    private SelfProfile requireCustomerSubject() {
        if (!subjects.isCustomerPrincipal()) {
            throw new BusinessException("account_deletion_not_supported",
                    "Only customer accounts can be deleted by their owner; other accounts are closed by an administrator",
                    HttpStatus.FORBIDDEN);
        }
        return subjects.currentSubject();
    }

    private static BusinessException notFound() {
        return new BusinessException("deletion_request_not_found", "No account deletion request found", HttpStatus.NOT_FOUND);
    }
}
