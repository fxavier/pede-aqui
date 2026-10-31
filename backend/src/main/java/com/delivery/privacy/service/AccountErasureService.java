package com.delivery.privacy.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.customer.api.CustomerPersonalData;
import com.delivery.notification.api.NotificationPreferences;
import com.delivery.order.api.CustomerOrderPrivacy;
import com.delivery.privacy.config.PrivacyProperties;
import com.delivery.privacy.entity.AccountDeletionRequest;
import com.delivery.privacy.entity.AccountDeletionStatus;
import com.delivery.privacy.repository.AccountDeletionRequestRepository;
import com.delivery.privacy.repository.RetentionRunLog;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processes due account deletion requests (LAC-19, W7-C; job {@code account-deletion}). Work-queue pattern: a
 * read-only scan of due ids, then each request in its own transaction that claims the row FOR UPDATE SKIP LOCKED and
 * re-checks it. Per request:
 * <ol>
 *   <li>PENDING and due: still an order in progress → postponed ({@code last_error=active_orders_present}, retried next
 *       run). Otherwise the Keycloak user is <b>disabled</b> (no new login/refresh), then in the same transaction the
 *       profile is pseudonymised, addresses/favourites, push devices, inbox and preferences deleted, order snapshots
 *       stripped of identifying fields ({@link CustomerOrderPrivacy}), an {@code ACCOUNT_PSEUDONYMISED} audit row
 *       written and the request moved to ERASED. Any failure rolls the transaction back and records the attempt.</li>
 *   <li>ERASED: the Keycloak user is <b>deleted</b> (404 = already gone) and the request becomes COMPLETED
 *       ({@code ACCOUNT_DELETION_COMPLETED}); a Keycloak failure leaves it ERASED for the next run.</li>
 * </ol>
 * Audit rows and status history written earlier keep the (now meaningless) Keycloak subject: they are append-only
 * and purged by the retention cleaner. Metrics: {@code privacy_account_deletions_total{outcome}}.
 */
@Service
public class AccountErasureService {
    public static final String JOB = "account-deletion";
    private static final Logger log = LoggerFactory.getLogger(AccountErasureService.class);

    /** Outcome of one request in one run. */
    public enum Outcome { ERASED, COMPLETED, POSTPONED, SKIPPED, FAILED }

    private final AccountDeletionRequestRepository repository;
    private final UserAccounts userAccounts;
    private final CustomerPersonalData customerData;
    private final NotificationPreferences notificationPreferences;
    private final CustomerOrderPrivacy orderPrivacy;
    private final AuditLogService auditLogService;
    private final RetentionRunLog runLog;
    private final PrivacyProperties properties;
    private final TransactionTemplate tx;
    private final MeterRegistry meterRegistry;
    private Clock clock = Clock.systemUTC();

    public AccountErasureService(AccountDeletionRequestRepository repository, UserAccounts userAccounts,
                                 CustomerPersonalData customerData, NotificationPreferences notificationPreferences,
                                 CustomerOrderPrivacy orderPrivacy, AuditLogService auditLogService, RetentionRunLog runLog,
                                 PrivacyProperties properties, TransactionTemplate tx, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.userAccounts = userAccounts;
        this.customerData = customerData;
        this.notificationPreferences = notificationPreferences;
        this.orderPrivacy = orderPrivacy;
        this.auditLogService = auditLogService;
        this.runLog = runLog;
        this.properties = properties;
        this.tx = tx;
        this.meterRegistry = meterRegistry;
    }

    /** Test seam for a fixed clock. */
    @Autowired(required = false)
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Handles every due request (bounded by the batch size); returns a PII-free summary for the job log. */
    public String processDue() {
        Instant startedAt = clock.instant();
        List<UUID> due = repository.findDueIds(AccountDeletionService.OPEN, startedAt,
                Limit.of(Math.max(1, properties.getDeletion().getBatchSize())));
        Map<Outcome, Integer> counts = new LinkedHashMap<>();
        for (UUID id : due) {
            Outcome outcome = process(id);
            counts.merge(outcome, 1, Integer::sum);
            meterRegistry.counter("privacy.account.deletions", "outcome", outcome.name().toLowerCase()).increment();
        }
        int failed = counts.getOrDefault(Outcome.FAILED, 0);
        runLog.record(JOB, JOB, properties.getDeletion().getReflectionPeriod(), null,
                counts.getOrDefault(Outcome.ERASED, 0) + counts.getOrDefault(Outcome.COMPLETED, 0),
                failed > 0 ? "FAILURE" : "SUCCESS", failed > 0 ? "failed=" + failed : null, startedAt, clock.instant());
        return "due=" + due.size() + " " + counts;
    }

    /** Result of the database step. */
    private enum Step { ERASED, ALREADY_ERASED, POSTPONED, SKIPPED }

    /**
     * Processes one request (database step, then the Keycloak deletion when the account is erased); never throws.
     * Returns ERASED/COMPLETED/POSTPONED/SKIPPED, or FAILED with the error code recorded on the request.
     */
    public Outcome process(UUID requestId) {
        Step step;
        try {
            step = tx.execute(status -> eraseIfDue(requestId));
        } catch (RuntimeException ex) {
            return failed(requestId, ex);
        }
        if (step == Step.POSTPONED) {
            return Outcome.POSTPONED;
        }
        if (step == Step.SKIPPED) {
            return Outcome.SKIPPED;
        }
        try {
            return completeKeycloakDeletion(requestId);
        } catch (RuntimeException ex) {
            // The database side stays pseudonymised (ERASED); the Keycloak deletion is retried by the next run.
            return failed(requestId, ex);
        }
    }

    private Outcome failed(UUID requestId, RuntimeException ex) {
        String code = ex instanceof BusinessException business ? business.getCode() : ex.getClass().getSimpleName();
        log.warn("Account deletion request {} failed: {}", requestId, code);
        recordFailure(requestId, code);
        return Outcome.FAILED;
    }

    private Step eraseIfDue(UUID requestId) {
        if (repository.claimSkipLocked(requestId).isEmpty()) {
            return Step.SKIPPED;
        }
        AccountDeletionRequest request = repository.findById(requestId).orElse(null);
        Instant now = clock.instant();
        if (request == null || request.getScheduledFor().isAfter(now)) {
            return Step.SKIPPED;
        }
        if (request.getStatus() == AccountDeletionStatus.ERASED) {
            return Step.ALREADY_ERASED;
        }
        if (request.getStatus() != AccountDeletionStatus.PENDING) {
            return Step.SKIPPED;
        }
        UUID tenantId = request.getTenantId();
        UUID profileId = request.getProfileId();
        if (orderPrivacy.hasActiveOrders(tenantId, profileId)) {
            request.recordAttemptFailure("active_orders_present", now);
            repository.save(request);
            return Step.POSTPONED;
        }
        // Disable first: if anything below fails, the transaction rolls back and the (idempotent) disable is retried.
        userAccounts.disableKeycloakUser(request.getKeycloakUserId());
        CustomerPersonalData.Erasure customer = customerData.erase(tenantId, profileId, request.getKeycloakUserId());
        NotificationPreferences.Erasure notifications = notificationPreferences.erase(tenantId, profileId);
        int orders = orderPrivacy.pseudonymiseCustomerOrders(tenantId, profileId);
        boolean profile = userAccounts.pseudonymise(tenantId, profileId);
        request.markErased(now);
        repository.save(request);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("job", JOB);
        details.put("profilePseudonymised", profile);
        details.put("addressesDeleted", customer.addresses());
        details.put("favouritesDeleted", customer.favourites());
        details.put("devicesDeleted", notifications.devices());
        details.put("inboxDeleted", notifications.inboxNotifications());
        details.put("ordersPseudonymised", orders);
        auditLogService.logForTenant(tenantId, AuditActions.ACCOUNT_PSEUDONYMISED, "user_profile", profileId.toString(),
                "deletion_request=" + requestId, "SUCCESS", details);
        return Step.ERASED;
    }

    private Outcome completeKeycloakDeletion(UUID requestId) {
        String keycloakUserId = tx.execute(status -> repository.findById(requestId)
                .filter(r -> r.getStatus() == AccountDeletionStatus.ERASED)
                .map(AccountDeletionRequest::getKeycloakUserId)
                .orElse(null));
        if (keycloakUserId == null) {
            return Outcome.SKIPPED;
        }
        userAccounts.deleteKeycloakUserStrict(keycloakUserId);
        return tx.execute(status -> {
            if (repository.claimSkipLocked(requestId).isEmpty()) {
                return Outcome.SKIPPED;
            }
            AccountDeletionRequest request = repository.findById(requestId).orElseThrow();
            if (request.getStatus() != AccountDeletionStatus.ERASED) {
                return Outcome.SKIPPED;
            }
            request.markCompleted(clock.instant());
            repository.save(request);
            auditLogService.logForTenant(request.getTenantId(), AuditActions.ACCOUNT_DELETION_COMPLETED, "account_deletion_request",
                    requestId.toString(), null, "SUCCESS", Map.of("job", JOB));
            return Outcome.COMPLETED;
        });
    }

    private void recordFailure(UUID requestId, String code) {
        try {
            tx.executeWithoutResult(status -> repository.findById(requestId).ifPresent(request -> {
                request.recordAttemptFailure(code, clock.instant());
                repository.save(request);
            }));
        } catch (RuntimeException ignored) {
            // A concurrent update wins; the next run records the next attempt.
        }
    }
}
