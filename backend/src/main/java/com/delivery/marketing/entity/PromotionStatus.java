package com.delivery.marketing.entity;

/** Lifecycle status of a promotion; EXPIRED is resolved from the validity window and is terminal. */
public enum PromotionStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    EXPIRED
}
