package com.delivery.payment.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment attempt state machine (LAC-32, W7-A): {@code INITIATED -> PENDING_CUSTOMER -> SUCCEEDED | FAILED | EXPIRED |
 * UNKNOWN}. UNKNOWN (timeout / ambiguous PSP answer) is never a failure and is resolved by query. EXPIRED is a local
 * decision (we stopped waiting), so a PSP-confirmed late success may still move it to SUCCEEDED — the money was taken
 * and must be accounted for. SUCCEEDED and FAILED are final.
 */
public enum PaymentAttemptStatus {
    INITIATED,
    PENDING_CUSTOMER,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    UNKNOWN;

    /** Statuses that still wait for an answer (at most one per payment, V068 partial unique index). */
    public static final Set<PaymentAttemptStatus> OPEN = EnumSet.of(INITIATED, PENDING_CUSTOMER, UNKNOWN);

    private static final Map<PaymentAttemptStatus, Set<PaymentAttemptStatus>> ALLOWED = Map.of(
            INITIATED, EnumSet.of(PENDING_CUSTOMER, SUCCEEDED, FAILED, EXPIRED, UNKNOWN),
            PENDING_CUSTOMER, EnumSet.of(SUCCEEDED, FAILED, EXPIRED, UNKNOWN),
            UNKNOWN, EnumSet.of(PENDING_CUSTOMER, SUCCEEDED, FAILED, EXPIRED),
            EXPIRED, EnumSet.of(SUCCEEDED),
            SUCCEEDED, EnumSet.noneOf(PaymentAttemptStatus.class),
            FAILED, EnumSet.noneOf(PaymentAttemptStatus.class));

    /** True when this status may move to {@code target} (a repeat of the same status is not a transition). */
    public boolean canTransitionTo(PaymentAttemptStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** True while the attempt still waits for an answer. */
    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
