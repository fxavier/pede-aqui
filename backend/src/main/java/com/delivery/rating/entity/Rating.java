package com.delivery.rating.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "rater_user_id", nullable = false)
    private UUID raterUserId;

    @Column(name = "rated_vendor_id")
    private UUID ratedVendorId;

    @Column(name = "rated_courier_id")
    private UUID ratedCourierId;

    @Column(nullable = false)
    private Integer stars;

    @Column(length = 500)
    private String comment;

    @Column(name = "vendor_reply", length = 500)
    private String vendorReply;

    @Column(name = "vendor_replied_at")
    private Instant vendorRepliedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Rating() {
    }

    public Rating(UUID id, UUID tenantId, UUID orderId, UUID raterUserId, Integer stars) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.raterUserId = raterUserId;
        this.stars = stars;
        this.createdAt = Instant.now();
    }

    public void setRatedVendor(UUID vendorId) {
        this.ratedVendorId = vendorId;
    }

    public void setRatedCourier(UUID courierId) {
        this.ratedCourierId = courierId;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void addVendorReply(String reply) {
        this.vendorReply = reply;
        this.vendorRepliedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getRaterUserId() {
        return raterUserId;
    }

    public UUID getRatedVendorId() {
        return ratedVendorId;
    }

    public UUID getRatedCourierId() {
        return ratedCourierId;
    }

    public Integer getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public String getVendorReply() {
        return vendorReply;
    }

    public Instant getVendorRepliedAt() {
        return vendorRepliedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}