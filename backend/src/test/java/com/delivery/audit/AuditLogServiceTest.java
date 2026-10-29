package com.delivery.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.delivery.common.config.CorrelationIdFilter;
import com.delivery.common.entity.AuditLog;
import com.delivery.common.repository.AuditLogRepository;
import com.delivery.common.security.TenantContext;
import com.delivery.common.service.AuditLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogServiceTest {
    private AuditLogRepository repository;
    private TenantContext tenantContext;
    private AuditLogService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(AuditLogRepository.class);
        tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(Optional.of(tenantId));
        when(tenantContext.currentKeycloakUserId()).thenReturn(Optional.of("ops-user"));
        service = new AuditLogService(repository, tenantContext);
    }

    @AfterEach
    void clearRequestState() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void writesSensitiveActionAuditFields() {
        service.log("DISPATCH_REASSIGN", "dispatch_job", "job-1", "order-1", "SUCCESS");

        AuditLog saved = captureSaved();
        assertThat(saved.getActorUserId()).isEqualTo("ops-user");
        assertThat(saved.getTargetType()).isEqualTo("dispatch_job");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getCreatedAt()).isNotNull();
        // Outside an HTTP request there is no request context to record.
        assertThat(saved.getIp()).isNull();
        assertThat(saved.getUserAgent()).isNull();
        assertThat(saved.getCorrelationId()).isNull();
        assertThat(saved.getDetails()).isNull();
    }

    @Test
    void recordsCorrelationIdIpAndUserAgentOfTheCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "Backoffice/1.0");
        // A spoofable proxy header is never trusted for the audit ip.
        request.addHeader("X-Forwarded-For", "6.6.6.6");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");

        service.log("FEE_POLICY_UPDATED", "fee_policy", "p-1", null, "SUCCESS");

        AuditLog saved = captureSaved();
        assertThat(saved.getCorrelationId()).isEqualTo("corr-123");
        assertThat(saved.getIp()).isEqualTo("10.1.2.3");
        assertThat(saved.getUserAgent()).isEqualTo("Backoffice/1.0");
    }

    @Test
    void truncatesOversizedUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(2000));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.log("ZONE_CREATED", "zone", "z-1", null, "SUCCESS");

        assertThat(captureSaved().getUserAgent()).hasSize(512);
    }

    @Test
    void masksSecretValuesInDetailsAtAnyDepth() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("deliveryCode", "482913");
        nested.put("zoneName", "Centro");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("pin", "1234");
        details.put("accessToken", "eyJ...");
        details.put("clientSecret", "s3cr3t");
        details.put("password", "hunter2");
        details.put("otp", "000111");
        details.put("deliveryFee", 50);
        details.put("nested", nested);
        details.put("list", List.of(Map.of("pinCode", "9999")));

        service.log("FEE_POLICY_UPDATED", "fee_policy", "p-1", null, "SUCCESS", details);

        Map<String, Object> saved = captureSaved().getDetails();
        assertThat(saved).containsEntry("pin", "***").containsEntry("accessToken", "***").containsEntry("clientSecret", "***")
                .containsEntry("password", "***").containsEntry("otp", "***").containsEntry("deliveryFee", 50);
        assertThat((Map<String, Object>) saved.get("nested")).containsEntry("deliveryCode", "***").containsEntry("zoneName", "Centro");
        assertThat(saved.get("list").toString()).doesNotContain("9999");
        // The caller's map is not mutated.
        assertThat(details).containsEntry("pin", "1234");
    }

    @Test
    void logsForAnExplicitTenantWithoutTenantContext() {
        when(tenantContext.currentTenantId()).thenReturn(Optional.empty());
        UUID target = UUID.randomUUID();

        service.logForTenant(target, "TENANT_STATUS_CHANGED", "tenant", target.toString(), null, "SUCCESS", Map.of("status", "SUSPENDED"));

        AuditLog saved = captureSaved();
        assertThat(saved.getTenantId()).isEqualTo(target);
        assertThat(saved.getDetails()).containsEntry("status", "SUSPENDED");
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
