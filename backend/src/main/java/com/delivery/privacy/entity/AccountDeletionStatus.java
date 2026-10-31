package com.delivery.privacy.entity;

/** Lifecycle of an account deletion request (V071 {@code ck_account_deletion_requests_status}). */
public enum AccountDeletionStatus {
    /** Reflection period: the customer can still cancel. */
    PENDING,
    /** Cancelled by the customer before the reflection period ended. */
    CANCELLED,
    /** Database pseudonymised; the Keycloak user deletion is still pending (retried by the job). */
    ERASED,
    /** Fully processed. */
    COMPLETED
}
