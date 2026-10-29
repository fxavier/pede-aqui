package com.delivery.common.service;

/** Central catalogue of audit_logs action constants for Spec-002 mutations (data-model §5) and LAC-13/LAC-15 coverage. */
public final class AuditActions {
    // Catalog
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_PRICE_UPDATED = "PRODUCT_PRICE_UPDATED";
    public static final String PRODUCT_PRICE_PENDING = "PRODUCT_PRICE_PENDING";
    public static final String PRODUCT_PRICE_APPROVED = "PRODUCT_PRICE_APPROVED";
    public static final String PRODUCT_PRICE_REJECTED = "PRODUCT_PRICE_REJECTED";
    public static final String PRODUCT_IMAGE_SET = "PRODUCT_IMAGE_SET";
    public static final String PRODUCT_IMAGE_CLEARED = "PRODUCT_IMAGE_CLEARED";
    public static final String PRODUCT_APPROVED = "PRODUCT_APPROVED";
    public static final String PRODUCT_REJECTED = "PRODUCT_REJECTED";
    // Inventory
    public static final String INVENTORY_STOCK_UPDATED = "INVENTORY_STOCK_UPDATED";
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
    // Users (LAC-13)
    public static final String USER_ROLES_ASSIGNED = "USER_ROLES_ASSIGNED";
    // Platform and tenant configuration (LAC-15)
    public static final String FEE_POLICY_UPDATED = "FEE_POLICY_UPDATED";
    public static final String ZONE_CREATED = "ZONE_CREATED";
    public static final String TENANT_CREATED = "TENANT_CREATED";
    public static final String TENANT_STATUS_CHANGED = "TENANT_STATUS_CHANGED";
    // Onboarding approvals (LAC-15)
    public static final String COURIER_APPROVED = "COURIER_APPROVED";
    public static final String COURIER_REJECTED = "COURIER_REJECTED";
    public static final String VENDOR_VERIFICATION_APPROVED = "VENDOR_VERIFICATION_APPROVED";
    public static final String VENDOR_VERIFICATION_REJECTED = "VENDOR_VERIFICATION_REJECTED";

    private AuditActions() {
    }
}
