package com.delivery.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Standard error payload returned by REST controllers; {@code details} is optional machine-readable context. */
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fieldErrors,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object details) {

    /** Describes a single invalid request field. */
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), List.of(), null);
    }

    public static ErrorResponse withFields(
            String code,
            String message,
            String correlationId,
            List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), fieldErrors, null);
    }

    public static ErrorResponse withDetails(String code, String message, String correlationId, Object details) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), List.of(), details);
    }
}
