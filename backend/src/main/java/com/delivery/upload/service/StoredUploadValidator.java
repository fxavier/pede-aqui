package com.delivery.upload.service;

import com.delivery.common.exception.BusinessException;
import com.delivery.upload.service.UploadLimits.Kind;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Checks an uploaded object before its storageKey is linked to an entity (LAC-12): the key must sit in the
 * caller tenant's namespace for the kind, and a HeadObject must show the object exists with an allowed
 * content type and a size within the configured limit. The presigned PUT already signs Content-Type and
 * Content-Length, so this is the second line of defence (and the only one for keys from older clients).
 */
@Service
public class StoredUploadValidator {
    private final S3Client s3Client;
    private final UploadLimits limits;
    private final String bucket;

    public StoredUploadValidator(S3Client s3Client, UploadLimits limits, @Value("${app.storage.s3-bucket}") String bucket) {
        this.s3Client = s3Client;
        this.limits = limits;
        this.bucket = bucket;
    }

    /** Validates an image key (product photo, vendor logo, delivery proof) of the tenant. */
    public void requireImage(UUID tenantId, String storageKey) {
        require(Kind.IMAGE, tenantId, storageKey);
    }

    /** Validates a document key (vendor/courier documents) of the tenant. */
    public void requireDocument(UUID tenantId, String storageKey) {
        require(Kind.DOCUMENT, tenantId, storageKey);
    }

    private void require(Kind kind, UUID tenantId, String storageKey) {
        if (storageKey == null || !storageKey.startsWith(limits.keyPrefix(kind, tenantId)) || storageKey.contains("..")) {
            throw new BusinessException("invalid_storage_key",
                    "storageKey does not belong to this tenant's " + kind.name().toLowerCase() + " upload namespace", HttpStatus.BAD_REQUEST);
        }
        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (NoSuchKeyException missing) {
            throw uploadNotFound();
        } catch (S3Exception s3Error) {
            if (s3Error.statusCode() == 404) {
                throw uploadNotFound();
            }
            throw storageUnavailable();
        } catch (SdkException unreachable) {
            throw storageUnavailable();
        }
        if (head.contentLength() == null || head.contentLength() > limits.maxBytes(kind)) {
            throw new BusinessException("upload_too_large",
                    "Uploaded file exceeds the maximum of " + limits.maxBytes(kind) + " bytes", HttpStatus.BAD_REQUEST);
        }
        if (!limits.allowsContentType(kind, head.contentType())) {
            throw new BusinessException("invalid_content_type", "Uploaded file type is not allowed", HttpStatus.BAD_REQUEST);
        }
    }

    private static BusinessException uploadNotFound() {
        return new BusinessException("upload_not_found", "No uploaded file was found for this storageKey", HttpStatus.BAD_REQUEST);
    }

    private static BusinessException storageUnavailable() {
        return new BusinessException("storage_unavailable", "File storage is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
