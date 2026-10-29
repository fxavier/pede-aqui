package com.delivery.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** LAC-11 key derivation: JWT sub beats IP; X-Forwarded-For only behind a configured trusted proxy. */
class ClientIdentityResolverTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedCallerIsKeyedByJwtSubjectNotIp() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("user-123")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(new ClientIdentityResolver(List.of()).resolve(request("10.0.0.7", null))).isEqualTo("sub:user-123");
    }

    @Test
    void anonymousCallerIsKeyedByRemoteAddress() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "k", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(new ClientIdentityResolver(List.of()).resolve(request("10.0.0.7", null))).isEqualTo("ip:10.0.0.7");
    }

    @Test
    void forwardedForIsIgnoredWithoutTrustedProxies() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(List.of());

        assertThat(resolver.clientIp(request("10.0.0.7", "1.2.3.4"))).isEqualTo("10.0.0.7");
    }

    @Test
    void forwardedForIsIgnoredWhenThePeerIsNotATrustedProxy() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(List.of("172.16.0.0/12"));

        assertThat(resolver.clientIp(request("10.0.0.7", "1.2.3.4"))).isEqualTo("10.0.0.7");
    }

    @Test
    void behindTrustedProxyTheRightMostUntrustedHopIsTheClient() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(List.of("172.16.0.0/12", "10.0.0.1"));

        // "6.6.6.6" was written by the client itself (spoof attempt); the ALB appended the real peer 1.2.3.4,
        // then an internal proxy 172.16.5.5 forwarded to 10.0.0.1.
        assertThat(resolver.clientIp(request("10.0.0.1", "6.6.6.6, 1.2.3.4, 172.16.5.5"))).isEqualTo("1.2.3.4");
    }

    @Test
    void garbageHopFallsBackToThePeerAddress() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(List.of("10.0.0.1"));

        assertThat(resolver.clientIp(request("10.0.0.1", "evil.example.com"))).isEqualTo("10.0.0.1");
        assertThat(resolver.clientIp(request("10.0.0.1", null))).isEqualTo("10.0.0.1");
    }

    @Test
    void ipv6TrustedProxyIsSupported() {
        ClientIdentityResolver resolver = new ClientIdentityResolver(List.of("::1"));

        assertThat(resolver.clientIp(request("::1", "2001:db8::5"))).isEqualTo("2001:db8::5");
    }

    @Test
    void bucketKeyUsesTheRoutePatternAndNeutralisesHashTags() {
        assertThat(RateLimitInterceptor.bucketKey("rl", "delivery-code", "post",
                "/api/v1/deliveries/{deliveryId}/complete", "sub:u1"))
                .isEqualTo("rl:delivery-code:POST /api/v1/deliveries/<deliveryId>/complete:sub:u1");
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/register");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
