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
import com.delivery.delivery.api.DeliveryRetirementService;
import com.delivery.inventory.service.InventoryService;
import com.delivery.marketing.api.CheckoutDiscount;
import com.delivery.marketing.api.CheckoutDiscountResolver;
import com.delivery.order.dto.AdminOrderPageResponse;
import com.delivery.order.dto.AdminOrderResponse;
import com.delivery.order.dto.OrderResponse;
import com.delivery.order.entity.DeliveryAddressSnapshot;
import com.delivery.order.entity.Order;
import com.delivery.order.entity.OrderItem;
import com.delivery.order.entity.OrderStatus;
import com.delivery.order.mapper.OrderMapper;
import com.delivery.order.repository.OrderRepository;
import com.delivery.payment.entity.Payment;
import com.delivery.payment.repository.PaymentRepository;
import com.delivery.vendor.api.MerchantScopeGuard;
import com.delivery.vendor.entity.Vendor;
import com.delivery.vendor.repository.VendorRepository;
import com.delivery.notification.service.NotificationService;
import com.delivery.payment.service.PaymentService;
import com.delivery.payment.dto.RefundResponse;
import com.delivery.order.api.CustomerPiiMasking;
import com.delivery.common.service.AuditLogService;
import com.delivery.cart.service.CartService;
import com.delivery.cart.service.PricingService;
import com.delivery.common.pricing.GeoPoint;
import com.delivery.common.pricing.PriceQuote;
import com.delivery.common.pricing.PricingMath;
import com.delivery.order.dto.CheckoutQuoteResponse;
import com.delivery.order.entity.PricingQuoteSnapshot;
import com.delivery.order.dto.CheckoutResult;
import com.delivery.order.entity.CheckoutQuote;
import com.delivery.common.pricing.TaxCategory;
import com.delivery.common.pricing.TaxableLine;
import com.delivery.common.pricing.VatBreakdown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.delivery.order.api.OrderStateMachine;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Handles checkout, order listing, and vendor fulfilment transitions (vendor users confined to their own vendor). */
@Service
public class OrderService {
    static final Set<OrderStatus> VENDOR_REJECTION_ALLOWED_STATUSES = Set.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.ACCEPTED_BY_VENDOR);
    /** A rejection of an order already in one of these is a no-op repeat (REJECTED_BY_VENDOR = legacy rows). */
    private static final Set<OrderStatus> REJECTION_ALREADY_SETTLED_STATUSES = Set.of(OrderStatus.CANCELLED, OrderStatus.REJECTED_BY_VENDOR);
    public static final String SELLER_REJECTION_AUDIT_ACTION = "ORDER_CANCELLED_SELLER_REJECTED";
    /** Idempotency key prefix of the rejection refund (`seller-reject-<orderId>`); legacy rows use `vendor-rejection-<orderId>`. */
    public static final String SELLER_REJECT_REFUND_KEY_PREFIX = "seller-reject-";
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
    private final MerchantScopeGuard accessGuard;
    private final CustomerPiiMasking piiMasking;
    private final DeliveryCodeHasher deliveryCodeHasher;
    private final OrderStateMachine stateMachine;
    private final OrderStockService orderStockService;
    private final CartService cartService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final DeliveryAddressResolver addressResolver;
    private final PricingService pricingService;
    private final DeliveryRetirementService deliveryRetirementService;
    private final DeliveryAreaPolicy deliveryAreaPolicy;
    private final CheckoutQuoteStore quoteStore;
    private final SecureRandom random = new SecureRandom();

    public OrderService(CartRepository cartRepository, OrderRepository orderRepository, PaymentRepository paymentRepository, DeliveryRepository deliveryRepository, InventoryService inventoryService, AppUserProfileRepository userProfileRepository, VendorRepository vendorRepository, NotificationService notificationService, PaymentService paymentService, AuditLogService auditLogService, OrderMapper mapper, TenantContext tenantContext, SkuRepository skuRepository, ProductRepository productRepository, CheckoutDiscountResolver discountResolver, DeliveryCodeHasher deliveryCodeHasher, OrderStockService orderStockService, CartService cartService, PlatformTransactionManager transactionManager, DeliveryAddressResolver addressResolver, PricingService pricingService, MerchantScopeGuard merchantScopeGuard, DeliveryRetirementService deliveryRetirementService, OrderStateMachine stateMachine, DeliveryAreaPolicy deliveryAreaPolicy, CheckoutQuoteStore quoteStore) {
        this.deliveryRetirementService = deliveryRetirementService;
        this.deliveryAreaPolicy = deliveryAreaPolicy;
        this.quoteStore = quoteStore;
        this.addressResolver = addressResolver;
        this.pricingService = pricingService;
        this.orderStockService = orderStockService;
        this.stateMachine = stateMachine;
        this.cartService = cartService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
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
        // Merchant scope comes straight from vendor.api; the SUPPORT masking rule is the order module's own
        // CustomerPiiMasking, shared with the sales lens (ADR-0002 C5: order no longer depends on sales).
        this.accessGuard = merchantScopeGuard;
        this.piiMasking = new CustomerPiiMasking(merchantScopeGuard);
        this.deliveryCodeHasher = deliveryCodeHasher;
    }

    /**
     * Returns the caller's newest orders with names resolved: tenant-wide for OPS/ADMIN, all tenants for the platform
     * admin, own vendor only for merchant users. Capped at 200 (legacy array, LAC-14): the controller adds
     * {@code X-Truncated: true} when more exist; {@code GET /orders/cursor} pages through everything.
     */
    @Transactional(readOnly = true)
    public com.delivery.common.pagination.CappedList<AdminOrderResponse> listForCurrentContext() {
        // Fully qualified pagination types: keeps this LAC-14 change inside the method (OrderService is a merge hotspot).
        Pageable cap = com.delivery.common.pagination.PageLimits.legacyCap();
        Page<Order> orders;
        if (accessGuard.isMerchantScoped()) {
            UUID tenantId = tenantId();
            orders = orderRepository.findByTenantIdAndVendorId(tenantId, accessGuard.requireOwnVendorId(tenantId), cap);
        } else {
            orders = tenantContext.isPlatformAdmin()
                    ? orderRepository.findAll(cap)
                    : orderRepository.findByTenantId(tenantId(), cap);
        }
        return new com.delivery.common.pagination.CappedList<>(toAdminResponses(orders.getContent()), orders.hasNext());
    }

    /** Resolves customer/vendor names (and SUPPORT masking) for a list of orders; shared with {@link OrderListingService}. */
    List<AdminOrderResponse> toAdminResponses(List<Order> orders) {
        Set<UUID> customerIds = orders.stream().map(Order::getCustomerId).collect(Collectors.toSet());
        Set<UUID> vendorIds = orders.stream().map(Order::getVendorId).collect(Collectors.toSet());
        Map<UUID, String> customerNames = userProfileRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(AppUserProfile::getId, AppUserProfile::getDisplayName));
        Map<UUID, String> vendorNames = vendorRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(Vendor::getId, Vendor::getName));
        // Same PII rule as the sales lens: only a pure SUPPORT caller gets masked customer names.
        boolean maskPii = piiMasking.shouldMaskCustomerPii();
        return orders.stream()
                .map(o -> mapper.toAdminResponse(o,
                        maskPii ? CustomerPiiMasking.maskName(customerNames.getOrDefault(o.getCustomerId(), "Unknown"))
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

    /**
     * Creates an order, reserves stock, and creates local payment/delivery records idempotently.
     * Deliberately not @Transactional: the order is created in its own transaction so that, when a
     * concurrent request with the same key wins the unique constraint (COR-06), this request can roll
     * back and re-read the winner in a fresh transaction instead of failing with a 500.
     */
    public CheckoutResult checkout(UUID cartId, String idempotencyKey) {
        return checkout(cartId, idempotencyKey, null);
    }

    /**
     * Checkout with a delivery address (INC-12): the customer's own address is copied into an immutable order
     * snapshot. A null addressId is the legacy request shape, accepted only while the compatibility window is open.
     */
    public CheckoutResult checkout(UUID cartId, String idempotencyKey, UUID addressId) {
        return checkout(cartId, idempotencyKey, addressId, null);
    }

    /**
     * Checkout v2 (W6-A): with {@code quoteId} (from GET /checkout/quote) the order is charged exactly that quote, or
     * 409 quote_changed carries a fresh quote when it expired or its prices/fees moved (a repriced SKU included, so
     * price_changed never surfaces on this path). Without quoteId the pre-W6-A revalidation applies (409 price_changed).
     */
    public CheckoutResult checkout(UUID cartId, String idempotencyKey, UUID addressId, UUID quoteId) {
        UUID tenantId = tenantId();
        UUID customerId = getCurrentCustomerId();
        try {
            return transactionTemplate.execute(status -> checkoutInTransaction(tenantId, customerId, cartId, idempotencyKey, addressId, quoteId));
        } catch (DataIntegrityViolationException race) {
            // Another request with this key committed first and our INSERT hit uq_orders_tenant_checkout_key.
            CheckoutResult replay = readOnlyTransactionTemplate.execute(status -> orderRepository
                    .findByTenantIdAndCheckoutIdempotencyKey(tenantId, idempotencyKey)
                    .map(existing -> replay(existing, customerId, cartId, addressId))
                    .orElse(null));
            if (replay == null) {
                throw race; // not an idempotency race (e.g. another constraint): surfaces as a neutral 409
            }
            return replay;
        } catch (PriceChangedException changed) {
            // Never charge a stale price silently: refresh the cart so the customer can review and reconfirm (AS-22).
            cartService.repriceItems(tenantId, cartId, changed.getChanges().stream()
                    .collect(Collectors.toMap(PriceChangedException.PriceChange::skuId, PriceChangedException.PriceChange::currentUnitPrice, (first, second) -> first)));
            if (quoteId != null) {
                throw new QuoteChangedException(QuoteChangedException.Reason.PRICES_CHANGED)
                        .withFreshQuote(freshQuote(tenantId, customerId, cartId, addressId), changed.getChanges());
            }
            throw changed;
        } catch (QuoteChangedException stale) {
            // The checkout transaction rolled back; hand the customer a new quote to review (own transaction).
            throw stale.withFreshQuote(freshQuote(tenantId, customerId, cartId, addressId), List.of());
        }
    }

    private CheckoutQuoteResponse freshQuote(UUID tenantId, UUID customerId, UUID cartId, UUID addressId) {
        return transactionTemplate.execute(status -> quoteInTransaction(tenantId, customerId, cartId, addressId));
    }

    private CheckoutResult checkoutInTransaction(UUID tenantId, UUID customerId, UUID cartId, String idempotencyKey, UUID addressId, UUID quoteId) {
        Optional<Order> existing = orderRepository.findByTenantIdAndCheckoutIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), customerId, cartId, addressId);
        }
        // Row lock serializes concurrent checkouts of the same cart (double click, client retries).
        Cart cart = cartRepository.lockByTenantIdAndId(tenantId, cartId).orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!cart.getCustomerId().equals(customerId)) {
            throw new BusinessException("cart_access_denied", "Access to this cart is not permitted", HttpStatus.FORBIDDEN);
        }
        // A duplicate request may have committed while this one waited for the cart lock.
        existing = orderRepository.findByTenantIdAndCheckoutIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), customerId, cartId, addressId);
        }
        CheckoutQuote confirmedQuote = quoteId == null ? null : requireUsableQuote(tenantId, customerId, quoteId, cartId, addressId);
        Optional<DeliveryAddressSnapshot> deliveryAddress = addressResolver.resolve(tenantId, customerId, addressId);
        return new CheckoutResult(mapper.toResponse(createOrder(tenantId, customerId, cart, idempotencyKey, deliveryAddress, confirmedQuote)), false);
    }

    /** The caller's quote for this cart and address (404 / 422 quote_mismatch), not yet expired (409 quote_changed). */
    private CheckoutQuote requireUsableQuote(UUID tenantId, UUID customerId, UUID quoteId, UUID cartId, UUID addressId) {
        CheckoutQuote quote = quoteStore.requireOwn(tenantId, customerId, quoteId);
        if (!quote.getCartId().equals(cartId) || !java.util.Objects.equals(quote.getAddressId(), addressId)) {
            throw new BusinessException("quote_mismatch", "The checkout quote was issued for another cart or address", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (quote.isExpiredAt(java.time.Instant.now())) {
            throw new QuoteChangedException(QuoteChangedException.Reason.EXPIRED);
        }
        return quote;
    }

    /** Returns the order already created for this key, but only to the same customer, cart and address (409 otherwise). */
    private CheckoutResult replay(Order existing, UUID customerId, UUID cartId, UUID addressId) {
        boolean sameCustomer = existing.getCustomerId().equals(customerId);
        // Legacy orders (before V041) carry no cart link; for them only the customer can be compared.
        boolean sameCart = existing.getCartId() == null || existing.getCartId().equals(cartId);
        // A retry may omit addressId (legacy shape); a different address under the same key is a different checkout.
        boolean sameAddress = addressId == null || existing.getDeliveryAddress() == null
                || addressId.equals(existing.getDeliveryAddress().getSourceAddressId());
        if (!sameCustomer || !sameCart || !sameAddress) {
            throw new BusinessException("idempotency_key_reused", "This idempotency key was already used for a different checkout", HttpStatus.CONFLICT);
        }
        return new CheckoutResult(mapper.toResponse(existing), true);
    }

    /** Checkout-time revalidation: SKU active, product ACTIVE and price unchanged since the cart snapshot. */
    private void revalidateItems(UUID tenantId, Cart cart) {
        List<PriceChangedException.PriceChange> changes = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Sku sku = skuRepository.findByTenantIdAndId(tenantId, item.getSkuId())
                    .filter(Sku::isActive)
                    .orElseThrow(() -> new BusinessException("sku_inactive", "A product variant in the cart is no longer available", HttpStatus.CONFLICT));
            boolean productActive = productRepository.findByTenantIdAndId(tenantId, sku.getProductId()).map(Product::isActive).orElse(false);
            if (!productActive) {
                throw new BusinessException("product_not_available", "A product in the cart is not available for ordering", HttpStatus.CONFLICT);
            }
            if (sku.getPrice().compareTo(item.getUnitPriceSnapshot()) != 0) {
                changes.add(new PriceChangedException.PriceChange(sku.getId(), item.getUnitPriceSnapshot(), sku.getPrice()));
            }
        }
        if (!changes.isEmpty()) {
            throw new PriceChangedException(changes);
        }
    }

    private Order createOrder(UUID tenantId, UUID currentCustomerId, Cart cart, String idempotencyKey, Optional<DeliveryAddressSnapshot> deliveryAddress,
                              CheckoutQuote confirmedQuote) {
        if (!"ACTIVE".equals(cart.getStatus()) || cart.getItems().isEmpty()) {
            throw new BusinessException("invalid_cart", "Cart must be active and contain items", HttpStatus.BAD_REQUEST);
        }
        // LAC-27/LAC-64: store open now and no pharmacy product while the vertical is blocked (422 store_closed /
        // pharmacy_not_available), checked before the price revalidation so a closed store never reprices the cart.
        cartService.requireCheckoutEligible(tenantId, cart);
        revalidateItems(tenantId, cart);
        // Fees and VAT are re-quoted here with the delivery address (INC-13): the cart's own fees were estimated
        // without one. Discounts are applied below through the spec-002 seam, on top of this quote. W6-A: the address
        // must be inside the vendor's service area and within its maximum delivery distance (422 otherwise).
        Map<UUID, Product> productBySkuId = resolveProductBySkuId(cart);
        GeoPoint destination = destinationOf(deliveryAddress);
        Vendor vendor = vendorRepository.findByTenantIdAndId(tenantId, cart.getVendorId()).orElse(null);
        deliveryAreaPolicy.requireInServiceArea(tenantId, vendor, destination);
        PriceQuote quote = pricingService.quote(tenantId, cart.getVendorId(), taxableLines(cart, productBySkuId), destination);
        deliveryAreaPolicy.requireWithinMaxDistance(vendor, quote);
        if (confirmedQuote != null && !confirmedQuote.samePricesAndFees(quote.subtotal(), quote.deliveryFee(), quote.serviceFee(), quote.taxes())) {
            throw new QuoteChangedException(QuoteChangedException.Reason.AMOUNTS_CHANGED);
        }
        // Fixed SKU order for the inventory row locks prevents deadlocks between carts sharing SKUs.
        List<CartItem> bySku = cart.getItems().stream().sorted(Comparator.comparing(CartItem::getSkuId)).toList();
        for (CartItem item : bySku) {
            inventoryService.reserve(tenantId, item.getSkuId(), item.getQuantity());
        }
        // Plain SecureRandom PIN, HMAC-hashed; no per-tenant uniqueness (a PIN is verified against one delivery only).
        String code = deliveryCodeHasher.generateCode();
        Order order = new Order(UUID.randomUUID(), tenantId, reference(), cart.getCustomerId(), cart.getVendorId(), cart.getSubtotal(), quote.fees(), quote.taxes(), cart.getDiscounts(), quote.total(), idempotencyKey, deliveryCodeHasher.hash(code), code);
        order.linkCart(cart.getId());
        deliveryAddress.ifPresent(order::recordDeliveryAddress);
        for (CartItem item : cart.getItems()) {
            Product product = productBySkuId.get(item.getSkuId());
            OrderItem line = new OrderItem(UUID.randomUUID(), order, tenantId, item.getSkuId(), item.getProductNameSnapshot(), item.getSkuNameSnapshot(),
                    item.getUnitPriceSnapshot(), item.getQuantity(), product == null ? null : product.getCategoryId());
            TaxCategory category = taxCategoryOf(product);
            BigDecimal vatRate = category.rate(quote.taxRate());
            line.recordTax(category, vatRate, quote.taxInclusive() ? VatBreakdown.vatIn(line.getLineTotal(), vatRate) : null);
            order.addItem(line);
        }
        // Discount seam (spec 002): the resolver defaults to zero, keeping legacy checkout totals untouched.
        CheckoutDiscount discount = discountResolver.resolve(cart, currentCustomerId);
        if (discount.discountTotal().signum() > 0) {
            order.applyDiscount(discount.appliedPromotionId(), discount.discountTotal());
        }
        // W6-A: the quote snapshot carries the VAT contained in the charged total (after the discount).
        order.recordPricingQuote(PricingQuoteSnapshot.of(quote, quote.vat(order.getDiscountTotal()), confirmedQuote == null ? null : confirmedQuote.getId()));
        Order saved = orderRepository.save(order);
        stateMachine.recordCreated(saved); // history row null -> PAYMENT_PENDING (V058)
        Payment payment = paymentRepository.save(new Payment(UUID.randomUUID(), tenantId, saved.getId(), saved.getTotal(), idempotencyKey));
        paymentService.recordCreated(payment); // history row null -> PENDING_CONFIRMATION (V058)
        deliveryRepository.save(new Delivery(UUID.randomUUID(), tenantId, saved.getId(), saved.getDeliveryConfirmationCodeHash()));
        cart.markCheckedOut();
        return saved;
    }

    /**
     * Price quote for checking out the customer's cart to one of their addresses, from the same engine and inputs
     * as checkout (INC-13). Without addressId the base delivery fee applies (estimate). The discount shown is the
     * cart's pending coupon/promotion; checkout re-validates it. W6-A: the quote is stored with a TTL and its
     * quoteId/expiresAt returned (send quoteId on POST /checkout); the address must be inside the vendor's service
     * area and maximum distance (422 out_of_service_area / address_too_far), and the response carries the VAT
     * contained in the total ("IVA incluído").
     */
    @Transactional
    public CheckoutQuoteResponse quote(UUID cartId, UUID addressId) {
        return quoteInTransaction(tenantId(), getCurrentCustomerId(), cartId, addressId);
    }

    private CheckoutQuoteResponse quoteInTransaction(UUID tenantId, UUID customerId, UUID cartId, UUID addressId) {
        Cart cart = cartRepository.findByTenantIdAndId(tenantId, cartId).orElseThrow(() -> new NotFoundException("Cart was not found"));
        if (!cart.getCustomerId().equals(customerId)) {
            throw new BusinessException("cart_access_denied", "Access to this cart is not permitted", HttpStatus.FORBIDDEN);
        }
        GeoPoint destination = null;
        if (addressId != null) {
            destination = destinationOf(addressResolver.resolve(tenantId, customerId, addressId));
        }
        Vendor vendor = cart.getVendorId() == null ? null : vendorRepository.findByTenantIdAndId(tenantId, cart.getVendorId()).orElse(null);
        deliveryAreaPolicy.requireInServiceArea(tenantId, vendor, destination);
        PriceQuote quote = pricingService.quote(tenantId, cart.getVendorId(), taxableLines(cart, resolveProductBySkuId(cart)), destination);
        deliveryAreaPolicy.requireWithinMaxDistance(vendor, quote);
        BigDecimal discountTotal = cart.getDiscountTotal();
        BigDecimal total = PricingMath.orderTotal(quote.subtotal(), quote.fees(), quote.taxes(), discountTotal);
        VatBreakdown vat = quote.vat(discountTotal);
        CheckoutQuote stored = quoteStore.save(tenantId, customerId, cart.getId(), addressId, cart.getVendorId(), new CheckoutQuote.Amounts(
                quote.subtotal(), quote.deliveryFee(), quote.serviceFee(), quote.taxes(), discountTotal, total, vat.vatTotal(), quote.taxInclusive(), quote.engineVersion()));
        return new CheckoutQuoteResponse(cart.getId(), addressId, quote.subtotal(), quote.deliveryFee(), quote.serviceFee(), quote.taxes(),
                discountTotal, total, quote.delivery().basis(), quote.delivery().distanceKm(), stored.getId(), stored.getExpiresAt(),
                quote.taxInclusive(), quote.taxRate(), vat.vatTotal(), vat.goodsVat(), vat.feesVat(), vat.netTotal());
    }

    /** Cart lines as VAT-taxable lines (gross = unit price snapshot × quantity) with each product's tax category. */
    private static List<TaxableLine> taxableLines(Cart cart, Map<UUID, Product> productBySkuId) {
        return cart.getItems().stream()
                .map(item -> new TaxableLine(item.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())),
                        taxCategoryOf(productBySkuId.get(item.getSkuId()))))
                .toList();
    }

    private static TaxCategory taxCategoryOf(Product product) {
        return product == null || product.getTaxCategory() == null ? TaxCategory.STANDARD : product.getTaxCategory();
    }

    private static GeoPoint destinationOf(Optional<DeliveryAddressSnapshot> address) {
        return address.flatMap(a -> GeoPoint.of(a.getLatitude(), a.getLongitude())).orElse(null);
    }

    private String reference() { return "PA-" + System.currentTimeMillis() + "-" + random.nextInt(10_000); }

    /**
     * Current product per cart SKU, read once per checkout/quote: its category is snapshotted on the order line (so
     * category reports stay stable over recategorisation) and its tax category drives the VAT extraction (W6-A).
     */
    private Map<UUID, Product> resolveProductBySkuId(Cart cart) {
        Set<UUID> skuIds = cart.getItems().stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> productIdBySkuId = skuRepository.findAllById(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Sku::getProductId));
        Map<UUID, Product> productById = productRepository.findAllById(Set.copyOf(productIdBySkuId.values())).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        Map<UUID, Product> result = new java.util.HashMap<>();
        productIdBySkuId.forEach((skuId, productId) -> {
            Product product = productById.get(productId);
            if (product != null) {
                result.put(skuId, product);
            }
        });
        return result;
    }

    private UUID getCurrentCustomerId() {
        UUID tenantId = tenantId();
        String keycloakUserId = tenantContext.currentKeycloakUserId().orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        AppUserProfile currentUser = userProfileRepository.findByTenantIdAndKeycloakUserId(tenantId, keycloakUserId)
                .orElseThrow(() -> new NotFoundException("User profile was not found"));
        return currentUser.getId();
    }

    private UUID tenantId() { return tenantContext.currentTenantId().orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN)); }

    /** Marks an order as accepted by the vendor; only from PAYMENT_CONFIRMED (409 invalid_order_transition otherwise). */
    @Transactional
    public OrderResponse acceptByVendor(UUID orderId) {
        Order order = findOrder(orderId);
        stateMachine.transition(order, OrderStatus.ACCEPTED_BY_VENDOR, "VENDOR_ACCEPTED"); // only from PAYMENT_CONFIRMED (409 otherwise)
        auditLogService.log("ORDER_ACCEPTED", "order", orderId.toString(), order.getReference(), "SUCCESS");
        return mapper.toResponse(order);
    }

    /**
     * Seller rejection (unified product decision): every rejection ends with the order CANCELLED and
     * {@code cancellation_reason = SELLER_REJECTED}, the vendor text kept in {@code rejection_reason}, stock released
     * (INC-07), the plain delivery PIN cleared, the delivery retired (CANCELLED, active dispatch job cancelled) while
     * its goods are still in the store, a refund of whatever is still refundable on the payment (the full
     * amount when nothing was refunded before, nothing when it is already fully covered), one customer notification
     * and one audit entry — all in one transaction, so any failure rolls back everything. The financial state
     * (PARTIALLY_/FULLY_REFUNDED) lives on the payment. Allowed from PAYMENT_CONFIRMED/ACCEPTED_BY_VENDOR only (409
     * invalid_order_transition otherwise).
     *
     * Lock order rule: the payment row is locked FOR UPDATE before the order is read (SalesService does the same),
     * so concurrent rejections, cancellations and refunds of one order serialize and the second caller sees the
     * committed state. A repeat on an order already CANCELLED (any reason) or in the legacy REJECTED_BY_VENDOR
     * status returns the current state without new side effects (idempotent).
     */
    @Transactional
    public OrderResponse rejectByVendor(UUID orderId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("rejection_reason_required", "A rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        Payment payment = paymentRepository.findByTenantIdAndOrderIdForUpdate(tenantId(), orderId).orElse(null);
        Order order = findOrder(orderId);
        if (REJECTION_ALREADY_SETTLED_STATUSES.contains(order.getStatus())) {
            return mapper.toResponse(order); // idempotent repeat: already cancelled/rejected, nothing more to do
        }
        if (!VENDOR_REJECTION_ALLOWED_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("invalid_order_transition", "Order cannot be rejected in status " + order.getStatus(), HttpStatus.CONFLICT);
        }
        if (payment == null) {
            throw new NotFoundException("Payment was not found for order");
        }
        stateMachine.cancelBySellerRejection(order, reason);
        deliveryRetirementService.retireBeforePickup(order.getTenantId(), order.getId(), "seller rejected the order");
        orderStockService.release(order); // INC-07 (COR-01): rejected orders give their reservation back
        // The refund is created REQUESTED here; a future PSP payout runs after commit (PaymentService#requestRemainingRefund)
        // and can never undo this cancellation.
        BigDecimal refunded = paymentService.requestRemainingRefund(payment.getId(), "Vendor rejection: " + reason, SELLER_REJECT_REFUND_KEY_PREFIX + orderId)
                .map(RefundResponse::amount)
                .orElse(BigDecimal.ZERO);
        notifyRejected(order, reason);
        // No PII: the order id identifies the target; the business reference carries only the refunded remainder.
        auditLogService.log(SELLER_REJECTION_AUDIT_ACTION, "order", orderId.toString(),
                "refund_remaining=" + refunded.setScale(2, RoundingMode.HALF_UP).toPlainString(), "SUCCESS");
        return mapper.toResponse(order);
    }

    private void notifyRejected(Order order, String reason) {
        notificationService.create(
                order.getCustomerId(),
                "CUSTOMER",
                "order_rejected",
                "Order Rejected",
                String.format("Your order %s has been rejected by the vendor. Reason: %s", order.getReference(), reason),
                order.getReference());
    }

    /** Marks an accepted order as preparing (409 invalid_order_transition from any other status). */
    @Transactional
    public OrderResponse markPreparing(UUID orderId) {
        Order order = findOrder(orderId);
        stateMachine.transition(order, OrderStatus.PREPARING, "VENDOR_PREPARING"); // only from ACCEPTED_BY_VENDOR (409 otherwise)
        auditLogService.log("ORDER_PREPARING", "order", orderId.toString(), order.getReference(), "SUCCESS");
        return mapper.toResponse(order);
    }

    /** Marks a preparing order as ready for pickup (409 invalid_order_transition from any other status). */
    @Transactional
    public OrderResponse markReadyForPickup(UUID orderId) {
        Order order = findOrder(orderId);
        stateMachine.transition(order, OrderStatus.READY_FOR_PICKUP, "VENDOR_READY"); // only from PREPARING (409 otherwise)
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
