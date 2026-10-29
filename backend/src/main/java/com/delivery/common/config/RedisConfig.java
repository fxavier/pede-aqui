package com.delivery.common.config;

import com.delivery.geo.service.VendorSearchCache;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis caching configuration (LAC-51). The cache manager itself comes from Spring Boot (spring.cache.type, redis
 * by default; tests set none); this class fixes JSON serialization and the per-cache TTLs, and makes cache
 * failures non-fatal: if Redis is down the search runs uncached instead of failing.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    /** JSON values, string keys; vendorSearch entries expire after {@link VendorSearchCache#TTL}. */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer() {
        RedisCacheConfiguration json = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(VendorSearchCache.TTL)
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return builder -> builder
                .cacheDefaults(json)
                .withCacheConfiguration(VendorSearchCache.CACHE_NAME, json.entryTtl(VendorSearchCache.TTL));
    }

    /** Logs and ignores cache get/put/evict errors (e.g. Redis unreachable): the cache is only an optimisation. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
