package com.delivery.order.service;

import com.delivery.auth.api.UserAccounts;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.api.DeliveryRetirementService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.api.OrderStateMachine;
import com.delivery.order.dto.CancellationRequestResponse;
import com.delivery.order.dto.CustomerCancellationResponse;
import com.delivery.order.dto.CustomerCancellationResponse.Outcome;
import com.delivery.order.entity.CancellationReason;
import com.delivery.order.entity.CancellationRequest;
import com.delivery.order.entity.CancellationRequestClosure;
import com.delivery.order.entity.CancellationRequestStatus;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.CancellationRequestRepository;
import com.delivery.order.repository.CustomerOrderQueryRepository;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.CustomerCancellationPolicy.Phase;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer cancellation (LAC-26, W5-A) per {@link CustomerCancellationPolicy}: the customer's own
 * {@code POST /me/orders/{id}/cancel} (free cancellation before acceptance, a request after it) and the OPS/ADMIN
 * decision on requests. Every money path follows the lock order rule: the order's payment row {@code FOR UPDATE}
 * first, then the order, then the request row, the delivery/dispatch rows (retirement) and finally inventory; the
 * refund (re-entrant lock on the same payment row) is created REQUESTED in the same transaction. A concurrent vendor
 * transition of the same order (accept, preparing, ready) loses or wins on the order's {@code @Version}: exactly one of
 * them commits, the other answers 409 {@code concurrent_modification}/{@code invalid_order_transition} with no effect.
 */
@Service
public class CustomerCancellationService {
    /** Idempotency key prefix of the customer-cancellation refund (`customer-cancel-<orderId>`), both phases. */
    public static final String REFUND_KEY_PREFIX = "customer-cancel-";

    private final OrderRepository orderRepository;
    private final CustomerOrderQueryRepository customerOrders;
    private final CancellationRequestRepository requestRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OrderStateMachine stateMachine;
    private final OrderStockService orderStockService;
    private final DeliveryRetirementService deliveryRetirementService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final UserAccounts userAccounts;
    private final TenantContext tenantContext;
    private final BigDecimal feeRate;

    public CustomerCancellationService(OrderRepository orderRepository, CustomerOrderQueryRepository customerOrders,
                                       CancellationRequestRepository requestRepository, PaymentRepository paymentRepository,
                                       PaymentService paymentService, OrderStateMachine stateMachine,
                                       OrderStockService orderStockService, DeliveryRetirementService deliveryRetirementService,
                                       NotificationService notificationService, AuditLogService auditLogService,
                                       UserAccounts userAccounts, TenantContext tenantContext,
                                       @Value("${app.orders.cancellation.fee-rate:0.10}") BigDecimal feeRate) {
        if (feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("app.orders.cancellation.fee-rate must be between 0 and 1, was " + feeRate);
        }
        this.orderRepository = orderRepository;
        this.customerOrders = customerOrders;
        this.requestRepository = requestRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.stateMachine = stateMachine;
        this.orderStockService = orderStockService;
        this.deliveryRetirementService = deliveryRetirementService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.userAccounts = userAccounts;
        this.tenantContext = tenantContext;
        this.feeRate = feeRate;
    }

    /** The configured fee rate (fraction of the subtotal) charged when an accepted order is cancelled on request. */
    public BigDecimal feeRate() {
        return feeRate;
    }

    /**
     * The customer cancels (or asks to cancel) one of their own orders; another customer's order is 404. Free phase →
     * CANCELLED at once ({@code CUSTOMER_CANCELLED}: pending payment cancelled, captured payment refunded in full, delivery
     * retired, stock released, customer notified, audited). Request phase → an open request (created once; a repeat
     * returns it). Already CANCELLED → the current state, no new effect. READY_FOR_PICKUP onwards → 409
     * {@code order_not_cancellable} (an open request of that order is closed as ORDER_ADVANCED/ORDER_CLOSED).
     */
    @Transactional(noRollbackFor = OrderNotCancellableException.class)
    public CustomerCancellationResponse cancelMine(UUID orderId, String reason) {
        UUID tenantId = tenantId();
        UUID customerId = currentCustomerId(tenantId);
        if (!customerOrders.existsByTenantIdAndIdAndCustomerId(tenantId, orderId, customerId)) {
            throw new NotFoundException("Order was not found");
        }
        // Lock order rule: payment row first, then the order.
        Payment payment = paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        return switch (CustomerCancellationPolicy.phaseOf(order.getStatus())) {
            case ALREADY_CANCELLED -> cancelledResponse(order, null);
            case NOT_CANCELLABLE -> {
                closeStaleRequest(order, "customer");
                throw new OrderNotCancellableException(order.getStatus());
            }
            case FREE -> cancelFree(order, payment);
            case REQUEST -> requestCancellation(order, customerId, reason);
        };
    }

    private CustomerCancellationResponse cancelFree(Order order, Payment payment) {
        UUID tenantId = order.getTenantId();
        stateMachine.cancel(order, CancellationReason.CUSTOMER_CANCELLED, null);
        // Delivery/dispatch rows, then inventory, then the payment side (re-entrant lock on the same payment row).
        deliveryRetirementService.retireBeforePickup(tenantId, order.getId(), "customer cancelled the order");
        orderStockService.release(order);
        BigDecimal refunded = null;
        if (payment != null && payment.isPendingConfirmation()) {
            paymentService.cancelPending(tenantId, payment.getId(), CancellationReason.CUSTOMER_CANCELLED.name());
        } else if (payment != null && payment.isCaptured()) {
            refunded = paymentService.requestRemainingRefundForTenant(tenantId, payment.getId(), "Customer cancellation",
                            REFUND_KEY_PREFIX + order.getId())
                    .map(RefundResponse::amount)
                    .orElse(BigDecimal.ZERO);
        }
        notificationService.createForTenant(tenantId, order.getCustomerId(), "CUSTOMER", "order_cancelled_by_customer", "Order Cancelled",
                String.format("Your order %s was cancelled.%s", order.getReference(),
                        refunded != null && refunded.signum() > 0 ? " The amount paid will be refunded." : ""),
                order.getReference());
        auditLogService.log(AuditActions.ORDER_CANCELLED_BY_CUSTOMER, "order", order.getId().toString(),
                "refund_remaining=" + money(refunded == null ? BigDecimal.ZERO : refunded), "SUCCESS");
        return new CustomerCancellationResponse(order.getId(), order.getStatus(), order.getCancellationReason(), Outcome.CANCELLED,
                refunded, null);
    }

    private CustomerCancellationResponse requestCancellation(Order order, UUID customerId, String reason) {
        CancellationRequest open = requestRepository
                .findByTenantIdAndOrderIdAndStatus(order.getTenantId(), order.getId(), CancellationRequestStatus.REQUESTED)
                .orElse(null);
        if (open == null) {
            // Concurrent requests of one order serialize on the payment lock; the partial unique index is the backstop.
            open = requestRepository.saveAndFlush(new CancellationRequest(order.getTenantId(), order.getId(), customerId,
                    normalize(reason), feeRate, CustomerCancellationPolicy.fee(order.getSubtotal(), feeRate)));
            // The customer's text stays on the request only (free text is never written to the audit log).
            auditLogService.log(AuditActions.ORDER_CANCELLATION_REQUESTED, "cancellation_request", open.getId().toString(),
                    "order=" + order.getId() + ";fee=" + money(open.getFeeAmount()), "SUCCESS");
        }
        return new CustomerCancellationResponse(order.getId(), order.getStatus(), order.getCancellationReason(), Outcome.REQUESTED,
                null, toResponse(open, order));
    }

    /**
     * OPS/ADMIN approve an open request: the order becomes CANCELLED ({@code CUSTOMER_CANCELLED_WITH_FEE}), the delivery
     * is retired, the stock released and {@code refundable − fee} refunded (fee = the quoted fee capped at what is
     * still refundable; no refund row when nothing is left), the customer notified, audited. A repeat on an APPROVED
     * request returns it; a REJECTED one is 409 {@code cancellation_request_closed}. If the order moved on
     * (READY_FOR_PICKUP onwards, or ended by another path) the request is closed and the call answers 409
     * {@code order_not_cancellable} — the closure is committed.
     */
    @Transactional(noRollbackFor = OrderNotCancellableException.class)
    public CancellationRequestResponse approve(UUID requestId) {
        UUID tenantId = tenantId();
        UUID orderId = requestRepository.findOrderIdByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new NotFoundException("Cancellation request was not found"));
        // Lock order rule: payment row, then the order, then the request row.
        Payment payment = paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        CancellationRequest request = lockRequest(tenantId, requestId);
        if (request.getStatus() == CancellationRequestStatus.APPROVED) {
            return toResponse(request, order); // idempotent repeat
        }
        if (!request.isOpen()) {
            throw new BusinessException("cancellation_request_closed", "The cancellation request was already decided", HttpStatus.CONFLICT);
        }
        if (CustomerCancellationPolicy.phaseOf(order.getStatus()) != Phase.REQUEST) {
            closeStaleRequest(order, actor());
            throw new OrderNotCancellableException(order.getStatus());
        }
        BigDecimal fee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean captured = payment != null && payment.isCaptured();
        if (captured) {
            fee = CustomerCancellationPolicy.appliedFee(request.getFeeAmount(), paymentService.refundableAmount(tenantId, payment.getId()));
        }
        stateMachine.cancel(order, CancellationReason.CUSTOMER_CANCELLED_WITH_FEE, null);
        deliveryRetirementService.retireBeforePickup(tenantId, orderId, "customer cancellation approved");
        orderStockService.release(order);
        BigDecimal refunded = BigDecimal.ZERO;
        if (captured) {
            refunded = paymentService.requestRemainingRefundLessForTenant(tenantId, payment.getId(), fee,
                            "Customer cancellation (fee withheld)", REFUND_KEY_PREFIX + orderId)
                    .map(RefundResponse::amount)
                    .orElse(BigDecimal.ZERO);
        } else if (payment != null && payment.isPendingConfirmation()) {
            paymentService.cancelPending(tenantId, payment.getId(), CancellationReason.CUSTOMER_CANCELLED_WITH_FEE.name());
        }
        request.approve(actor(), fee, refunded.setScale(2, RoundingMode.HALF_UP));
        notificationService.create(order.getCustomerId(), "CUSTOMER", "order_cancellation_approved", "Cancellation Approved",
                String.format("Your cancellation of order %s was approved. A cancellation fee of %s was withheld; %s will be refunded.",
                        order.getReference(), money(fee), money(refunded)),
                order.getReference());
        auditLogService.log(AuditActions.ORDER_CANCELLATION_APPROVED, "order", orderId.toString(),
                "refund_remaining=" + money(refunded) + ";fee=" + money(fee), "SUCCESS", Map.of("requestId", requestId.toString()));
        return toResponse(request, order);
    }

    /**
     * OPS/ADMIN reject an open request: the order continues. A repeat on a REJECTED request returns it; an APPROVED one
     * is 409 {@code cancellation_request_closed}. Locks only the request row (no money or order change).
     */
    @Transactional
    public CancellationRequestResponse reject(UUID requestId) {
        UUID tenantId = tenantId();
        CancellationRequest request = lockRequest(tenantId, requestId);
        Order order = orderRepository.findByTenantIdAndId(tenantId, request.getOrderId()).orElse(null);
        if (request.getStatus() == CancellationRequestStatus.REJECTED) {
            return toResponse(request, order);
        }
        if (!request.isOpen()) {
            throw new BusinessException("cancellation_request_closed", "The cancellation request was already decided", HttpStatus.CONFLICT);
        }
        request.close(CancellationRequestClosure.OPS_REJECTED, actor());
        if (order != null) {
            notificationService.create(order.getCustomerId(), "CUSTOMER", "order_cancellation_rejected", "Cancellation Rejected",
                    String.format("Your request to cancel order %s was not approved; the order continues.", order.getReference()),
                    order.getReference());
        }
        auditLogService.log(AuditActions.ORDER_CANCELLATION_REJECTED, "cancellation_request", requestId.toString(),
                "order=" + request.getOrderId(), "SUCCESS");
        return toResponse(request, order);
    }

    /** OPS queue: one page of the tenant's requests in {@code status} (default REQUESTED), newest first. */
    @Transactional(readOnly = true)
    public PageResponse<CancellationRequestResponse> list(CancellationRequestStatus status, int page, int size) {
        UUID tenantId = tenantId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PageLimits.MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
        Page<CancellationRequest> rows = requestRepository.findByTenantIdAndStatus(tenantId,
                status == null ? CancellationRequestStatus.REQUESTED : status, pageable);
        Map<UUID, Order> orders = rows.isEmpty() ? Map.of()
                : orderRepository.findByTenantIdAndIdIn(tenantId, rows.map(CancellationRequest::getOrderId).toList()).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity()));
        return PageResponse.of(rows, r -> toResponse(r, orders.get(r.getOrderId())));
    }

    /** The order's most recent request, for the customer's order detail (null when there is none). */
    @Transactional(readOnly = true)
    public CancellationRequestResponse latestFor(Order order) {
        return requestRepository.findFirstByTenantIdAndOrderIdOrderByRequestedAtDescIdDesc(order.getTenantId(), order.getId())
                .map(r -> toResponse(r, order))
                .orElse(null);
    }

    private CustomerCancellationResponse cancelledResponse(Order order, CancellationRequestResponse request) {
        BigDecimal refunded = null;
        if (order.getCancellationReason() == CancellationReason.CUSTOMER_CANCELLED
                || order.getCancellationReason() == CancellationReason.CUSTOMER_CANCELLED_WITH_FEE) {
            refunded = paymentService.refundAmountByKey(order.getTenantId(), REFUND_KEY_PREFIX + order.getId()).orElse(null);
        }
        return new CustomerCancellationResponse(order.getId(), order.getStatus(), order.getCancellationReason(), Outcome.CANCELLED,
                refunded, request);
    }

    /** Closes the order's open request because the order left the request phase (no-op when there is none). */
    private void closeStaleRequest(Order order, String decidedBy) {
        requestRepository.findByTenantIdAndOrderIdAndStatus(order.getTenantId(), order.getId(), CancellationRequestStatus.REQUESTED)
                .ifPresent(open -> {
                    CancellationRequestClosure closure = order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED
                            ? CancellationRequestClosure.ORDER_CLOSED : CancellationRequestClosure.ORDER_ADVANCED;
                    open.close(closure, decidedBy);
                    auditLogService.log(AuditActions.ORDER_CANCELLATION_REQUEST_CLOSED, "cancellation_request", open.getId().toString(),
                            "order=" + order.getId() + ";closure=" + closure.name(), "SUCCESS");
                });
    }

    private CancellationRequest lockRequest(UUID tenantId, UUID requestId) {
        return requestRepository.findByTenantIdAndIdForUpdate(tenantId, requestId)
                .orElseThrow(() -> new NotFoundException("Cancellation request was not found"));
    }

    static CancellationRequestResponse toResponse(CancellationRequest r, Order order) {
        return new CancellationRequestResponse(r.getId(), r.getOrderId(), order == null ? null : order.getReference(),
                order == null ? null : order.getStatus(), r.getStatus(), r.getClosureReason(), r.getReason(), r.getFeeRate(),
                r.getFeeAmount(), r.getRefundAmount(), r.getRequestedAt(), r.getDecidedAt());
    }

    private static String normalize(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String trimmed = reason.strip();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String actor() {
        return tenantContext.currentKeycloakUserId().orElse("unknown");
    }

    private UUID currentCustomerId(UUID tenantId) {
        String keycloakUserId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        // No profile in this tenant: the customer has no orders here, so any order id is "not found".
        return userAccounts.profileIdOf(tenantId, keycloakUserId).orElseThrow(() -> new NotFoundException("Order was not found"));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
