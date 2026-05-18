package com.delivery.dispatch.entity;

/** Supported dispatch job lifecycle statuses. */
public enum DispatchJobStatus {
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    REASSIGNABLE,
    COMPLETED,
    CANCELLED
}
