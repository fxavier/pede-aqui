package com.delivery.payment.service;

import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentAttemptStatus;
import com.delivery.payment.entity.PaymentReconciliationCase;
import com.delivery.payment.repository.PaymentAttemptRepository;
import com.delivery.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place where a provider answer changes an attempt (LAC-31/32): used by attempt creation (synchronous answer),
 * webhooks, the status poller and attempt expiry. Always runs inside the caller's transaction after the payment row was
 * locked (lock order: payment → attempt → order/ledger effects). A SUCCEEDED attempt confirms the payment through the
 * existing confirmation path ({@link PaymentService#confirmFromProvider}: order PAYMENT_CONFIRMED + PaymentConfirmed →
 * ledger); a success that cannot confirm it opens a reconciliation case instead. FAILED/EXPIRED leave the order
 * PAYMENT_PENDING (the customer may retry until the payment-expiry job cancels it).
 */
@Component
public class PaymentAttemptOutcomes {
    private static final Logger log = LoggerFactory.getLogger(PaymentAttemptOutcomes.class);

    /** What applying an answer did. */
    public enum Applied {
        /** Attempt SUCCEEDED and the payment was confirmed. */
        CONFIRMED,
        /** Attempt SUCCEEDED but the payment could not be confirmed: a reconciliation case was opened. */
        CASE_OPENED,
        /** Attempt moved to another status (PENDING_CUSTOMER, FAILED, EXPIRED, UNKNOWN). */
        TRANSITIONED,
        /** Repeat or disallowed move (e.g. FAILED after SUCCEEDED): nothing changed. */
        IGNORED
    }

    /** Payment and attempt locked in the current transaction. */
    public record Locked(Payment payment, PaymentAttempt attempt) {
    }

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentService paymentService;
    private final PaymentReconciliationService reconciliation;
    private final PaymentMetrics metrics;

    public PaymentAttemptOutcomes(PaymentRepository paymentRepository, PaymentAttemptRepository attemptRepository,
                                  PaymentService paymentService, PaymentReconciliationService reconciliation, PaymentMetrics metrics) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.paymentService = paymentService;
        this.reconciliation = reconciliation;
        this.metrics = metrics;
    }

    /** Locks the attempt's payment row (blocking) and then the attempt — request paths (webhook, creation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Locked lock(PaymentAttemptRepository.AttemptKeys keys) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(keys.getTenantId(), keys.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("payment of attempt " + keys.getId() + " is missing"));
        PaymentAttempt attempt = attemptRepository.findByIdForUpdate(keys.getId())
                .orElseThrow(() -> new IllegalStateException("attempt " + keys.getId() + " is missing"));
        return new Locked(payment, attempt);
    }

    /**
     * Job variant (LAC-46 work queue): claims the payment row with {@code FOR UPDATE SKIP LOCKED}; empty when a request
     * holds it (the job retries next tick instead of queueing behind user traffic).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Locked> tryLock(PaymentAttemptRepository.AttemptKeys keys) {
        List<UUID> claimed = paymentRepository.lockIdsByTenantIdAndOrderIdSkipLocked(keys.getTenantId(), keys.getOrderId());
        if (!claimed.contains(keys.getPaymentId())) {
            return Optional.empty();
        }
        return Optional.of(lock(keys));
    }

    /**
     * Applies {@code target} to the locked attempt. {@code reportedAmount} (nullable) is what the PSP says it charged:
     * a success for another amount is recorded but never confirms the payment (case AMOUNT_MISMATCH).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Applied apply(Locked locked, PaymentAttemptStatus target, String errorCode, String providerReference,
                         BigDecimal reportedAmount, Instant now) {
        PaymentAttempt attempt = locked.attempt();
        attempt.recordProviderReference(providerReference, now);
        if (!attempt.transitionTo(target, errorCode, now)) {
            return Applied.IGNORED;
        }
        metrics.attempt(attempt.getProvider(), target.name());
        if (target != PaymentAttemptStatus.SUCCEEDED) {
            return Applied.TRANSITIONED;
        }
        if (reportedAmount != null && reportedAmount.compareTo(attempt.getAmount()) != 0) {
            reconciliation.openForAttempt(PaymentReconciliationCase.Type.AMOUNT_MISMATCH, attempt, attempt.getAmount(), reportedAmount,
                    "reported_amount");
            return Applied.CASE_OPENED;
        }
        return switch (paymentService.confirmFromProvider(locked.payment(), attempt.getProvider())) {
            case CONFIRMED -> Applied.CONFIRMED;
            case ALREADY_CAPTURED -> {
                reconciliation.openForAttempt(PaymentReconciliationCase.Type.DUPLICATE_SUCCESS, attempt, attempt.getAmount(),
                        attempt.getAmount(), "payment_already_captured");
                yield Applied.CASE_OPENED;
            }
            case NOT_CONFIRMABLE -> {
                log.warn("PSP success for a payment that can no longer be confirmed: payment={} attempt={} status={}",
                        locked.payment().getId(), attempt.getId(), locked.payment().statusName());
                reconciliation.openForAttempt(PaymentReconciliationCase.Type.SUCCEEDED_NOT_CAPTURED, attempt, attempt.getAmount(),
                        attempt.getAmount(), "payment_" + locked.payment().statusName().toLowerCase());
                yield Applied.CASE_OPENED;
            }
        };
    }
}
