package com.delivery.common.service;

import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.security.TenantContext;
import org.slf4j.MDC;

/**
 * Who caused a status change and under which correlation id (LAC-15 status history). {@code USER} = an authenticated
 * caller (actor id = Keycloak subject); {@code SYSTEM} = a scheduled job (actor id = job name) or an unauthenticated
 * internal path (actor id null). The correlation id is the request's X-Correlation-Id, or the job run id for jobs.
 */
public record StatusChangeActor(String actorType, String actorId, String correlationId) {
    public static final String USER = "USER";
    public static final String SYSTEM = "SYSTEM";
    static final int MAX_ID = 255;
    static final int MAX_CORRELATION_ID = 128;

    /** Resolves the actor of the current thread: JWT subject when authenticated, otherwise the running job (MDC). */
    public static StatusChangeActor current(TenantContext tenantContext) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) {
            correlationId = MDC.get("jobRunId");
        }
        String subject = tenantContext.currentKeycloakUserId().orElse(null);
        if (subject != null) {
            return new StatusChangeActor(USER, truncate(subject, MAX_ID), truncate(correlationId, MAX_CORRELATION_ID));
        }
        return new StatusChangeActor(SYSTEM, truncate(MDC.get("job"), MAX_ID), truncate(correlationId, MAX_CORRELATION_ID));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
