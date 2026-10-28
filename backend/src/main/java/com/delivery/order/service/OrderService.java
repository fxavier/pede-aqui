package com.delivery.order.service;

import com.delivery.auth.entity.AppUserProfile;
import com.delivery.auth.repository.AppUserProfileRepository;
import com.delivery.cart.entity.Cart;
import com.delivery.cart.entity.CartItem;
import com.delivery.cart.repository.CartRepository;
import com.delivery.catalog.entity.Product;
import com.delivery.catalog.entity.Sku;
import com.delivery.catalog.repository.ProductRepository;
import com.delivery.catalog.repository.SkuRepository;
import com.delivery.common.exception.BusinessException;
import com.delivery.common.exception.NotFoundException;
import com.delivery.common.security.TenantContext;
import com.delivery.delivery.entity.Delivery;
import com.delivery.delivery.repository.DeliveryRepository;
import com.delivery.delivery.service.DeliveryCodeHasher;
import com.delivery.inventory.service.InventoryService;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.dto.AdminOrderResponse;
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
import com.delivery.notification.service.NotificationService;
import com.delivery.payment.service.PaymentService;
import com.delivery.payment.dto.RefundRequest;
import com.delivery.sales.service.SalesAccessGuard;
import com.delivery.common.service.AuditLogService;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles checkout, order listing, and vendor fulfilment transitions (vendor users confined to their own vendor). */
@Service
public class OrderService {
    private static final Set<OrderStatus> VENDOR_REJECTION_ALLOWED_STATUSES = Set.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.ACCEPTED_BY_VENDOR);
    private static final int MAX_PAGE_SIZE = 100;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final InventoryService inventoryService;
    private final AppUserProfileRepository userProfileRepository;
    private final VendorRepository vendorRepository;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final AuditLogService auditLogService;
    private final OrderMapper mapper;
    private final TenantContext tenantContext;
    private final SkuRepository skuRepository;
    private final ProductRepository productRepository;
    private final CheckoutDiscountResolver discountResolver;
    private final SalesAccessGuard accessGuard;
    private final DeliveryCodeHasher deliveryCodeHasher;
    private final SecureRandom random = new SecureRandom();

    public OrderService(CartRepository cartRepository, OrderRepository orderRepository, PaymentRepository paymentRepository, DeliveryRepository deliveryRepository, InventoryService inventoryService, AppUserProfileRepository userProfileRepository, VendorRepository vendorRepository, NotificationService notificationService, PaymentService paymentService, AuditLogService auditLogService, OrderMapper mapper, TenantContext tenantContext, SkuRepository skuRepository, ProductRepository productRepository, CheckoutDiscountResolver discountResolver, DeliveryCodeHasher deliveryCodeHasher) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.inventoryService = inventoryService;
        this.userProfileRepository = userProfileRepository;
        this.vendorRepository = vendorRepository;
        this.notificationService = notificationService;
        this.paymentService = paymentService;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.tenantContext = tenantContext;
        this.skuRepository = skuRepository;
        this.productRepository = productRepository;
        this.discountResolver = discountResolver;
        // SalesAccessGuard is stateless (only reads the SecurityContext and VendorRepository); building it here
        // reuses the sales scoping rules without widening this constructor, which other increments also touch.
        this.accessGuard = new SalesAccessGuard(vendorRepository);
        this.deliveryCodeHasher = deliveryCodeHasher;
    }

    /** Returns the caller's orders with names resolved: tenant-wide for OPS/ADMIN, own vendor only for merchant users. */
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listForCurrentContext() {
        List<Order> orders;
        if (accessGuard.isMerchantScoped()) {
            UUID tenantId = tenantId();
            orders = orderRepository.findByTenantIdAndVendorId(tenantId, accessGuard.requireOwnVendorId(tenantId));
        } else {
            orders = tenantContext.isPlatformAdmin()
                    ? orderRepository.findAll()
                    : orderRepository.findByTenantId(tenantId());
        }
        return toAdminResponses(orders);
    }

    private List<AdminOrderResponse> toAdminResponses(List<Order> orders) {
        Set<UUID> customerIds = orders.stream().map(Order::getCustomerId).collect(Collectors.toSet());
        Set<UUID> vendorIds = orders.stream().map(Order::getVendorId).collect(Collectors.toSet());
        Map<UUID, String> customerNames = userProfileRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(AppUserProfile::getId, AppUserProfile::getDisplayName));
        Map<UUID, String> vendorNames = vendorRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName));
        // Same PII rule as the sales lens: only a pure SUPPORT caller gets masked customer names.
        boolean maskPii = accessGuard.shouldMaskCustomerPii();
        return orders.stream()
                .map(o -> mapper.toAdminResponse(o,
                        maskPii ? SalesAccessGuard.maskName(customerNames.getOrDefault(o.getCustomerId(), "Unknown"))
                                : customerNames.getOrDefault(o.getCustomerId(), "Unknown"),
                        vendorNames.getOrDefault(o.getVendorId(), "Unknown")))
                .toList();
    }

    /** Returns one page of orders for the current context (newest first, size capped). */
    @Transactional(readOnly = true)
    public AdminOrderPageResponse listPageForCurrentContext(int page, int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int effectivePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(effectivePage, effectiveSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result;
        if (accessGuard.isMerchantScoped()) {
            UUID tenantId = tenantId();
            result = orderRepository.findByTenantIdAndVendorId(tenantId, accessGuard.requireOwnVendorId(tenantId), pageable);
        } else {
            result = tenantContext.isPlatformAdmin()
                    ? orderRepository.findAll(pageable)
                    : orderRepository.findByTenantId(tenantId(), pageable);
        }
        return new AdminOrderPageResponse(toAdminResponses(result.getContent()), effectivePage, effectiveSize, result.getTotalElements());
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
        UUID currentCustomerId = getCurrentCustomerId();
        Cart cart = cartRepository.findByTenantIdAndId(tenantId, cartId).orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!cart.getCustomerId().equals(currentCustomerId)) {
            throw new BusinessException("cart_access_denied", "Access to this cart is not permitted", HttpStatus.FORBIDDEN);
        }
        if (!"ACTIVE".equals(cart.getStatus()) || cart.getItems().isEmpty()) {
            throw new BusinessException("invalid_cart", "Cart must be active and contain items", HttpStatus.BAD_REQUEST);
        }
        for (CartItem item : cart.getItems()) {
            inventoryService.reserve(tenantId, item.getSkuId(), item.getQuantity());
        }
        // Plain SecureRandom PIN, HMAC-hashed; no per-tenant uniqueness (a PIN is verified against one delivery only).
        String code = deliveryCodeHasher.generateCode();
        Order order = new Order(UUID.randomUUID(), tenantId, reference(), cart.getCustomerId(), cart.getVendorId(), cart.getSubtotal(), cart.getFees(), cart.getTaxes(), cart.getDiscounts(), cart.getTotal(), idempotencyKey, deliveryCodeHasher.hash(code), code);
        Map<UUID, UUID> categoryBySkuId = resolveCategoryBySkuId(cart);
        for (CartItem item : cart.getItems()) {
            order.addItem(new OrderItem(UUID.randomUUID(), order, tenantId, item.getSkuId(), item.getProductNameSnapshot(), item.getSkuNameSnapshot(), item.getUnitPriceSnapshot(), item.getQuantity(), categoryBySkuId.get(item.getSkuId())));
        }
        // Discount seam (spec 002): the resolver defaults to zero, keeping legacy checkout totals untouched.
        CheckoutDiscount discount = discountResolver.resolve(cart, currentCustomerId);
        if (discount.discountTotal().signum() > 0) {
            order.applyDiscount(discount.appliedPromotionId(), discount.discountTotal());
        }
        Order saved = orderRepository.save(order);
        paymentRepository.save(new Payment(UUID.randomUUID(), tenantId, saved.getId(), saved.getTotal(), idempotencyKey));
        deliveryRepository.save(new Delivery(UUID.randomUUID(), tenantId, saved.getId(), saved.getDeliveryConfirmationCodeHash()));
        cart.markCheckedOut();
        return saved;
    }

    private String reference() { return "PA-" + System.currentTimeMillis() + "-" + random.nextInt(10_000); }

    /** Snapshots the current product category per cart SKU so category reports stay stable over recategorisation. */
    private Map<UUID, UUID> resolveCategoryBySkuId(Cart cart) {
        Set<UUID> skuIds = cart.getItems().stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        Map<UUID, UUID> productBySkuId = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Sku::getProductId));
        Map<UUID, UUID> categoryByProductId = productRepository.findAllById(Set.copyOf(productBySkuId.values())).stream()
                .collect(Collectors.toMap(Product::getId, Product::getCategoryId));
        return productBySkuId.entrySet().stream()
                .filter(entry -> categoryByProductId.containsKey(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> categoryByProductId.get(entry.getValue())));
    }

    private UUID getCurrentCustomerId() {
        UUID tenantId = tenantId();
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        AppUserProfile currentUser = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return currentUser.getId();
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
        auditLogService.log("ORDER_ACCEPTED", "order", orderId.toString(), order.getReference(), "SUCCESS");
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
        
        // Mark order as rejected by vendor with reason
        order.markRejectedByVendor(reason);
        
        // Send notification to customer
        notificationService.create(
            order.getCustomerId(), 
            "CUSTOMER", 
            "order_rejected", 
            "Order Rejected", 
            String.format("Your order %s has been rejected by the vendor. Reason: %s", order.getReference(), reason), 
            order.getReference()
        );
        
        // Initiate refund process
        Payment payment = paymentRepository.findByTenantIdAndOrderId(tenantId(), orderId)
            .orElseThrow(() -> new NotFoundException("Payment was not found for order"));
        
        String refundIdempotencyKey = "vendor-rejection-" + orderId.toString();
        paymentService.requestRefund(payment.getId(), new RefundRequest(payment.getAmount(), "Vendor rejection: " + reason, refundIdempotencyKey));
        
        // COR-07: the order stays REJECTED_BY_VENDOR; the refund is tracked only on the Refund record.
        auditLogService.log("ORDER_REJECTED", "order", orderId.toString(), order.getReference(), "SUCCESS");
        
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
        auditLogService.log("ORDER_PREPARING", "order", orderId.toString(), order.getReference(), "SUCCESS");
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
        auditLogService.log("ORDER_READY_FOR_PICKUP", "order", orderId.toString(), order.getReference(), "SUCCESS");
        return mapper.toResponse(order);
    }

    /**
     * Loads an order for a vendor fulfilment transition. Merchant users only see their own vendor's
     * orders: another vendor's order answers 404 exactly like a missing one (existence is not revealed).
     */
    private Order findOrder(UUID orderId) {
        UUID tenantId = tenantId();
        Order order = orderRepository.findByTenantIdAndId(tenantId, orderId).orElseThrow(() -> new NotFoundException("Order was not found"));
        if (accessGuard.isMerchantScoped() && !accessGuard.requireOwnVendorId(tenantId).equals(order.getVendorId())) {
            throw new NotFoundException("Order was not found");
        }
        return order;
    }
}
