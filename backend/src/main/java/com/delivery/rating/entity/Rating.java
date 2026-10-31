package com.delivery.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer's rating of one delivered order (LAC-28): vendor stars ({@code stars}) with an optional comment, optional
 * courier stars, the vendor's single reply and the moderation state of the comment. {@code raterUserId} is the
 * customer's profile id (= {@code orders.customer_id}). Rules (windows, once-only) are enforced by the service.
 */
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

    @Column(name = "courier_stars")
    private Integer courierStars;

    @Column(length = 500)
    private String comment;

    @Column(name = "comment_hidden", nullable = false)
    private boolean commentHidden;

    @Column(name = "comment_hidden_at")
    private Instant commentHiddenAt;

    @Column(name = "comment_hidden_reason", length = 200)
    private String commentHiddenReason;

    @Column(name = "vendor_reply", length = 500)
    private String vendorReply;

    @Column(name = "vendor_replied_at")
    private Instant vendorRepliedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Rating() {
    }

    public Rating(UUID id, UUID tenantId, UUID orderId, UUID raterUserId, UUID ratedVendorId, UUID ratedCourierId,
                  int stars, Integer courierStars, String comment, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderId = orderId;
        this.raterUserId = raterUserId;
        this.ratedVendorId = ratedVendorId;
        this.ratedCourierId = ratedCourierId;
        this.stars = stars;
        this.courierStars = courierStars;
        this.comment = comment;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces the customer-editable values (the service checks the edit window first). */
    public void edit(int stars, Integer courierStars, String comment, Instant now) {
        this.stars = stars;
        this.courierStars = courierStars;
        this.comment = comment;
        this.updatedAt = now;
    }

    /** Records the vendor's one and only reply (the service rejects a second one). */
    public void reply(String reply, Instant now) {
        this.vendorReply = reply;
        this.vendorRepliedAt = now;
        this.updatedAt = now;
    }

    /** Hides the comment from public/vendor views; the stars keep counting in the averages. */
    public void hideComment(String reason, Instant now) {
        this.commentHidden = true;
        this.commentHiddenAt = now;
        this.commentHiddenReason = reason;
        this.updatedAt = now;
    }

    /** Makes a hidden comment visible again. */
    public void unhideComment(Instant now) {
        this.commentHidden = false;
        this.commentHiddenAt = null;
        this.commentHiddenReason = null;
        this.updatedAt = now;
    }

    public boolean hasVendorReply() {
        return vendorReply != null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getOrderId() { return orderId; }
    public UUID getRaterUserId() { return raterUserId; }
    public UUID getRatedVendorId() { return ratedVendorId; }
    public UUID getRatedCourierId() { return ratedCourierId; }
    public Integer getStars() { return stars; }
    public Integer getCourierStars() { return courierStars; }
    public String getComment() { return comment; }
    public boolean isCommentHidden() { return commentHidden; }
    public Instant getCommentHiddenAt() { return commentHiddenAt; }
    public String getCommentHiddenReason() { return commentHiddenReason; }
    public String getVendorReply() { return vendorReply; }
    public Instant getVendorRepliedAt() { return vendorRepliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
