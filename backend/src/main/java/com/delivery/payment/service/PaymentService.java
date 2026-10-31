package com.delivery.payment.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.common.service.StatusChangeActor;
import com.delivery.payment.PaymentConfirmed;
import com.delivery.payment.RefundApproved;
import com.delivery.payment.api.PaymentOrderHooks;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.PaymentStatusHistory;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.PaymentStatusHistoryRepository;
import com.delivery.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles payment confirmation (provider-verified in production, {@link #confirmFromProvider}; local/mock in dev) and the
 * administrative refund workflow (no PSP payout yet: refunds are records only).
 * Order-side effects go through {@link PaymentOrderHooks} (implemented by the order module, same transaction —
 * ADR-0002 C1: payment does not depend on order or delivery).
 */
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentOrderHooks orderHooks;
    private final RefundRepository refundRepository;
    private final AuditLogService auditLogService;
    private final PaymentMapper mapper;
    private final TenantContext tenantContext;
    private final AppUserProfileRepository userProfileRepository;
    private final PaymentMockConfirmationProperties mockConfirmationProperties;
    private final PaymentStatusHistoryRepository historyRepository;
    /** Payments and refunds carry no currency column yet: one currency per tenant (ADR-0003), MZN today. */
    static final String CURRENCY = "MZN";
    private ApplicationEventPublisher eventPublisher = event -> { };

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentOrderHooks orderHooks,
            RefundRepository refundRepository,
            AuditLogService auditLogService,
            PaymentMapper mapper,
            TenantContext tenantContext,
            AppUserProfileRepository userProfileRepository,
            PaymentMockConfirmationProperties mockConfirmationProperties,
            PaymentStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
        this.paymentRepository = paymentRepository;
        this.orderHooks = orderHooks;
        this.refundRepository = refundRepository;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.userProfileRepository = userProfileRepository;
        this.mockConfirmationProperties = mockConfirmationProperties;
    }

    /**
     * Optional (LAC-33): publishes {@link PaymentConfirmed}/{@link RefundApproved} into the transactional event registry.
     * Unit tests that build the service by hand keep the no-op publisher.
     */
    @Autowired(required = false)
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Manually confirms a pending mock payment (development-only feature, AS-01). Disabled → 404 for
     * everyone; enabled → only the customer who owns the order, or OPS/ADMIN, may confirm. Duplicate
     * calls return the same confirmed result without a second audit entry.
     *
     * LAC-01 (W7-A): the endpoint is not mapped at all in the prod profile ({@code MockPaymentConfirmationController} is
     * {@code @Profile("!prod")}); production confirmation comes only from {@link #confirmFromProvider}.
     */
    @Transactional
    public PaymentResponse confirm(UUID paymentId, String idempotencyKey) {
        if (!mockConfirmationProperties.isEnabled()) {
            throw new NotFoundException("Payment was not found");
        }
        boolean staff = hasAuthority("ROLE_OPS") || hasAuthority("ROLE_ADMIN");
        if (!staff && !hasAuthority("ROLE_CUSTOMER")) {
            throw new BusinessException("forbidden", "You do not have permission to perform this action", HttpStatus.FORBIDDEN);
        }
        UUID tenantId = tenantId();
        Payment payment = paymentRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .orElseGet(() -> paymentRepository.findByTenantIdAndId(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found")));
        PaymentOrderHooks.OrderForPayment order = orderHooks.orderForPayment(tenantId, payment.getOrderId());
        // Ownership before the key/id check so a customer cannot probe other customers' payments; 404 hides existence.
        if (!staff && !order.customerId().equals(currentCustomerId(tenantId))) {
            throw new NotFoundException("Payment was not found");
        }
        if (!payment.getId().equals(paymentId)) {
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another payment", HttpStatus.CONFLICT);
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return mapper.toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING_CONFIRMATION || !order.awaitingPayment()) {
            throw new BusinessException("payment_not_confirmable", "Payment can no longer be confirmed", HttpStatus.CONFLICT);
        }
        PaymentStatus previous = payment.getStatus();
        payment.confirm();
        recordStatusChange(payment, previous, "MOCK_CONFIRMATION");
        publishConfirmation(tenantId, payment);
        auditLogService.log("PAYMENT_CONFIRMED", "payment", payment.getId().toString(), order.reference(), "SUCCESS");
        return mapper.toResponse(payment);
    }

    /** Result of {@link #confirmFromProvider}. */
    public enum ProviderConfirmation {
        /** PENDING_CONFIRMATION → CONFIRMED, order PAYMENT_PENDING → PAYMENT_CONFIRMED, PaymentConfirmed published. */
        CONFIRMED,
        /** The payment was already captured (another attempt or the mock path): nothing changed. */
        ALREADY_CAPTURED,
        /** The payment/order can no longer be confirmed (expired, cancelled): nothing changed. */
        NOT_CONFIRMABLE
    }

    /**
     * Production confirmation path (LAC-01/LAC-31, W7-A): confirms {@code payment} after a provider-verified success
     * (signed webhook, status query or synchronous PSP answer). Same effects as the mock confirmation — payment
     * CONFIRMED (provider recorded), history row {@code PSP_CONFIRMATION} by actor SYSTEM {@code psp:<provider>}, order
     * PAYMENT_PENDING → PAYMENT_CONFIRMED through {@link PaymentOrderHooks}, {@link PaymentConfirmed} for the ledger and
     * audit {@code PAYMENT_CONFIRMED}. The caller must already hold the payment row lock in its transaction (lock order:
     * payment first) — {@code payment} is that locked entity.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProviderConfirmation confirmFromProvider(Payment payment, String provider) {
        if (payment.isCaptured()) {
            return ProviderConfirmation.ALREADY_CAPTURED;
        }
        UUID tenantId = payment.getTenantId();
        PaymentOrderHooks.OrderForPayment order = orderHooks.orderForPayment(tenantId, payment.getOrderId());
        if (payment.getStatus() != PaymentStatus.PENDING_CONFIRMATION || !order.awaitingPayment()) {
            return ProviderConfirmation.NOT_CONFIRMABLE;
        }
        PaymentStatus previous = payment.getStatus();
        payment.confirmByProvider(provider.toUpperCase(java.util.Locale.ROOT));
        StatusChangeActor current = StatusChangeActor.current(tenantContext);
        recordStatusChange(payment, previous, "PSP_CONFIRMATION",
                new StatusChangeActor(StatusChangeActor.SYSTEM, "psp:" + provider, current.correlationId()));
        publishConfirmation(tenantId, payment);
        auditLogService.logForTenant(tenantId, "PAYMENT_CONFIRMED", "payment", payment.getId().toString(), order.reference(),
                "SUCCESS", java.util.Map.of("provider", provider));
        return ProviderConfirmation.CONFIRMED;
    }

    /** Order side + ledger event of a confirmation, shared by the mock and the provider paths. */
    private void publishConfirmation(UUID tenantId, Payment payment) {
        orderHooks.markPaymentConfirmed(tenantId, payment.getOrderId());
        eventPublisher.publishEvent(new PaymentConfirmed(tenantId, payment.getId(), payment.getOrderId(), payment.getAmount(),
                CURRENCY, payment.getConfirmedAt()));
    }

    /**
     * Creates a refund request idempotently for finance/admin review (INC-06, COR-04).
     * The payment row is locked (SELECT ... FOR UPDATE) so concurrent requests are serialized: the
     * amount must be positive and the sum of non-rejected refunds may never exceed payment.amount (422).
     * Reusing a key returns the existing refund; a key bound to another payment is a 409.
     */
    @Transactional
    public RefundResponse requestRefund(UUID paymentId, RefundRequest request) {
        return requestRefundForTenant(tenantId(), paymentId, request);
    }

    /** {@link #requestRefund} for an explicit tenant (scheduled jobs run without a tenant context). */
    @Transactional
    public RefundResponse requestRefundForTenant(UUID tenantId, UUID paymentId, RefundRequest request) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        Refund existing = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getPaymentId().equals(paymentId)) {
                throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another refund", HttpStatus.CONFLICT);
            }
            return toRefundResponse(existing);
        }
        BigDecimal amount = request.amount();
        BigDecimal refundable = payment.getAmount().subtract(committedRefunds(tenantId, paymentId));
        if (amount == null || amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
            throw new BusinessException("invalid_refund_amount",
                    "Refund amount must be positive and not exceed the payment minus refunds already requested (" + refundable + ")",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Refund refund;
        try {
            refund = refundRepository.saveAndFlush(new Refund(UUID.randomUUID(), tenantId, paymentId, payment.getOrderId(), amount,
                    request.reason(), RefundStatus.REQUESTED, request.idempotencyKey()));
        } catch (DataIntegrityViolationException e) {
            // uq_refunds_tenant_idempotency (V040): the key was taken concurrently by a refund of another payment.
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another refund", HttpStatus.CONFLICT);
        }
        return toRefundResponse(refund);
    }

    /**
     * Requests a refund of whatever is still refundable on the payment: amount − Σ(non-rejected refunds), so
     * completed and pending refunds are never duplicated. Returns empty (no refund row) when nothing remains.
     * Runs on the same locked path as {@link #requestRefund}; reusing the idempotency key returns the existing refund.
     *
     * PSP seam: there is no PSP yet, so the refund is only created REQUESTED inside the caller's transaction.
     * When a PSP is integrated, the payout call must run AFTER COMMIT (never inside this transaction), be
     * idempotent by the refund's idempotency key, and record a PSP failure on the refund itself (a retryable
     * state) — a PSP failure must never roll back the order cancellation that created the refund.
     */
    @Transactional
    public Optional<RefundResponse> requestRemainingRefund(UUID paymentId, String reason, String idempotencyKey) {
        return requestRemainingRefundForTenant(tenantId(), paymentId, reason, idempotencyKey);
    }

    /** {@link #requestRemainingRefund} for an explicit tenant (AcceptanceTimeoutJob runs without a tenant context). */
    @Transactional
    public Optional<RefundResponse> requestRemainingRefundForTenant(UUID tenantId, UUID paymentId, String reason, String idempotencyKey) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        boolean keyUsed = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).isPresent();
        BigDecimal remaining = payment.getAmount().subtract(committedRefunds(tenantId, paymentId));
        if (!keyUsed && remaining.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(requestRefundForTenant(tenantId, paymentId, new RefundRequest(remaining, reason, idempotencyKey)));
    }

    /**
     * Approves a pending refund once (tenant-scoped; another tenant's refund is a 404).
     * There is no PSP yet: approval is an administrative record only — NO money is returned to the
     * customer by this call. The payment carries the financial state: PARTIALLY_REFUNDED while approved
     * refunds cover part of the amount, FULLY_REFUNDED once they cover all of it. On full coverage the
     * order becomes REFUNDED, except a CANCELLED order, which stays CANCELLED (order and financial state
     * are independent); a partial approval leaves the order status unchanged.
     *
     * Full coverage also closes what the order's delivery row allows (lock order: payment row first, then the
     * order, then the delivery/dispatch rows, then inventory — done by the order module's
     * {@link PaymentOrderHooks#onPaymentFullyRefunded}): the plain PIN is always cleared (DeliveryService refuses
     * to progress or complete a REFUNDED order, 409 order_not_deliverable, so no courier needs it any more), and unless
     * the goods already left the store ({@code DeliveryStatus.isGoodsLeftTheStore}, or an order status past pickup —
     * V043 rule) the delivery is retired (CANCELLED, active dispatch job CANCELLED) and the stock reservation released.
     * Both are idempotent (delivery status, stock_status), so an order cancelled earlier is not retired or released
     * twice. After pickup the delivery is left as it is (the goods are out; ops resolve it).
     */
    @Transactional
    public RefundResponse approveRefund(UUID refundId) {
        UUID tenantId = tenantId();
        Refund refund = refundRepository.findByTenantIdAndId(tenantId, refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REJECTED) {
            throw new BusinessException("refund_rejected", "Rejected refunds cannot be approved", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            // Lock the payment so concurrent approvals of partial refunds see each other when summing.
            Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, refund.getPaymentId())
                    .orElseThrow(() -> new NotFoundException("Payment was not found"));
            refund.approve();
            eventPublisher.publishEvent(new RefundApproved(tenantId, refund.getId(), refund.getPaymentId(), refund.getOrderId(),
                    refund.getAmount(), CURRENCY, refund.getUpdatedAt()));
            auditLogService.log("REFUND_APPROVED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
            PaymentStatus previous = payment.getStatus();
            if (approvedRefunds(tenantId, payment.getId(), refund).compareTo(payment.getAmount()) >= 0) {
                payment.markFullyRefunded();
                recordStatusChange(payment, previous, "REFUND_APPROVED");
                orderHooks.onPaymentFullyRefunded(tenantId, refund.getOrderId());
            } else {
                payment.markPartiallyRefunded();
                recordStatusChange(payment, previous, "REFUND_APPROVED");
            }
        }
        return toRefundResponse(refund);
    }

    /** Records the creation of a payment at checkout (history row {@code null -> PENDING_CONFIRMATION}, V058). */
    @Transactional
    public void recordCreated(Payment payment) {
        recordStatusChange(payment, null, "CHECKOUT");
    }

    /**
     * PaymentExpiryJob (W4-A): a payment still {@code PENDING_CONFIRMATION} becomes {@code CANCELLED} (nothing was
     * captured). Locks the payment row FOR UPDATE (re-entrant after the job's SKIP LOCKED claim). Returns false — and
     * changes nothing — when the payment is in any other status (confirmed meanwhile, already cancelled).
     */
    @Transactional
    public boolean expirePending(UUID tenantId, UUID paymentId) {
        return cancelPending(tenantId, paymentId, "PAYMENT_EXPIRED");
    }

    /**
     * A payment still {@code PENDING_CONFIRMATION} becomes {@code CANCELLED} (nothing was captured), recording the fixed
     * code {@code reason} in its history — payment expiry and the customer's free cancellation (LAC-26). Locks the row
     * FOR UPDATE (re-entrant). Returns false, changing nothing, when the payment is in any other status.
     */
    @Transactional
    public boolean cancelPending(UUID tenantId, UUID paymentId, String reason) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        if (payment.getStatus() != PaymentStatus.PENDING_CONFIRMATION) {
            return false;
        }
        payment.markCancelled();
        recordStatusChange(payment, PaymentStatus.PENDING_CONFIRMATION, reason);
        return true;
    }

    /**
     * Customer cancellation with a fee (LAC-26): requests a refund of {@code remaining − withheld}, where remaining is
     * {@code amount − Σ non-rejected refunds}; the withheld fee stays captured. Same locked path and idempotency as
     * {@link #requestRemainingRefundForTenant}; returns empty (no refund row) when nothing is left after the fee.
     */
    @Transactional
    public Optional<RefundResponse> requestRemainingRefundLessForTenant(UUID tenantId, UUID paymentId, BigDecimal withheld,
                                                                        String reason, String idempotencyKey) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        boolean keyUsed = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).isPresent();
        BigDecimal remaining = payment.getAmount().subtract(committedRefunds(tenantId, paymentId)).subtract(withheld);
        if (!keyUsed && remaining.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(requestRefundForTenant(tenantId, paymentId, new RefundRequest(remaining, reason, idempotencyKey)));
    }

    /** Amount of the tenant's refund created with {@code idempotencyKey}, if any (read-only; idempotent replies). */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> refundAmountByKey(UUID tenantId, String idempotencyKey) {
        return refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).map(Refund::getAmount);
    }

    /** What can still be refunded on the payment:{@code amount − Σ non-rejected refunds} (locks the payment row). */
    @Transactional
    public BigDecimal refundableAmount(UUID tenantId, UUID paymentId) {
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        return payment.getAmount().subtract(committedRefunds(tenantId, paymentId));
    }

    /** Appends a payment_status_history row in the current transaction when the status actually changed. */
    private void recordStatusChange(Payment payment, PaymentStatus previous, String reason) {
        recordStatusChange(payment, previous, reason, StatusChangeActor.current(tenantContext));
    }

    private void recordStatusChange(Payment payment, PaymentStatus previous, String reason, StatusChangeActor actor) {
        if (previous == payment.getStatus()) {
            return;
        }
        historyRepository.save(new PaymentStatusHistory(payment, previous, actor.actorType(), actor.actorId(), reason,
                actor.correlationId(), Instant.now()));
    }

    /** Rejects a pending refund once (tenant-scoped; another tenant's refund is a 404) and audits the decision. */
    @Transactional
    public RefundResponse rejectRefund(UUID refundId) {
        Refund refund = refundRepository.findByTenantIdAndId(tenantId(), refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REFUNDED) {
            throw new BusinessException("refund_completed", "Refund already completed", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REJECTED) {
            refund.reject();
            auditLogService.log("REFUND_REJECTED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
        }
        return toRefundResponse(refund);
    }

    /** Sum of refunds for the payment that still count against it (anything not REJECTED). */
    private BigDecimal committedRefunds(UUID tenantId, UUID paymentId) {
        return refundRepository.findByTenantIdAndPaymentId(tenantId, paymentId).stream()
                .filter(refund -> refund.getStatus() != RefundStatus.REJECTED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sum of REFUNDED refunds for the payment, always counting {@code justApproved}. */
    private BigDecimal approvedRefunds(UUID tenantId, UUID paymentId, Refund justApproved) {
        BigDecimal others = refundRepository.findByTenantIdAndPaymentId(tenantId, paymentId).stream()
                .filter(refund -> !refund.getId().equals(justApproved.getId()))
                .filter(refund -> refund.getStatus() == RefundStatus.REFUNDED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return others.add(justApproved.getAmount());
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getPaymentId(), refund.getOrderId(), refund.getAmount(), refund.getReason(), refund.getStatus());
    }

    private UUID currentCustomerId(UUID tenantId) {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        return userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .map(AppUserProfile::getId)
                .orElseThrow(() -> new NotFoundException("Payment was not found"));
    }

    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
