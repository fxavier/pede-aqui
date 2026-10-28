package com.delivery.common.exception;

import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Converts application exceptions into consistent REST error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.withFields(
                "validation_failed",
                "Request validation failed",
                correlationId(request),
                errors));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(ErrorResponse.withDetails(
                exception.getCode(),
                exception.getMessage(),
                correlationId(request),
                exception.getDetails()));
    }

    /** Unique/foreign-key conflicts become a neutral 409; constraint names and SQL never reach the client (COR-06). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity conflict on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "conflict",
                "The request conflicts with the current state of the resource",
                correlationId(request)));
    }

    /** @Version conflicts between concurrent writers become 409 so the client can re-read and retry (COR-06). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception, HttpServletRequest request) {
        log.warn("Concurrent modification on {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "concurrent_modification",
                "The resource was modified concurrently; reload and try again",
                correlationId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                "forbidden",
                "You do not have permission to perform this action",
                correlationId(request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatusCode()).body(ErrorResponse.of(
                "error",
                exception.getReason() != null ? exception.getReason() : exception.getMessage(),
                correlationId(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                "internal_error",
                "Unexpected error",
                correlationId(request)));
    }

    private ErrorResponse.FieldError toFieldError(FieldError error) {
        return new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage());
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
