package com.delivery.upload.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Upload rules shared by presigning and linking (LAC-12): maximum size per kind (configurable, defaults
 * images 5 MB / documents 10 MB), allowed content types, and the tenant-scoped key namespace of each kind.
 */
@Component
public class UploadLimits {
    /** The two upload kinds; each has its own key namespace, size cap and content types. */
    public enum Kind { IMAGE, DOCUMENT }

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp");

    private final long maxImageBytes;
    private final long maxDocumentBytes;

    public UploadLimits(
            @Value("${app.storage.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.storage.max-document-bytes:10485760}") long maxDocumentBytes) {
        this.maxImageBytes = maxImageBytes;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    /** Largest accepted object, in bytes, for the kind. */
    public long maxBytes(Kind kind) {
        return kind == Kind.IMAGE ? maxImageBytes : maxDocumentBytes;
    }

    /** True when the (case-insensitive) content type may be stored for the kind. */
    public boolean allowsContentType(Kind kind, String contentType) {
        if (contentType == null) {
            return false;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        return (kind == Kind.IMAGE ? IMAGE_TYPES : DOCUMENT_TYPES).contains(normalized);
    }

    /** Key prefix every object of this kind in this tenant starts with: {@code tenants/{tenant}/uploads|documents/}. */
    public String keyPrefix(Kind kind, UUID tenantId) {
        return "tenants/" + tenantId + (kind == Kind.IMAGE ? "/uploads/" : "/documents/");
    }
}
