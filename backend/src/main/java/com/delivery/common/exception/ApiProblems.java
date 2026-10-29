package com.delivery.common.exception;

import com.delivery.common.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds the API error body (INC-14, AS-17): an RFC 9457 problem document whose extension members carry the
 * stable machine-readable {@code code}, optional {@code details}, {@code correlationId}/{@code traceId} and, during
 * the compatibility window, the legacy {@code message}, {@code timestamp} and {@code fieldErrors} fields.
 * See docs/api/ERROR-CONTRACT.md. Static so the security chain can use it without extra beans.
 */
public final class ApiProblems {

    /**
     * MDC key populated by Micrometer Tracing (LAC-50) for the request span — the W3C trace id that is exported to the
     * collector (ids are minted even when export is off). Falls back to the correlation id only when no tracer is
     * active (e.g. MVC slice tests).
     */
    static final String TRACE_ID_MDC_KEY = "traceId";

    private ApiProblems() {
    }

    /** Creates a complete problem document for the given status, stable code and client-safe detail. */
    public static ProblemDetail create(HttpStatusCode status, String code, String detail, HttpServletRequest request) {
        return enrich(ProblemDetail.forStatusAndDetail(status, detail), code, request);
    }

    /** Adds the extension and legacy members to a problem document (e.g. one built by Spring MVC). */
    public static ProblemDetail enrich(ProblemDetail problem, String code, HttpServletRequest request) {
        if (problem.getDetail() == null) {
            problem.setDetail(problem.getTitle());
        }
        if (problem.getInstance() == null && request != null) {
            // Path only: the query string may carry personal data (search terms, emails).
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        String correlationId = correlationId(request);
        Map<String, Object> existing = problem.getProperties();
        problem.setProperty("code", code);
        problem.setProperty("message", problem.getDetail());
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("traceId", traceId(correlationId));
        problem.setProperty("timestamp", Instant.now());
        if (existing == null || !existing.containsKey("fieldErrors")) {
            problem.setProperty("fieldErrors", List.of());
        }
        return problem;
    }

    /** Attaches per-field validation failures (legacy {@code fieldErrors} shape: field + message). */
    public static ProblemDetail withFieldErrors(ProblemDetail problem, List<ValidationError> fieldErrors) {
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** Attaches optional machine-readable context; omitted from the body when null. */
    public static ProblemDetail withDetails(ProblemDetail problem, Object details) {
        if (details != null) {
            problem.setProperty("details", details);
        }
        return problem;
    }

    /** Default stable code for a status when the exception carries no more specific one. */
    public static String codeForStatus(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            return status.is5xxServerError() ? "internal_error" : "error";
        }
        return switch (resolved) {
            case BAD_REQUEST -> "bad_request";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not_found";
            case METHOD_NOT_ALLOWED -> "method_not_allowed";
            case NOT_ACCEPTABLE -> "not_acceptable";
            case CONFLICT -> "conflict";
            case PAYLOAD_TOO_LARGE -> "payload_too_large";
            case UNSUPPORTED_MEDIA_TYPE -> "unsupported_media_type";
            case TOO_MANY_REQUESTS -> "too_many_requests";
            case SERVICE_UNAVAILABLE -> "service_unavailable";
            default -> resolved.is5xxServerError() ? "internal_error" : "error";
        };
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value != null ? value.toString() : MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    private static String traceId(String correlationId) {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId != null && !traceId.isBlank() ? traceId : correlationId;
    }
}
