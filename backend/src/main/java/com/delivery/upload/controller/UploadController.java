package com.delivery.upload.controller;

import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.CreateDocumentUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exposes endpoints that generate presigned S3 upload URLs. */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) { this.uploadService = uploadService; }

    @PostMapping("/images/presigned-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createImageUploadUrl(@Valid @RequestBody CreateUploadUrlRequest request) {
        return uploadService.createImageUploadUrl(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN','OPS','VENDOR_ADMIN','VENDOR_STAFF','COURIER')")
    @PostMapping("/documents/presigned-url")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadUrlResponse createDocumentUploadUrl(@Valid @RequestBody CreateDocumentUploadUrlRequest request) {
        return uploadService.createDocumentUploadUrl(request);
    }
}
