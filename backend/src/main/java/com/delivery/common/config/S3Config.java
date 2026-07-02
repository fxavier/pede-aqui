package com.delivery.common.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Configures the AWS S3 presigner used to create upload URLs. */
@Configuration
public class S3Config {
    @Bean
    S3Presigner s3Presigner(
            @Value("${app.storage.s3-region}") String region,
            @Value("${app.storage.s3-endpoint:}") String endpointOverride,
            @Value("${app.storage.s3-access-key:}") String accessKey,
            @Value("${app.storage.s3-secret-key:}") String secretKey) {
        // Explicit keys (from .env) take priority; the SDK default chain
        // (~/.aws profile, instance role) only applies when they're absent.
        var credentialsProvider = (!accessKey.isBlank() && !secretKey.isBlank())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider);

        // A custom endpoint means a MinIO/S3-compatible store (local dev), which
        // requires path-style URLs (http://host:9000/bucket/key) rather than the
        // virtual-hosted style (https://bucket.host) AWS uses by default.
        if (!endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        return builder.build();
    }
}
