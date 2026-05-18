package com.delivery.common.security;

/** Supported marketplace roles mapped from Keycloak JWT claims. */
public enum MarketplaceRole {
    CUSTOMER,
    VENDOR_ADMIN,
    VENDOR_STAFF,
    COURIER,
    ADMIN,
    OPS,
    FINANCE,
    SUPPORT
}
