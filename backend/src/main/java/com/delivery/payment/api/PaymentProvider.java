package com.delivery.payment.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-agnostic seam to a payment service provider (LAC-31, W7-A). One Spring bean per PSP, selected by
 * {@code app.payments.provider} (the bean's {@link #id()}); today only {@code sandbox} exists — the real M-Pesa
 * Moçambique implementation is added as a new bean once a contract and credentials exist.
 *
 * <p>Contract every implementation must honour:
 * <ul>
 *   <li>{@link #initiate} is called <b>outside</b> any database transaction and is <b>never retried</b> by the caller;
 *       it must be idempotent by {@code ourReference} on the PSP side where the PSP allows it. A timeout or an ambiguous
 *       answer must be thrown as {@link ProviderUnavailableException} (the attempt becomes UNKNOWN — a timeout is never a
 *       failure).</li>
 *   <li>{@link #query} is safe to retry (the status poller and attempt expiry call it).</li>
 *   <li>{@link #verifyCallback} authenticates a provider callback (signature, timestamp) and parses it; it never trusts
 *       anything in the payload before the signature checks out.</li>
 *   <li>No raw phone numbers, credentials or full provider payloads may be logged.</li>
 * </ul>
 */
public interface PaymentProvider {

    /** Stable lower-case id: value of {@code app.payments.provider}, webhook path segment and {@code payment_attempts.provider}. */
    String id();

    /** Asks the PSP to charge the customer (e.g. USSD push to the phone). */
    InitiationResult initiate(InitiationRequest request);

    /** Current PSP-side status of a charge, by provider reference when known, else by our reference. */
    StatusResult query(String providerReference, String ourReference);

    /**
     * Verifies and parses a callback. {@code headers} keys are lower-case.
     *
     * @throws InvalidCallbackSignatureException when the signature/timestamp does not verify
     */
    CallbackEvent verifyCallback(Map<String, String> headers, byte[] body);

    /**
     * Refund/reversal hook for the PSP payout of an approved refund (LAC-36, later package): must be called after commit
     * and be idempotent by {@code idempotencyKey}. Providers without the capability keep this default.
     */
    default RefundResult refund(RefundInstruction instruction) {
        return new RefundResult(RefundResult.Status.NOT_SUPPORTED, null, "refund_not_supported");
    }

    /** PSP-side state of a charge, as reported by the provider. */
    enum Outcome {
        /** Accepted, waiting for the customer to confirm on the phone. */
        PENDING,
        SUCCEEDED,
        /** Definitive refusal (insufficient balance, wrong PIN, cancelled by the customer…). */
        FAILED,
        /** The PSP gave up waiting for the customer. */
        EXPIRED,
        /** The PSP does not know the reference (the request never reached it). */
        NOT_FOUND
    }

    /** What to charge. {@code msisdn} is the E.164 phone; implementations must not log it. */
    record InitiationRequest(UUID tenantId, String ourReference, BigDecimal amount, String currency, String msisdn,
                             String description) {
    }

    /** Synchronous answer of {@link #initiate}: usually PENDING (async) — M-Pesa C2B may answer SUCCEEDED/FAILED directly. */
    record InitiationResult(Outcome outcome, String providerReference, String errorCode) {
    }

    /** Answer of {@link #query}. */
    record StatusResult(Outcome outcome, String providerReference, String errorCode, BigDecimal amount) {
    }

    /** A verified provider callback. {@code amount} may be null when the provider does not report it. */
    record CallbackEvent(String eventId, String providerReference, String ourReference, Outcome outcome, BigDecimal amount,
                         String errorCode) {
    }

    /** Payout of an approved refund (later package). */
    record RefundInstruction(UUID tenantId, String idempotencyKey, String providerReference, BigDecimal amount, String currency) {
    }

    /** Result of {@link #refund}. */
    record RefundResult(Status status, String providerReference, String errorCode) {
        /** Refund outcome. */
        public enum Status { SUCCEEDED, PENDING, FAILED, NOT_SUPPORTED }
    }

    /** Timeout or ambiguous PSP answer: the outcome is unknown and must be resolved by query/reconciliation. */
    class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public ProviderUnavailableException(String message) {
            super(message);
        }
    }

    /** The callback did not authenticate (bad/missing signature, stale timestamp, malformed payload). */
    class InvalidCallbackSignatureException extends RuntimeException {
        public InvalidCallbackSignatureException(String message) {
            super(message);
        }
    }
}
