package com.delivery.common.ratelimit;

import io.lettuce.core.RedisURI;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the distributed rate limiter (LAC-11, replaces the old in-memory fixed-window interceptor): route groups
 * from {@code app.rate-limit.groups}, Bucket4j buckets in Redis (same server as spring.data.redis, standalone only)
 * or in memory when {@code app.rate-limit.backend=local}, and the MVC interceptor that enforces them.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    private final RateLimitProperties properties;
    private final ObjectProvider<RateLimitInterceptor> interceptor;

    public RateLimitConfig(RateLimitProperties properties, ObjectProvider<RateLimitInterceptor> interceptor) {
        this.properties = properties;
        this.interceptor = interceptor;
    }

    @Bean
    RateLimitPolicy rateLimitPolicy() {
        return new RateLimitPolicy(properties.getGroups());
    }

    @Bean
    ClientIdentityResolver clientIdentityResolver() {
        return new ClientIdentityResolver(properties.getTrustedProxies());
    }

    @Bean
    RateLimiter rateLimiter(ObjectProvider<RedisConnectionDetails> connectionDetails,
                            ObjectProvider<RedisProperties> redisProperties,
                            ObjectProvider<MeterRegistry> meterRegistry) {
        RedisURI uri = properties.getBackend() == RateLimitProperties.Backend.REDIS
                ? redisUri(connectionDetails.getIfAvailable(), redisProperties.getIfAvailable())
                : null;
        if (properties.getBackend() == RateLimitProperties.Backend.REDIS && uri == null) {
            log.warn("app.rate-limit.backend=redis but no standalone Redis is configured; using per-instance buckets");
        }
        return new RateLimiter(properties, uri, meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(RateLimitPolicy policy, RateLimiter limiter,
                                              ClientIdentityResolver identityResolver,
                                              ObjectProvider<MeterRegistry> meterRegistry) {
        return new RateLimitInterceptor(policy, limiter, identityResolver,
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new), properties.getKeyPrefix());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (properties.isEnabled()) {
            registry.addInterceptor(interceptor.getObject());
        }
    }

    /** Standalone Redis URI from Spring Boot's Redis connection details; null when absent or not standalone. */
    @Nullable
    static RedisURI redisUri(@Nullable RedisConnectionDetails details, @Nullable RedisProperties redisProperties) {
        if (details == null || details.getStandalone() == null
                || details.getSentinel() != null || details.getCluster() != null) {
            return null;
        }
        RedisConnectionDetails.Standalone standalone = details.getStandalone();
        boolean ssl = standalone.getSslBundle() != null
                || (redisProperties != null && redisProperties.getSsl().isEnabled());
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase())
                .withSsl(ssl);
        if (StringUtils.hasText(details.getPassword())) {
            if (StringUtils.hasText(details.getUsername())) {
                builder.withAuthentication(details.getUsername(), details.getPassword());
            } else {
                builder.withPassword(details.getPassword().toCharArray());
            }
        }
        return builder.build();
    }
}
