package com.delivery.payment.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.OrderStockService;
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
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles local/mock payment confirmation and the administrative refund workflow (no PSP: refunds are records only). */
@Service
public class PaymentService {
    /** Order statuses that by themselves mean the goods left the store (V043 COMMIT set) — covers rows without a delivery. */
    private static final Set<OrderStatus> ORDER_STATUSES_PAST_PICKUP = EnumSet.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING, OrderStatus.DELIVERED);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final AuditLogService auditLogService;
    private final PaymentMapper mapper;
    private final TenantContext tenantContext;
    private final AppUserProfileRepository userProfileRepository;
    private final PaymentMockConfirmationProperties mockConfirmationProperties;
    private final DeliveryRepository deliveryRepository;
    private final OrderStockService orderStockService;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            RefundRepository refundRepository,
            AuditLogService auditLogService,
            PaymentMapper mapper,
            TenantContext tenantContext,
            AppUserProfileRepository userProfileRepository,
            PaymentMockConfirmationProperties mockConfirmationProperties,
            DeliveryRepository deliveryRepository,
            OrderStockService orderStockService) {
        this.orderStockService = orderStockService;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.userProfileRepository = userProfileRepository;
        this.mockConfirmationProperties = mockConfirmationProperties;
        this.deliveryRepository = deliveryRepository;
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

    /**
     * Creates a refund request idempotently for finance/admin review (INC-06, COR-04).
     * The payment row is locked (SELECT ... FOR UPDATE) so concurrent requests are serialized: the
     * amount must be positive and the sum of non-rejected refunds may never exceed payment.amount (422).
     * Reusing a key returns the existing refund; a key bound to another payment is a 409.
     */
    @Transactional
    public RefundResponse requestRefund(UUID paymentId, RefundRequest request) {
        UUID tenantId = tenantId();
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
        UUID tenantId = tenantId();
        Payment payment = paymentRepository.findByTenantIdAndIdForUpdate(tenantId, paymentId).orElseThrow(() -> new NotFoundException("Payment was not found"));
        boolean keyUsed = refundRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).isPresent();
        BigDecimal remaining = payment.getAmount().subtract(committedRefunds(tenantId, paymentId));
        if (!keyUsed && remaining.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(requestRefund(paymentId, new RefundRequest(remaining, reason, idempotencyKey)));
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
     * order, then inventory): the plain PIN is cleared unless a courier is engaged on the delivery
     * ({@link DeliveryStatus#isCourierEngaged}, V042 rule), and the stock reservation is released unless the goods
     * already left the store ({@link DeliveryStatus#isGoodsLeftTheStore}, or an order status past pickup — V043
     * rule). The release is idempotent (stock_status), so an order cancelled earlier is not released twice.
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
            auditLogService.log("REFUND_APPROVED", "refund", refund.getId().toString(), refund.getOrderId().toString(), "SUCCESS");
            if (approvedRefunds(tenantId, payment.getId(), refund).compareTo(payment.getAmount()) >= 0) {
                payment.markFullyRefunded();
                Order order = orderRepository.findByTenantIdAndId(tenantId, refund.getOrderId())
                        .orElseThrow(() -> new NotFoundException("Order was not found"));
                // One delivery per order (uq_deliveries_tenant_order); absent before dispatch.
                Optional<DeliveryStatus> deliveryStatus = deliveryRepository.findByTenantIdAndOrderId(tenantId, order.getId()).map(Delivery::getStatus);
                // Decide on the stock before markRefunded() changes the order status it reads.
                boolean goodsLeftTheStore = deliveryStatus.map(DeliveryStatus::isGoodsLeftTheStore).orElse(false)
                        || ORDER_STATUSES_PAST_PICKUP.contains(order.getStatus());
                if (order.getStatus() != OrderStatus.CANCELLED) {
                    order.markRefunded(deliveryStatus.map(DeliveryStatus::isCourierEngaged).orElse(false));
                    auditLogService.log("ORDER_REFUNDED", "order", order.getId().toString(), order.getReference(), "SUCCESS");
                }
                if (!goodsLeftTheStore) {
                    orderStockService.release(order); // INC-07 (COR-01): the sale is undone before pickup; no-op if already released/committed
                }
            } else {
                payment.markPartiallyRefunded();
            }
        }
        return toRefundResponse(refund);
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
