package com.delivery.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id (INC-14, AS-15): reuses a sane incoming {@code X-Correlation-Id} or
 * generates a UUID, exposes it as a request attribute, in the MDC ({@code correlationId}) and in the response
 * header. Ordered first so responses written by the security chain also carry it. Logs nothing itself.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    public static final String MDC_KEY = "correlationId";

    /**
     * Bounded charset/length: the value is echoed into a header, the error body and the JSON log line, so
     * anything that could break those (quotes, whitespace, control chars) or bloat them is replaced.
     */
    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(CORRELATION_ID_HEADER);
        String correlationId = isAcceptable(incoming) ? incoming : UUID.randomUUID().toString();

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** True when a client-supplied correlation id is safe to propagate as-is. */
    static boolean isAcceptable(String value) {
        return value != null && ACCEPTABLE.matcher(value).matches();
    }
}
