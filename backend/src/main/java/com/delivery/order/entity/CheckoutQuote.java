package com.delivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A checkout price quote handed to the customer with a validity window (V064, W6-A): GET /checkout/quote stores one
 * and returns its id + expiresAt; POST /checkout with that quoteId refuses (409 quote_changed) once it expired or when
 * the prices/fees it showed moved. Insert-only; old rows are purged by the checkout-quote-cleanup job.
 */
@Entity
@Table(name = "checkout_quotes")
public class CheckoutQuote {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;
    @Column(name = "address_id", updatable = false)
    private UUID addressId;
    @Column(name = "vendor_id", updatable = false)
    private UUID vendorId;
    @Column(nullable = false, updatable = false)
    private BigDecimal subtotal;
    @Column(name = "delivery_fee", nullable = false, updatable = false)
    private BigDecimal deliveryFee;
    @Column(name = "service_fee", nullable = false, updatable = false)
    private BigDecimal serviceFee;
    @Column(nullable = false, updatable = false)
    private BigDecimal taxes;
    @Column(name = "discount_total", nullable = false, updatable = false)
    private BigDecimal discountTotal;
    @Column(nullable = false, updatable = false)
    private BigDecimal total;
    @Column(name = "vat_total", nullable = false, updatable = false)
    private BigDecimal vatTotal;
    @Column(name = "tax_inclusive", nullable = false, updatable = false)
    private boolean taxInclusive;
    @Column(name = "engine_version", nullable = false, updatable = false)
    private String engineVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected CheckoutQuote() {}

    /** Amounts of one quote as shown to the customer. */
    public record Amounts(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal taxes,
                          BigDecimal discountTotal, BigDecimal total, BigDecimal vatTotal, boolean taxInclusive, String engineVersion) {}

    public CheckoutQuote(UUID id, UUID tenantId, UUID customerId, UUID cartId, UUID addressId, UUID vendorId,
                         Amounts amounts, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.cartId = cartId;
        this.addressId = addressId;
        this.vendorId = vendorId;
        this.subtotal = amounts.subtotal();
        this.deliveryFee = amounts.deliveryFee();
        this.serviceFee = amounts.serviceFee();
        this.taxes = amounts.taxes();
        this.discountTotal = amounts.discountTotal();
        this.total = amounts.total();
        this.vatTotal = amounts.vatTotal();
        this.taxInclusive = amounts.taxInclusive();
        this.engineVersion = amounts.engineVersion();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** True once {@code now} is at or after the expiry instant. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * True when the prices and fees of {@code current} equal this quote's (subtotal, delivery fee, service fee, tax
     * line). The discount is deliberately not compared: checkout re-resolves promotions (a coupon that became invalid
     * fails on its own with 422; an automatic promotion only lowers the total).
     */
    public boolean samePricesAndFees(BigDecimal currentSubtotal, BigDecimal currentDeliveryFee, BigDecimal currentServiceFee, BigDecimal currentTaxes) {
        return subtotal.compareTo(currentSubtotal) == 0 && deliveryFee.compareTo(currentDeliveryFee) == 0
                && serviceFee.compareTo(currentServiceFee) == 0 && taxes.compareTo(currentTaxes) == 0;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getCartId() { return cartId; }
    public UUID getAddressId() { return addressId; }
    public UUID getVendorId() { return vendorId; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public BigDecimal getTaxes() { return taxes; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getVatTotal() { return vatTotal; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public String getEngineVersion() { return engineVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
