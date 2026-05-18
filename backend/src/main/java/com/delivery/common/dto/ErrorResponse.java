package com.delivery.common.dto;

import java.time.Instant;
import java.util.List;

/** Standard error payload returned by REST controllers. */
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    /** Describes a single invalid request field. */
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), List.of());
    }

    public static ErrorResponse withFields(
            String code,
            String message,
            String correlationId,
            List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, correlationId, Instant.now(), fieldErrors);
    }
}
