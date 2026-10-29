package com.delivery.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Derives the "who" half of a rate-limit key (LAC-11): the JWT subject when the caller is authenticated, otherwise
 * the client IP. The IP is {@code getRemoteAddr()}; X-Forwarded-For is honoured only when the direct peer is a
 * configured trusted proxy, and then the right-most hop that is not itself a trusted proxy is used (left-most entries
 * are client-controlled and never trusted).
 */
public class ClientIdentityResolver {

    /** IP literals only: anything else (host names, garbage) is never resolved or trusted. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIdentityResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    /** "sub:&lt;jwt sub&gt;" for authenticated callers, "ip:&lt;client ip&gt;" otherwise. */
    public String resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null) {
            return "sub:" + jwt.getToken().getSubject();
        }
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "user:" + authentication.getName();
        }
        return "ip:" + clientIp(request);
    }

    /** Client IP honouring X-Forwarded-For only behind a trusted proxy. */
    public String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedProxies.isEmpty() || !isTrusted(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!IP_LITERAL.matcher(hop).matches()) {
                // Unparseable hop: stop walking, everything to its left is untrusted anyway.
                return remote;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return remote;
    }

    private boolean isTrusted(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a valid address for this matcher (e.g. malformed literal): not trusted.
            }
        }
        return false;
    }
}
