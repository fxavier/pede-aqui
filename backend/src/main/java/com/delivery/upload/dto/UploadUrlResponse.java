package com.delivery.upload.dto;

/**
 * Response containing a presigned PUT URL and the generated S3 object key. The PUT must send exactly the declared
 * Content-Type and Content-Length (both are signed); {@code maxBytes} is the size limit applied to this kind of upload.
 */
public record UploadUrlResponse(String uploadUrl, String storageKey, long expiresInSeconds, long maxBytes) {}
