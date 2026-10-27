package com.delivery.payment.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.config.PaymentMockConfirmationProperties;
import com.delivery.payment.dto.PaymentResponse;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.mapper.PaymentMapper;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.repository.RefundRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles local/mock payment confirmation with idempotency. */
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final AuditLogService auditLogService;
    private final PaymentMapper mapper;
    private final TenantContext tenantContext;
    private final AppUserProfileRepository userProfileRepository;
    private final PaymentMockConfirmationProperties mockConfirmationProperties;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RefundRepository refundRepository,
            AuditLogService auditLogService,
            PaymentMapper mapper,
            TenantContext tenantContext,
            AppUserProfileRepository userProfileRepository,
            PaymentMockConfirmationProperties mockConfirmationProperties) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.userProfileRepository = userProfileRepository;
        this.mockConfirmationProperties = mockConfirmationProperties;
    }

    /**
     * Manually confirms a pending mock payment (development-only feature, AS-01). Disabled → 404 for
     * everyone; enabled → only the customer who owns the order, or OPS/ADMIN, may confirm. Duplicate
     * calls return the same confirmed result without a second audit entry.
     *
     * TODO(FR-030/FR-032): once a PSP is integrated, confirmation must come exclusively from the
     * authenticated PSP webhook / status query; this manual path must stay disabled in production.
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
        Order order = orderRepository.findByTenantIdAndId(tenantId, payment.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
        // Ownership before the key/id check so a customer cannot probe other customers' payments; 404 hides existence.
        if (!staff && !order.getCustomerId().equals(currentCustomerId(tenantId))) {
            throw new NotFoundException("Payment was not found");
        }
        if (!payment.getId().equals(paymentId)) {
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another payment", HttpStatus.CONFLICT);
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return mapper.toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING_CONFIRMATION || order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessException("payment_not_confirmable", "Payment can no longer be confirmed", HttpStatus.CONFLICT);
        }
        payment.confirm();
        order.markPaymentConfirmed();
        auditLogService.log("PAYMENT_CONFIRMED", "payment", payment.getId().toString(), order.getReference(), "SUCCESS");
        return mapper.toResponse(payment);
    }

    /** Creates a refund request idempotently for finance/admin review. */
    @Transactional
    public RefundResponse requestRefund(UUID paymentId, RefundRequest request) {
        UUID tenantId = tenantId();
        Payment payment = paymentRepository.findByTenantIdAndId(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        Refund refund = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, request.idempotencyKey())
                .orElseGet(() -> refundRepository.save(new Refund(UUID.randomUUID(), tenantId, paymentId, payment.getOrderId(), request.amount(), request.reason(), RefundStatus.REQUESTED, request.idempotencyKey())));
        if (!refund.getPaymentId().equals(paymentId)) {
            throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another refund", HttpStatus.CONFLICT);
        }
        return toRefundResponse(refund);
    }

    /** Approves a pending refund and marks it refunded once. */
    @Transactional
    public RefundResponse approveRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REJECTED) {
            throw new BusinessException("refund_rejected", "Rejected refunds cannot be approved", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            refund.approve();
            auditLogService.log("REFUND_APPROVED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
        }
        return toRefundResponse(refund);
    }

    /** Rejects a pending refund once and audits the decision. */
    @Transactional
    public RefundResponse rejectRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(() -> new NotFoundException("Refund was not found"));
        if (refund.getStatus() == RefundStatus.REFUNDED) {
            throw new BusinessException("refund_completed", "Refund already completed", HttpStatus.CONFLICT);
        }
        if (refund.getStatus() != RefundStatus.REJECTED) {
            refund.reject();
            auditLogService.log("REFUND_REJECTED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
        }
        return toRefundResponse(refund);
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
