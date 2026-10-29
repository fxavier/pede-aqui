package com.delivery.common.ratelimit;

import com.delivery.common.exception.RateLimitedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Applies the configured route-group limits to every MVC request (LAC-11). Runs after the security filter chain, so
 * authenticated callers are keyed by JWT sub. The bucket key is (group, method + route pattern, identity): the route
 * pattern is the handler's mapping (e.g. /api/v1/deliveries/{deliveryId}/complete), never the raw URI, so ids in the
 * path cannot be used to get a fresh bucket. A rejection is thrown as {@link RateLimitedException} so the 429 is the
 * standard problem document with code rate_limited and a Retry-After header.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String MESSAGE = "Too many requests for sensitive endpoint";

    private final RateLimitPolicy policy;
    private final RateLimiter limiter;
    private final ClientIdentityResolver identityResolver;
    private final MeterRegistry meterRegistry;
    private final String keyPrefix;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public RateLimitInterceptor(RateLimitPolicy policy, RateLimiter limiter, ClientIdentityResolver identityResolver,
                                MeterRegistry meterRegistry, String keyPrefix) {
        this.policy = policy;
        this.limiter = limiter;
        this.identityResolver = identityResolver;
        this.meterRegistry = meterRegistry;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = urlPathHelper.getPathWithinApplication(request);
        Optional<RateLimitPolicy.Match> match = policy.resolve(request.getMethod(), path);
        if (match.isEmpty()) {
            return true;
        }
        RateLimitPolicy.Rule rule = match.get().rule();
        Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = bestPattern instanceof String pattern ? pattern : match.get().groupPattern();
        String key = bucketKey(keyPrefix, rule.group(), request.getMethod(), route, identityResolver.resolve(request));

        RateLimiter.Decision decision = limiter.tryConsume(rule, key);
        if (!decision.allowed()) {
            rejections(rule.group()).increment();
            throw new RateLimitedException(MESSAGE, decision.retryAfterSeconds());
        }
        return true;
    }

    /**
     * Redis key for one bucket. Braces of path variables become angle brackets so they never act as Redis Cluster
     * hash tags (which would pin every bucket of a route to one slot).
     */
    static String bucketKey(String prefix, String group, String method, String route, String identity) {
        String safeRoute = route.replace('{', '<').replace('}', '>');
        return prefix + ":" + group + ":" + method.toUpperCase(Locale.ROOT) + " " + safeRoute + ":" + identity;
    }

    private Counter rejections(String group) {
        return Counter.builder("rate.limit.rejections")
                .description("Requests rejected with 429 by the application rate limiter")
                .tag("group", group)
                .register(meterRegistry);
    }
}
