package com.delivery.upload.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.upload.dto.CreateDocumentUploadUrlRequest;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.UploadLimits.Kind;
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

/**
 * Generates tenant-scoped S3 presigned PUT URLs for image and document uploads. The declared Content-Type and
 * Content-Length are part of the signature (LAC-12), so the storage rejects a PUT whose body size or type
 * differs from what was declared here, and the declared size must be within {@link UploadLimits}. AWS SDK v2
 * has no presigned POST (content-length-range), hence signed headers plus {@link StoredUploadValidator} on link.
 */
@Service
public class UploadService {
    private final S3Presigner s3Presigner;
    private final TenantContext tenantContext;
    private final UploadLimits limits;
    private final String bucket;
    private final long expirationSeconds;

    public UploadService(
            S3Presigner s3Presigner,
            TenantContext tenantContext,
            UploadLimits limits,
            @Value("${app.storage.s3-bucket}") String bucket,
            @Value("${app.storage.presigned-url-expiration-seconds}") long expirationSeconds) {
        this.s3Presigner = s3Presigner;
        this.tenantContext = tenantContext;
        this.limits = limits;
        this.bucket = bucket;
        this.expirationSeconds = expirationSeconds;
    }

    /** Builds a presigned URL for uploading one image directly to S3. */
    public UploadUrlResponse createImageUploadUrl(CreateUploadUrlRequest request) {
        return presign(Kind.IMAGE, request.contentType(), request.contentLength(), request.purpose(), request.fileName());
    }

    /** Builds a presigned URL for uploading one document (PDF or image) directly to S3. */
    public UploadUrlResponse createDocumentUploadUrl(CreateDocumentUploadUrlRequest request) {
        return presign(Kind.DOCUMENT, request.contentType(), request.contentLength(), request.purpose(), request.fileName());
    }

    private UploadUrlResponse presign(Kind kind, String contentType, Long contentLength, String purpose, String fileName) {
        UUID tenantId = tenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException("tenant_required", "Tenant context is required", HttpStatus.FORBIDDEN));
        String userId = tenantContext.currentKeycloakUserId()
                .orElseThrow(() -> new BusinessException("user_required", "Authenticated user is required", HttpStatus.FORBIDDEN));
        if (!limits.allowsContentType(kind, contentType)) {
            throw new BusinessException("invalid_content_type",
                    kind == Kind.IMAGE ? "Only JPEG, PNG, and WEBP images are supported"
                            : "Only PDF, JPEG, PNG, and WEBP documents are supported", HttpStatus.BAD_REQUEST);
        }
        if (contentLength == null || contentLength <= 0) {
            throw new BusinessException("invalid_content_length", "contentLength must be the file size in bytes", HttpStatus.BAD_REQUEST);
        }
        if (contentLength > limits.maxBytes(kind)) {
            throw new BusinessException("upload_too_large",
                    "File exceeds the maximum of " + limits.maxBytes(kind) + " bytes", HttpStatus.BAD_REQUEST);
        }

        String normalizedType = contentType.trim().toLowerCase(Locale.ROOT);
        String storageKey = "%s%s/%s/%s-%s.%s".formatted(limits.keyPrefix(kind, tenantId), sanitizeSegment(purpose), userId,
                Instant.now().toEpochMilli(), sanitizeFileName(fileName), extensionFromContentType(normalizedType));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(normalizedType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new UploadUrlResponse(presignedRequest.url().toString(), storageKey, expirationSeconds, limits.maxBytes(kind));
    }

    private String sanitizeSegment(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        if (normalized.isBlank()) {
            throw new BusinessException("invalid_purpose", "Upload purpose is invalid", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BusinessException("invalid_content_type", "Unsupported content type", HttpStatus.BAD_REQUEST);
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
