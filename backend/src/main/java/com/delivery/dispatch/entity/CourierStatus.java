package com.delivery.dispatch.entity;

/**
 * Operational status of a courier (LAC-42), independent of {@link CourierVerificationStatus}. Dispatch offers only to
 * {@link #AVAILABLE}; {@link #BUSY} is derived from an accepted delivery; {@link #SUSPENDED} is set by OPS.
 */
public enum CourierStatus {
    OFFLINE,
    AVAILABLE,
    BUSY,
    SUSPENDED;

    /** On duty = location may be collected; also the value of the deprecated {@code available} flag. */
    public boolean isOnDuty() {
        return this == AVAILABLE || this == BUSY;
    }
}
