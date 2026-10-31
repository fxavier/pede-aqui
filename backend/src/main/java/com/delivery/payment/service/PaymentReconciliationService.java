package com.delivery.payment.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.payment.api.PspStatementSource;
import com.delivery.payment.config.PaymentAttemptProperties;
import com.delivery.payment.dto.ReconciliationCaseResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentReconciliationCase;
import com.delivery.payment.repository.PaymentAttemptRepository;
import com.delivery.payment.repository.PaymentReconciliationCaseRepository;
import com.delivery.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment reconciliation framework (LAC-32, W7-A): opens cases idempotently (by dedup key) when the payment path meets
 * a mismatch, runs the daily comparison of SUCCEEDED attempts with captured payments (job {@code payment-reconciliation})
 * and serves the OPS/FINANCE case list. PSP statement import is PSP-specific ({@link PspStatementSource}, no
 * implementation yet).
 */
@Service
public class PaymentReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    static final int PAGE = 500;
    static final int MAX_PAGES = 20;

    /** Counts of one daily run (log summary). */
    public record RunResult(int examined, int opened) {
        @Override
        public String toString() {
            return "examined=" + examined + " opened=" + opened;
        }
    }

    private final PaymentReconciliationCaseRepository caseRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptProperties properties;
    private final PaymentMetrics metrics;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final ObjectProvider<PspStatementSource> statementSources;

    public PaymentReconciliationService(PaymentReconciliationCaseRepository caseRepository, PaymentAttemptRepository attemptRepository,
                                        PaymentRepository paymentRepository, PaymentAttemptProperties properties,
                                        PaymentMetrics metrics, TenantContext tenantContext, AuditLogService auditLogService,
                                        ObjectProvider<PspStatementSource> statementSources) {
        this.caseRepository = caseRepository;
        this.attemptRepository = attemptRepository;
        this.paymentRepository = paymentRepository;
        this.properties = properties;
        this.metrics = metrics;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.statementSources = statementSources;
    }

    /** Opens a case for {@code attempt} unless the same (type, attempt) case exists; joins the caller's transaction. */
    @Transactional
    public boolean openForAttempt(PaymentReconciliationCase.Type type, PaymentAttempt attempt, BigDecimal expected,
                                  BigDecimal observed, String detailCode) {
        return open(type, type.name() + ":" + attempt.getId(), attempt.getTenantId(), attempt.getPaymentId(), attempt.getId(),
                attempt.getOrderId(), attempt.getProvider(), expected, observed, detailCode);
    }

    /**
     * Daily comparison (job {@code payment-reconciliation}), cross-tenant, bounded to the lookback window: every
     * SUCCEEDED attempt must belong to a captured payment of the same amount and be its first success; UNKNOWN attempts
     * older than {@code unknown-review-after} need manual review; every provider-confirmed payment needs a SUCCEEDED
     * attempt. Missing cases are opened (idempotent); then the open-cases gauge is refreshed.
     */
    @Transactional
    public RunResult runDaily() {
        Instant now = Instant.now();
        Instant since = now.minus(properties.getReconciliationLookback());
        int examined = 0;
        int opened = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            List<PaymentAttempt> succeeded = attemptRepository.findSucceededSince(since, PageRequest.of(page, PAGE));
            for (PaymentAttempt attempt : succeeded) {
                examined++;
                opened += checkSucceeded(attempt) ? 1 : 0;
            }
            if (succeeded.size() < PAGE) {
                break;
            }
        }
        for (PaymentAttempt attempt : attemptRepository.findUnknownBefore(now.minus(properties.getUnknownReviewAfter()), PageRequest.of(0, PAGE))) {
            examined++;
            opened += openForAttempt(PaymentReconciliationCase.Type.ATTEMPT_UNRESOLVED, attempt, attempt.getAmount(), null, "unknown_too_long") ? 1 : 0;
        }
        for (Payment payment : paymentRepository.findProviderConfirmedSince(since, PageRequest.of(0, PAGE * MAX_PAGES))) {
            examined++;
            if (!attemptRepository.existsByPaymentIdAndStatus(payment.getId(), com.delivery.payment.entity.PaymentAttemptStatus.SUCCEEDED)) {
                opened += open(PaymentReconciliationCase.Type.CONFIRMED_WITHOUT_ATTEMPT,
                        PaymentReconciliationCase.Type.CONFIRMED_WITHOUT_ATTEMPT.name() + ":" + payment.getId(), payment.getTenantId(),
                        payment.getId(), null, payment.getOrderId(), payment.getProvider().toLowerCase(), payment.getAmount(), null,
                        "no_succeeded_attempt") ? 1 : 0;
            }
        }
        if (statementSources.stream().findAny().isEmpty()) {
            log.debug("No PSP statement source configured; statement matching skipped (LAC-32 step 1)");
        }
        metrics.openCases(caseRepository.countByStatus(PaymentReconciliationCase.Status.OPEN));
        return new RunResult(examined, opened);
    }

    /** OPS/FINANCE list of the caller tenant's cases, newest first, optionally filtered by status. */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationCaseResponse> list(PaymentReconciliationCase.Status status, int page, int size) {
        UUID tenantId = tenantId();
        var pageable = PageLimits.page(page, size);
        var result = status == null ? caseRepository.findByTenantId(tenantId, pageable)
                : caseRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        return PageResponse.of(result, ReconciliationCaseResponse::of);
    }

    /** Closes a case with a short note (idempotent: a repeat returns the case unchanged); audited. */
    @Transactional
    public ReconciliationCaseResponse resolve(UUID caseId, String note) {
        UUID tenantId = tenantId();
        PaymentReconciliationCase found = caseRepository.findByTenantIdAndId(tenantId, caseId)
                .orElseThrow(() -> new NotFoundException("Reconciliation case was not found"));
        String actor = tenantContext.currentKeycloakUserId().orElse("unknown");
        if (found.resolve(actor, note, Instant.now())) {
            auditLogService.log(com.delivery.common.service.AuditActions.PAYMENT_RECONCILIATION_CASE_RESOLVED, "payment_reconciliation_case", found.getId().toString(),
                    found.getCaseType().name(), "SUCCESS");
        }
        return ReconciliationCaseResponse.of(found);
    }

    private boolean checkSucceeded(PaymentAttempt attempt) {
        Optional<Payment> payment = paymentRepository.findByTenantIdAndId(attempt.getTenantId(), attempt.getPaymentId());
        if (payment.isEmpty()) {
            return openForAttempt(PaymentReconciliationCase.Type.SUCCEEDED_NOT_CAPTURED, attempt, attempt.getAmount(), null, "payment_missing");
        }
        Payment found = payment.get();
        if (found.getAmount().compareTo(attempt.getAmount()) != 0) {
            return openForAttempt(PaymentReconciliationCase.Type.AMOUNT_MISMATCH, attempt, found.getAmount(), attempt.getAmount(), "attempt_amount");
        }
        if (!found.isCaptured()) {
            return openForAttempt(PaymentReconciliationCase.Type.SUCCEEDED_NOT_CAPTURED, attempt, found.getAmount(), attempt.getAmount(),
                    "payment_" + found.statusName().toLowerCase());
        }
        List<PaymentAttempt> successes = attemptRepository.findSucceededByPayment(attempt.getPaymentId());
        if (!successes.isEmpty() && !successes.get(0).getId().equals(attempt.getId())) {
            return openForAttempt(PaymentReconciliationCase.Type.DUPLICATE_SUCCESS, attempt, found.getAmount(), attempt.getAmount(), "second_success");
        }
        return false;
    }

    private boolean open(PaymentReconciliationCase.Type type, String dedupKey, UUID tenantId, UUID paymentId, UUID attemptId,
                         UUID orderId, String provider, BigDecimal expected, BigDecimal observed, String detailCode) {
        boolean inserted = caseRepository.openIfAbsent(UUID.randomUUID(), tenantId, type.name(), dedupKey, paymentId, attemptId,
                orderId, provider, expected, observed, detailCode) == 1;
        if (inserted) {
            metrics.caseOpened(type.name());
            log.warn("Payment reconciliation case opened: type={} payment={} attempt={}", type, paymentId, attemptId);
        }
        return inserted;
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
