package com.delivery.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Correlation id filter (INC-14, AS-15): MDC lifecycle, sanitisation of the incoming header and ordering. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void putsCorrelationIdInMdcDuringRequestAndClearsItAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("abc-123");
        assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void acceptsOnlySaneValues() {
        assertThat(CorrelationIdFilter.isAcceptable("3f2c1a8e-1b2c-4d5e-8f90-0123456789ab")).isTrue();
        assertThat(CorrelationIdFilter.isAcceptable("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isTrue();
        assertThat(CorrelationIdFilter.isAcceptable("req_42.retry:1")).isTrue();
        assertThat(CorrelationIdFilter.isAcceptable(null)).isFalse();
        assertThat(CorrelationIdFilter.isAcceptable("")).isFalse();
        assertThat(CorrelationIdFilter.isAcceptable("has space")).isFalse();
        assertThat(CorrelationIdFilter.isAcceptable("line\nbreak")).isFalse();
        assertThat(CorrelationIdFilter.isAcceptable("quote\"inject")).isFalse();
        assertThat(CorrelationIdFilter.isAcceptable("a".repeat(128))).isTrue();
        assertThat(CorrelationIdFilter.isAcceptable("a".repeat(129))).isFalse();
    }

    @Test
    void runsBeforeTheSecurityFilterChain() {
        // Spring Security's DelegatingFilterProxy is registered at order -100; the correlation filter must
        // precede it so 401/403 written by the security chain (and INC-11 tenant filter) carry the header.
        assertThat(OrderUtils.getOrder(CorrelationIdFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
