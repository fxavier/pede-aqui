package com.delivery.payment.service;

import com.delivery.payment.api.PaymentProvider;
import com.delivery.payment.config.PaymentAttemptProperties;
import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentAttemptStatus;
import com.delivery.payment.entity.PaymentReconciliationCase;
import com.delivery.payment.provider.PaymentProviderRegistry;
import com.delivery.payment.repository.PaymentAttemptRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves open attempts without a callback (LAC-32, W7-A): the {@code payment-status-poller} queries the provider for
 * due INITIATED/PENDING_CUSTOMER/UNKNOWN attempts (exponential backoff up to {@code poll-max-backoff}; UNKNOWN longer
 * than {@code unknown-review-after} also opens an ATTEMPT_UNRESOLVED case), and {@code payment-attempt-expiry} closes
 * INITIATED/PENDING_CUSTOMER attempts older than {@code ttl} after a last query (a success found then still wins; an
 * unanswerable query makes the attempt UNKNOWN — we never expire on ambiguity). Work-queue pattern (LAC-46): read-only
 * bounded selection, the PSP query outside any transaction, then one short transaction per attempt that claims the
 * payment row with SKIP LOCKED, re-reads the attempt under the lock and applies the answer idempotently.
 */
@Service
public class PaymentAttemptResolver {
    private static final Logger log = LoggerFactory.getLogger(PaymentAttemptResolver.class);
    private static final EnumSet<PaymentAttemptStatus> EXPIRABLE = EnumSet.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.PENDING_CUSTOMER);

    /** Per-item outcome of a run. */
    public enum Outcome { RESOLVED, RESCHEDULED, SKIPPED_BUSY, ALREADY_SETTLED, FAILED }

    /** Counts of one run (log summary). */
    public record Result(int examined, int resolved, int rescheduled, int skippedBusy, int alreadySettled, int failed) {
        @Override
        public String toString() {
            return "examined=" + examined + " resolved=" + resolved + " rescheduled=" + rescheduled + " skipped_busy=" + skippedBusy
                    + " already_settled=" + alreadySettled + " failed=" + failed;
        }
    }

    private final PaymentAttemptRepository attemptRepository;
    private final PaymentProviderRegistry providers;
    private final PaymentAttemptOutcomes outcomes;
    private final PaymentReconciliationService reconciliation;
    private final PaymentAttemptProperties properties;
    private final TransactionTemplate tx;

    public PaymentAttemptResolver(PaymentAttemptRepository attemptRepository, PaymentProviderRegistry providers,
                                  PaymentAttemptOutcomes outcomes, PaymentReconciliationService reconciliation,
                                  PaymentAttemptProperties properties, PlatformTransactionManager transactionManager) {
        this.attemptRepository = attemptRepository;
        this.providers = providers;
        this.outcomes = outcomes;
        this.reconciliation = reconciliation;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Poller run: queries one batch of due open attempts. */
    public Result pollDue() {
        List<UUID> due = attemptRepository.findDueForPoll(PaymentAttemptStatus.OPEN, Instant.now(), PageRequest.of(0, properties.getBatchSize()));
        return run(due, false);
    }

    /** Expiry run: closes one batch of INITIATED/PENDING_CUSTOMER attempts older than the TTL. */
    public Result expireStale() {
        List<UUID> stale = attemptRepository.findStaleOpen(EXPIRABLE, Instant.now().minus(properties.getTtl()),
                PageRequest.of(0, properties.getBatchSize()));
        return run(stale, true);
    }

    /** Polls one attempt now (also used by tests). */
    public Outcome pollOne(UUID attemptId) {
        return handle(attemptId, false);
    }

    /** Expires one attempt now if still expirable (also used by tests). */
    public Outcome expireOne(UUID attemptId) {
        return handle(attemptId, true);
    }

    private Result run(List<UUID> ids, boolean expire) {
        int resolved = 0, rescheduled = 0, busy = 0, settled = 0, failed = 0;
        for (UUID id : ids) {
            switch (handle(id, expire)) {
                case RESOLVED -> resolved++;
                case RESCHEDULED -> rescheduled++;
                case SKIPPED_BUSY -> busy++;
                case ALREADY_SETTLED -> settled++;
                case FAILED -> failed++;
            }
        }
        return new Result(ids.size(), resolved, rescheduled, busy, settled, failed);
    }

    private Outcome handle(UUID attemptId, boolean expire) {
        try {
            Optional<PaymentAttemptRepository.AttemptKeys> found = attemptRepository.findKeysById(attemptId);
            if (found.isEmpty() || !found.get().getStatus().isOpen()) {
                return Outcome.ALREADY_SETTLED;
            }
            PaymentAttemptRepository.AttemptKeys keys = found.get();
            Optional<PaymentProvider> provider = providers.activeById(keys.getProvider());
            if (provider.isEmpty()) {
                log.warn("Payment attempt {} belongs to inactive provider {}; not polled", attemptId, keys.getProvider());
                return Outcome.FAILED;
            }
            PaymentProvider.StatusResult answer = query(provider.get(), keys);
            return tx.execute(status -> apply(keys, answer, expire));
        } catch (RuntimeException e) {
            log.error("Payment attempt {} could not be resolved", attemptId, e);
            return Outcome.FAILED;
        }
    }

    private PaymentProvider.StatusResult query(PaymentProvider provider, PaymentAttemptRepository.AttemptKeys keys) {
        try {
            return provider.query(keys.getProviderReference(), keys.getOurReference());
        } catch (RuntimeException e) {
            log.warn("Payment status query failed: provider={} attempt={} error={}", provider.id(), keys.getId(), e.getClass().getSimpleName());
            return null;
        }
    }

    private Outcome apply(PaymentAttemptRepository.AttemptKeys keys, PaymentProvider.StatusResult answer, boolean expire) {
        Optional<PaymentAttemptOutcomes.Locked> claimed = outcomes.tryLock(keys);
        if (claimed.isEmpty()) {
            return Outcome.SKIPPED_BUSY;
        }
        PaymentAttemptOutcomes.Locked locked = claimed.get();
        PaymentAttempt attempt = locked.attempt();
        if (!attempt.getStatus().isOpen() || (expire && !EXPIRABLE.contains(attempt.getStatus()))) {
            return Outcome.ALREADY_SETTLED;
        }
        Instant now = Instant.now();
        String reference = answer == null ? null : answer.providerReference();
        PaymentAttemptStatus target = decide(attempt, answer, expire, now);
        if (target != null && target != attempt.getStatus()) {
            outcomes.apply(locked, target, answer == null || answer.errorCode() == null ? defaultError(target, expire) : answer.errorCode(),
                    reference, answer == null ? null : answer.amount(), now);
        } else {
            attempt.recordProviderReference(reference, now);
        }
        if (attempt.getStatus().isOpen()) {
            attempt.schedulePoll(now, properties.getPollBackoffBase(), properties.getPollMaxBackoff());
            if (attempt.getStatus() == PaymentAttemptStatus.UNKNOWN
                    && attempt.getCreatedAt().isBefore(now.minus(properties.getUnknownReviewAfter()))) {
                reconciliation.openForAttempt(PaymentReconciliationCase.Type.ATTEMPT_UNRESOLVED, attempt, attempt.getAmount(), null,
                        "unknown_too_long");
            }
            return Outcome.RESCHEDULED;
        }
        return Outcome.RESOLVED;
    }

    /** Target status for an answer, or null to keep the current one. */
    private PaymentAttemptStatus decide(PaymentAttempt attempt, PaymentProvider.StatusResult answer, boolean expire, Instant now) {
        if (answer == null) {
            // Query failed: we cannot tell whether the customer paid — UNKNOWN, never FAILED/EXPIRED.
            return attempt.getStatus() == PaymentAttemptStatus.UNKNOWN ? null : PaymentAttemptStatus.UNKNOWN;
        }
        return switch (answer.outcome()) {
            case SUCCEEDED -> PaymentAttemptStatus.SUCCEEDED;
            case FAILED -> PaymentAttemptStatus.FAILED;
            case EXPIRED -> PaymentAttemptStatus.EXPIRED;
            case PENDING -> expire ? PaymentAttemptStatus.EXPIRED : PaymentAttemptStatus.PENDING_CUSTOMER;
            // The PSP does not know the charge: after the TTL it never will (never reached it); before, wait.
            case NOT_FOUND -> expire || attempt.getCreatedAt().isBefore(now.minus(properties.getTtl()))
                    ? (expire ? PaymentAttemptStatus.EXPIRED : PaymentAttemptStatus.FAILED) : null;
        };
    }

    private static String defaultError(PaymentAttemptStatus target, boolean expire) {
        return switch (target) {
            case EXPIRED -> expire ? "attempt_ttl_elapsed" : "provider_expired";
            case FAILED -> "not_found_at_provider";
            case UNKNOWN -> "status_query_failed";
            default -> null;
        };
    }
}
