package com.delivery.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.UploadService;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class UploadServiceTest {
    private final S3Presigner s3Presigner = Mockito.mock(S3Presigner.class);
    private final TenantContext tenantContext = Mockito.mock(TenantContext.class);

    private UploadService uploadService;

    @BeforeEach
    void setUp() throws Exception {
        uploadService = new UploadService(s3Presigner, tenantContext, "delivery-bucket", 900);

        PresignedPutObjectRequest presignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://example-bucket.s3.amazonaws.com/object"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
    }

    @Test
    void createsPresignedUrlForTenantUserScopedKey() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(UUID.fromString("35ac0a89-f6fd-4979-a6a2-95fbe99f7dcf")));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("user-123"));

        UploadUrlResponse response = uploadService.createImageUploadUrl(
                new CreateUploadUrlRequest("image/png", "delivery-proof", "proof-image"));

        assertThat(response.uploadUrl()).isEqualTo("https://example-bucket.s3.amazonaws.com/object");
        assertThat(response.storageKey()).contains("tenants/35ac0a89-f6fd-4979-a6a2-95fbe99f7dcf/uploads/delivery-proof/user-123/");
        assertThat(response.storageKey()).endsWith(".png");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsUploadWhenTenantIsMissing() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.createImageUploadUrl(
                        new CreateUploadUrlRequest("image/jpeg", "prescription", "rx")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenant context is required");
    }
}
