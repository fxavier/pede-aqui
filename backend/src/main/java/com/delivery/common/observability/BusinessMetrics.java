package com.delivery.common.observability;

import com.delivery.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Business outcome counters (LAC-50). Tags are low-cardinality only: a fixed result, the stable error {@code code}
 * of a {@link BusinessException} as reason, and small enums — never ids, tenants, amounts or personal data.
 * Every meter name always carries the same tag keys (Prometheus requires it).
 *
 * <p>Callers receive it through an optional setter ({@code @Autowired(required = false)}) and default to
 * {@link #NOOP}, so slice tests without a {@link MeterRegistry} need no extra wiring.
 */
@Component
public class BusinessMetrics {
    /** {@code orders_checkout_total{result,reason}}: result = created | replayed | rejected | error. */
    public static final String CHECKOUT = "orders.checkout";
    /** {@code deliveries_completion_total{result,reason}}: PIN completion; reason invalid_delivery_code / delivery_code_locked… */
    public static final String DELIVERY_COMPLETION = "deliveries.completion";
    /** {@code deliveries_status_updates_total{status,result,reason}}: courier/ops transitions (FAILED_DELIVERY = failures). */
    public static final String DELIVERY_STATUS_UPDATES = "deliveries.status.updates";
    /** {@code uploads_rejected_total{stage,kind,reason}}: stage = presign | link, kind = image | document. */
    public static final String UPLOAD_REJECTIONS = "uploads.rejected";
    /** {@code audit_events_total{action,result}}: committed audit entries (PAYMENT_CONFIRMED, REFUND_APPROVED, DELIVERY_CODE_LOCKED…). */
    public static final String AUDIT_EVENTS = "audit.events";
    /** {@code orders_created_total}: orders persisted by checkout (OrderStateMachine.recordCreated), after commit. */
    public static final String ORDERS_CREATED = "orders.created";
    /** {@code orders_cancelled_total{reason}}: reason = seller_rejected | payment_expired | vendor_timeout | other. */
    public static final String ORDERS_CANCELLED = "orders.cancelled";

    public static final String SUCCESS = "success";
    public static final String REJECTED = "rejected";
    public static final String ERROR = "error";
    public static final String NO_REASON = "none";

    /** Counts nothing; the default for components built without a meter registry (unit and slice tests). */
    public static final BusinessMetrics NOOP = new BusinessMetrics(new CompositeMeterRegistry());

    private static final Pattern SAFE_CODE = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern SAFE_ACTION = Pattern.compile("[A-Z0-9_]{1,64}");

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Increments a counter now. {@code tags} are key/value pairs. */
    public void increment(String name, String... tags) {
        registry.counter(name, tags).increment();
    }

    /**
     * Increments after the surrounding transaction commits (nothing on rollback); immediately when no transaction
     * synchronization is active.
     */
    public void incrementAfterCommit(String name, String... tags) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment(name, tags);
                }
            });
        } else {
            increment(name, tags);
        }
    }

    /**
     * Runs {@code action} and counts its outcome with tags {@code result} and {@code reason} (plus {@code extraTags}):
     * on success result = {@code successResult.apply(value)}, reason = none; on failure result = rejected (4xx
     * business/authorization error) or error, reason = the stable error code. The exception is rethrown unchanged.
     */
    public <T> T recordOutcome(String name, Supplier<T> action, Function<? super T, String> successResult, String... extraTags) {
        T value;
        try {
            value = action.get();
        } catch (RuntimeException ex) {
            increment(name, withOutcome(extraTags, failureResult(ex), reason(ex)));
            throw ex;
        }
        increment(name, withOutcome(extraTags, successResult.apply(value), NO_REASON));
        return value;
    }

    /** Same as {@link #recordOutcome(String, Supplier, Function, String...)} with result = success. */
    public <T> T recordOutcome(String name, Supplier<T> action, String... extraTags) {
        return recordOutcome(name, action, value -> SUCCESS, extraTags);
    }

    /** rejected for client-side refusals (business 4xx, access denied, unauthenticated), error otherwise. */
    public static String failureResult(Throwable ex) {
        if (ex instanceof BusinessException business) {
            return business.getStatus() != null && business.getStatus().is4xxClientError() ? REJECTED : ERROR;
        }
        return ex instanceof AccessDeniedException || ex instanceof AuthenticationException ? REJECTED : ERROR;
    }

    /** The stable error code when it is a safe tag value; a fixed fallback otherwise (keeps cardinality bounded). */
    public static String reason(Throwable ex) {
        if (ex instanceof BusinessException business) {
            return safeCode(business.getCode());
        }
        if (ex instanceof AccessDeniedException) {
            return "forbidden";
        }
        if (ex instanceof AuthenticationException) {
            return "unauthorized";
        }
        return "internal_error";
    }

    /** Lower snake_case error code, or "other" for anything unexpected. */
    public static String safeCode(String code) {
        return code != null && SAFE_CODE.matcher(code).matches() ? code : "other";
    }

    /** Upper snake_case audit action/result, or "OTHER" for anything unexpected. */
    public static String safeAction(String action) {
        return action != null && SAFE_ACTION.matcher(action).matches() ? action : "OTHER";
    }

    private static String[] withOutcome(String[] extraTags, String result, String reason) {
        String[] tags = Arrays.copyOf(extraTags, extraTags.length + 4);
        tags[extraTags.length] = "result";
        tags[extraTags.length + 1] = result;
        tags[extraTags.length + 2] = "reason";
        tags[extraTags.length + 3] = reason;
        return tags;
    }
}
