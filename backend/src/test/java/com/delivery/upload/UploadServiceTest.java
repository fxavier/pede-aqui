package com.delivery.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.upload.dto.CreateDocumentUploadUrlRequest;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.UploadLimits;
import com.delivery.upload.service.UploadService;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class UploadServiceTest {
    private static final UUID TENANT = UUID.fromString("35ac0a89-f6fd-4979-a6a2-95fbe99f7dcf");
    private static final UploadLimits LIMITS = new UploadLimits(5_242_880, 10_485_760);

    private final S3Presigner s3Presigner = Mockito.mock(S3Presigner.class);
    private final TenantContext tenantContext = Mockito.mock(TenantContext.class);

    private UploadService uploadService;

    @BeforeEach
    void setUp() throws Exception {
        uploadService = new UploadService(s3Presigner, tenantContext, LIMITS, "delivery-bucket", 900);

        PresignedPutObjectRequest presignedRequest = Mockito.mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://example-bucket.s3.amazonaws.com/object"));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(TENANT));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("user-123"));
    }

    @Test
    void createsPresignedUrlForTenantUserScopedKey() {
        UploadUrlResponse response = uploadService.createImageUploadUrl(
                new CreateUploadUrlRequest("image/png", "delivery-proof", "proof-image", 2048L));

        assertThat(response.uploadUrl()).isEqualTo("https://example-bucket.s3.amazonaws.com/object");
        assertThat(response.storageKey()).contains("tenants/35ac0a89-f6fd-4979-a6a2-95fbe99f7dcf/uploads/delivery-proof/user-123/");
        assertThat(response.storageKey()).endsWith(".png");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        assertThat(response.maxBytes()).isEqualTo(5_242_880);
    }

    @Test
    void rejectsUploadWhenTenantIsMissing() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> uploadService.createImageUploadUrl(
                        new CreateUploadUrlRequest("image/jpeg", "prescription", "rx", 10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenant context is required");
    }

    /** LAC-12: an image declared above 5 MB never gets a URL. */
    @Test
    void rejectsImageAboveLimitBeforePresigning() {
        assertThatThrownBy(() -> uploadService.createImageUploadUrl(
                        new CreateUploadUrlRequest("image/jpeg", "product", "big", 5_242_881L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("upload_too_large"));
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    /** LAC-12: documents get the larger 10 MB cap; above it they are refused. */
    @Test
    void appliesDocumentLimit() {
        UploadUrlResponse ok = uploadService.createDocumentUploadUrl(
                new CreateDocumentUploadUrlRequest("application/pdf", "vendor-document", "alvara", 10_485_760L));
        assertThat(ok.storageKey()).startsWith("tenants/" + TENANT + "/documents/vendor-document/").endsWith(".pdf");
        assertThat(ok.maxBytes()).isEqualTo(10_485_760);

        assertThatThrownBy(() -> uploadService.createDocumentUploadUrl(
                        new CreateDocumentUploadUrlRequest("application/pdf", "vendor-document", "alvara", 10_485_761L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("upload_too_large"));
    }

    @Test
    void rejectsMissingOrNonPositiveContentLength() {
        assertThatThrownBy(() -> uploadService.createImageUploadUrl(new CreateUploadUrlRequest("image/png", "p", "f", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("invalid_content_length"));
        assertThatThrownBy(() -> uploadService.createImageUploadUrl(new CreateUploadUrlRequest("image/png", "p", "f", 0L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("invalid_content_length"));
    }

    /**
     * LAC-12: with a real presigner (pure local crypto, no network) the declared Content-Length and Content-Type are
     * signed headers of the URL, so S3/MinIO reject a PUT whose body size or type differs from what was declared.
     */
    @Test
    void signsContentLengthAndContentTypeIntoTheUrl() {
        try (S3Presigner realPresigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .endpointOverride(URI.create("http://localhost:9000"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            UploadService service = new UploadService(realPresigner, tenantContext, LIMITS, "delivery-bucket", 900);

            UploadUrlResponse response = service.createImageUploadUrl(new CreateUploadUrlRequest("image/webp", "product", "foto", 1234L));

            String query = URLDecoder.decode(URI.create(response.uploadUrl()).getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query).contains("X-Amz-SignedHeaders=content-length;content-type;host");
        }
    }
}
