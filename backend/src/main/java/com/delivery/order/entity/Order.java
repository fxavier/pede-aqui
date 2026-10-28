package com.delivery.order.entity;

import com.delivery.common.pricing.PricingMath;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Represents a confirmed customer purchase for one vendor. */
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, unique = true)
    private String reference;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Column(nullable = false)
    private BigDecimal subtotal;
    @Column(nullable = false)
    private BigDecimal fees;
    @Column(nullable = false)
    private BigDecimal taxes;
    @Column(nullable = false)
    private BigDecimal discounts;
    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;
    @Column(name = "applied_promotion_id")
    private UUID appliedPromotionId;
    @Column(nullable = false)
    private BigDecimal total;
    @Column(name = "checkout_idempotency_key", nullable = false)
    private String checkoutIdempotencyKey;
    @Column(name = "delivery_confirmation_code_hash", nullable = false)
    private String deliveryConfirmationCodeHash;
    /** Plain PIN shown to the customer while the delivery is active; cleared (null) once the order is delivered or cancelled. */
    @Column(name = "delivery_confirmation_code_display")
    private String deliveryConfirmationCodeDisplay;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "rejection_reason")
    private String rejectionReason;
    /** Why the order was cancelled (V044); null unless a cancellation path records one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private CancellationReason cancellationReason;
    @Column(name = "cart_id")
    private UUID cartId;
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false)
    private StockStatus stockStatus = StockStatus.RESERVED;
    /** Delivery address snapshot taken at checkout (INC-12); null for legacy orders. */
    @Embedded
    private DeliveryAddressSnapshot deliveryAddress;
    @Version
    private long version;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(UUID id, UUID tenantId, String reference, UUID customerId, UUID vendorId, BigDecimal subtotal, BigDecimal fees, BigDecimal taxes, BigDecimal discounts, BigDecimal total, String checkoutIdempotencyKey, String codeHash, String codeDisplay) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.customerId = customerId;
        this.vendorId = vendorId;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.subtotal = subtotal;
        this.fees = fees;
        this.taxes = taxes;
        this.discounts = discounts;
        this.discountTotal = BigDecimal.ZERO;
        this.total = total;
        this.checkoutIdempotencyKey = checkoutIdempotencyKey;
        this.deliveryConfirmationCodeHash = codeHash;
        this.deliveryConfirmationCodeDisplay = codeDisplay;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(OrderItem item) { items.add(item); }
    /** Records the cart this order was checked out from (idempotency-key reuse detection). */
    public void linkCart(UUID cartId) { this.cartId = cartId; }
    /** Records where the order goes; only before the order is first persisted (the snapshot is immutable). */
    public void recordDeliveryAddress(DeliveryAddressSnapshot snapshot) {
        if (this.deliveryAddress != null) {
            throw new IllegalStateException("Delivery address snapshot is immutable");
        }
        this.deliveryAddress = snapshot;
    }
    /** Moves RESERVED stock to RELEASED; returns false when stock was already released or committed. */
    public boolean markStockReleased() { return transitionStock(StockStatus.RELEASED); }
    /** Moves RESERVED stock to COMMITTED; returns false when stock was already released or committed. */
    public boolean markStockCommitted() { return transitionStock(StockStatus.COMMITTED); }
    private boolean transitionStock(StockStatus target) {
        if (stockStatus != StockStatus.RESERVED) return false;
        stockStatus = target;
        updatedAt = Instant.now();
        return true;
    }
    /** Applies the checkout-resolved discount and recomputes the total via the canonical pricing formula. */
    public void applyDiscount(UUID appliedPromotionId, BigDecimal discountTotal) {
        this.appliedPromotionId = appliedPromotionId;
        this.discountTotal = discountTotal;
        this.total = PricingMath.orderTotal(this.subtotal, this.fees, this.taxes, discountTotal);
        this.updatedAt = Instant.now();
    }
    public void markPaymentConfirmed() { this.status = OrderStatus.PAYMENT_CONFIRMED; this.updatedAt = Instant.now(); }
    public void markAcceptedByVendor() { this.status = OrderStatus.ACCEPTED_BY_VENDOR; this.updatedAt = Instant.now(); }
    public void markPreparing() { this.status = OrderStatus.PREPARING; this.updatedAt = Instant.now(); }
    public void markReadyForPickup() { this.status = OrderStatus.READY_FOR_PICKUP; this.updatedAt = Instant.now(); }
    public void markAssignedToCourier() { this.status = OrderStatus.ASSIGNED_TO_COURIER; this.updatedAt = Instant.now(); }
    public void markRefundPending() { this.status = OrderStatus.REFUND_PENDING; this.updatedAt = Instant.now(); }
    public void markRefunded() { this.status = OrderStatus.REFUNDED; this.updatedAt = Instant.now(); }
    public void markCancelled() { this.status = OrderStatus.CANCELLED; this.deliveryConfirmationCodeDisplay = null; this.updatedAt = Instant.now(); }
    /** Seller rejection once the payment already carries refunds: CANCELLED with reason SELLER_REJECTED (financial state lives on Payment). */
    public void markCancelledBySellerRejection(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = CancellationReason.SELLER_REJECTED;
        this.rejectionReason = reason;
        this.deliveryConfirmationCodeDisplay = null;
        this.updatedAt = Instant.now();
    }
    public void markRejectedByVendor(String reason) { this.status = OrderStatus.REJECTED_BY_VENDOR; this.rejectionReason = reason; this.updatedAt = Instant.now(); }
    public void markDelivered() { this.status = OrderStatus.DELIVERED; this.deliveryConfirmationCodeDisplay = null; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getReference() { return reference; }
    public UUID getCustomerId() { return customerId; }
    public UUID getVendorId() { return vendorId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getFees() { return fees; }
    public BigDecimal getTaxes() { return taxes; }
    public BigDecimal getDiscounts() { return discounts; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public UUID getAppliedPromotionId() { return appliedPromotionId; }
    public BigDecimal getTotal() { return total; }
    public String getCheckoutIdempotencyKey() { return checkoutIdempotencyKey; }
    public String getDeliveryConfirmationCodeHash() { return deliveryConfirmationCodeHash; }
    public String getDeliveryConfirmationCodeDisplay() { return deliveryConfirmationCodeDisplay; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRejectionReason() { return rejectionReason; }
    public CancellationReason getCancellationReason() { return cancellationReason; }
    public UUID getCartId() { return cartId; }
    public StockStatus getStockStatus() { return stockStatus; }
    /** The checkout-time delivery address, or null for orders placed without one (pre-INC-12 / legacy window). */
    public DeliveryAddressSnapshot getDeliveryAddress() { return deliveryAddress; }
    public List<OrderItem> getItems() { return items; }
}
