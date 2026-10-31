package com.delivery.order.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.pagination.PageLimits;
import com.delivery.common.pagination.PageResponse;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditActions;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.api.CourierPosition;
import com.delivery.delivery.api.DeliveryFailure;
import com.delivery.notification.api.NotificationChannel;
import com.delivery.notification.api.NotificationDispatcher;
import com.delivery.notification.api.NotificationRequest;
import com.delivery.order.DeliveryIncidentResolved;
import com.delivery.order.dto.DeliveryIncidentResponse;
import com.delivery.order.dto.ResolveDeliveryIncidentRequest;
import com.delivery.order.entity.DeliveryAddressSnapshot;
import com.delivery.order.entity.DeliveryIncident;
import com.delivery.order.entity.DeliveryIncident.Amounts;
import com.delivery.order.entity.DeliveryIncident.Decision;
import com.delivery.order.entity.DeliveryIncident.Evidence;
import com.delivery.order.entity.DeliveryIncidentStatus;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.PricingQuoteSnapshot;
import com.delivery.order.repository.DeliveryIncidentRepository;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.payment.service.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery incidents (LAC-44, W7-B): opened when a delivery ends in FAILED_DELIVERY (from {@link OrderDeliveryHooks},
 * inside the delivery transaction) and resolved once by OPS/ADMIN. The default decision comes from
 * {@link DeliveryIncidentPolicy}; OPS may override any part of it with a reason (audited). Resolution follows the lock
 * order rule — the order's payment row {@code FOR UPDATE} first, then the order, then the incident row — so two
 * operators resolving the same incident serialize and the second sees the committed result. It requests the refund
 * (REQUESTED, key {@code delivery-incident-<incidentId>}; FINANCE approves it as any other refund), never moves stock
 * (the goods left the store; a pre-pickup STORE_FAULT already released the reservation), leaves the order CANCELLED
 * (no order transition: CANCELLED is terminal in {@code OrderStateMachine}), notifies the customer through the W6-C
 * dispatcher and publishes {@link DeliveryIncidentResolved} for the settlement ledger.
 */
@Service
public class DeliveryIncidentService {
    /** Idempotency key prefix of the incident refund ({@code delivery-incident-<incidentId>}). */
    public static final String REFUND_KEY_PREFIX = "delivery-incident-";
    static final String TEMPLATE_OPENED = "delivery_failed";
    static final String TEMPLATE_REFUND_FULL = "delivery_incident_refund_full";
    static final String TEMPLATE_REFUND_PARTIAL = "delivery_incident_refund_partial";
    private static final Set<NotificationChannel> CHANNELS = EnumSet.of(NotificationChannel.IN_APP, NotificationChannel.PUSH,
            NotificationChannel.SMS, NotificationChannel.EMAIL);
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final DeliveryIncidentRepository incidentRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final DeliveryIncidentPolicy policy;
    private final NotificationDispatcher notificationDispatcher;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final ApplicationEventPublisher eventPublisher;

    public DeliveryIncidentService(DeliveryIncidentRepository incidentRepository, OrderRepository orderRepository,
                                   PaymentRepository paymentRepository, PaymentService paymentService, DeliveryIncidentPolicy policy,
                                   NotificationDispatcher notificationDispatcher, AuditLogService auditLogService,
                                   TenantContext tenantContext, ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.policy = policy;
        this.notificationDispatcher = notificationDispatcher;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Opens the OPEN incident of a failed delivery in the caller's (delivery) transaction: default decision from the
     * policy, arrival evidence (courier position + distance to the address snapshot), audit, customer notification.
     * Idempotent per delivery (one incident per delivery; a repeat returns the existing one).
     */
    @Transactional
    public DeliveryIncident open(Order order, DeliveryFailure failure) {
        UUID tenantId = order.getTenantId();
        var existing = incidentRepository.findByTenantIdAndDeliveryId(tenantId, failure.deliveryId());
        if (existing.isPresent()) {
            return existing.get();
        }
        Decision defaults = policy.defaultFor(failure.reason());
        DeliveryIncident incident = incidentRepository.saveAndFlush(new DeliveryIncident(tenantId, order.getId(), failure.deliveryId(),
                failure.courierId(), failure.reason().name(), failure.note(), failure.proofPhotoStorageKey(), failure.reportedBy(),
                failure.reportedByRole(), failure.fromStatus(), evidence(failure.evidence(), order.getDeliveryAddress()), defaults));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", failure.reason().name());
        details.put("reportedByRole", failure.reportedByRole());
        details.put("defaultCostBearer", defaults.costBearer().name());
        details.put("defaultOutcome", defaults.outcome().name());
        details.put("defaultGoodsDisposition", defaults.goodsDisposition().name());
        details.put("evidence", incident.getEvidenceRecordedAt() != null);
        auditLogService.logForTenant(tenantId, AuditActions.DELIVERY_INCIDENT_OPENED, "delivery_incident", incident.getId().toString(),
                "order=" + order.getId() + ";reason=" + failure.reason().name(), "SUCCESS", details);
        notify(order, TEMPLATE_OPENED, "delivery-incident:" + incident.getId() + ":opened");
        return incident;
    }

    /** OPS queue: one page of the tenant's incidents in {@code status} (default OPEN), newest first. */
    @Transactional(readOnly = true)
    public PageResponse<DeliveryIncidentResponse> list(DeliveryIncidentStatus status, int page, int size) {
        UUID tenantId = tenantId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), PageLimits.MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<DeliveryIncident> rows = incidentRepository.findByTenantIdAndStatus(tenantId,
                status == null ? DeliveryIncidentStatus.OPEN : status, pageable);
        Map<UUID, Order> orders = rows.isEmpty() ? Map.of()
                : orderRepository.findByTenantIdAndIdIn(tenantId, rows.map(DeliveryIncident::getOrderId).toList()).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity()));
        return PageResponse.of(rows, incident -> toResponse(incident, orders.get(incident.getOrderId())));
    }

    /** One incident of the tenant (another tenant's or unknown → 404). */
    @Transactional(readOnly = true)
    public DeliveryIncidentResponse get(UUID incidentId) {
        UUID tenantId = tenantId();
        DeliveryIncident incident = incidentRepository.findByTenantIdAndId(tenantId, incidentId)
                .orElseThrow(() -> new NotFoundException("Delivery incident was not found"));
        return toResponse(incident, orderRepository.findByTenantIdAndId(tenantId, incident.getOrderId()).orElse(null));
    }

    /**
     * OPS/ADMIN resolve an OPEN incident: the request's fields over the policy default (any difference = override,
     * needs a reason), refund of the remainder (minus the delivery fee when so decided) requested in the same
     * transaction, decision + money figures recorded, customer notified, audited ({@code DELIVERY_INCIDENT_RESOLVED},
     * with the default and final decision), {@link DeliveryIncidentResolved} published. A repeat with the same decision
     * returns the resolved incident unchanged; a different decision on a RESOLVED incident is 409
     * {@code incident_already_resolved}.
     */
    @Transactional
    public DeliveryIncidentResponse resolve(UUID incidentId, ResolveDeliveryIncidentRequest request) {
        UUID tenantId = tenantId();
        UUID orderId = incidentRepository.findOrderIdByTenantIdAndId(tenantId, incidentId)
                .orElseThrow(() -> new NotFoundException("Delivery incident was not found"));
        // Lock order rule: payment row first, then the order, then the incident row.
        Payment payment = paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId, orderId).orElse(null);
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        DeliveryIncident incident = incidentRepository.findByTenantIdAndIdForUpdate(tenantId, incidentId)
                .orElseThrow(() -> new NotFoundException("Delivery incident was not found"));
        ResolveDeliveryIncidentRequest body = request == null ? new ResolveDeliveryIncidentRequest(null, null, null, null) : request;
        Decision defaults = incident.defaultDecision();
        Decision decision = new Decision(
                body.costBearer() != null ? body.costBearer() : defaults.costBearer(),
                body.outcome() != null ? body.outcome() : defaults.outcome(),
                body.goodsDisposition() != null ? body.goodsDisposition() : defaults.goodsDisposition());
        if (!incident.isOpen()) {
            if (decision.equals(incident.finalDecision())) {
                return toResponse(incident, order); // idempotent repeat
            }
            throw new BusinessException("incident_already_resolved", "The delivery incident was already resolved with another decision",
                    HttpStatus.CONFLICT);
        }
        boolean overridden = !decision.equals(defaults);
        String overrideReason = normalize(body.overrideReason());
        if (overridden && overrideReason == null) {
            throw new BusinessException("override_reason_required", "A reason is required to override the default incident outcome",
                    HttpStatus.BAD_REQUEST);
        }
        boolean captured = payment != null && payment.isCaptured();
        BigDecimal refundable = captured ? paymentService.refundableAmount(tenantId, payment.getId()) : BigDecimal.ZERO;
        Amounts planned = DeliveryIncidentPolicy.amounts(decision, refundable, deliveryFeeOf(order), order.getFees(),
                nz(order.getSubtotal()).add(nz(order.getTaxes())));
        BigDecimal refunded = BigDecimal.ZERO;
        if (captured && planned.refund().signum() > 0) {
            refunded = paymentService.requestRemainingRefundLessForTenant(tenantId, payment.getId(), planned.retainedFee(),
                            "Delivery incident", REFUND_KEY_PREFIX + incident.getId())
                    .map(RefundResponse::amount)
                    .orElse(BigDecimal.ZERO);
        }
        Amounts amounts = new Amounts(money(refunded), planned.retainedFee(), planned.merchantCharge(), planned.platformCompensation());
        incident.resolve(decision, overridden, overridden ? overrideReason : null, amounts, actor());
        eventPublisher.publishEvent(new DeliveryIncidentResolved(tenantId, incident.getId(), orderId, order.getVendorId(),
                decision.costBearer().name(), decision.outcome().name(), decision.goodsDisposition().name(), amounts.refund(),
                amounts.retainedFee(), amounts.merchantCharge(), amounts.platformCompensation(), incident.getResolvedAt()));
        if (amounts.refund().signum() > 0) {
            notify(order, amounts.retainedFee().signum() > 0 ? TEMPLATE_REFUND_PARTIAL : TEMPLATE_REFUND_FULL,
                    "delivery-incident:" + incident.getId() + ":resolved");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("costBearer", decision.costBearer().name());
        details.put("outcome", decision.outcome().name());
        details.put("goodsDisposition", decision.goodsDisposition().name());
        details.put("overridden", overridden);
        details.put("defaultCostBearer", defaults.costBearer().name());
        details.put("defaultOutcome", defaults.outcome().name());
        details.put("defaultGoodsDisposition", defaults.goodsDisposition().name());
        details.put("refund", plain(amounts.refund()));
        details.put("retainedFee", plain(amounts.retainedFee()));
        details.put("merchantCharge", plain(amounts.merchantCharge()));
        details.put("platformCompensation", plain(amounts.platformCompensation()));
        auditLogService.log(AuditActions.DELIVERY_INCIDENT_RESOLVED, "delivery_incident", incident.getId().toString(),
                "order=" + orderId + ";refund=" + plain(amounts.refund()) + ";bearer=" + decision.costBearer().name(),
                overridden ? "OVERRIDDEN" : "SUCCESS", details);
        return toResponse(incident, order);
    }

    private void notify(Order order, String templateKey, String dedupKey) {
        if (order.getCustomerId() == null) {
            return;
        }
        notificationDispatcher.dispatch(new NotificationRequest(order.getTenantId(), order.getCustomerId(), "CUSTOMER", templateKey,
                order.getReference(), Map.of("orderReference", Objects.toString(order.getReference(), "")), CHANNELS, dedupKey));
    }

    /** The order's delivery fee from its applied quote; orders without one fall back to the order's fees. */
    static BigDecimal deliveryFeeOf(Order order) {
        PricingQuoteSnapshot quote = order.getPricingQuote();
        if (quote != null && quote.getDeliveryFee() != null) {
            return quote.getDeliveryFee();
        }
        return nz(order.getFees());
    }

    /** Evidence row: the courier's last position and its great-circle distance to the address snapshot, when both exist. */
    static Evidence evidence(CourierPosition position, DeliveryAddressSnapshot address) {
        if (position == null) {
            return Evidence.NONE;
        }
        Double distance = null;
        if (address != null && address.getLatitude() != null && address.getLongitude() != null) {
            distance = Math.round(haversineMeters(position.latitude(), position.longitude(), address.getLatitude(), address.getLongitude()) * 10d) / 10d;
        }
        return new Evidence(position.latitude(), position.longitude(), position.accuracyMeters(), position.recordedAt(), distance);
    }

    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    static DeliveryIncidentResponse toResponse(DeliveryIncident i, Order order) {
        Decision defaults = i.defaultDecision();
        Decision decided = i.finalDecision();
        DeliveryIncidentResponse.Resolution resolution = decided == null ? null : new DeliveryIncidentResponse.Resolution(
                toDto(decided), i.isOverridden(), i.getOverrideReason(), i.getRefundAmount(), i.getRetainedFeeAmount(),
                i.getMerchantChargeAmount(), i.getPlatformCompensationAmount(), i.getDecidedBy(), i.getResolvedAt());
        return new DeliveryIncidentResponse(i.getId(), i.getOrderId(), order == null ? null : order.getReference(),
                order == null ? null : order.getStatus(), i.getDeliveryId(), i.getCourierId(), i.getReason(), i.getNote(),
                i.getProofPhotoStorageKey(), i.getReportedByRole(), i.getFailedFromStatus(),
                new DeliveryIncidentResponse.Evidence(i.getEvidenceLatitude(), i.getEvidenceLongitude(), i.getEvidenceAccuracyMeters(),
                        i.getEvidenceRecordedAt(), i.getEvidenceDistanceMeters()),
                i.getStatus(), toDto(defaults), resolution, i.getCreatedAt());
    }

    private static DeliveryIncidentResponse.Decision toDto(Decision d) {
        return new DeliveryIncidentResponse.Decision(d.costBearer(), d.outcome(), d.goodsDisposition());
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.strip();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return money(value).toPlainString();
    }

    private String actor() {
        return tenantContext.currentKeycloakUserId().orElse("unknown");
    }

    private UUID tenantId() {
        return tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
    }
}
