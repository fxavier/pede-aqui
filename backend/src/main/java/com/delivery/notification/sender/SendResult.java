package com.delivery.notification.sender;

/**
 * Outcome of one provider call (LAC-48). {@code errorCode} is a short, stable code (e.g. {@code provider_timeout},
 * {@code unregistered}) — never the provider's free-text message, which may echo the recipient.
 */
public record SendResult(Outcome outcome, String providerMessageId, String errorCode) {

    /** What the delivery job does next. */
    public enum Outcome {
        /** Accepted by the provider. */
        SENT,
        /** Transient failure (timeout, 5xx, throttling): retried with backoff. */
        RETRYABLE,
        /** Permanent failure (bad request, rejected content): not retried. */
        PERMANENT,
        /** The address/token is not valid any more: not retried; a push token is deleted. */
        INVALID_RECIPIENT
    }

    public static SendResult sent(String providerMessageId) {
        return new SendResult(Outcome.SENT, providerMessageId, null);
    }

    public static SendResult retryable(String errorCode) {
        return new SendResult(Outcome.RETRYABLE, null, errorCode);
    }

    public static SendResult permanent(String errorCode) {
        return new SendResult(Outcome.PERMANENT, null, errorCode);
    }

    public static SendResult invalidRecipient(String errorCode) {
        return new SendResult(Outcome.INVALID_RECIPIENT, null, errorCode);
    }
}
