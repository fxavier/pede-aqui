package com.delivery.delivery.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.dto.DeliveryResponse;
import com.delivery.delivery.entity.DeliveryStatus;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.entity.DeliveryEvent;
import com.delivery.delivery.mapper.DeliveryMapper;
import com.delivery.delivery.repository.DeliveryEventRepository;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.order.entity.Order;
import com.delivery.order.repository.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles delivery completion using the customer confirmation code. */
@Service
public class DeliveryService {
    private static final String EVENT_STATUS_UPDATE = "STATUS_UPDATE";
    private final DeliveryRepository deliveryRepository;
    private final DeliveryEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final DeliveryMapper mapper;
    private final TenantContext tenantContext;

    public DeliveryService(DeliveryRepository deliveryRepository, DeliveryEventRepository eventRepository, OrderRepository orderRepository, DeliveryMapper mapper, TenantContext tenantContext) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Marks delivery and order delivered only when the submitted 6-digit code matches. */
    @Transactional
    public DeliveryResponse complete(UUID deliveryId, String confirmationCode) {
        UUID tenantId = tenantId();
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, deliveryId).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        if (!delivery.getConfirmationCodeHash().equals(hash(confirmationCode))) {
            delivery.recordFailedAttempt();
            eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "CONFIRMATION_FAILED", "Invalid confirmation code submitted"));
            throw new BusinessException("invalid_delivery_code", "Delivery confirmation code is invalid", HttpStatus.BAD_REQUEST);
        }
        delivery.markDelivered();
        Order order = orderRepository.findByTenantIdAndId(tenantId, delivery.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
        order.markDelivered();
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), "DELIVERED", "Delivery completed with customer code"));
        return mapper.toResponse(delivery);
    }

    /** Moves delivery through courier lifecycle statuses and records metadata when provided. */
    @Transactional
    public DeliveryResponse updateStatus(UUID deliveryId, DeliveryStatus status, String proofPhotoStorageKey, java.math.BigDecimal cashCollectedAmount) {
        UUID tenantId = tenantId();
        Delivery delivery = deliveryRepository.findByTenantIdAndId(tenantId, deliveryId).orElseThrow(() -> new NotFoundException("Delivery was not found"));
        validateTransition(delivery.getStatus(), status);
        if (proofPhotoStorageKey != null && !proofPhotoStorageKey.isBlank()) {
            delivery.setProofPhotoStorageKey(proofPhotoStorageKey);
        }
        if (cashCollectedAmount != null) {
            delivery.recordCashCollected(cashCollectedAmount);
        }
        delivery.updateStatus(status);
        if (status == DeliveryStatus.FAILED_DELIVERY) {
            Order order = orderRepository.findByTenantIdAndId(tenantId, delivery.getOrderId()).orElseThrow(() -> new NotFoundException("Order was not found"));
            order.markCancelled();
        }
        eventRepository.save(new DeliveryEvent(UUID.randomUUID(), tenantId, delivery.getId(), EVENT_STATUS_UPDATE, "Delivery moved to " + status));
        return mapper.toResponse(delivery);
    }

    private void validateTransition(DeliveryStatus current, DeliveryStatus next) {
        boolean valid = switch (current) {
            case ACCEPTED -> next == DeliveryStatus.ARRIVED_AT_VENDOR;
            case ARRIVED_AT_VENDOR -> next == DeliveryStatus.PICKED_UP;
            case PICKED_UP -> next == DeliveryStatus.ON_ROUTE_TO_CUSTOMER;
            case ON_ROUTE_TO_CUSTOMER -> next == DeliveryStatus.ARRIVED_AT_CUSTOMER;
            case ARRIVED_AT_CUSTOMER -> next == DeliveryStatus.FAILED_DELIVERY || next == DeliveryStatus.DELIVERED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException("invalid_delivery_transition", "Invalid delivery status transition", HttpStatus.BAD_REQUEST);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }
}
