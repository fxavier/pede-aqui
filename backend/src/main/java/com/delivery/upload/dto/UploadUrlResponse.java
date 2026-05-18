package com.delivery.upload.dto;

/** Response containing a presigned URL and the generated S3 object key. */
public record UploadUrlResponse(String uploadUrl, String storageKey, long expiresInSeconds) {}
