package com.delivery.payment.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.auth.validation.PhoneNumberE164;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.payment.api.PaymentOrderHooks;
import com.delivery.payment.api.PaymentProvider;
import com.delivery.payment.config.PaymentAttemptProperties;
import com.delivery.payment.dto.OrderPaymentResponse;
import com.delivery.payment.dto.PaymentAttemptResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentAttempt;
import com.delivery.payment.entity.PaymentAttemptStatus;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.provider.PaymentProviderRegistry;
import com.delivery.payment.repository.PaymentAttemptRepository;
import com.delivery.payment.repository.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Customer payment attempts against the active {@link PaymentProvider} (LAC-31, W7-A). Creation runs in three steps so
 * no database transaction is open during the PSP call: (1) lock the payment row (lock order), check owner/state and
 * insert the attempt INITIATED; (2) call {@link PaymentProvider#initiate} — never retried, a timeout/error makes the
 * attempt UNKNOWN (never FAILED); (3) record the answer through {@link PaymentAttemptOutcomes}. Not {@code @Transactional}
 * on purpose (TransactionTemplate per step, like checkout).
 */
@Service
public class PaymentAttemptService {
    private static final Logger log = LoggerFactory.getLogger(PaymentAttemptService.class);
    private static final java.util.Set<String> STAFF = java.util.Set.of("ROLE_OPS", "ROLE_FINANCE", "ROLE_ADMIN");

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentOrderHooks orderHooks;
    private final UserAccounts userAccounts;
    private final TenantContext tenantContext;
    private final PaymentProviderRegistry providers;
    private final PaymentAttemptProperties properties;
    private final PaymentAttemptOutcomes outcomes;
    private final PaymentMetrics metrics;
    private final TransactionTemplate tx;

    public PaymentAttemptService(PaymentRepository paymentRepository, PaymentAttemptRepository attemptRepository,
                                 PaymentOrderHooks orderHooks, UserAccounts userAccounts, TenantContext tenantContext,
                                 PaymentProviderRegistry providers, PaymentAttemptProperties properties, PaymentAttemptOutcomes outcomes,
                                 PaymentMetrics metrics, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.orderHooks = orderHooks;
        this.userAccounts = userAccounts;
        this.tenantContext = tenantContext;
        this.providers = providers;
        this.properties = properties;
        this.outcomes = outcomes;
        this.metrics = metrics;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Starts a payment attempt for the caller's own pending payment and returns its state (202 for the client).
     * 404 for a payment that is not the caller's; 400 {@code invalid_msisdn}; 409 {@code payment_not_payable} (not
     * PENDING_CONFIRMATION or the order no longer PAYMENT_PENDING), {@code payment_attempt_in_progress} (an attempt is
     * still open — including UNKNOWN, which must be resolved before charging again) or {@code payment_attempts_exhausted}.
     */
    public PaymentAttemptResponse create(UUID paymentId, String rawMsisdn) {
        String msisdn = PhoneNumberE164.normalize(rawMsisdn).orElseThrow(() ->
                new BusinessException("invalid_msisdn", "The phone number is not a valid mobile number", HttpStatus.BAD_REQUEST));
        UUID tenantId = tenantId();
        UUID profileId = callerProfileId(tenantId);
        PaymentProvider provider = providers.active();
        PaymentAttempt created = tx.execute(status -> {
            Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId)
                    .orElseThrow(() -> new NotFoundException("Payment was not found"));
            PaymentOrderHooks.OrderForPayment order = orderHooks.orderForPayment(tenantId, payment.getOrderId());
            if (!order.customerId().equals(profileId)) {
                throw new NotFoundException("Payment was not found");
            }
            if (payment.getStatus() != PaymentStatus.PENDING_CONFIRMATION || !order.awaitingPayment()) {
                throw new BusinessException("payment_not_payable", "This payment can no longer be paid", HttpStatus.CONFLICT);
            }
            if (attemptRepository.existsByPaymentIdAndStatusIn(paymentId, PaymentAttemptStatus.OPEN)) {
                throw inProgress();
            }
            if (attemptRepository.countByTenantIdAndPaymentId(tenantId, paymentId) >= properties.getMaxPerPayment()) {
                throw new BusinessException("payment_attempts_exhausted", "Too many payment attempts for this order", HttpStatus.CONFLICT);
            }
            Instant now = Instant.now();
            PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), tenantId, paymentId, payment.getOrderId(), provider.id(),
                    newOurReference(), payment.getAmount(), PaymentService.CURRENCY, sha256(msisdn), msisdn.substring(msisdn.length() - 3),
                    now, now.plus(properties.getPollAfter()));
            try {
                return attemptRepository.saveAndFlush(attempt);
            } catch (DataIntegrityViolationException e) {
                throw inProgress(); // uq_payment_attempts_active_payment: a concurrent request opened one first
            }
        });
        metrics.attempt(provider.id(), PaymentAttemptStatus.INITIATED.name());

        PaymentProvider.InitiationResult answer;
        try {
            answer = provider.initiate(new PaymentProvider.InitiationRequest(tenantId, created.getOurReference(), created.getAmount(),
                    created.getCurrency(), msisdn, "Pede Aqui"));
        } catch (RuntimeException e) {
            // Timeout or ambiguous answer: the PSP may have charged — UNKNOWN, resolved by the poller (never FAILED).
            log.warn("Payment provider initiate failed: provider={} attempt={} error={}", provider.id(), created.getId(),
                    e.getClass().getSimpleName());
            answer = new PaymentProvider.InitiationResult(null, null, "provider_unavailable");
        }
        PaymentProvider.InitiationResult result = answer;
        return tx.execute(status -> {
            PaymentAttemptOutcomes.Locked locked = outcomes.lock(attemptRepository.findKeysById(created.getId()).orElseThrow());
            PaymentAttemptStatus target = result.outcome() == null ? PaymentAttemptStatus.UNKNOWN : switch (result.outcome()) {
                case PENDING -> PaymentAttemptStatus.PENDING_CUSTOMER;
                case SUCCEEDED -> PaymentAttemptStatus.SUCCEEDED;
                case FAILED -> PaymentAttemptStatus.FAILED;
                case EXPIRED -> PaymentAttemptStatus.EXPIRED;
                case NOT_FOUND -> PaymentAttemptStatus.UNKNOWN;
            };
            // If a webhook already resolved the attempt in between, apply() ignores this older answer (state machine).
            outcomes.apply(locked, target, result.errorCode(), result.providerReference(), null, Instant.now());
            return PaymentAttemptResponse.of(locked.attempt());
        });
    }

    /** One attempt of a payment: the owning customer, or OPS/FINANCE/ADMIN of the tenant (others → 404). */
    public PaymentAttemptResponse get(UUID paymentId, UUID attemptId) {
        UUID tenantId = tenantId();
        return tx.execute(status -> {
            PaymentAttempt attempt = attemptRepository.findByTenantIdAndPaymentIdAndId(tenantId, paymentId, attemptId)
                    .orElseThrow(() -> new NotFoundException("Payment attempt was not found"));
            if (!isStaff()) {
                Payment payment = paymentRepository.findByTenantIdAndId(tenantId, paymentId)
                        .orElseThrow(() -> new NotFoundException("Payment attempt was not found"));
                if (!orderHooks.orderForPayment(tenantId, payment.getOrderId()).customerId().equals(callerProfileId(tenantId))) {
                    throw new NotFoundException("Payment attempt was not found");
                }
            }
            return PaymentAttemptResponse.of(attempt);
        });
    }

    /** The caller's view of their order's payment (polling target of the "confirm on your phone" screen). */
    public OrderPaymentResponse forOrder(UUID orderId) {
        UUID tenantId = tenantId();
        UUID profileId = callerProfileId(tenantId);
        return tx.execute(status -> {
            Payment payment = paymentRepository.findByTenantIdAndOrderId(tenantId, orderId)
                    .orElseThrow(() -> new NotFoundException("Payment was not found"));
            PaymentOrderHooks.OrderForPayment order = orderHooks.orderForPayment(tenantId, orderId);
            if (!order.customerId().equals(profileId)) {
                throw new NotFoundException("Payment was not found");
            }
            var attempts = attemptRepository.findByTenantIdAndPaymentIdOrderByCreatedAtDesc(tenantId, payment.getId());
            boolean open = attempts.stream().anyMatch(a -> a.getStatus().isOpen());
            boolean payable = payment.getStatus() == PaymentStatus.PENDING_CONFIRMATION && order.awaitingPayment() && !open
                    && attempts.size() < properties.getMaxPerPayment();
            return new OrderPaymentResponse(payment.getId(), orderId, payment.getStatus().name(), payment.getAmount(),
                    PaymentService.CURRENCY, payable, attempts.isEmpty() ? null : PaymentAttemptResponse.of(attempts.get(0)));
        });
    }

    private static BusinessException inProgress() {
        return new BusinessException("payment_attempt_in_progress",
                "A payment attempt is still in progress for this order", HttpStatus.CONFLICT);
    }

    /** 20 chars, [A-Z0-9] — fits M-Pesa's third-party reference. */
    static String newOurReference() {
        return "PA" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase(Locale.ROOT);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID callerProfileId(UUID tenantId) {
        String subject = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        return userAccounts.profileIdOf(tenantId, subject).orElseThrow(() -> new NotFoundException("Payment was not found"));
    }

    private static boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(STAFF::contains);
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
