package com.delivery.marketing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Usage ledger row recording one promotion redemption per order (unique promotion_id + order_id). */
@Entity
@Table(name = "promotion_redemption")
public class PromotionRedemption {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromotionRedemption() {}

    public PromotionRedemption(UUID id, UUID tenantId, UUID promotionId, UUID customerId, UUID orderId, BigDecimal amount) {
        this.id = id;
        this.tenantId = tenantId;
        this.promotionId = promotionId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPromotionId() { return promotionId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
