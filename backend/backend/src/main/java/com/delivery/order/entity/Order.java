package com.delivery.order.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
    @Column(nullable = false)
    private BigDecimal total;
    @Column(name = "checkout_idempotency_key", nullable = false)
    private String checkoutIdempotencyKey;
    @Column(name = "delivery_confirmation_code_hash", nullable = false)
    private String deliveryConfirmationCodeHash;
    @Column(name = "delivery_confirmation_code_display", nullable = false)
    private String deliveryConfirmationCodeDisplay;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
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
        this.total = total;
        this.checkoutIdempotencyKey = checkoutIdempotencyKey;
        this.deliveryConfirmationCodeHash = codeHash;
        this.deliveryConfirmationCodeDisplay = codeDisplay;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(OrderItem item) { items.add(item); }
    public void markPaymentConfirmed() { this.status = OrderStatus.PAYMENT_CONFIRMED; this.updatedAt = Instant.now(); }
    public void markAcceptedByVendor() { this.status = OrderStatus.ACCEPTED_BY_VENDOR; this.updatedAt = Instant.now(); }
    public void markPreparing() { this.status = OrderStatus.PREPARING; this.updatedAt = Instant.now(); }
    public void markReadyForPickup() { this.status = OrderStatus.READY_FOR_PICKUP; this.updatedAt = Instant.now(); }
    public void markAssignedToCourier() { this.status = OrderStatus.ASSIGNED_TO_COURIER; this.updatedAt = Instant.now(); }
    public void markRefundPending() { this.status = OrderStatus.REFUND_PENDING; this.updatedAt = Instant.now(); }
    public void markRefunded() { this.status = OrderStatus.REFUNDED; this.updatedAt = Instant.now(); }
    public void markCancelled() { this.status = OrderStatus.CANCELLED; this.updatedAt = Instant.now(); }
    public void markDelivered() { this.status = OrderStatus.DELIVERED; this.updatedAt = Instant.now(); }

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
    public BigDecimal getTotal() { return total; }
    public String getCheckoutIdempotencyKey() { return checkoutIdempotencyKey; }
    public String getDeliveryConfirmationCodeHash() { return deliveryConfirmationCodeHash; }
    public String getDeliveryConfirmationCodeDisplay() { return deliveryConfirmationCodeDisplay; }
    public List<OrderItem> getItems() { return items; }
}
