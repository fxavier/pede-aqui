package com.delivery.upload.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Generates presigned GET URLs so browsers can display stored images (product photos, vendor logos). */
@Service
public class StorageUrlService {
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final long expirationSeconds;

    public StorageUrlService(
            S3Presigner s3Presigner,
            @Value("${app.storage.s3-bucket}") String bucket,
            @Value("${app.storage.presigned-url-expiration-seconds}") long expirationSeconds) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.expirationSeconds = expirationSeconds;
    }

    /** Returns a presigned GET URL for a storage key, or null when the key is absent. Pure local crypto — no network call. */
    public String presignGet(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
