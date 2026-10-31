package com.delivery.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every exception reaching Spring MVC into an RFC 9457 problem document (INC-14, AS-17). Spring's
 * standard MVC exceptions (404 unknown path, 405, 415, malformed body, missing/mistyped parameters, ...) are
 * mapped by {@link ResponseEntityExceptionHandler}; application exceptions by the handlers below. Body shape:
 * {@link ApiProblems} and docs/api/ERROR-CONTRACT.md.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ProblemDetail problem = ApiProblems.create(
                HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", servletRequest(request));
        ApiProblems.withFieldErrors(problem, errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** Enriches the problem documents built by Spring for its standard MVC exceptions with code and correlation. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail existing
                ? existing
                : ProblemDetail.forStatus(statusCode);
        ApiProblems.enrich(problem, codeFor(exception, statusCode), servletRequest(request));
        return super.handleExceptionInternal(exception, problem, headers, statusCode, request);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.create(
                exception.getStatus(), exception.getCode(), exception.getMessage(), request);
        ApiProblems.withDetails(problem, exception.getDetails());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (exception instanceof RateLimitedException limited) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(limited.getRetryAfterSeconds()));
        }
        return response.body(problem);
    }

    /** Unique/foreign-key conflicts become a neutral 409; constraint names and SQL never reach the client (COR-06). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity conflict on {} {}", request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.CONFLICT, "conflict",
                "The request conflicts with the current state of the resource", request);
    }

    /** @Version conflicts between concurrent writers become 409 so the client can re-read and retry (COR-06). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException exception, HttpServletRequest request) {
        log.warn("Concurrent modification on {} {}", request.getMethod(), request.getRequestURI());
        return problem(HttpStatus.CONFLICT, "concurrent_modification",
                "The resource was modified concurrently; reload and try again", request);
    }

    /**
     * Lock acquisition failures (deadlock victim 40P01, lock timeout) mean another writer won the race on the same
     * rows: a neutral 409 the client can retry, never a 500. Paths follow the lock order rule so this is a safety net.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException exception, HttpServletRequest request) {
        log.warn("Lock conflict on {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "concurrent_modification",
                "The resource was modified concurrently; reload and try again", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden",
                "You do not have permission to perform this action", request);
    }

    /** Anything unmapped is a 500 with a generic detail; the exception is logged server-side only. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected error", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiProblems.create(status, code, detail, request));
    }

    /** Stable codes for Spring's standard exceptions where the status alone is too coarse for clients. */
    private static String codeFor(Exception exception, HttpStatusCode status) {
        if (exception instanceof HandlerMethodValidationException) {
            return "validation_failed";
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "malformed_request";
        }
        if (exception instanceof TypeMismatchException) {
            return "invalid_parameter";
        }
        if (exception instanceof ServletRequestBindingException || exception instanceof MissingServletRequestPartException) {
            return "missing_parameter";
        }
        return ApiProblems.codeForStatus(status);
    }

    private ValidationError toFieldError(FieldError error) {
        return new ValidationError(error.getField(), error.getDefaultMessage());
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof NativeWebRequest nativeRequest
                ? nativeRequest.getNativeRequest(HttpServletRequest.class)
                : null;
    }
}
