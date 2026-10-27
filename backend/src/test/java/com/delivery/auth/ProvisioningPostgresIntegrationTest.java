package com.delivery.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.delivery.auth.dto.TenantProvisioningRequest;
import com.delivery.auth.dto.TenantProvisioningResponse;
import com.delivery.auth.entity.ProvisioningRequest;
import com.delivery.auth.repository.ProvisioningRequestRepository;
import com.delivery.auth.service.KeycloakAdminService;
import com.delivery.auth.service.MerchantRegistrationService;
import com.delivery.auth.service.SubscriptionProvisioningService;
import com.delivery.common.PostgresTestContainerConfig;
import com.delivery.common.exception.BusinessException;
import com.delivery.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * Runs the full provisioning path (real saga, real Flyway schema, real advisory lock) against
 * Postgres. Doubles as the ddl-auto=validate check for V034. Mirrors the docker-skip pattern of
 * AbstractSalesReportPostgresTest. Tests commit real transactions, so each test uses unique emails.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({MerchantRegistrationService.class, SubscriptionProvisioningService.class,
        ProvisioningPostgresIntegrationTest.TestBeans.class})
class ProvisioningPostgresIntegrationTest {

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper provisioningObjectMapper() {
            return new ObjectMapper();
        }
    }

    @BeforeAll
    static void requiresDockerOrExternalDatabase() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE || System.getenv("SPRING_DATASOURCE_URL") != null,
                "Skipping provisioning Postgres tests: no Docker environment and no SPRING_DATASOURCE_URL override");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        PostgresTestContainerConfig.POSTGRES.start();
        registry.add("spring.datasource.url", PostgresTestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainerConfig.POSTGRES::getPassword);
    }

    @Autowired
    private SubscriptionProvisioningService service;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ProvisioningRequestRepository provisioningRequestRepository;
    @MockBean
    private KeycloakAdminService keycloakAdminService;

    private final AtomicInteger keycloakUserSequence = new AtomicInteger();

    @BeforeEach
    void stubKeycloak() {
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> "kc-user-" + keycloakUserSequence.incrementAndGet());
    }

    private TenantProvisioningRequest lead(String company, String email) {
        return new TenantProvisioningRequest(
                company, "Ana Machava", email, "+258-84-123-4567", "Maputo", "pro-monthly", "MONTHLY", null);
    }

    @Test
    void provisionReplayAndDuplicateEmailNeverCreateTwins() {
        String email = "owner-" + UUID.randomUUID() + "@replay.test";
        TenantProvisioningRequest request = lead("Replay Restaurante " + UUID.randomUUID(), email);
        long tenantsBefore = tenantRepository.count();

        TenantProvisioningResponse first = service.provision("replay-key-1-" + email, request);
        assertThat(first.provisioned()).isTrue();
        assertThat(first.alreadyExisted()).isFalse();

        TenantProvisioningResponse replay = service.provision("replay-key-1-" + email, request);
        assertThat(replay.alreadyExisted()).isTrue();
        assertThat(replay.tenantId()).isEqualTo(first.tenantId());

        TenantProvisioningResponse sameEmailNewKey = service.provision("replay-key-2-" + email, request);
        assertThat(sameEmailNewKey.alreadyExisted()).isTrue();
        assertThat(sameEmailNewKey.tenantId()).isEqualTo(first.tenantId());

        assertThat(tenantRepository.findById(first.tenantId())).isPresent();
        assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 1);
    }

    @Test
    void failedAttemptIsRecordedAndRetryWithSameKeySucceeds() {
        String email = "owner-" + UUID.randomUUID() + "@retry.test";
        String key = "retry-key-" + email;
        TenantProvisioningRequest request = lead("Retry Restaurante " + UUID.randomUUID(), email);

        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException("keycloak_error", "idp down", HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn("kc-user-retry");

        BusinessException failure = assertThrows(BusinessException.class, () -> service.provision(key, request));
        assertThat(failure.getCode()).isEqualTo("downstream_idp");
        assertThat(provisioningRequestRepository.findByIdempotencyKey(key))
                .map(ProvisioningRequest::getStatus)
                .contains(ProvisioningRequest.STATUS_FAILED);

        TenantProvisioningResponse retried = service.provision(key, request);
        assertThat(retried.provisioned()).isTrue();
        assertThat(provisioningRequestRepository.findByIdempotencyKey(key))
                .map(ProvisioningRequest::getStatus)
                .contains(ProvisioningRequest.STATUS_SUCCEEDED);
    }

    @Test
    void concurrentDuplicateLeadsCreateExactlyOneTenant() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@concurrent.test";
        String company = "Concurrent Restaurante " + UUID.randomUUID();
        long tenantsBefore = tenantRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TenantProvisioningResponse>> futures = List.of(
                    executor.submit(() -> {
                        start.await();
                        return service.provision("concurrent-a-" + email, lead(company, email));
                    }),
                    executor.submit(() -> {
                        start.await();
                        return service.provision("concurrent-b-" + email, lead(company, email));
                    }));
            start.countDown();

            TenantProvisioningResponse first = futures.get(0).get(30, TimeUnit.SECONDS);
            TenantProvisioningResponse second = futures.get(1).get(30, TimeUnit.SECONDS);

            assertThat(first.tenantId()).isEqualTo(second.tenantId());
            assertThat(tenantRepository.count()).isEqualTo(tenantsBefore + 1);
            assertThat(List.of(first.alreadyExisted(), second.alreadyExisted())).contains(true, false);
        } finally {
            executor.shutdownNow();
        }
    }
}
