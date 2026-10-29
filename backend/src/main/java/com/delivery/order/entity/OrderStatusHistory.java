package com.delivery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One order status transition (V058, LAC-15), written by {@code OrderStateMachine} in the transaction that changed the
 * order. Append-only: the table rejects UPDATE/DELETE (trigger), and the entity has no setters.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;
    /** Null for the creation row (checkout). */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;
    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;
    @Column(name = "actor_id", updatable = false)
    private String actorId;
    @Column(updatable = false)
    private String reason;
    @Column(name = "correlation_id", updatable = false)
    private String correlationId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusHistory() {}

    public OrderStatusHistory(UUID tenantId, UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String actorType,
                              String actorId, String reason, String correlationId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getReason() { return reason; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}
