package com.delivery.sales.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.notification.service.NotificationService;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.PaymentStatus;
import com.delivery.payment.entity.Refund;
import com.delivery.payment.entity.RefundStatus;
import com.delivery.payment.service.PaymentService;
import com.delivery.sales.config.SalesStatusOverrideProperties;
import com.delivery.sales.dto.SaleDetailResponse;
import com.delivery.sales.dto.SaleLineItemResponse;
import com.delivery.sales.dto.SalePaymentResponse;
import com.delivery.sales.dto.SaleRefundResponse;
import com.delivery.sales.dto.SalesActionResponse;
import com.delivery.sales.dto.SalesNotificationType;
import com.delivery.sales.dto.SalesPageResponse;
import com.delivery.sales.dto.SalesRowResponse;
import com.delivery.sales.dto.SalesSearchFilter;
import com.delivery.sales.repository.PaymentSummary;
import com.delivery.sales.repository.SalesCommissionReadRepository;
import com.delivery.sales.repository.SalesOrderSearchRepository;
import com.delivery.sales.repository.SalesPaymentReadRepository;
import com.delivery.sales.repository.SalesRefundReadRepository;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commercial (revenue) projection over existing orders plus guarded operational sales actions. */
@Service
public class SalesService {
    /** Pre-dispatch states in which a sale may still be cancelled (AC-6.1). */
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAYMENT_CONFIRMED,
            OrderStatus.ACCEPTED_BY_VENDOR,
            OrderStatus.PREPARING);
    /** Payment states that mean money was captured and is therefore refundable. */
    private static final Set<PaymentStatus> PAID_STATUSES = EnumSet.of(
            PaymentStatus.CONFIRMED,
            PaymentStatus.REFUND_PENDING,
            PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED);
    /** Allow-list transition matrix for the gated status override (plan §3.3). */
    private static final Map<OrderStatus, OrderStatus> OVERRIDE_ALLOW_LIST = Map.of(
            OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED,
            OrderStatus.ACCEPTED_BY_VENDOR, OrderStatus.PREPARING,
            OrderStatus.DISPATCH_PENDING, OrderStatus.ASSIGNED_TO_COURIER,
            OrderStatus.DELIVERING, OrderStatus.DELIVERED);
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final SalesOrderSearchRepository searchRepository;
    private final SalesPaymentReadRepository paymentReadRepository;
    private final SalesRefundReadRepository refundReadRepository;
    private final SalesCommissionReadRepository commissionReadRepository;
    private final VendorRepository vendorRepository;
    private final AppUserProfileRepository userProfileRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final SalesStatusOverrideProperties statusOverrideProperties;
    private final SalesAccessGuard accessGuard;

    public SalesService(
            OrderRepository orderRepository,
            SalesOrderSearchRepository searchRepository,
            SalesPaymentReadRepository paymentReadRepository,
            SalesRefundReadRepository refundReadRepository,
            SalesCommissionReadRepository commissionReadRepository,
            VendorRepository vendorRepository,
            AppUserProfileRepository userProfileRepository,
            PaymentService paymentService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TenantContext tenantContext,
            SalesStatusOverrideProperties statusOverrideProperties,
            SalesAccessGuard accessGuard) {
        this.orderRepository = orderRepository;
        this.searchRepository = searchRepository;
        this.paymentReadRepository = paymentReadRepository;
        this.refundReadRepository = refundReadRepository;
        this.commissionReadRepository = commissionReadRepository;
        this.vendorRepository = vendorRepository;
        this.userProfileRepository = userProfileRepository;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.statusOverrideProperties = statusOverrideProperties;
        this.accessGuard = accessGuard;
    }

    /** Returns paginated commercial sales rows; VENDOR_ADMIN is forced to their vendor, SUPPORT gets masked PII. */
    @Transactional(readOnly = true)
    public SalesPageResponse search(SalesSearchFilter filter) {
        UUID tenantId = tenantId();
        UUID effectiveVendorId = filter.vendorId();
        if (accessGuard.isVendorScoped()) {
            UUID ownVendorId = accessGuard.resolveOwnVendorId(tenantId);
            if (ownVendorId != null) {
                effectiveVendorId = ownVendorId;
            }
        }
        Specification<Order> spec = SalesOrderSpecifications.tenant(tenantId);
        if (filter.from() != null) spec = spec.and(SalesOrderSpecifications.createdFrom(filter.from()));
        if (filter.to() != null) spec = spec.and(SalesOrderSpecifications.createdTo(filter.to()));
        if (filter.status() != null && !filter.status().isBlank()) spec = spec.and(SalesOrderSpecifications.status(parseStatus(filter.status())));
        if (effectiveVendorId != null) spec = spec.and(SalesOrderSpecifications.vendor(effectiveVendorId));
        if (filter.productId() != null) spec = spec.and(SalesOrderSpecifications.productId(filter.productId()));
        if (filter.skuId() != null) spec = spec.and(SalesOrderSpecifications.skuId(filter.skuId()));
        if (filter.paymentProvider() != null && !filter.paymentProvider().isBlank()) spec = spec.and(SalesOrderSpecifications.paymentProvider(filter.paymentProvider()));
        if (filter.q() != null && !filter.q().isBlank()) spec = spec.and(SalesOrderSpecifications.freeText(filter.q()));

        int size = Math.min(Math.max(filter.size(), 1), MAX_PAGE_SIZE);
        int page = Math.max(filter.page(), 0);
        Page<Order> result = searchRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Order> orders = result.getContent();
        Map<UUID, String> vendorNames = vendorNames(orders);
        Map<UUID, String> customerNames = customerNames(orders);
        Map<UUID, PaymentSummary> paymentsByOrder = paymentsByOrder(tenantId, orders);
        boolean maskPii = accessGuard.shouldMaskCustomerPii();

        List<SalesRowResponse> rows = orders.stream()
                .map(order -> toRow(order, vendorNames, customerNames, paymentsByOrder.get(order.getId()), maskPii))
                .toList();
        return new SalesPageResponse(rows, page, size, result.getTotalElements());
    }

    /** Returns the sale detail with immutable item snapshots, promotion, payments, refunds, and commission. */
    @Transactional(readOnly = true)
    public SaleDetailResponse detail(UUID orderId) {
        UUID tenantId = tenantId();
        Order order = findOrder(tenantId, orderId);
        accessGuard.ensureOwnVendorOrder(tenantId, order.getVendorId());

        boolean maskPii = accessGuard.shouldMaskCustomerPii();
        Map<UUID, String> vendorNames = vendorNames(List.of(order));
        Map<UUID, String> customerNames = customerNames(List.of(order));
        List<PaymentSummary> payments = paymentReadRepository.findByTenantIdAndOrderId(tenantId, orderId);
        PaymentSummary primaryPayment = payments.isEmpty() ? null : payments.get(0);
        SalesRowResponse row = toRow(order, vendorNames, customerNames, primaryPayment, maskPii);

        List<SaleLineItemResponse> items = order.getItems().stream()
                .map(this::toLineItem)
                .toList();
        List<SalePaymentResponse> paymentRows = payments.stream()
                .map(payment -> new SalePaymentResponse(payment.getId(), payment.getAmount(), payment.getProvider(), payment.getStatus().name()))
                .toList();
        List<SaleRefundResponse> refundRows = refundReadRepository.findByTenantIdAndOrderId(tenantId, orderId).stream()
                .map(refund -> new SaleRefundResponse(refund.getId(), refund.getAmount(), refund.getStatus().name()))
                .toList();
        BigDecimal commission = commissionReadRepository.findByTenantIdAndOrderId(tenantId, orderId).stream()
                .map(item -> item.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SaleDetailResponse(
                row.orderId(), row.reference(), row.createdAt(), row.vendorId(), row.vendorName(), row.customerName(),
                row.itemCount(), row.subtotal(), row.fees(), row.taxes(), row.discountTotal(), row.total(),
                row.orderStatus(), row.paymentStatus(), row.paymentProvider(),
                items, order.getAppliedPromotionId(), paymentRows, refundRows, commission);
    }

    /** Cancels a sale through the existing order state machine; allowed only in pre-dispatch states (409 otherwise). */
    @Transactional
    public SalesActionResponse cancel(UUID orderId, String reason) {
        UUID tenantId = tenantId();
        Order order = findOrder(tenantId, orderId);
        accessGuard.ensureOwnVendorOrder(tenantId, order.getVendorId());
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("sale_not_cancellable", "Order can no longer be cancelled in status " + order.getStatus(), HttpStatus.CONFLICT);
        }
        order.markCancelled();
        notificationService.create(order.getCustomerId(), "CUSTOMER", "order_cancelled", "Order Cancelled",
                String.format("Your order %s was cancelled. Reason: %s", order.getReference(), reason), order.getReference());
        auditLogService.log("SALE_CANCELLED", "order", orderId.toString(), order.getReference() + " reason=" + reason, "SUCCESS");
        return new SalesActionResponse(order.getId(), order.getReference(), order.getStatus().name());
    }

    /**
     * Creates a full (amount omitted) or partial refund via the existing finance path.
     * Capped at paid − alreadyRefunded (422) and idempotent on the Idempotency-Key header.
     */
    @Transactional
    public RefundResponse refund(UUID orderId, BigDecimal amount, String reason, String idempotencyKey) {
        UUID tenantId = tenantId();
        Order order = findOrder(tenantId, orderId);
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? "sales-refund-" + UUID.randomUUID() : idempotencyKey;

        // Idempotent retry: an existing refund for this key is returned as-is, no double refund.
        Refund existing = refundReadRepository.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(orderId)) {
                throw new BusinessException("idempotency_conflict", "Idempotency key belongs to another order", HttpStatus.CONFLICT);
            }
            return new RefundResponse(existing.getId(), existing.getPaymentId(), existing.getOrderId(), existing.getAmount(), existing.getReason(), existing.getStatus());
        }

        PaymentSummary payment = paymentReadRepository.findByTenantIdAndOrderId(tenantId, orderId).stream()
                .filter(item -> PAID_STATUSES.contains(item.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("payment_not_captured", "No captured payment exists for this order", HttpStatus.UNPROCESSABLE_ENTITY));

        BigDecimal alreadyRefunded = refundReadRepository.findByTenantIdAndOrderId(tenantId, orderId).stream()
                .filter(refund -> refund.getStatus() != RefundStatus.REJECTED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundable = payment.getAmount().subtract(alreadyRefunded);
        BigDecimal effectiveAmount = amount == null ? refundable : amount;
        if (effectiveAmount.signum() <= 0 || effectiveAmount.compareTo(refundable) > 0) {
            throw new BusinessException("invalid_refund_amount",
                    "Refund amount must be positive and not exceed paid minus already refunded (" + refundable + ")", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        RefundResponse response = paymentService.requestRefund(payment.getId(), new RefundRequest(effectiveAmount, reason, key));
        auditLogService.log("SALE_REFUNDED", "order", orderId.toString(), order.getReference() + " amount=" + effectiveAmount + " reason=" + reason, "SUCCESS");
        return response;
    }

    /** Re-sends a customer notification; the DELIVERY_CODE OTP is never returned by the API and never logged. */
    @Transactional
    public void resendNotification(UUID orderId, SalesNotificationType type) {
        UUID tenantId = tenantId();
        Order order = findOrder(tenantId, orderId);
        accessGuard.ensureOwnVendorOrder(tenantId, order.getVendorId());
        switch (type) {
            case CONFIRMATION -> notificationService.create(order.getCustomerId(), "CUSTOMER", "order_confirmation", "Order Confirmation",
                    String.format("Your order %s was received. Total: %s MZN.", order.getReference(), order.getTotal()), order.getReference());
            case STATUS -> notificationService.create(order.getCustomerId(), "CUSTOMER", "order_status", "Order Status",
                    String.format("Your order %s is currently %s.", order.getReference(), order.getStatus()), order.getReference());
            // The OTP goes only into the customer's own notification message; it is never logged or echoed back.
            case DELIVERY_CODE -> notificationService.create(order.getCustomerId(), "CUSTOMER", "order_delivery_code", "Delivery Confirmation Code",
                    String.format("Your delivery confirmation code for order %s is %s.", order.getReference(), order.getDeliveryConfirmationCodeDisplay()), order.getReference());
        }
        auditLogService.log("SALE_NOTIFICATION_RESENT", "order", orderId.toString(), order.getReference() + " type=" + type, "SUCCESS");
    }

    /** Config-gated manual transition restricted to the allow-list matrix; retries with the same target are no-ops. */
    @Transactional
    public SalesActionResponse statusOverride(UUID orderId, String targetStatusRaw, String reason) {
        if (!statusOverrideProperties.isEnabled()) {
            throw new BusinessException("status_override_disabled", "Status override is disabled", HttpStatus.FORBIDDEN);
        }
        OrderStatus target = parseStatus(targetStatusRaw);
        UUID tenantId = tenantId();
        Order order = findOrder(tenantId, orderId);
        if (order.getStatus() == target) {
            // Idempotent retry: the transition already happened; return the same result.
            return new SalesActionResponse(order.getId(), order.getReference(), order.getStatus().name());
        }
        OrderStatus allowedTarget = OVERRIDE_ALLOW_LIST.get(order.getStatus());
        if (allowedTarget != target) {
            throw new BusinessException("transition_not_allowed",
                    "Transition " + order.getStatus() + " -> " + target + " is not in the override allow-list", HttpStatus.CONFLICT);
        }
        applyOverride(order, target);
        auditLogService.log("SALE_STATUS_OVERRIDDEN", "order", orderId.toString(),
                order.getReference() + " target=" + target + " reason=" + reason, "SUCCESS");
        return new SalesActionResponse(order.getId(), order.getReference(), order.getStatus().name());
    }

    private void applyOverride(Order order, OrderStatus target) {
        switch (target) {
            case CANCELLED -> order.markCancelled();
            case PREPARING -> order.markPreparing();
            case ASSIGNED_TO_COURIER -> order.markAssignedToCourier();
            case DELIVERED -> order.markDelivered();
            default -> throw new BusinessException("transition_not_allowed", "Unsupported override target " + target, HttpStatus.CONFLICT);
        }
    }

    private SalesRowResponse toRow(Order order, Map<UUID, String> vendorNames, Map<UUID, String> customerNames, PaymentSummary payment, boolean maskPii) {
        String customerName = customerNames.getOrDefault(order.getCustomerId(), "Unknown");
        return new SalesRowResponse(
                order.getId(),
                order.getReference(),
                order.getCreatedAt(),
                order.getVendorId(),
                vendorNames.getOrDefault(order.getVendorId(), "Unknown"),
                maskPii ? maskName(customerName) : customerName,
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getSubtotal(),
                order.getFees(),
                order.getTaxes(),
                order.getDiscountTotal(),
                order.getTotal(),
                order.getStatus().name(),
                payment == null ? null : payment.getStatus().name(),
                payment == null ? null : payment.getProvider());
    }

    private SaleLineItemResponse toLineItem(OrderItem item) {
        return new SaleLineItemResponse(item.getProductNameSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity(), item.getLineTotal());
    }

    /** Masks a person's name for SUPPORT: keeps the first letter of each word ("Ana Lima" -> "A*** L***"). */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return java.util.Arrays.stream(name.trim().split("\\s+"))
                .map(word -> word.charAt(0) + "***")
                .collect(Collectors.joining(" "));
    }

    private Map<UUID, String> vendorNames(List<Order> orders) {
        Set<UUID> ids = orders.stream().map(Order::getVendorId).collect(Collectors.toSet());
        return vendorRepository.findAllById(ids).stream().collect(Collectors.toMap(Vendor::getId, Vendor::getName));
    }

    private Map<UUID, String> customerNames(List<Order> orders) {
        Set<UUID> ids = orders.stream().map(Order::getCustomerId).collect(Collectors.toSet());
        return userProfileRepository.findAllById(ids).stream().collect(Collectors.toMap(AppUserProfile::getId, AppUserProfile::getDisplayName));
    }

    private Map<UUID, PaymentSummary> paymentsByOrder(UUID tenantId, List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = orders.stream().map(Order::getId).collect(Collectors.toSet());
        return paymentReadRepository.findByTenantIdAndOrderIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(PaymentSummary::getOrderId, payment -> payment, (first, second) -> first));
    }

    private OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("invalid_status", "Unknown order status: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private Order findOrder(UUID tenantId, UUID orderId) {
        return orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
