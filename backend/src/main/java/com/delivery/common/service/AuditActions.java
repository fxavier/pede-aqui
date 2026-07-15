package com.delivery.common.service;

/** Central catalogue of audit_logs action constants for Spec-002 mutations (data-model §5). */
public final class AuditActions {
    // Catalog
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_PRICE_UPDATED = "PRODUCT_PRICE_UPDATED";
    public static final String PRODUCT_PRICE_PENDING = "PRODUCT_PRICE_PENDING";
    public static final String PRODUCT_PRICE_APPROVED = "PRODUCT_PRICE_APPROVED";
    public static final String PRODUCT_PRICE_REJECTED = "PRODUCT_PRICE_REJECTED";
    public static final String PRODUCT_IMAGE_SET = "PRODUCT_IMAGE_SET";
    public static final String PRODUCT_IMAGE_CLEARED = "PRODUCT_IMAGE_CLEARED";
    // Sales
    public static final String SALE_CANCELLED = "SALE_CANCELLED";
    public static final String SALE_REFUNDED = "SALE_REFUNDED";
    public static final String SALE_NOTIFICATION_RESENT = "SALE_NOTIFICATION_RESENT";
    public static final String SALE_STATUS_OVERRIDDEN = "SALE_STATUS_OVERRIDDEN";
    // Promotions
    public static final String PROMOTION_CREATED = "PROMOTION_CREATED";
    public static final String PROMOTION_UPDATED = "PROMOTION_UPDATED";
    public static final String PROMOTION_ACTIVATED = "PROMOTION_ACTIVATED";
    public static final String PROMOTION_PAUSED = "PROMOTION_PAUSED";
    public static final String PROMOTION_DELETED = "PROMOTION_DELETED";

    private AuditActions() {
    }
}
