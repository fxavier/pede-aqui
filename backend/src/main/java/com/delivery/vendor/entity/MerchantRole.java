package com.delivery.vendor.entity;

/** Role of a merchant user inside one vendor (ADR-0001 §3): OWNER manages the store, STAFF operates it. */
public enum MerchantRole {
    OWNER,
    STAFF
}
