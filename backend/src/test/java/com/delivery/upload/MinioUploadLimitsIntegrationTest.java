package com.delivery.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.delivery.common.exception.BusinessException;
import com.delivery.common.security.TenantContext;
import com.delivery.upload.dto.CreateUploadUrlRequest;
import com.delivery.upload.dto.UploadUrlResponse;
import com.delivery.upload.service.StoredUploadValidator;
import com.delivery.upload.service.UploadLimits;
import com.delivery.upload.service.UploadService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * LAC-12 end to end against the same MinIO image as docker-compose (dev profile): the presigned PUT signs
 * Content-Length and Content-Type, so the storage itself rejects a body of another size or type, and
 * {@link StoredUploadValidator} accepts only existing objects within the limits when a key is linked.
 */
@Testcontainers(disabledWithoutDocker = true)
class MinioUploadLimitsIntegrationTest {
    private static final String BUCKET = "pede-aqui-uploads";
    private static final String USER = "minioadmin";
    private static final String PASSWORD = "minioadmin";
    private static final UUID TENANT = UUID.randomUUID();
    /** Small limits keep the payloads tiny: images 1 KB, documents 2 KB. */
    private static final UploadLimits LIMITS = new UploadLimits(1024, 2048);

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-10-13T13-34-11Z")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", USER)
            .withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    private static S3Presigner presigner;
    private static S3Client s3Client;
    private static UploadService uploadService;
    private static StoredUploadValidator validator;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() {
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(USER, PASSWORD));
        presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(credentials).endpointOverride(endpoint)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
        s3Client = S3Client.builder().region(Region.US_EAST_1).credentialsProvider(credentials).endpointOverride(endpoint)
                .forcePathStyle(true).build();
        s3Client.createBucket(b -> b.bucket(BUCKET));

        TenantContext tenantContext = Mockito.mock(TenantContext.class);
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(TENANT));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("user-1"));
        uploadService = new UploadService(presigner, tenantContext, LIMITS, BUCKET, 300);
        validator = new StoredUploadValidator(s3Client, LIMITS, BUCKET);
    }

    @AfterAll
    static void tearDown() {
        if (presigner != null) presigner.close();
        if (s3Client != null) s3Client.close();
    }

    @Test
    void exactDeclaredSizeAndTypeUploadsAndValidates() throws Exception {
        UploadUrlResponse url = presign(500);

        assertThat(put(url.uploadUrl(), new byte[500], "image/png")).isEqualTo(200);
        assertThatCode(() -> validator.requireImage(TENANT, url.storageKey())).doesNotThrowAnyException();
    }

    @Test
    void storageRejectsBodyLargerThanDeclared() throws Exception {
        UploadUrlResponse url = presign(500);

        assertThat(put(url.uploadUrl(), new byte[900], "image/png")).isEqualTo(403);
        assertThatThrownBy(() -> validator.requireImage(TENANT, url.storageKey()))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("upload_not_found"));
    }

    @Test
    void storageRejectsContentTypeOtherThanDeclared() throws Exception {
        UploadUrlResponse url = presign(500);

        assertThat(put(url.uploadUrl(), new byte[500], "text/html")).isEqualTo(403);
    }

    /** An object that bypassed the presign limits (e.g. written by an older client) is refused when linked. */
    @Test
    void validatorRejectsOversizedObjectAndForeignTenantKey() {
        String key = "tenants/" + TENANT + "/uploads/product/legacy/big.png";
        s3Client.putObject(b -> b.bucket(BUCKET).key(key).contentType("image/png"), RequestBody.fromBytes(new byte[1500]));

        assertThatThrownBy(() -> validator.requireImage(TENANT, key))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("upload_too_large"));
        assertThatThrownBy(() -> validator.requireImage(UUID.randomUUID(), key))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("invalid_storage_key"));
    }

    private static UploadUrlResponse presign(long size) {
        return uploadService.createImageUploadUrl(new CreateUploadUrlRequest("image/png", "product", "foto", size));
    }

    private static int put(String url, byte[] body, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
