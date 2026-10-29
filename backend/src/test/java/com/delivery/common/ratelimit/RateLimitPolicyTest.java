package com.delivery.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** LAC-11 configuration: group matching rules and the shipped defaults in application.yml. */
class RateLimitPolicyTest {

    @Test
    void mostSpecificPatternWinsRegardlessOfDeclarationOrder() {
        Map<String, RateLimitProperties.Group> groups = new LinkedHashMap<>();
        groups.put("wide", group(List.of("/api/v1/deliveries/**"), List.of(), 100));
        groups.put("narrow", group(List.of("/api/v1/deliveries/*/complete"), List.of("POST"), 3));
        RateLimitPolicy policy = new RateLimitPolicy(groups);

        assertThat(group(policy.resolve("POST", "/api/v1/deliveries/42/complete"))).contains("narrow");
        assertThat(group(policy.resolve("PATCH", "/api/v1/deliveries/42/status"))).contains("wide");
        // Method filter: a GET on the narrow path falls back to the wide group.
        assertThat(group(policy.resolve("GET", "/api/v1/deliveries/42/complete"))).contains("wide");
        assertThat(policy.resolve("GET", "/api/v1/orders")).isEmpty();
    }

    @Test
    void invalidGroupFailsAtStartup() {
        assertThatThrownBy(() -> new RateLimitPolicy(Map.of("bad", group(List.of("/x"), List.of(), 0))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.rate-limit.groups.bad");
    }

    @Test
    void shippedDefaultsCoverTheSensitiveRoutes() throws IOException {
        RateLimitProperties properties = shippedProperties();
        RateLimitPolicy policy = new RateLimitPolicy(properties.getGroups());

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBackend()).isEqualTo(RateLimitProperties.Backend.REDIS);
        assertThat(properties.getTrustedProxies()).allMatch(String::isBlank);

        assertThat(group(policy.resolve("POST", "/api/v1/register"))).contains("registration");
        assertThat(group(policy.resolve("POST", "/api/v1/customers/register"))).contains("registration");
        assertThat(group(policy.resolve("POST", "/api/v1/deliveries/abc/complete"))).contains("delivery-code");
        assertThat(group(policy.resolve("PATCH", "/api/v1/deliveries/abc/status"))).contains("sensitive");
        assertThat(group(policy.resolve("POST", "/api/v1/checkout"))).contains("checkout");
        assertThat(group(policy.resolve("POST", "/api/v1/uploads/images/presigned-url"))).contains("uploads");
        assertThat(group(policy.resolve("POST", "/api/v1/uploads/documents/presigned-url"))).contains("uploads");
        assertThat(group(policy.resolve("POST", "/api/v1/payments/p1/refunds"))).contains("payments");
        assertThat(group(policy.resolve("GET", "/api/v1/search/vendors"))).contains("browse");
        assertThat(group(policy.resolve("GET", "/api/v1/catalog/categories"))).contains("browse");

        RateLimitProperties.Group registration = properties.getGroups().get("registration");
        RateLimitProperties.Group browse = properties.getGroups().get("browse");
        // Strict for anonymous writes and the PIN, generous for browsing.
        assertThat(perMinute(registration)).isLessThan(1.0);
        assertThat(perMinute(properties.getGroups().get("delivery-code"))).isLessThanOrEqualTo(2.0);
        assertThat(perMinute(browse)).isGreaterThanOrEqualTo(100.0);
    }

    private static double perMinute(RateLimitProperties.Group group) {
        return group.getCapacity() * 60.0 / group.getPeriod().toSeconds();
    }

    static RateLimitProperties shippedProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        sources.forEach(environment.getPropertySources()::addLast);
        // Only main application.yml (classpath root), not the test override under config/.
        return new Binder(ConfigurationPropertySources.from(environment.getPropertySources()),
                new PropertySourcesPlaceholdersResolver(environment))
                .bind("app.rate-limit", RateLimitProperties.class)
                .get();
    }

    private static Optional<String> group(Optional<RateLimitPolicy.Match> match) {
        return match.map(m -> m.rule().group());
    }

    private static RateLimitProperties.Group group(List<String> paths, List<String> methods, long capacity) {
        RateLimitProperties.Group group = new RateLimitProperties.Group();
        group.setPaths(paths);
        group.setMethods(methods);
        group.setCapacity(capacity);
        group.setPeriod(Duration.ofMinutes(1));
        return group;
    }
}
