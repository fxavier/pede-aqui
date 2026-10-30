package com.delivery.common.security;

import com.delivery.common.config.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures JWT authentication and role-based endpoint protection. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173,http://localhost:5174}")
    private List<String> allowedOrigins;

    @Value("${server.port:8080}")
    private int serverPort;

    /** -1 = actuator shares the application port (dev default). */
    @Value("${management.server.port:-1}")
    private int managementPort;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        // 401/403 decided here are problem documents like the MVC ones (INC-14, AS-17).
        ProblemDetailSecurityHandlers problemHandlers = new ProblemDetailSecurityHandlers(objectMapper);
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                // Write the security headers before the chain runs, not in its `finally`: with async
                // responses (the StreamingResponseBody CSV export) the streaming thread may already be
                // committing the response, and two threads writing headers raced (ConcurrentModificationException).
                .headers(headers -> headers.withObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                    @Override
                    public <O extends HeaderWriterFilter> O postProcess(O filter) {
                        filter.setShouldWriteHeadersEagerly(true);
                        return filter;
                    }
                }))
                .authorizeHttpRequests(auth -> auth
                        // Separate management port (prod): reachable only on the internal network, so
                        // Prometheus scrapes without a token. Never covers non-actuator paths (SEC-19).
                        .requestMatchers(this::isActuatorOnManagementPort).permitAll()
                        // Public reads only (LAC-02): every rule is pinned to GET so a write on the same path
                        // (e.g. POST /catalog/categories) still needs a token.
                        .requestMatchers(HttpMethod.GET,
                            "/actuator/health/**", "/livez", "/readyz", "/api-docs/**", "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/api/v1/catalog/categories",
                            "/api/v1/catalog/vendors/*/products",
                            "/api/v1/catalog/vendors/*",
                            "/api/v1/search/vendors"
                        ).permitAll()
                        // The only anonymous writes: self-registration of merchants and customers.
                        .requestMatchers(HttpMethod.POST, "/api/v1/register", "/api/v1/customers/register").permitAll()
                        // metrics/prometheus/etc. on the public application port: platform ADMIN only (SEC-19).
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/tenants/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/provisioning/**").hasAuthority("ROLE_provisioning:write")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    private boolean isActuatorOnManagementPort(HttpServletRequest request) {
        return managementPort > 0
                && managementPort != serverPort
                && request.getLocalPort() == managementPort
                && request.getRequestURI().startsWith("/actuator/");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Tenant-Id", CorrelationIdFilter.CORRELATION_ID_HEADER));
        // Browsers can only read the correlation id (to show it in error toasts / support tickets) if exposed;
        // X-Truncated (LAC-14) tells a client that a capped legacy list endpoint returned only the first 200 rows;
        // Retry-After (LAC-11) is not CORS-safelisted, so a 429 would hide it from browser clients otherwise.
        config.setExposedHeaders(List.of(CorrelationIdFilter.CORRELATION_ID_HEADER,
                com.delivery.common.pagination.CappedList.TRUNCATED_HEADER, "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
