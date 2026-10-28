package com.delivery.delivery.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import com.delivery.delivery.config.DeliveryCodeProperties;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryEvent;
import com.delivery.delivery.exception.InvalidDeliveryCodeException;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.order.dto.DeliveryAddressResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.repository.OrderRepository;
import com.delivery.order.service.OrderStockService;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles courier delivery lifecycle and completion using the customer confirmation code (PIN). */
@Service
public class DeliveryService {
    private static final String EVENT_STATUS_UPDATE = "STATUS_UPDATE";
    private static final String AUDIT_CODE_LOCKED = "DELIVERY_CODE_LOCKED";
    private static final String AUDIT_CODE_UNLOCKED = "DELIVERY_CODE_UNLOCKED";
    private static final String AUDIT_STATUS_UPDATED = "DELIVERY_STATUS_UPDATED";
    /**
     * States from which the assigned courier may complete with the PIN. ARRIVED_AT_CUSTOMER is the
     * lifecycle target; ACCEPTED is kept because the courier app posts /complete right after accepting
     * the job without PATCHing intermediate statuses (INC-02 decision B; narrow once the app does).
     */
    private static final Set<DeliveryStatus> COMPLETABLE_STATUSES = Set.of(DeliveryStatus.ACCEPTED, DeliveryStatus.ARRIVED_AT_CUSTOMER);
    /**
     * States in which the assigned courier sees the customer's address: from accepting the job until arrival.
     * Not while merely offered (ASSIGNED, could still reject) nor after the delivery finished (data minimisation).
     */
    private static final Set<DeliveryStatus> ADDRESS_VISIBLE_STATUSES = Set.of(DeliveryStatus.ACCEPTED, DeliveryStatus.ARRIVED_AT_VENDOR,
            DeliveryStatus.PICKED_UP, DeliveryStatus.ON_ROUTE_TO_CUSTOMER, DeliveryStatus.ARRIVED_AT_CUSTOMER);

    /**
     * Order statuses in which the goods must not move any further: REFUNDED (money returned; pre-pickup deliveries are
     * retired by PaymentService.approveRefund), CANCELLED (sales cancel, override-cancel, seller rejection, failed
     * delivery) and the legacy REJECTED_BY_VENDOR. All three are terminal: no code path leaves them. REFUND_PENDING is
     * not included: no code path writes it, and a pending refund may still be rejected. DELIVERED is guarded by the
     * delivery's own status (delivery_already_completed).
     */
    private static final Set<OrderStatus> NON_DELIVERABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.REFUNDED, OrderStatus.CANCELLED, OrderStatus.REJECTED_BY_VENDOR);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final DeliveryMapper mapper;
    private final TenantContext tenantContext;
    private final DeliveryAccessGuard accessGuard;
    private final DeliveryCodeHasher codeHasher;
    private final DeliveryCodeProperties codeProperties;
    private final AuditLogService auditLogService;
    private final OrderStockService orderStockService;

    public DeliveryService(DeliveryRepository deliveryRepository, DeliveryEventRepository eventRepository, OrderRepository orderRepository, DeliveryMapper mapper, TenantContext tenantContext,
                           DeliveryAccessGuard accessGuard, DeliveryCodeHasher codeHasher, DeliveryCodeProperties codeProperties, AuditLogService auditLogService,
                           OrderStockService orderStockService) {
        this.orderStockService = orderStockService;
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.accessGuard = accessGuard;
        this.codeHasher = codeHasher;
        this.codeProperties = codeProperties;
        this.auditLogService = auditLogService;
    }

    /**
     * Marks delivery and order delivered when the assigned courier submits the matching 6-digit code.
     * A wrong code commits only the attempt counter and its events (noRollbackFor); concurrent attempts
     * are serialised by the delivery @Version column.
     */
    @Transactional(noRollbackFor = InvalidDeliveryCodeException.class)
    public DeliveryResponse complete(UUID deliveryId, String confirmationCode) {
        UUID tenantId = tenantId();
        Delivery delivery = findDelivery(tenantId, deliveryId);
        accessGuard.ensureAssignedCourier(tenantId, delivery);
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("delivery_already_completed", "Delivery is already completed", HttpStatus.CONFLICT);
        }
        if (!COMPLETABLE_STATUSES.contains(delivery.getStatus())) {
            throw new BusinessException("invalid_delivery_transition", "Delivery cannot be completed from its current status", HttpStatus.CONFLICT);
        }
        // Checked before the lockout and the PIN compare, so a refused call has no side effect (no counter, no event).
        Order order = findDeliverableOrder(tenantId, delivery);
        int maxAttempts = codeProperties.getMaxCodeAttempts();
        if (delivery.isCodeLocked(maxAttempts)) {
            throw new BusinessException("delivery_code_locked", "Delivery confirmation code is locked after too many failed attempts", HttpStatus.LOCKED);
        }
        if (!codeHasher.matches(confirmationCode, delivery.getConfirmationCodeHash(), delivery.isConfirmationCodeLegacy())) {
            // Failure path: only the counter and the events below are mutated (they commit; see noRollbackFor).
            delivery.recordFailedAttempt();
            eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "CONFIRMATION_FAILED", "Invalid confirmation code submitted"));
            if (delivery.isCodeLocked(maxAttempts)) {
                eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "CONFIRMATION_LOCKED", "Confirmation code locked after " + maxAttempts + " failed attempts"));
                auditLogService.log(AUDIT_CODE_LOCKED, "delivery", delivery.getId().toString(), delivery.getOrderId().toString(), "LOCKED");
                throw InvalidDeliveryCodeException.locked();
            }
            throw InvalidDeliveryCodeException.invalid();
        }
        delivery.markDelivered();
        order.markDelivered();
        // INC-07 (COR-01) x INC-02: complete() is also allowed straight from ACCEPTED (no PICKED_UP PATCH),
        // so close the stock lifecycle here too; no-op when pickup already committed it.
        orderStockService.commit(order);
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "DELIVERED", "Delivery completed with customer code"));
        return mapper.toResponse(delivery);
    }

    /**
     * Returns the full delivery address (order checkout snapshot) to the courier currently assigned to the
     * delivery, and only while that courier is working it (accepted, not yet finished). Anyone else gets 403
     * through the access guard; a reassigned courier loses access with the assignment (AS-02, INC-12).
     */
    @Transactional(readOnly = true)
    public DeliveryAddressResponse getAddressForAssignedCourier(UUID deliveryId) {
        UUID tenantId = tenantId();
        Delivery delivery = findDelivery(tenantId, deliveryId);
        accessGuard.ensureAssignedCourier(tenantId, delivery);
        if (!ADDRESS_VISIBLE_STATUSES.contains(delivery.getStatus())) {
            throw new BusinessException("delivery_address_unavailable", "The delivery address is only available while the delivery is in progress", HttpStatus.CONFLICT);
        }
        Order order = orderRepository.findByTenantIdAndId(tenantId, delivery.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
        DeliveryAddressResponse address = DeliveryAddressResponse.from(order.getDeliveryAddress());
        if (address == null) {
            throw new NotFoundException("Delivery address was not recorded for this order");
        }
        return address;
    }

    /**
     * Moves delivery through courier lifecycle statuses and records metadata when provided. Only the
     * assigned courier, or OPS/ADMIN (audited), may do so. DELIVERED is reachable only via complete().
     */
    @Transactional
    public DeliveryResponse updateStatus(UUID deliveryId, DeliveryStatus status, String proofPhotoStorageKey, java.math.BigDecimal cashCollectedAmount) {
        UUID tenantId = tenantId();
        Delivery delivery = findDelivery(tenantId, deliveryId);
        boolean operatorOverride = accessGuard.isOperator();
        if (!operatorOverride) {
            accessGuard.ensureAssignedCourier(tenantId, delivery);
        }
        DeliveryStatus previous = delivery.getStatus();
        validateTransition(previous, status);
        Order order = findDeliverableOrder(tenantId, delivery);
        if (proofPhotoStorageKey != null && !proofPhotoStorageKey.isBlank()) {
            delivery.setProofPhotoStorageKey(proofPhotoStorageKey);
        }
        if (cashCollectedAmount != null) {
            delivery.recordCashCollected(cashCollectedAmount);
        }
        delivery.updateStatus(status);
        if (status == DeliveryStatus.PICKED_UP) orderStockService.commit(order); // INC-07 (COR-01)
        if (status == DeliveryStatus.FAILED_DELIVERY) {
            order.markCancelled();
            orderStockService.release(order); // INC-07 (COR-01): no-op once pickup committed the stock
        }
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), EVENT_STATUS_UPDATE, "Delivery moved to " + status));
        if (operatorOverride) {
            auditLogService.log(AUDIT_STATUS_UPDATED, "delivery", delivery.getId().toString(), previous + "->" + status, "SUCCESS");
        }
        return mapper.toResponse(delivery);
    }

    /** OPS/ADMIN: clears a locked (or partially used) PIN attempt counter so the courier can retry; audited. */
    @Transactional
    public DeliveryResponse unlockCode(UUID deliveryId) {
        UUID tenantId = tenantId();
        Delivery delivery = findDelivery(tenantId, deliveryId);
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("delivery_already_completed", "Delivery is already completed", HttpStatus.CONFLICT);
        }
        int previousAttempts = delivery.getConfirmationAttempts();
        delivery.resetConfirmationAttempts();
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "CONFIRMATION_UNLOCKED", "Confirmation code attempts reset by operations"));
        auditLogService.log(AUDIT_CODE_UNLOCKED, "delivery", delivery.getId().toString(), "attempts=" + previousAttempts, "SUCCESS");
        return mapper.toResponse(delivery);
    }

    private void validateTransition(DeliveryStatus current, DeliveryStatus next) {
        boolean valid = switch (current) {
            case ACCEPTED -> next == DeliveryStatus.ARRIVED_AT_VENDOR;
            case ARRIVED_AT_VENDOR -> next == DeliveryStatus.PICKED_UP;
            case PICKED_UP -> next == DeliveryStatus.ON_ROUTE_TO_CUSTOMER;
            case ON_ROUTE_TO_CUSTOMER -> next == DeliveryStatus.ARRIVED_AT_CUSTOMER;
            // DELIVERED requires the customer PIN and is only reachable through complete().
            case ARRIVED_AT_CUSTOMER -> next == DeliveryStatus.FAILED_DELIVERY;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("invalid_delivery_transition", "Invalid delivery status transition", HttpStatus.CONFLICT);
        }
    }

    /**
     * Loads the delivery's order and refuses (409 order_not_deliverable) when it was refunded or cancelled: the goods
     * must not leave the store or be handed over for an order whose sale was undone. Called after the ownership and
     * delivery-state checks and before any mutation.
     */
    private Order findDeliverableOrder(UUID tenantId, Delivery delivery) {
        Order order = orderRepository.findByTenantIdAndId(tenantId, delivery.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
        if (NON_DELIVERABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("order_not_deliverable", "The order was " + order.getStatus() + " and can no longer be delivered", HttpStatus.CONFLICT);
        }
        return order;
    }

    private Delivery findDelivery(UUID tenantId, UUID deliveryId) {
        return deliveryRepository.findByTenantIdAndId(tenantId, deliveryId).orElseThrow(() -> new NotFoundException("Delivery was not found"));
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
