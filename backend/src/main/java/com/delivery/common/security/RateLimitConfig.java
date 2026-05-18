package com.delivery.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Adds lightweight in-memory rate limiting for sensitive API endpoints. */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SensitiveEndpointRateLimitInterceptor());
    }

    /** Applies fixed-window request limits per user and endpoint key. */
    static class SensitiveEndpointRateLimitInterceptor implements HandlerInterceptor {
        private static final int LIMIT = 20;
        private static final Duration WINDOW = Duration.ofMinutes(1);
        private final Map<String, Counter> counters = new ConcurrentHashMap<>();

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (!isSensitive(request)) {
                return true;
            }
            String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : request.getRemoteAddr();
            String key = user + "|" + request.getMethod() + "|" + request.getRequestURI();
            Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(0, Instant.now()));
            synchronized (counter) {
                Instant now = Instant.now();
                if (counter.windowStart.plus(WINDOW).isBefore(now)) {
                    counter.windowStart = now;
                    counter.count = 0;
                }
                counter.count++;
                if (counter.count > LIMIT) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"rate_limited\",\"message\":\"Too many requests for sensitive endpoint\"}");
                    return false;
                }
            }
            return true;
        }

        private boolean isSensitive(HttpServletRequest request) {
            String path = request.getRequestURI();
            return path.contains("/checkout")
                    || path.contains("/payments/")
                    || path.contains("/refunds")
                    || path.contains("/deliveries/")
                    || path.contains("/dispatch")
                    || path.contains("/support/tickets/")
                    || path.contains("/admin")
                    || path.contains("/me")
                    || path.contains("/auth")
                    || path.contains("/login");
        }

        static class Counter {
            int count;
            Instant windowStart;

            Counter(int count, Instant windowStart) {
                this.count = count;
                this.windowStart = windowStart;
            }
        }
    }
}
