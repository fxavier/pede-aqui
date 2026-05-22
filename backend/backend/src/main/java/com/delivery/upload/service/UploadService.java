package com.delivery.upload.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Generates tenant-scoped S3 presigned URLs for image uploads. */
@Service
public class UploadService {
    private final S3Presigner s3Presigner;
    private final TenantContext tenantContext;
    private final String bucket;
    private final long expirationSeconds;

    public UploadService(
            S3Presigner s3Presigner,
            TenantContext tenantContext,
            @Value("${app.storage.s3-bucket}") String bucket,
            @Value("${app.storage.presigned-url-expiration-seconds}") long expirationSeconds) {
        this.s3Presigner = s3Presigner;
        this.tenantContext = tenantContext;
        this.bucket = bucket;
        this.expirationSeconds = expirationSeconds;
    }

    /** Builds a presigned URL for uploading one image directly to S3. */
    public UploadUrlResponse createImageUploadUrl(CreateUploadUrlRequest request) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        String userId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));

        String sanitizedPurpose = sanitizeSegment(request.purpose());
        String extension = extensionFromContentType(request.contentType());
        String sanitizedFileName = sanitizeFileName(request.fileName());
        String storageKey = "tenants/%s/uploads/%s/%s/%s-%s.%s"
                .formatted(tenantId, sanitizedPurpose, userId, Instant.now().toEpochMilli(), sanitizedFileName, extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(request.contentType().toLowerCase(Locale.ROOT))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new UploadUrlResponse(presignedRequest.url().toString(), storageKey, expirationSeconds);
    }

    private String sanitizeSegment(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isBlank()) {
            throw new BusinessException("invalid_purpose", "Upload purpose is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException("invalid_content_type", "Only JPEG, PNG, and WEBP images are supported", HttpStatus.BAD_REQUEST);
        };
    }

    private String sanitizeFileName(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isBlank()) {
            return "image";
        }
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }
}
