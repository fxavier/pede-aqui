package com.delivery.order.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.cart.repository.CartRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles checkout and order creation for customer carts. */
@Service
public class OrderService {
    private static final Set<OrderStatus> VENDOR_REJECTION_ALLOWED_STATUSES = Set.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.ACCEPTED_BY_VENDOR);
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final InventoryService inventoryService;
    private final AppUserProfileRepository userProfileRepository;
    private final VendorRepository vendorRepository;
    private final OrderMapper mapper;
    private final TenantContext tenantContext;
    private final SecureRandom random = new SecureRandom();

    public OrderService(CartRepository cartRepository, OrderRepository orderRepository, PaymentRepository paymentRepository, DeliveryRepository deliveryRepository, InventoryService inventoryService, AppUserProfileRepository userProfileRepository, VendorRepository vendorRepository, OrderMapper mapper, TenantContext tenantContext) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.inventoryService = inventoryService;
        this.userProfileRepository = userProfileRepository;
        this.vendorRepository = vendorRepository;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
    }

    /** Returns all orders for the current tenant with customer and vendor names resolved. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listForCurrentContext() {
        UUID tenantId = tenantId();
        List<Order> orders = orderRepository.findByTenantId(tenantId);
        Set<UUID> customerIds = orders.stream().map(Order::getCustomerId).collect(Collectors.toSet());
        Set<UUID> vendorIds = orders.stream().map(Order::getVendorId).collect(Collectors.toSet());
        Map<UUID, String> customerNames = userProfileRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(AppUserProfile::getId, AppUserProfile::getDisplayName));
        Map<UUID, String> vendorNames = vendorRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName));
        return orders.stream()
                .map(o -> mapper.toResponse(o,
                        customerNames.getOrDefault(o.getCustomerId(), "Unknown"),
                        vendorNames.getOrDefault(o.getVendorId(), "Unknown")))
                .toList();
    }

    /** Creates an order, reserves stock, and creates local payment/delivery records idempotently. */
    @Transactional
    public OrderResponse checkout(UUID cartId, String idempotencyKey) {
        UUID tenantId = tenantId();
        return orderRepository.findByTenantIdAndCheckoutIdempotencyKey(tenantId, idempotencyKey)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(createOrder(tenantId, cartId, idempotencyKey)));
    }

    private Order createOrder(UUID tenantId, UUID cartId, String idempotencyKey) {
        Cart cart = cartRepository.findByTenantIdAndId(tenantId, cartId).orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!"ACTIVE".equals(cart.getStatus()) || cart.getItems().isEmpty()) {
            throw new BusinessException("invalid_cart", "Cart must be active and contain items", HttpStatus.BAD_REQUEST);
        }
        for (CartItem item : cart.getItems()) {
            inventoryService.reserve(tenantId, item.getSkuId(), item.getQuantity());
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        Order order = new Order(UUID.randomUUID(), tenantId, reference(), cart.getCustomerId(), cart.getVendorId(), cart.getSubtotal(), cart.getFees(), cart.getTaxes(), cart.getDiscounts(), cart.getTotal(), idempotencyKey, hash(code), code);
        for (CartItem item : cart.getItems()) {
            order.addItem(new OrderItem(UUID.randomUUID(), order, tenantId, item.getSkuId(), item.getProductNameSnapshot(), item.getSkuNameSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity()));
        }
        Order saved = orderRepository.save(order);
        paymentRepository.save(new Payment(UUID.randomUUID(), tenantId, saved.getId(), saved.getTotal(), idempotencyKey));
        deliveryRepository.save(new Delivery(UUID.randomUUID(), tenantId, saved.getId(), saved.getDeliveryConfirmationCodeHash()));
        cart.markCheckedOut();
        return saved;
    }

    private String reference() { return "PA-" + System.currentTimeMillis() + "-" + random.nextInt(10_000); }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }

    /** Marks an order as accepted by the vendor when in PAYMENT_CONFIRMED state. */
    @Transactional
    public OrderResponse acceptByVendor(UUID orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_CONFIRMED) {
            throw new BusinessException("invalid_order_transition", "Only payment confirmed orders can be accepted", HttpStatus.BAD_REQUEST);
        }
        order.markAcceptedByVendor();
        return mapper.toResponse(order);
    }

    /** Rejects an order with a required reason when still in vendor-owned states. */
    @Transactional
    public OrderResponse rejectByVendor(UUID orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("rejection_reason_required", "A rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        Order order = findOrder(orderId);
        if (!VENDOR_REJECTION_ALLOWED_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("invalid_order_transition", "Order cannot be rejected in the current status", HttpStatus.BAD_REQUEST);
        }
        order.markCancelled();
        return mapper.toResponse(order);
    }

    /** Marks an accepted order as preparing. */
    @Transactional
    public OrderResponse markPreparing(UUID orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.ACCEPTED_BY_VENDOR) {
            throw new BusinessException("invalid_order_transition", "Only accepted orders can move to preparing", HttpStatus.BAD_REQUEST);
        }
        order.markPreparing();
        return mapper.toResponse(order);
    }

    /** Marks a preparing order as ready for pickup. */
    @Transactional
    public OrderResponse markReadyForPickup(UUID orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new BusinessException("invalid_order_transition", "Only preparing orders can move to ready for pickup", HttpStatus.BAD_REQUEST);
        }
        order.markReadyForPickup();
        return mapper.toResponse(order);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByTenantIdAndId(tenantId(), orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
    }
}
